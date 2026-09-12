import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import "../../styles/button.css";
import "../../styles/colors.css";
import "../../styles/fonts.css";
import "../../styles/input.css";
import "./dashboard-paciente.css";

function parseJwt(token) {
    try {
        return JSON.parse(atob(token.split(".")[1]));
    } catch (e) {
        return null;
    }
}

export default function DashboardPaciente() {
    const navigate = useNavigate();

    const [pacienteInfo, setPacienteInfo] = useState(null);
    const [dashboardData, setDashboardData] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const fetchData = async () => {
            const token = localStorage.getItem("authToken");
            if (!token) {
                setError("Token não encontrado.");
                navigate("/login");
                return;
            }

            const decodedToken = parseJwt(token);
            const userId = decodedToken?.id;
            if (!userId) {
                setError("Usuário inválido.");
                navigate("/login");
                return;
            }

            try {
                const [pacienteRes, dashRes] = await Promise.all([
                    fetch(`http://localhost:8080/paciente/${userId}`, {
                        headers: { Authorization: `Bearer ${token}` },
                    }),
                    fetch(
                        `http://localhost:8080/dashboard/paciente/${userId}`,
                        {
                            headers: { Authorization: `Bearer ${token}` },
                        },
                    ),
                ]);

                if (!pacienteRes.ok) throw new Error("Erro ao buscar paciente");
                if (!dashRes.ok)
                    throw new Error("Erro ao buscar dados da dashboard");

                const pacienteData = await pacienteRes.json();
                const dashboard = await dashRes.json();

                setPacienteInfo(pacienteData);
                setDashboardData(dashboard);
            } catch (err) {
                setError(err.message);
            } finally {
                setIsLoading(false);
            }
        };

        fetchData();
    }, [navigate]);

    const handleLogout = (e) => {
        e.preventDefault();
        localStorage.removeItem("authToken");
        navigate("/login");
        window.location.reload();
    };

    const handleWeeklyMonitoring = () => navigate("/acompanhamento-paciente");
    const handleAgendarConsulta = () => navigate("/agendamento-consulta");
    // falta ajustar com o retorno do backend para redirecionar para a escala hamilton
    const handleOpenHamAScale = () => navigate("/escala-hamilton");
    const handleNotificacoes = () => navigate("/notificacoes-paciente");
    // falta ajustar com o retorno do backend para redirecionar para a diária do sono
    const handleSleep = () => navigate("/diario-sono");
    const handleAnamnese = () => navigate("/anamnese");
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
            <header className="dashboard-header">
                <div className="dashboard-header__logo">
                    <img
                        src="/images/logotipo-icon.svg"
                        alt="Logo"
                        className="logo"
                    />
                </div>
                <div className="dashboard-header__user">
                    <span>Olá, {pacienteInfo?.name}</span>
                    <button
                        className="button-secondary"
                        onClick={handleNotificacoes}
                    >
                        Notificações
                    </button>
                    <button className="button-secondary" onClick={handleLogout}>
                        Sair
                    </button>
                </div>
            </header>

            <main className="dashboard-main">
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
