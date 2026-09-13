import "../../styles/colors.css";
import "../../styles/fonts.css";
import "../../styles/button.css";
import "../../styles/input.css";
import "./consulta-clinica.css";
import {apiService, ApiError} from "../../services/api.js";
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import Header from "../../components/header/header.jsx";


export default function ConsultaClinica() {
    const [abaAtiva, setAbaAtiva] = useState('observacoes');
    const navigate = useNavigate();
    const { patientId } = useParams();

    const [queixaPrincipal, setQueixaPrincipal] = useState('');
    const [exameFisico, setExameFisico] = useState('');
    const [evolucao, setEvolucao] = useState('');
    const [pressao, setPressao] = useState('');
    const [peso, setPeso] = useState('');
    const [altura, setAltura] = useState('');
    const [observacoesGerais, setObservacoesGerais] = useState('');
    const [hipoteseDiagnostica, setHipoteseDiagnostica] = useState('');
    const [conduta, setConduta] = useState('');
    const [examesComplementares, setExamesComplementares] = useState('');

    const [patient, setPatient] = useState(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);
    const [success, setSuccess] = useState(null);
    // guarda o id da consulta salva pra liberar o botao de prescricao
    const [consultaSalvaId, setConsultaSalvaId] = useState(null);

    useEffect(() => {
        const fetchPatientData = async () => {
            setIsLoading(true);

            try {
                // o cliente cuida do token e manda pro login se a sessao acabou
                const data = await apiService.get(`/paciente/${patientId}`);
                setPatient(data);
            } catch (err) {
                setError(err instanceof ApiError ? err.message : "Falha ao carregar dados do paciente.");
            } finally {
                setIsLoading(false);
            }
        };

        if (patientId) {
            fetchPatientData();
        }
    }, [patientId, navigate]);


    const handleSave = async (e) => {
        e.preventDefault();
        setIsLoading(true);
        setError(null);
        setSuccess(null);

        // os campos da tela mapeados nos da consulta.
        // queixa e observacoes gerais vao juntas na observacao clinica,
        // que eh campo de texto livre
        const consulta = {
            patientId: parseInt(patientId),
            dateTime: new Date().toISOString().slice(0, 19),
            modality: "PRESENCIAL",
            status: "CONCLUIDA",
            diagnosis: hipoteseDiagnostica,
            clinicalObservation: `Queixa Principal: ${queixaPrincipal}\n\nObservações Gerais: ${observacoesGerais}`,
            therapeuticPlan: conduta,
            evolution: evolucao,
            physicalExam: exameFisico,
            complementaryExams: examesComplementares,
            bloodPressure: pressao,
            weight: peso ? parseFloat(peso) : null,
            height: altura ? parseInt(altura) : null
        };

        try {
            // a consulta salva volta com o id, que eh o que a prescricao
            // precisa saber pra nascer amarrada nela (RF05)
            const consultaSalva = await apiService.post("/consulta", consulta);

            setSuccess("Consulta clínica salva com sucesso!");
            setConsultaSalvaId(consultaSalva.id);

        } catch (err) {
            setError(err instanceof ApiError ? err.message : "Ocorreu um erro ao salvar a consulta.");
        } finally {
            setIsLoading(false);
        }
    };


    const handleBack = () => {
        navigate(-1);
    };

    if (isLoading) {
        return (
            <div className="consulta-clinica-loading">
                <p>Carregando dados do paciente...</p>
            </div>
        );
    }

    if (error) {
        return (
            <div className="consulta-clinica-error">
                <p>Erro ao carregar dados: {error}</p>
                <button onClick={() => navigate(-1)} className="button">Voltar</button>
            </div>
        );
    }

    if (!patient) {
        return (
            <div className="consulta-clinica">
                <Header
                    title=" "
                    showBackButton={true}
                    backButtonText="Voltar"
                    onBackClick={handleBack}
                />
                <main className="consulta-main">
                    <div className="no-patient-found">
                        <div className="no-patient-found-message">
                            <p>Nenhuma consulta encontrada.</p>
                            <button onClick={handleBack} className="button">Voltar</button>
                        </div>
                    </div>
                </main>
            </div>
        );
    }


    const calculateAge = (birthDate) => {
        if (!birthDate) return '';
        return new Date().getFullYear() - new Date(birthDate).getFullYear();
    }

    return (
        <div className="consulta-clinica">
            <Header
                title="Dr. Maria Santos - CRM 12345"
                showBackButton={true}
                backButtonText="Voltar"
                onBackClick={handleBack}
            />

            <main className="consulta-main">
                <div className="consulta-title">
                    <h1>Consulta</h1>
                    <h2>{patient.name}</h2>
                    <p>Idade: {calculateAge(patient.birthDate)} anos</p>
                    <p>ID: {patient.id}</p>
                </div>
                <div className="consulta-layout">
                    {/* Sidebar com informações do paciente */}
                    <aside className="consulta-sidebar">
                        <div className="paciente-card">
                            <div className="paciente-avatar">JS</div>
                            <div className="paciente-info">
                                <h2>{patient.name}</h2>
                                <p>{calculateAge(patient.birthDate)} anos • {patient.gender || 'Não informado'}</p>
                                {patient.cpf && <p>CPF: {patient.cpf}</p>}
                                {patient.phone && <p>Telefone: {patient.phone}</p>}
                            </div>
                        </div>

                        <div className="historico-section">
                            <h3>Histórico Recente</h3>
                            <div className="historico-item">
                                <div className="historico-date">08/01/2025</div>
                                <div className="historico-content">
                                    <p>Acompanhamento semanal</p>
                                    <span>Melhora nos sintomas de ansiedade</span>
                                </div>
                            </div>
                            <div className="historico-item">
                                <div className="historico-date">01/01/2025</div>
                                <div className="historico-content">
                                    <p>Primeira consulta</p>
                                    <span>Prescrição inicial CBD 5%</span>
                                </div>
                            </div>
                        </div>

                        <div className="prescricoes-ativas">
                            <h3>Prescrições Ativas</h3>
                            <div className="prescricao-item">
                                <h4>CBD Full Spectrum 5%</h4>
                                <p>2 gotas, 2x ao dia</p>
                                <span className="prescricao-numero">#2024-001</span>
                            </div>
                        </div>
                    </aside>

                    {/* Conteúdo principal da consulta */}
                    <div className="consulta-content">
                        <div className="consulta-tabs">
                            <button
                                className={`tab-button ${abaAtiva === 'observacoes' ? 'active' : ''}`}
                                onClick={() => setAbaAtiva('observacoes')}
                            >
                                Observações
                            </button>
                            <button
                                className={`tab-button ${abaAtiva === 'objetivos' ? 'active' : ''}`}
                                onClick={() => setAbaAtiva('objetivos')}
                            >
                                Objetivos
                            </button>
                            <button
                                className={`tab-button ${abaAtiva === 'arquivos' ? 'active' : ''}`}
                                onClick={() => setAbaAtiva('arquivos')}
                            >
                                Arquivos
                            </button>
                            <button
                                className={`tab-button ${abaAtiva === 'escalas' ? 'active' : ''}`}
                                onClick={() => setAbaAtiva('escalas')}
                            >
                                Escalas
                            </button>
                        </div>

                        {/* Aba de Observações */}
                        <div className={`tab-content ${abaAtiva === 'observacoes' ? 'active' : ''}`}>
                            <div className="observacoes-section">
                                <h3>Observações Clínicas</h3>
                                <div className="form-group">
                                    <label htmlFor="queixa-principal">Queixa Principal</label>
                                    <textarea id="queixa-principal" placeholder="Descreva a queixa principal do paciente..." rows="3" value={queixaPrincipal} onChange={(e) => setQueixaPrincipal(e.target.value)}></textarea>
                                </div>

                                <div className="form-group">
                                    <label htmlFor="exame-fisico">Exame Físico</label>
                                    <textarea id="exame-fisico" placeholder="Registre os achados do exame físico..." rows="4" value={exameFisico} onChange={(e) => setExameFisico(e.target.value)}></textarea>
                                </div>

                                <div className="form-group">
                                    <label htmlFor="evolucao">Evolução do Tratamento</label>
                                    <textarea id="evolucao" placeholder="Descreva a evolução desde a última consulta..." rows="4" value={evolucao} onChange={(e) => setEvolucao(e.target.value)}></textarea>
                                </div>

                                <div className="form-row">
                                    <div className="form-group">
                                        <label htmlFor="pressao">Pressão Arterial</label>
                                        <input id="pressao" type="text" placeholder="120/80 mmHg" value={pressao} onChange={(e) => setPressao(e.target.value)} />
                                    </div>
                                    <div className="form-group">
                                        <label htmlFor="peso">Peso (kg)</label>
                                        <input id="peso" type="number" placeholder="70" value={peso} onChange={(e) => setPeso(e.target.value)} />
                                    </div>
                                    <div className="form-group">
                                        <label htmlFor="altura">Altura (cm)</label>
                                        <input id="altura" type="number" placeholder="175" value={altura} onChange={(e) => setAltura(e.target.value)} />
                                    </div>
                                </div>

                                <div className="form-group">
                                    <label htmlFor="hipotese-diagnostica">Hipótese Diagnóstica</label>
                                    <textarea id="hipotese-diagnostica" placeholder="Descreva a hipótese diagnóstica..." rows="3" value={hipoteseDiagnostica} onChange={(e) => setHipoteseDiagnostica(e.target.value)}></textarea>
                                </div>

                                <div className="form-group">
                                    <label htmlFor="conduta">Conduta / Plano Terapêutico</label>
                                    <textarea id="conduta" placeholder="Descreva a conduta definida..." rows="3" value={conduta} onChange={(e) => setConduta(e.target.value)}></textarea>
                                </div>

                                <div className="form-group">
                                    <label htmlFor="exames-complementares">Exames Complementares</label>
                                    <textarea id="exames-complementares" placeholder="Exames solicitados ou resultados..." rows="3" value={examesComplementares} onChange={(e) => setExamesComplementares(e.target.value)}></textarea>
                                </div>

                                <div className="form-group">
                                    <label htmlFor="observacoes-gerais">Observações Gerais</label>
                                    <textarea id="observacoes-gerais" placeholder="Outras observações relevantes..." rows="3" value={observacoesGerais} onChange={(e) => setObservacoesGerais(e.target.value)}></textarea>
                                </div>
                            </div>
                        </div>


                        {/* Aba de Objetivos */}
                        <div className={`tab-content ${abaAtiva === 'objetivos' ? 'active' : ''}`}>
                            <div className="objetivos-section">
                                <h3>Objetivos Terapêuticos</h3>
                                <div className="objetivo-item">
                                    <div className="form-group">
                                        <label htmlFor="objetivo-1">Objetivo Principal</label>
                                        <input
                                            id="objetivo-1"
                                            type="text"
                                            placeholder="Ex: Reduzir ansiedade em 50%"
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label htmlFor="prazo-1">Prazo</label>
                                        <input
                                            id="prazo-1"
                                            type="date"
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label htmlFor="metrica-1">Métrica de Avaliação</label>
                                        <select id="metrica-1">
                                            <option>Escala de Hamilton</option>
                                            <option>Escala de Beck</option>
                                            <option>Qualidade do Sono</option>
                                            <option>Autorrelato</option>
                                        </select>
                                    </div>
                                </div>
                                <button className="button-secondary">+ Adicionar Objetivo</button>
                            </div>
                        </div>

                        {/* Aba de Arquivos */}
                        <div className={`tab-content ${abaAtiva === 'arquivos' ? 'active' : ''}`}>
                            <div className="arquivos-section">
                                <h3>Arquivos e Documentos</h3>
                                <div className="upload-area">
                                    <div className="upload-icon">📁</div>
                                    <p>Arraste arquivos aqui ou clique para selecionar</p>
                                </div>
                                <button className="button-secondary">Selecionar Arquivos</button>
                                <div className="arquivos-lista">

                                </div>
                            </div>
                        </div>

                        {/* Aba de Escalas */}
                        <div className={`tab-content ${abaAtiva === 'escalas' ? 'active' : ''}`}>
                            <div className="escalas-section">
                                <h3>Escalas Clínicas</h3>
                                <div className="escala-item">
                                    <div className="escala-info">
                                        <h4>Escala de Hamilton (Ansiedade)</h4>
                                        <p>Última aplicação: 08/01/2025</p>
                                        <span className="escala-resultado">Pontuação: 18 (Ansiedade moderada)</span>
                                    </div>
                                    <div className="escala-actions">
                                        <button className="button-secondary">Ver histórico</button>
                                        <button className="button">Aplicar novamente</button>
                                    </div>
                                </div>
                                <div className="escala-item">
                                    <div className="escala-info">
                                        <h4>Índice de Qualidade do Sono</h4>
                                        <p>Última aplicação: 01/01/2025</p>
                                        <span className="escala-resultado">Pontuação: 12 (Qualidade ruim)</span>
                                    </div>
                                    <div className="escala-actions">
                                        <button className="button-secondary">Ver histórico</button>
                                        <button className="button">Aplicar novamente</button>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="feedback-messages">
                            {isLoading && <p className="feedback-loading">Salvando consulta...</p>}
                            {error && <p className="feedback-error">Erro: {error}</p>}
                            {success && <p className="feedback-success">{success}</p>}
                            {/* consulta salva: agora da pra emitir a prescricao
                                dela, ou seguir pro historico do paciente */}
                            {consultaSalvaId && (
                                <div className="form__content__form__actions">
                                    <button
                                        type="button"
                                        onClick={() => navigate(`/consulta/${consultaSalvaId}/prescricao`)}
                                    >
                                        Emitir prescrição
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => navigate(`/consulta/${consultaSalvaId}/mini-exame`)}
                                    >
                                        Aplicar mini-exame
                                    </button>
                                    <button
                                        type="button"
                                        className="button-secondary"
                                        onClick={() => navigate(`/paciente/${patientId}/historico`)}
                                    >
                                        Ir para o histórico
                                    </button>
                                </div>
                            )}
                        </div>

                        {/* Ações da consulta */}
                        <div className="consulta-actions">
                            <div className="actions-left">
                                <button className="button-secondary" disabled={isLoading}>Salvar Rascunho</button>
                            </div>
                            <div className="actions-right">
                                <button className="button" onClick={handleSave} disabled={isLoading}>
                                    {isLoading ? 'Salvando...' : 'Finalizar e Salvar'}
                                </button>
                            </div>
                        </div>

                    </div>
                </div>
            </main>
        </div>
    );
}