import {useEffect, useState} from "react";
import ScaleSelector from "../../components/scale-selector/scale-selector.jsx";
import "./acompanhamento-semanal-paciente.css";
import {useNavigate} from "react-router";
import Header from "../../components/header/header.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";

export default function AcompanhamentoSemanalPaciente() {
    const navigate = useNavigate();
    const [data, setData] = useState({
        morningDrops: "",
        afternoonDrops: "",
        pain: 0,
        sleep: 0,
        mood: 0,
        shaking: 0,
        anxiety: 0,
        energy: 0,
        bowelFunction: 0,
        apetite: 0,
        focus: 0,
        socialInteraction: 0,
        rigidity: 0,
        substance: 0,
        sport: 0,
        vomit: 0,
        dermat: 0,
        comment: ""
    });

    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);

    // se n tiver logado manda pra tela de login
    useEffect(() => {
        if (!getLoggedUser()) {
            navigate("/login");
        }
    }, [navigate]);


    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsLoading(true);
        setError(null);

        const patientId = getLoggedUser()?.id;
        if (!patientId) {
            setError("Sua sessão expirou. Faça o login novamente.");
            setIsLoading(false);
            navigate('/login'); // redireciona pro login
            return;
        }

        // payload com os nomes corretos que o backend espera
        const payload = {
            assessmentDate: new Date().toISOString().split('T')[0],
            patient: {id: patientId},
            morningDrops: Number(data.morningDrops) || 0,
            afternoonDrops: Number(data.afternoonDrops) || 0,
            pain: data.pain,
            sleep: data.sleep,
            mood: data.mood,
            tremor: data.shaking,
            anxiety: data.anxiety,
            disposition: data.energy,
            intestinalFunction: data.bowelFunction,
            appetite: data.apetite,
            concentration: data.focus,
            socialInteraction: data.socialInteraction,
            rigiditySpasticity: data.rigidity,
            substanceReduction: data.substance,
            sportsPerformance: data.sport,
            nausea: data.vomit,
            dermatologicalDisease: data.dermat,
            comment: data.comment
        };

        try {
            await apiService.post("/acompanhamento", payload);
            navigate("/dashboard-paciente", {state: {aviso: "Acompanhamento salvo."}});
        } catch (err) {
            setError(err instanceof ApiError ? err.message : "Falha ao salvar o acompanhamento.");
        } finally {
            setIsLoading(false);
        }
    };

    const handleBack = () => {
        navigate(-1); // volta pra pagina anterior
    };

    const handleCancel = () => {
        if (window.confirm('Tem certeza que deseja cancelar? Todos os dados serão perdidos')) {
            navigate('/dashboard-paciente');
        }
    };

    return (
        <div>
            <Header
                showBackButton={true}
                backButtonText="Voltar"
                onBackClick={handleBack}
            />
            <div className="acompanhamento-paciente-page">
                <div className="acompanhamento-paciente__content">
                    <div className="acompanhamento-paciente__header">
                        <h1>Acompanhamento Semanal</h1>
                        <h2>Registre como foi sua semana nos últimos 7 dias:</h2>
                    </div>
                    <form className="acompanhamento-paciente__form" onSubmit={handleSubmit}>
                        {error && <p className="error-message">{error}</p>}
                        <div className="acompanhamento-paciente__form__row">
                            <div>
                                <label htmlFor="morningDrops">Nº gotas manhã</label>
                                <input
                                    id="morningDrops"
                                    name="morningDrops"
                                    onChange={(e) => setData(prev => ({...prev, morningDrops: e.target.value}))}
                                    placeholder="Ex: 2"
                                    type="number"
                                    min="0"
                                    value={data.morningDrops}
                                />
                            </div>
                            <div>
                                <label htmlFor="afternoonDrops">
                                    Nº gotas tarde
                                </label>
                                <input
                                    id="afternoonDrops"
                                    name="afternoonDrops"
                                    onChange={(e) => setData(prev => ({...prev, afternoonDrops: e.target.value}))}
                                    placeholder="Ex: 2"
                                    type="number"
                                    min="0"
                                    value={data.afternoonDrops}
                                />
                            </div>
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Dor</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Sem dor"
                                rightLabel="Dor intensa"
                                value={data.pain}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, pain: value}))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Sono</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Excelente"
                                rightLabel="Muito ruim"
                                value={data.sleep}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, sleep: value}))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Humor</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Muito positivo"
                                rightLabel="Deprimido"
                                value={data.mood}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, mood: value}))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Tremor</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Ausente"
                                rightLabel="Grave"
                                value={data.shaking}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, shaking: value}))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Ansiedade</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Tranquilo"
                                rightLabel="Muito ansioso"
                                value={data.anxiety}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, anxiety: value}))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Disposição/Energia</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Muito enérgico"
                                rightLabel="Sem energia"
                                value={data.energy}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, energy: value}))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Função Intestinal</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Normal"
                                rightLabel="Irregular"
                                value={data.bowelFunction}
                                onChangeValue={(value) =>
                                    setData((prev) => ({
                                        ...prev,
                                        bowelFunction: value,
                                    }))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Apetite</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Apetite saudável"
                                rightLabel="Alteração no apetite"
                                value={data.apetite}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, apetite: value}))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Concentração</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Excelente"
                                rightLabel="Muito baixa"
                                value={data.focus}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, focus: value}))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Interação social</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Muito social"
                                rightLabel="Isolado"
                                value={data.socialInteraction}
                                onChangeValue={(value) =>
                                    setData((prev) => ({
                                        ...prev,
                                        socialInteraction: value,
                                    }))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Rigidez/Espasticidade</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Nenhuma"
                                rightLabel="Intensa"
                                value={data.rigidity}
                                onChangeValue={(value) =>
                                    setData((prev) => ({
                                        ...prev,
                                        rigidity: value,
                                    }))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Diminuição de Maconha Fumada/Outra Substância</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Completa"
                                rightLabel="Nenhuma"
                                value={data.substance}
                                onChangeValue={(value) =>
                                    setData((prev) => ({
                                        ...prev,
                                        substance: value,
                                    }))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Náusea e Vômito</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Ausente"
                                rightLabel="Frequente"
                                value={data.vomit}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, vomit: value}))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Performance no Esporte</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Ótimo desempenho"
                                rightLabel="Baixo desempeho"
                                value={data.sport}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, sport: value}))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Doença dermatológica</h3>
                            <ScaleSelector
                                    minValue={0}
                                    maxValue={10}
                                leftLabel="Nenhuma"
                                rightLabel="Intensa"
                                value={data.dermat}
                                onChangeValue={(value) =>
                                    setData((prev) => ({...prev, dermat: value}))
                                }
                            />
                        </div>
                        <div className="acompanhamento-paciente__form__group">
                            <h3>Anotações</h3>
                            <textarea
                                id="anotacao"
                                name="comment"
                                placeholder="Escreva aqui qualquer ponto que julgar relevante"
                                rows="4"
                                value={data.comment}
                                onChange={(e) => setData(prev => ({...prev, comment: e.target.value}))}
                            ></textarea>
                        </div>
                        <div className="acompanhamento-paciente_end">
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
