import React, {useEffect, useState} from "react";
import {useNavigate} from "react-router-dom";
import Header from "../../components/header/header.jsx";
import "./diario-sono.css";
import ScaleSelector from "../../components/scale-selector/scale-selector.jsx";

// funcao auxiliar pra pegar o id do usuario do token
function getUserIdFromToken() {
    const token = localStorage.getItem("authToken");
    if (!token) return null;
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        return payload.id;
    } catch (e) {
        console.error("Erro ao decodificar o token:", e);
        return null;
    }
}

// funcao auxiliar para formatar os minutos em horas
function formatMinutesToHours(totalMinutes) {
    if (isNaN(totalMinutes) || totalMinutes < 0) {
        return "0h 0m";
    }
    const hours = Math.floor(totalMinutes / 60);
    const minutes = Math.round(totalMinutes % 60);
    return `${hours}h ${minutes}m`;
}

export default function DiarioSono() {
    const navigate = useNavigate();
    const [data, setData] = useState({
        bedTime: "",
        wakeUpTime: "",
        timeInBed: 0,
        timeToFallAsleep: 0,
        timesWokenUp: 0,
        totalTimeAwakeDuringNight: 0,
        totalTimeAwake: 0,
        totalSleepTime: 0,
        isCommonDay: true,
        fatigue: 0,
        stress: 0,
        daytimeSleepiness: 0,
        inattention: 0,
        irritability: 0,
        pain: 0,
        healthPerception: 0,
        physicalActivityTime: 0,
        timeAwayFromHome: 0,
        usedSleepMedication: false,
        alcoholConsumption: 0,
        napsTime: 0,
        coffeeConsumption: 0,
        nighttimeSmoking: 0,
        comment: ""
    });

    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);

    // hook pra proteger a rota: se nao tiver logado, manda pra tela de login
    useEffect(() => {
        const token = localStorage.getItem("authToken");
        if (!token) {
            // alert("Você precisa estar logado para acessar esta página.");
            navigate("/login");
        }
    }, [navigate]);

    // hook pra calcular os totais de tempo automaticamente
    useEffect(() => {
        if (data.bedTime && data.wakeUpTime) {
            const bed = new Date(`1970-01-01T${data.bedTime}:00`);
            let wake = new Date(`1970-01-01T${data.wakeUpTime}:00`);

            // se o horario de acordar for antes do de dormir, assume que eh no dia seguinte
            if (wake < bed) {
                wake.setDate(wake.getDate() + 1);
            }

            const timeInBedMinutes = (wake - bed) / 60000;
            const timeToFallAsleepMinutes = Number(data.timeToFallAsleep) || 0;
            const timeAwakeDuringNightMinutes = Number(data.totalTimeAwakeDuringNight) || 0;
            const totalTimeAwakeMinutes = timeToFallAsleepMinutes + timeAwakeDuringNightMinutes;
            const totalSleepTimeMinutes = timeInBedMinutes - totalTimeAwakeMinutes;

            setData(prev => ({
                ...prev,
                timeInBed: timeInBedMinutes,
                totalTimeAwake: totalTimeAwakeMinutes,
                totalSleepTime: Math.max(0, totalSleepTimeMinutes)
            }));
        }
    }, [data.bedTime, data.wakeUpTime, data.timeToFallAsleep, data.totalTimeAwakeDuringNight]);

    const handleChange = (e) => {
        const {name, value, type, checked} = e.target;
        setData(prev => ({...prev, [name]: type === 'checkbox' ? checked : value}));
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsLoading(true);
        setError(null);

        const patientId = getUserIdFromToken();
        if (!patientId) {
            setError("Sua sessão expirou. Faça o login novamente.");
            setIsLoading(false);
            navigate('/login'); // redireciona pro login
            return;
        }

        // payload com os dados que o backend espera
        const payload = {
            patient: {id: patientId},
            assessmentDate: new Date().toISOString().split('T')[0],
            bedTime: data.bedTime,
            wakeUpTime: data.wakeUpTime,
            timeToFallAsleep: Number(data.timeToFallAsleep),
            timesWokenUp: Number(data.timesWokenUp),
            totalTimeAwakeDuringNight: Number(data.totalTimeAwakeDuringNight),
            isCommonDay: data.isCommonDay,
            fatigue: data.fatigue,
            stress: data.stress,
            daytimeSleepiness: data.daytimeSleepiness,
            inattention: data.inattention,
            irritability: data.irritability,
            pain: data.pain,
            healthPerception: data.healthPerception,
            physicalActivityTime: Number(data.physicalActivityTime),
            timeAwayFromHome: Number(data.timeAwayFromHome),
            usedSleepMedication: data.usedSleepMedication,
            alcoholConsumption: Number(data.alcoholConsumption),
            napsTime: Number(data.napsTime),
            coffeeConsumption: Number(data.coffeeConsumption),
            nighttimeSmoking: Number(data.nighttimeSmoking),
            // Adicione outros campos se o backend precisar, como o 'comment'
        };

        try {
            const token = localStorage.getItem("authToken");
            const response = await fetch("http://localhost:8080/registro-sono", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${token}`
                },
                body: JSON.stringify(payload)
            });

            // se o token for invalido ou expirou, o backend retorna 401 ou 403
            if (response.status === 401 || response.status === 403) {
                setError("Sua sessão expirou. Por favor, faça login novamente.");
                localStorage.removeItem("authToken"); // limpa o token velho
                navigate("/login");
                return;
            }

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText || "Falha ao salvar o diário de sono!");
            }

            // CORRIGIDO: loga a variavel 'payload' que existe
            console.log('Dados do diário de sono salvos:', payload);

            // o alert pausa a execucao aqui ate o usuario clicar em OK
            alert("Diário de sono salvo com sucesso!");

            // so depois do OK, a navegacao acontece
            navigate("/dashboard-paciente");

        } catch (err) {
            setError(err.message);
        } finally {
            setIsLoading(false);
        }
    };


    const handleBack = () => {
        navigate('/dashboard-paciente');
    };

    const handleCancel = () => {
        // usei o confirm do navegador, eh simples e funciona
        if (window.confirm('Tem certeza que deseja cancelar? Todos os dados serão perdidos.')) {
            navigate('/dashboard-paciente');
        }
    };

    return (
        <div>
            <Header
                showBackButton={true}
                backButtonText="Voltar"
                onBackClick={handleBack}/>
            <div className="diario-sono-page">
                <div className="diario-sono__content">
                    <div className="diario-sono__header">
                        <h1>Diário do Sono</h1>
                        <h2>Preencha os campos com as informações da sua última noite de sono</h2>
                    </div>
                    <form className="diario-sono__form" onSubmit={handleSubmit}>
                        {error && <p className="error-message">{error}</p>}
                        <div className="form-row">

                            {/* coluna 1: medicacao */}
                            <div className="form-group">
                                <label>Usou medicação para dormir?</label>
                                <label className="toggle-switch">
                                    <input
                                        type="checkbox"
                                        name="usedSleepMedication"
                                        checked={data.usedSleepMedication}
                                        onChange={handleChange}
                                    />
                                    <span className="slider"></span>
                                </label>
                            </div>
                            <div className="form-group">
                                <label>Foi um dia comum?</label>
                                <label className="toggle-switch">
                                    <input
                                        type="checkbox"
                                        name="isCommonDay"
                                        checked={data.isCommonDay}
                                        onChange={handleChange}
                                    />
                                    <span className="slider"></span>
                                </label>
                            </div>
                        </div>
                        <div className="form-row">
                            <div className="form-group">
                                <h3>Horário em que foi dormir</h3>
                                <label htmlFor="bedTime">Horário que apagou as luzes (ontem):</label>
                                <input type="time"
                                       id="bedTime"
                                       name="bedTime"
                                       value={data.bedTime}
                                       onChange={handleChange} required/>
                            </div>
                            <div className="form-group">
                                <h3>Horário em que se levantou</h3>
                                <label htmlFor="wakeUpTime">Horário em que saiu da cama (hoje):</label>
                                <input type="time"
                                       id="wakeUpTime"
                                       name="wakeUpTime"
                                       value={data.wakeUpTime}
                                       onChange={handleChange} required/>
                            </div>
                        </div>

                        <div className="form-row">
                            <div className="form-group">
                                <h3>Tempo na cama</h3>
                                <label>Tempo entre dormir e levantar:</label>
                                <input type="text"
                                       value={formatMinutesToHours(data.timeInBed)} readOnly
                                       className="readonly-input"/>
                            </div>
                            <div className="form-group">
                                <h3>Tempo para adormecer (minutos)</h3>
                                <label htmlFor="timeToFallAsleep">Tempo entre deitar e dormir:</label>
                                <input type="number"
                                       id="timeToFallAsleep"
                                       name="timeToFallAsleep"
                                       min="0"
                                       value={data.timeToFallAsleep} onChange={handleChange} required/>
                            </div>
                        </div>

                        <div className="form-row">
                            <div className="form-group">
                                <h3>Número de vezes que acordou</h3>
                                <label htmlFor="timesWokenUp">Conte as vezes que acordou:</label>
                                <input type="number"
                                       id="timesWokenUp"
                                       name="timesWokenUp"
                                       min="0"
                                       value={data.timesWokenUp}
                                       onChange={handleChange} required/>
                            </div>
                            <div className="form-group">
                                <h3>Duração acordado à noite (minutos)</h3>
                                <label htmlFor="totalTimeAwakeDuringNight">Some os minutos em que acordou:</label>
                                <input type="number"
                                       id="totalTimeAwakeDuringNight"
                                       name="totalTimeAwakeDuringNight"
                                       min="0"
                                       value={data.totalTimeAwakeDuringNight} onChange={handleChange} required/>
                            </div>
                        </div>

                        <div className="form-row">
                            <div className="form-group">
                                <h3>Total acordado no horário do sono</h3>
                                <label>Tempo antes de dormir + tempo acordado:</label>
                                <input type="text" value={formatMinutesToHours(data.totalTimeAwake)} readOnly
                                       className="readonly-input"/>
                            </div>
                            <div className="form-group">
                                <h3>Tempo total de sono</h3>
                                <label>Diferença entre o tempo na cama e o tempo total acordado:</label>
                                <input type="text" value={formatMinutesToHours(data.totalSleepTime)} readOnly
                                       className="readonly-input"/>
                            </div>
                        </div>
                        <div className="form-row">
                            <div className="form-group">
                                <h3>Tempo em atividades físicas (minutos)</h3>
                                <label htmlFor="physicalActivityTime">Atividades como caminhada ou hidro:</label>
                                <input
                                    type="number"
                                    id="physicalActivityTime"
                                    name="physicalActivityTime"
                                    value={data.physicalActivityTime}
                                    onChange={handleChange}
                                    min="0"
                                    placeholder="Ex: 30"
                                />
                            </div>
                            <div className="form-group">
                                <h3>Doses de bebida alcoólica</h3>
                                <label htmlFor="alcoholConsumption">Anote número de doses (1 dose= 300ml
                                    cerveja):</label>
                                <input
                                    type="number"
                                    id="alcoholConsumption"
                                    name="alcoholConsumption"
                                    value={data.alcoholConsumption}
                                    onChange={handleChange}
                                    min="0"
                                    placeholder="Ex: 1"
                                />
                            </div>
                        </div>
                        <div className="form-row">
                            <div className="form-group">
                                <h3>Cochilos durante o dia (minutos)</h3>
                                <label htmlFor="napsTime">Some os minutos em que você cochilou:</label>
                                <input
                                    type="number"
                                    id="napsTime"
                                    name="napsTime"
                                    value={data.napsTime}
                                    onChange={handleChange}
                                    min="0"
                                    placeholder="Ex: 40"
                                />
                            </div>
                            <div className="form-group">
                                <h3>Café</h3>
                                <label htmlFor="coffeeConsumption">Anote o número de xícaras de café ingerido:</label>
                                <input
                                    type="number"
                                    id="coffeeConsumption"
                                    name="coffeeConsumption"
                                    value={data.coffeeConsumption}
                                    onChange={handleChange}
                                    min="0"
                                    placeholder="Ex: 1"
                                />
                            </div>
                        </div>
                        <div className="form-row">
                            <div className="form-group">
                                <h3>Cigarro à noite</h3>
                                <label htmlFor="nighttimeSmoking">Anote o número cigarros fumados durante a
                                    noite:</label>
                                <input
                                    type="number"
                                    id="nighttimeSmoking"
                                    name="nighttimeSmoking"
                                    value={data.nighttimeSmoking}
                                    onChange={handleChange}
                                    min="0"
                                    placeholder="Ex: 1"
                                />
                            </div>
                            <div className="form-group">
                                <h3>Tempo fora de casa (horas)</h3>
                                <label htmlFor="timeAwayFromHome">Horas que passou fora de casa:</label>
                                <input
                                    type="number"
                                    id="timeAwayFromHome"
                                    name="timeAwayFromHome"
                                    value={data.timeAwayFromHome}
                                    onChange={handleChange}
                                    min="0"
                                    placeholder="Ex: 8"
                                />
                            </div>
                        </div>

                        <div className="diario-sono__form__group">
                            <h3>Cansaço</h3>
                            <ScaleSelector
                                leftLabel="Nenhum cansaço"
                                rightLabel="Muito cansaço"
                                count={7}
                                value={data.fatigue}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, fatigue: value}))
                                }
                            />
                        </div>
                        <div className="diario-sono__form__group">
                            <h3>Estresse</h3>
                            <ScaleSelector
                                leftLabel="Nenhum estresse"
                                rightLabel="Muito estresse"
                                value={data.stress}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, stress: value}))
                                }
                            />
                        </div>
                        <div className="diario-sono__form__group">
                            <h3>Sonolência durante o dia</h3>
                            <ScaleSelector
                                leftLabel="Nenhuma sonolência"
                                rightLabel="Muita sonolência"
                                value={data.daytimeSleepiness}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, daytimeSleepiness: value}))
                                }
                            />
                        </div>
                        <div className="diario-sono__form__group">
                            <h3>Desatenção</h3>
                            <ScaleSelector
                                leftLabel="Nenhuma desatenção"
                                rightLabel="Muita desatenção"
                                value={data.inattention}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, inattention: value}))
                                }
                            />
                        </div>
                        <div className="diario-sono__form__group">
                            <h3>Irritabilidade</h3>
                            <ScaleSelector
                                leftLabel="Nenhuma irritabilidade"
                                rightLabel="Muita irritabilidade"
                                value={data.irritability}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, irritability: value}))
                                }
                            />
                        </div>
                        <div className="diario-sono__form__group">
                            <h3>Dor</h3>
                            <ScaleSelector
                                leftLabel="Nenhuma dor"
                                rightLabel="Muita dor"
                                value={data.pain}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, pain: value}))
                                }
                            />
                        </div>
                        <div className="diario-sono__form__group">
                            <h3>Saúde</h3>
                            <ScaleSelector
                                leftLabel="Sinto-me bem"
                                rightLabel="Mal"
                                value={data.healthPerception}
                                onChangeValue={(value) =>
                                    setData((prev) => ({
                                        ...prev,
                                        healthPerception: value,
                                    }))
                                }
                            />
                        </div>
                        <div className="diario-sono__form__group">
                            <h3>Anotações</h3>
                            <textarea
                                id="anotacao"
                                name="comment"
                                placeholder="Escreva aqui qualquer ponto que julgar relevante"
                                rows="4"
                                value={data.comment}
                                onChange={handleChange}
                            ></textarea>
                        </div>
                        <div className="diario-sono_end">
                            <button type="button" className="button-secondary" onClick={handleCancel}>
                                Cancelar
                            </button>
                            <button type="submit" className="button" disabled={isLoading}>
                                {isLoading ? "Salvando..." : "Salvar"}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
}
