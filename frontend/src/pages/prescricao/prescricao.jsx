import "../../styles/colors.css";
import "../../styles/fonts.css";
import "../../styles/button.css";
import "../../styles/input.css";
import "./prescricao.css";
import { useNavigate } from "react-router";
import Header from "../../components/header/header.jsx";
import React, { useState, useEffect } from "react";

// Função auxiliar para decodificar JWT
function parseJwt(token) {
    try {
        return JSON.parse(atob(token.split('.')[1]));
    } catch (e) {
        return null;
    }
}

export default function Prescricao() {
    const navigate = useNavigate();

    // Estados para gerenciar dados
    const [patient, setPatient] = useState(null);
    const [prescriber, setPrescriber] = useState(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);

    // Estados para os dados da prescrição
    const [prescriptionData, setPrescriptionData] = useState({
        dataPrescricao: new Date().toISOString().split('T')[0],
        formulacao: '',
        concentracao: '',
        volume: '',
        dosagem: '',
        frequencia: '',
        horario: '',
        viaAdministracao: '',
        instrucoes: '',
        precaucoes: '',
        efeitosEsperados: '',
        proximaConsulta: '',
        duracaoTratamento: '',
        escalasAcompanhamento: []
    });

    // Estados para protocolo de escalonamento
    const [escalonamento, setEscalonamento] = useState([
        { semana: 1, dosagem: '', observacao: '' },
        { semana: 2, dosagem: '', observacao: '' }
    ]);

    // Carregar dados do prescritor e paciente ao montar o componente
    useEffect(() => {
        const loadInitialData = async () => {
            try {
                const token = localStorage.getItem("authToken");
                const decodedToken = parseJwt(token);
                const prescriberId = decodedToken?.id;

                if (prescriberId) {
                    const prescriberData = await apiService.get(`/prescritor/${prescriberId}`);
                    setPrescriber(prescriberData);
                }


            } catch (err) {
                setError("Erro ao carregar dados iniciais");
                console.error(err);
            }
        };

        loadInitialData();
    }, []);

    // Função para atualizar dados da prescrição
    const handleInputChange = (field, value) => {
        setPrescriptionData(prev => ({
            ...prev,
            [field]: value
        }));
    };

    // Função para atualizar escalonamento
    const handleEscalonamentoChange = (index, field, value) => {
        setEscalonamento(prev => {
            const updated = [...prev];
            updated[index] = { ...updated[index], [field]: value };
            return updated;
        });
    };

    // Função para adicionar nova semana ao escalonamento
    const addEscalonamentoWeek = () => {
        setEscalonamento(prev => [
            ...prev,
            { semana: prev.length + 1, dosagem: '', observacao: '' }
        ]);
    };

    // Função para submeter a prescrição
    const handleSubmitPrescription = async (e) => {
        e.preventDefault();
        setIsLoading(true);
        setError(null);

        try {
            const token = localStorage.getItem("authToken");
            const decodedToken = parseJwt(token);
            const prescriberId = decodedToken?.id;

            if (!prescriberId || !patient) {
                throw new Error("Dados do prescritor ou paciente não encontrados");
            }

            // Preparar dados para envio
            const prescriptionPayload = {
                patientId: patient.id,
                prescriberId: prescriberId,
                productDescription: `${prescriptionData.formulacao} - ${prescriptionData.concentracao}% - ${prescriptionData.volume}ml`,
                posology: `${prescriptionData.dosagem} - ${prescriptionData.frequencia} - ${prescriptionData.horario}`,
                brand: "Marca Padrão", // TODO: Implementar seleção de marca
                concentration: prescriptionData.concentracao + "%",
                spectrum: prescriptionData.formulacao,
                observation: `Instruções: ${prescriptionData.instrucoes}\nPrecauções: ${prescriptionData.precaucoes}\nEfeitos Esperados: ${prescriptionData.efeitosEsperados}`,
                prescriptionDate: prescriptionData.dataPrescricao,
                nextConsultation: prescriptionData.proximaConsulta,
                treatmentDuration: prescriptionData.duracaoTratamento,
                escalationProtocol: escalonamento
            };

            await apiService.post("/prescricao", prescriptionPayload);

            alert("Prescrição criada com sucesso!");
            navigate("/dashboard-prescritor");

        } catch (err) {
            setError(err.message || "Erro ao criar prescrição");
            console.error("Erro ao criar prescrição:", err);
        } finally {
            setIsLoading(false);
        }
    };

    const handleReturnMenu = (e) => {
        e.preventDefault();
        navigate("/dashboard-prescritor");
    };

    const handleBack = () => {
        navigate(-1);
    };

    return (
        <div className="prescricao">
            <Header
                title={prescriber ? `Dr. ${prescriber.name} - ${prescriber.registryType} ${prescriber.registryNumber}` : "Carregando..."}
                showBackButton={true}
                backButtonText="Voltar"
                onBackClick={handleBack}
            />
            <main className="prescricao-main">
                <div className="precricao-title">
                    <h1>Nova Prescrição</h1>
                    <p>{patient ? `${patient.name} - Consulta: ${new Date().toLocaleDateString('pt-BR')}` : "Carregando dados do paciente..."}</p>
                </div>

                {/* Exibição de erro */}
                {error && (
                    <div style={{ color: 'red', padding: '10px', backgroundColor: '#ffe6e6', borderRadius: '4px', margin: '10px 0' }}>
                        {error}
                    </div>
                )}

                <div className="prescricao-layout">
                    <aside className="prescricao-sidebar">
                        <div className="paciente-card">
                            <div className="paciente-avatar">
                                {patient ? patient.name.split(' ').map(n => n[0]).join('').substring(0, 2) : "..."}
                            </div>
                            <div className="paciente-info">
                                <h2>{patient ? patient.name : "Carregando..."}</h2>
                                <p>{patient ? `${patient.age} anos • ${patient.gender}` : "..."}</p>
                                <p>{patient ? `Peso: ${patient.weight} • Altura: ${patient.height}` : "..."}</p>
                            </div>
                        </div>
                    </aside>

                    {/* Formulário de prescrição */}
                    <div className="prescricao-content">
                        <form className="prescricao-form" onSubmit={handleSubmitPrescription}>
                            <div className="form-section">
                                <h3>Informações da Prescrição</h3>

                                <div className="form-row">
                                    <div className="form-group">
                                        <label htmlFor="numero-controle">Número de Controle</label>
                                        <input
                                            id="numero-controle"
                                            type="text"
                                            value={`2025-${String(Math.floor(Math.random() * 1000)).padStart(3, '0')}`}
                                            readOnly
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label htmlFor="data-prescricao">Data da Prescrição</label>
                                        <input
                                            id="data-prescricao"
                                            type="date"
                                            value={prescriptionData.dataPrescricao}
                                            onChange={(e) => handleInputChange('dataPrescricao', e.target.value)}
                                        />
                                    </div>
                                </div>
                            </div>

                            <div className="form-section">
                                <h3>Medicamento</h3>

                                <div className="form-group">
                                    <label htmlFor="formulacao">Formulação</label>
                                    <select
                                        id="formulacao"
                                        value={prescriptionData.formulacao}
                                        onChange={(e) => handleInputChange('formulacao', e.target.value)}
                                    >
                                        <option value="">Selecione a formulação</option>
                                        <option value="CBD Isolado">CBD Isolado</option>
                                        <option value="CBD Broad Spectrum">CBD Broad Spectrum</option>
                                        <option value="CBD Full Spectrum">CBD Full Spectrum</option>
                                        <option value="THC Isolado">THC Isolado</option>
                                        <option value="THC + CBD">THC + CBD</option>
                                    </select>
                                </div>

                                <div className="form-row">
                                    <div className="form-group">
                                        <label htmlFor="concentracao">Concentração (%)</label>
                                        <select
                                            id="concentracao"
                                            value={prescriptionData.concentracao}
                                            onChange={(e) => handleInputChange('concentracao', e.target.value)}
                                        >
                                            <option value="">Selecione</option>
                                            <option value="2.5">2,5%</option>
                                            <option value="5">5%</option>
                                            <option value="10">10%</option>
                                            <option value="15">15%</option>
                                            <option value="20">20%</option>
                                        </select>
                                    </div>
                                    <div className="form-group">
                                        <label htmlFor="volume">Volume (ml)</label>
                                        <select
                                            id="volume"
                                            value={prescriptionData.volume}
                                            onChange={(e) => handleInputChange('volume', e.target.value)}
                                        >
                                            <option value="">Selecione</option>
                                            <option value="10">10ml</option>
                                            <option value="30">30ml</option>
                                            <option value="50">50ml</option>
                                        </select>
                                    </div>
                                </div>
                            </div>

                            <div className="form-section">
                                <h3>Posologia</h3>

                                <div className="form-row">
                                    <div className="form-group">
                                        <label htmlFor="dosagem">Dosagem Inicial</label>
                                        <input
                                            id="dosagem"
                                            type="text"
                                            placeholder="Ex: 2 gotas"
                                            value={prescriptionData.dosagem}
                                            onChange={(e) => handleInputChange('dosagem', e.target.value)}
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label htmlFor="frequencia">Frequência</label>
                                        <select
                                            id="frequencia"
                                            value={prescriptionData.frequencia}
                                            onChange={(e) => handleInputChange('frequencia', e.target.value)}
                                        >
                                            <option value="">Selecione</option>
                                            <option value="1x ao dia">1x ao dia</option>
                                            <option value="2x ao dia">2x ao dia</option>
                                            <option value="3x ao dia">3x ao dia</option>
                                            <option value="Se necessário">Se necessário</option>
                                        </select>
                                    </div>
                                    <div className="form-group">
                                        <label htmlFor="horario">Horário</label>
                                        <input
                                            id="horario"
                                            type="text"
                                            placeholder="Ex: Manhã e noite"
                                            value={prescriptionData.horario}
                                            onChange={(e) => handleInputChange('horario', e.target.value)}
                                        />
                                    </div>
                                </div>

                                <div className="form-group">
                                    <label htmlFor="via-administracao">Via de Administração</label>
                                    <select
                                        id="via-administracao"
                                        value={prescriptionData.viaAdministracao}
                                        onChange={(e) => handleInputChange('viaAdministracao', e.target.value)}
                                    >
                                        <option value="">Selecione</option>
                                        <option value="Sublingual">Sublingual</option>
                                        <option value="Oral">Oral</option>
                                        <option value="Tópica">Tópica</option>
                                    </select>
                                </div>
                            </div>

                            <div className="form-section">
                                <h3>Protocolo de Escalonamento</h3>

                                {escalonamento.map((item, index) => (
                                    <div key={index} className="escalonamento-item">
                                        <div className="form-row">
                                            <div className="form-group">
                                                <label htmlFor={`semana-${item.semana}`}>Semana {item.semana}</label>
                                                <input
                                                    id={`semana-${item.semana}`}
                                                    type="text"
                                                    placeholder="Ex: 1 gota, 2x ao dia"
                                                    value={item.dosagem}
                                                    onChange={(e) => handleEscalonamentoChange(index, 'dosagem', e.target.value)}
                                                />
                                            </div>
                                            <div className="form-group">
                                                <label htmlFor={`observacao-${item.semana}`}>Observação</label>
                                                <input
                                                    id={`observacao-${item.semana}`}
                                                    type="text"
                                                    placeholder="Observações para esta fase"
                                                    value={item.observacao}
                                                    onChange={(e) => handleEscalonamentoChange(index, 'observacao', e.target.value)}
                                                />
                                            </div>
                                        </div>
                                    </div>
                                ))}

                                <button type="button" className="button-secondary" onClick={addEscalonamentoWeek}>
                                    + Adicionar Semana
                                </button>
                            </div>

                            <div className="form-section">
                                <h3>Instruções Específicas</h3>

                                <div className="form-group">
                                    <label htmlFor="instrucoes">Instruções de Uso</label>
                                    <textarea
                                        id="instrucoes"
                                        rows="4"
                                        placeholder="Instruções detalhadas para o paciente sobre como usar o medicamento..."
                                        value={prescriptionData.instrucoes}
                                        onChange={(e) => handleInputChange('instrucoes', e.target.value)}
                                    ></textarea>
                                </div>

                                <div className="form-group">
                                    <label htmlFor="precaucoes">Precauções e Contraindicações</label>
                                    <textarea
                                        id="precaucoes"
                                        rows="3"
                                        placeholder="Precauções importantes e contraindicações..."
                                        value={prescriptionData.precaucoes}
                                        onChange={(e) => handleInputChange('precaucoes', e.target.value)}
                                    ></textarea>
                                </div>

                                <div className="form-group">
                                    <label htmlFor="efeitos-esperados">Efeitos Esperados</label>
                                    <textarea
                                        id="efeitos-esperados"
                                        rows="3"
                                        placeholder="Descreva os efeitos esperados do tratamento..."
                                        value={prescriptionData.efeitosEsperados}
                                        onChange={(e) => handleInputChange('efeitosEsperados', e.target.value)}
                                    ></textarea>
                                </div>
                            </div>

                            <div className="form-section">
                                <h3>Acompanhamento</h3>

                                <div className="form-row">
                                    <div className="form-group">
                                        <label htmlFor="proxima-consulta">Próxima Consulta</label>
                                        <input
                                            id="proxima-consulta"
                                            type="date"
                                            value={prescriptionData.proximaConsulta}
                                            onChange={(e) => handleInputChange('proximaConsulta', e.target.value)}
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label htmlFor="duracao-tratamento">Duração do Tratamento</label>
                                        <select
                                            id="duracao-tratamento"
                                            value={prescriptionData.duracaoTratamento}
                                            onChange={(e) => handleInputChange('duracaoTratamento', e.target.value)}
                                        >
                                            <option value="">Selecione</option>
                                            <option value="30 dias">30 dias</option>
                                            <option value="60 dias">60 dias</option>
                                            <option value="90 dias">90 dias</option>
                                            <option value="Contínuo">Contínuo</option>
                                        </select>
                                    </div>
                                </div>

                                <div className="form-group">
                                    <label htmlFor="escalas-acompanhamento">Escalas de Acompanhamento</label>
                                    <div className="checkbox-group">
                                        <label className="checkbox-item">
                                            <input type="checkbox" />
                                            <span>Escala de Hamilton (Ansiedade)</span>
                                        </label>
                                        <label className="checkbox-item">
                                            <input type="checkbox" />
                                            <span>Índice de Qualidade do Sono</span>
                                        </label>
                                        <label className="checkbox-item">
                                            <input type="checkbox" />
                                            <span>Escala de Beck (Depressão)</span>
                                        </label>
                                        <label className="checkbox-item">
                                            <input type="checkbox" />
                                            <span>Mini-Mental</span>
                                        </label>
                                    </div>
                                </div>
                            </div>

                            <div className="prescricao-actions">
                                <div className="actions-left">
                                    <button type="button" className="button-secondary" disabled={isLoading}>
                                        Salvar Rascunho
                                    </button>
                                    <button type="button" className="button-secondary" disabled={isLoading}>
                                        Visualizar
                                    </button>
                                </div>
                                <div className="actions-right">
                                    <button type="button" className="button-secondary" onClick={handleReturnMenu}>
                                        Cancelar
                                    </button>
                                    <button
                                        type="submit"
                                        className="button"
                                        disabled={isLoading}
                                    >
                                        {isLoading ? "Criando..." : "Emitir Prescrição"}
                                    </button>
                                </div>
                            </div>
                        </form>
                    </div>
                </div>
            </main>
        </div>
    );
}