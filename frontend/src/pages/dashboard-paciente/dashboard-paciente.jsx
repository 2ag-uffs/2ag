import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import "./dashboard-paciente.css";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";

export default function DashboardPaciente() {
    const navigate = useNavigate();
    const location = useLocation();
    // quem acabou de salvar um formulario chega aqui com esse aviso,
    // no lugar do alert que cada tela dava antes
    const aviso = location.state && location.state.aviso;

    const [pacienteInfo, setPacienteInfo] = useState(null);
    const [dashboardData, setDashboardData] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const usuario = getLoggedUser();
        if (!usuario) {
            navigate("/login");
            return;
        }

        Promise.all([
            apiService.get(`/paciente/${usuario.id}`),
            apiService.get(`/dashboard/paciente/${usuario.id}`),
        ])
            .then(([paciente, dashboard]) => {
                setPacienteInfo(paciente);
                setDashboardData(dashboard);
            })
            .catch((err) => {
                setError(err instanceof ApiError ? err.message : "Erro ao carregar a dashboard.");
            })
            .finally(() => setIsLoading(false));
    }, [navigate]);

    const handleWeeklyMonitoring = () => navigate("/acompanhamento-paciente");
    const handleAgendarConsulta = () => navigate("/agendamento-consulta");
    const handleAnamnese = () => navigate("/anamnese");
    const handleProgresso = () => navigate("/progresso");
    const handleEscalas = () => {
        if (pacienteInfo?.id) {
            navigate(`/pacientes/${pacienteInfo.id}/escalas`);
        } else {
            console.error("ID do paciente não encontrado");
        }
    };

    if (isLoading) {
        return (
            <div className="dashboard-loading">
                <h1>Carregando painel...</h1>
            </div>
        );
    }

    if (error) {
        return (
            <div className="dashboard-error">
                <h1>Erro</h1>
                <p>{error}</p>
            </div>
        );
    }

    return (
        <div className="dashboard-paciente">
            <main className="dashboard-main">
                {aviso && <p className="aviso">{aviso}</p>}
                <div className="dashboard-welcome">
                    <h1>Painel do Paciente!</h1>
                    <p>
                        Acompanhe seu tratamento e mantenha-se em dia com suas
                        consultas e escalas.
                    </p>
                </div>

                <section className="dashboard-actions">
                    <h2>Ações Rápidas</h2>
                    <div className="actions-grid">
                        <button
                            className="action-button"
                            onClick={handleWeeklyMonitoring}
                        >
                            <span className="action-icon">📋</span>
                            <span>Acompanhamento Semanal</span>
                        </button>
                        <button
                            className="action-button"
                            onClick={handleEscalas}
                        >
                            <span className="action-icon">📊</span>
                            <span>Ver Escalas</span>
                        </button>
                        <button className="action-button">
                            <span className="action-icon">💊</span>
                            <span>Minhas Prescrições</span>
                        </button>
                        <button
                            className="action-button"
                            onClick={handleAgendarConsulta}
                        >
                            <span className="action-icon">📅</span>
                            <span>Agendar Consulta</span>
                        </button>
                        <button
                            className="action-button"
                            onClick={handleAnamnese}
                        >
                            <span className="action-icon">🗒️</span>
                            <span>Anamnese</span>
                        </button>
                        <button
                            className="action-button"
                            onClick={handleProgresso}
                        >
                            <span className="action-icon">📈</span>
                            <span>Meu Progresso</span>
                        </button>
                    </div>
                </section>

                <div className="dashboard-grid">
                    {/* Consultas */}
                    <section className="dashboard-card">
                        <div className="card-header">
                            <h2>Próximas Consultas</h2>
                            <span className="card-badge">
                                {dashboardData.upcomingAppointments.length}{" "}
                                agendadas
                            </span>
                        </div>
                        <div className="card-content">
                            {dashboardData.upcomingAppointments.length > 0 ? (
                                dashboardData.upcomingAppointments.map(
                                    (consulta, index) => (
                                        <div
                                            key={index}
                                            className="consulta-item"
                                        >
                                            <div className="consulta-info">
                                                <h3>
                                                    {consulta.nomePrescritor}
                                                </h3>
                                                <p>{consulta.tipoConsulta}</p>
                                                <span className="consulta-data">
                                                    {consulta.data} -{" "}
                                                    {consulta.horario}
                                                </span>
                                            </div>
                                        </div>
                                    ),
                                )
                            ) : (
                                <p>Sem consultas agendadas</p>
                            )}
                        </div>
                    </section>

                    {/* Escalas Pendentes */}
                    <section className="dashboard-card">
                        <div className="card-header">
                            <h2>Status de Formulários</h2>
                            <span className="card-badge warning">
                                {dashboardData.pendingScales.length} pendente(s)
                            </span>
                        </div>
                        <div className="card-content">
                            {dashboardData.pendingScales.length > 0 ? (
                                dashboardData.pendingScales.map(
                                    (escala, index) => (
                                        <div
                                            key={index}
                                            className="formulario-item"
                                        >
                                            <div className="formulario-info">
                                                <h3>{escala.name}</h3>
                                                <span className="formulario-atraso">
                                                    Status: {escala.status}
                                                </span>
                                            </div>
                                            <button
                                                className="button"
                                                onClick={() =>
                                                    navigate(escala.rota)
                                                }
                                            >
                                                Preencher
                                            </button>
                                        </div>
                                    ),
                                )
                            ) : (
                                <p>Sem formulários pendentes</p>
                            )}
                        </div>
                    </section>
                </div>
            </main>
        </div>
    );
}
