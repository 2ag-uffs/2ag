import React, {useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import './anamnese.css';
import '../../styles/colors.css';
import '../../styles/fonts.css';
import '../../styles/button.css';
import '../../styles/input.css';
import Header from "../../components/header/header.jsx";

// funcao auxiliar pra pegar os dados do usuario do token
function getUserDataFromToken() {
    const token = localStorage.getItem("authToken");
    if (!token) return null;
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        // retorna o id e o nome
        return {id: payload.id, name: payload.name};
    } catch (e) {
        console.error("Erro ao decodificar o token:", e);
        return null;
    }
}

export default function Anamnese() {
    const navigate = useNavigate();
    const [userData, setUserData] = useState(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);

    const [formData, setFormData] = useState({
        assessmentDate: new Date().toISOString().split('T')[0],
        profession: '',
        reasonForVisit: '',
        previousDiagnosis: '',
        previousTreatment: '',
        currentMedication: '',
        diet: '',
        smokingHabits: 'Não',
        alcoholConsumption: 'Não',
        weight: '',
        height: '',
        substanceUse: '',
        physicalActivity: '',
        sleepHabits: '',
        anxiety: '',
        pain: '',
        familyHistory: '',
        adverseReaction: '',
        geneticCondition: '',
        expectations: '',
        treatmentAwareness: '',
        observation: '',
    });

    // protege a rota e pega dados do usuario
    useEffect(() => {
        const user = getUserDataFromToken();
        if (!user) {
            navigate("/login");
        } else {
            setUserData(user);
        }
    }, [navigate]);

    const handleChange = (e) => {
        const {name, value} = e.target;
        setFormData(prev => ({...prev, [name]: value}));
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsLoading(true);
        setError(null);

        if (!userData?.id) {
            setError("Sua sessão expirou. Faça o login novamente!");
            setIsLoading(false);
            navigate('/login');
            return;
        }

        // mapeio os nomes do front para o back
        const payload = {
            patient: {id: userData.id},
            assessmentDate: formData.assessmentDate,
            profession: formData.profession,
            reasonForVisit: formData.reasonForVisit,
            previousDiagnosis: formData.previousDiagnosis,
            previousTreatment: formData.previousTreatment,
            currentMedication: formData.currentMedication,
            diet: formData.diet,
            smokingHabits: formData.smokingHabits,
            alcoholConsumption: formData.alcoholConsumption,
            weight: formData.weight,
            height: formData.height,
            substanceUse: formData.substanceUse,
            physicalActivity: formData.physicalActivity,
            sleepHabits: formData.sleepHabits,
            anxiety: formData.anxiety,
            pain: formData.pain,
            familyHistory: formData.familyHistory,
            adverseReaction: formData.adverseReaction,
            geneticCondition: formData.geneticCondition,
            expectations: formData.expectations,
            treatmentAwareness: formData.treatmentAwareness === 'Sim' ? true : (formData.isAwareOfMonitoring === 'Não' ? false : null),
            observation: formData.observation,
        };

        try {
            const token = localStorage.getItem("authToken");
            const response = await fetch("http://localhost:8080/anamnese", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${token}`
                },
                body: JSON.stringify(payload)
            });

            if (response.status === 401 || response.status === 403) {
                throw new Error("Sua sessão expirou. Por favor, faça o login novamente!");
            }
            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText || "Falha ao enviar a ficha de anamnese :c");
            }

            alert('Ficha de Anamnese enviada com sucesso!');
            navigate('/dashboard-paciente');

        } catch (err) {
            setError(err.message);
            if (err.message.includes("sessão expirou")) {
                localStorage.removeItem("authToken");
                navigate('/login');
            }
        } finally {
            setIsLoading(false);
        }
    };

    const handleBack = () => {
        navigate(-1);
    };

    const handleCancel = () => {
        if (window.confirm('Tem certeza que deseja cancelar? Todos os dados preenchidos serão perdidos')) {
            navigate('/dashboard-paciente');
        }
    };

    return (
        <div className="anamnese-page">
            <Header
                title={userData?.name || "Carregando..."}
                showBackButton={true}
                backButtonText="Voltar"
                onBackClick={handleBack}
            />

            <main className="anamnese-main">
                <form className="anamnese-form" onSubmit={handleSubmit}>
                    <div className="anamnese-intro">
                        <h2>Bem-vindo(a) à sua avaliação inicial!</h2>
                        <p>
                            Esta ficha de anamnese tem como objetivo reunir informações para garantir um tratamento mais
                            seguro e eficaz, adaptado às suas necessidades individuais. Agradecemos por dedicar seu
                            tempo para preenche-la. Se tiver dúvidas durante o preenchimento, não hesite em nos contatar
                            para assistência!
                        </p>
                    </div>
                    {error &&
                        <p className="error-message" style={{textAlign: 'center', marginBottom: '1rem'}}>{error}</p>}
                    <div className="form-section">
                        <h3>Informações Gerais</h3>
                        <div className="form-grid">
                            <div className="form-group">
                                <label htmlFor="assessmentDate">Data de preenchimento</label>
                                <input type="date" id="assessmentDate" name="assessmentDate"
                                       value={formData.assessmentDate} onChange={handleChange}/>
                            </div>
                            <div className="form-group span-2">
                                <label htmlFor="profession">Ocupação</label>
                                <input type="text" id="profession" name="profession" value={formData.profession}
                                       onChange={handleChange}/>
                            </div>
                            <div className="form-group span-3">
                                <label htmlFor="reasonForVisit">Motivo principal da consulta</label>
                                <textarea id="reasonForVisit" name="reasonForVisit" value={formData.reasonForVisit}
                                          onChange={handleChange} rows="3"></textarea>
                            </div>
                        </div>
                    </div>

                    <div className="form-section">
                        <h3>Histórico de Saúde</h3>
                        <div className="form-grid">
                            <div className="form-group span-3">
                                <label htmlFor="previousDiagnosis">Diagnóstico(s) prévio(s)</label>
                                <input type="text" id="previousDiagnosis" name="previousDiagnosis"
                                       value={formData.previousDiagnosis} onChange={handleChange}/>
                            </div>
                            <div className="form-group span-3">
                                <label htmlFor="previousTreatment">Tratamentos anteriores</label>
                                <textarea id="previousTreatment" name="previousTreatment"
                                          value={formData.previousTreatment} onChange={handleChange}
                                          rows="3"></textarea>
                            </div>
                            <div className="form-group span-3">
                                <label htmlFor="currentMedication">Medicações em uso</label>
                                <textarea id="currentMedication" name="currentMedication"
                                          value={formData.currentMedication} onChange={handleChange}
                                          rows="3"></textarea>
                            </div>
                            <div className="form-group span-3">
                                <label htmlFor="familyHistory">Histórico familiar de doenças relevantes</label>
                                <input type="text" id="familyHistory" name="familyHistory"
                                       value={formData.familyHistory} onChange={handleChange}/>
                            </div>
                            <div className="form-group span-3">
                                <label htmlFor="adverseReaction">Reações adversas a medicamentos</label>
                                <input type="text" id="adverseReaction" name="adverseReaction"
                                       value={formData.adverseReaction} onChange={handleChange}/>
                            </div>
                            <div className="form-group span-3">
                                <label htmlFor="geneticCondition">Condições genéticas conhecidas</label>
                                <input type="text" id="geneticCondition" name="geneticCondition"
                                       value={formData.geneticCondition} onChange={handleChange}/>
                            </div>
                        </div>
                    </div>
                    <div className="form-section">
                        <h3>Hábitos e Estilo de Vida</h3>
                        <div className="form-grid">
                            <div className="form-group span-3">
                                <label htmlFor="diet">Tipo de dieta</label>
                                <input type="text" id="diet" name="diet"
                                       placeholder="Ex: onívora, vegetariana, vegana, etc." value={formData.diet}
                                       onChange={handleChange}/>
                            </div>
                            <div className="form-group">
                                <label htmlFor="smokingHabits">Hábito de fumar</label>
                                <select id="smokingHabits" name="smokingHabits" value={formData.smokingHabits}
                                        onChange={handleChange}>
                                    <option>Não</option>
                                    <option>Sim, diariamente</option>
                                    <option>Sim, ocasionalmente</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label htmlFor="alcoholConsumption">Consumo de álcool</label>
                                <select id="alcoholConsumption" name="alcoholConsumption"
                                        value={formData.alcoholConsumption} onChange={handleChange}>
                                    <option>Não</option>
                                    <option>Sim, socialmente</option>
                                    <option>Sim, frequentemente</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label htmlFor="weight">Peso (kg)</label>
                                <input type="number" id="weight" name="weight" placeholder="Ex: 70.5"
                                       value={formData.weight} onChange={handleChange}/>
                            </div>
                            <div className="form-group">
                                <label htmlFor="height">Altura (cm)</label>
                                <input type="number" id="height" name="height" placeholder="Ex: 175"
                                       value={formData.height} onChange={handleChange}/>
                            </div>
                            <div className="form-group span-2">
                                <label htmlFor="substanceUse">Uso de outras substâncias recreativas</label>
                                <input type="text" id="substanceUse" name="substanceUse" value={formData.substanceUse}
                                       onChange={handleChange}/>
                            </div>
                            <div className="form-group span-3">
                                <label htmlFor="physicalActivity">Prática de exercícios físicos (descreva frequência e
                                    tipo)</label>
                                <input type="text" id="physicalActivity" name="physicalActivity"
                                       value={formData.physicalActivity} onChange={handleChange}/>
                            </div>
                        </div>
                    </div>
                    <div className="form-section">
                        <h3>Sintomas e Queixas Atuais</h3>
                        <div className="form-grid">
                            <div className="form-group span-3">
                                <label htmlFor="sleepHabits">Qualidade do sono</label>
                                <input type="text" id="sleepHabits" name="sleepHabits"
                                       placeholder="Ex: durmo bem, tenho insônia, acordo várias vezes, etc."
                                       value={formData.sleepHabits} onChange={handleChange}/>
                            </div>
                            <div className="form-group span-3">
                                <label htmlFor="anxiety">Presença de ansiedade (descreva frequência e
                                    intensidade)</label>
                                <input type="text" id="anxiety" name="anxiety" value={formData.anxiety}
                                       onChange={handleChange}/>
                            </div>
                            <div className="form-group span-3">
                                <label htmlFor="pain">Presença de dor (descreva local, frequência e intensidade)</label>
                                <input type="text" id="pain" name="pain" value={formData.pain} onChange={handleChange}/>
                            </div>
                        </div>
                    </div>
                    <div className="form-section">
                        <h3>Tratamento</h3>
                        <div className="form-grid">
                            <div className="form-group span-3">
                                <label htmlFor="expectations">Expectativas com o tratamento</label>
                                <textarea id="expectations" name="expectations" value={formData.expectations}
                                          onChange={handleChange} rows="3"></textarea>
                            </div>
                            <div className="form-group span-3">
                                <label htmlFor="treatmentAwareness">Está ciente de que o tratamento deve ser monitorado
                                    regularmente?</label>
                                <select id="treatmentAwareness" name="treatmentAwareness"
                                        value={formData.treatmentAwareness} onChange={handleChange} required>
                                    <option value="" disabled>Selecione uma opção</option>
                                    <option>Sim</option>
                                    <option>Não</option>
                                </select>
                            </div>
                            <div className="form-group span-3">
                                <label htmlFor="observation">Observações gerais</label>
                                <textarea id="observation" name="observation"
                                          placeholder="Espaço para informações adicionais que julgar importantes."
                                          value={formData.observation} onChange={handleChange} rows="4"></textarea>
                            </div>
                        </div>
                    </div>
                    <div className="anamnese-actions">
                        <button type="button" className="button-secondary" onClick={handleCancel}>
                            Cancelar
                        </button>
                        <button type="submit" className="button" disabled={isLoading}>
                            {isLoading ? "Enviando..." : "Enviar Anamnese"}
                        </button>
                    </div>
                </form>
            </main>
        </div>
    );
}