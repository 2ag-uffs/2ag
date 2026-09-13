import { useCallback, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import InvitePatientModal from "../../components/invite-patient-modal/invite-patient-modal.jsx";
import "./dashboard-prescritor.css";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";

export default function DashboardPrescritor() {
    const navigate = useNavigate();
    const location = useLocation();
    // quem salvou algo em outra tela chega aqui com esse aviso
    const aviso = location.state && location.state.aviso;

    const [showModal, setShowModal] = useState(false);
    const [dashboardData, setDashboardData] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);

    const fetchData = useCallback(async () => {
        const usuario = getLoggedUser();
        if (!usuario) {
            navigate("/login");
            return;
        }

        try {
            const dashboard = await apiService.get(`/dashboard/prescritor/${usuario.id}`);
            setDashboardData(dashboard);
        } catch (err) {
            setError(err instanceof ApiError ? err.message : "Erro ao carregar o painel.");
        } finally {
            setIsLoading(false);
        }
    }, [navigate]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    // toda consulta eh de um paciente entao comeca escolhendo ele na lista
    const handleNewConsult = (e) => {
        e.preventDefault();
        navigate("/lista-paciente");
    };

    // prescricao n existe solta, ela sai de dentro de uma consulta.
    // entao o atalho leva pra lista de pacientes, que eh por onde comeca
    const handleNewPrescription = (e) => {
        e.preventDefault();
        navigate("/lista-paciente");
    };

    const handleAgenda = (e) => {
        e.preventDefault();
        navigate("/agendamento-prescritor");
    };

    const handlePaciente = (e) => {
        e.preventDefault();
        navigate("/lista-paciente");
    };

    if (isLoading) {
        return (
            <div className="dashboard-loading">
                <h1>Carregando dados do painel...</h1>
            </div>
        );
    }

    if (error) {
        return (
            <div className="dashboard-error">
                <h1>Erro ao carregar o painel</h1>
                <p>{error}</p>
                <button onClick={() => navigate("/login")}>
                    Voltar ao Login
                </button>
            </div>
        );
    }

    return (
        <div className="dashboard-prescritor">
            <main className="dashboard-main">
                {aviso && <p className="aviso">{aviso}</p>}
                <div className="dashboard-welcome">
                    <h1>Painel do Prescritor!</h1>
                    <p>
                        Gerencie seus pacientes, consultas e acompanhe a
                        evolução dos tratamentos.
                    </p>
                </div>

                {/* Estatísticas Rápidas */}
                <div className="stats-grid">
                    <div className="stat-card" onClick={handlePaciente}>
                        <div className="stat-number">
                            {dashboardData?.activePatientsCount ?? 0}
                        </div>
                        <div className="stat-label">Pacientes Ativos</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-number">
                            {dashboardData?.appointmentsTodayCount ?? 0}
                        </div>
                        <div className="stat-label">Consultas Hoje</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-number">
                            {dashboardData?.pendingFormsCount ?? 0}
                        </div>
                        <div className="stat-label">Fichas Pendentes</div>
                    </div>
                    <div className="stat-card warning">
                        <div className="stat-number">
                            {dashboardData?.stats?.alertasClinicos ?? 0}
                        </div>
                        <div className="stat-label">Alertas Clínicos</div>
                    </div>
                </div>

                {/* Ações Rápidas */}
                <section className="dashboard-actions">
                    <h2>Ações rápidas</h2>
                    <div className="actions-grid">
                        <button
                            className="action-button"
                            onClick={() => setShowModal(true)}
                        >
                            <span className="action-icon">👥</span>
                            <span>Convidar paciente</span>
                        </button>
                        <button
                            className="action-button"
                            onClick={handleNewConsult}
                        >
                            <span className="action-icon">📋</span>
                            <span>Nova consulta</span>
                        </button>
                        <button
                            className="action-button"
                            onClick={handleNewPrescription}
                        >
                            <span className="action-icon">💊</span>
                            <span>Prescrever</span>
                        </button>
                        <button
                            className="action-button"
                            onClick={handleAgenda}
                        >
                            <span className="action-icon">📅</span>
                            <span>Agenda</span>
                        </button>
                        {/* <button className="action-button">
                            <span className="action-icon">⚙️</span>
                            <span>Configurações</span>
                        </button> */}
                    </div>
                </section>

                <div className="dashboard-grid">
                    {/* Agendamentos do Dia */}
                    <section className="dashboard-card">
                        <div className="card-header">
                            <h2>Agendamentos de hoje</h2>
                            <span className="card-badge">
                                {dashboardData?.agendamentos?.length ?? 0}{" "}
                                consultas
                            </span>
                        </div>
                        <div className="card-content">
                            {dashboardData?.todaysAppointments?.length > 0 ? (
                                dashboardData.todaysAppointments.map(
                                    (agendamento) => (
                                        <div
                                            key={agendamento.id}
                                            className="agendamento-item-wrapper"
                                        >
                                            <div className="agendamento-item">
                                                <div className="agendamento-time">
                                                    {agendamento.horario}
                                                </div>
                                                <div className="agendamento-info">
                                                    <h3>
                                                        {
                                                            agendamento.nomePaciente
                                                        }
                                                    </h3>
                                                    <p>
                                                        {
                                                            agendamento.tipoConsulta
                                                        }
                                                    </p>
                                                    <span
                                                        className={`agendamento-tipo ${agendamento.modalidade?.toLowerCase()}`}
                                                    >
                                                        {agendamento.modalidade}
                                                    </span>
                                                </div>
                                            </div>
                                            <button className="button-secondary">
                                                Iniciar
                                            </button>
                                        </div>
                                    ),
                                )
                            ) : (
                                <p>Nenhum agendamento para hoje.</p>
                            )}
                        </div>
                        <div className="card-footer">
                            <button className="button" onClick={handleAgenda}>
                                Ver todos os agendamentos
                            </button>
                        </div>
                    </section>

                    {/* Fichas Pendentes */}
                    <section className="dashboard-card">
                        <div className="card-header">
                            <h2>Fichas pendentes de análise</h2>
                            <span className="card-badge warning">
                                {dashboardData?.fichasPendentes?.length ?? 0}{" "}
                                pendentes
                            </span>
                        </div>
                        <div className="card-content">
                            {dashboardData?.fichasPendentes?.length > 0 ? (
                                dashboardData.fichasPendentes.map((ficha) => (
                                    <div
                                        key={ficha.id}
                                        className="ficha-item-wrapper"
                                    >
                                        <div className="ficha-item">
                                            <div className="ficha-info">
                                                <h3>{ficha.nomePaciente}</h3>
                                                <p>{ficha.tipo}</p>
                                                <span className="ficha-data">
                                                    {ficha.dataEnvio}
                                                </span>
                                            </div>
                                            <div
                                                className={`ficha-priority ${ficha.prioridade?.toLowerCase()}`}
                                            >
                                                {ficha.prioridade}
                                            </div>
                                        </div>
                                        <button className="button-secondary">
                                            Analisar
                                        </button>
                                    </div>
                                ))
                            ) : (
                                <p>Nenhuma ficha pendente.</p>
                            )}
                        </div>
                        <div className="card-footer">
                            <button className="button">
                                Ver todas as fichas
                            </button>
                        </div>
                    </section>

                    {/* Alertas Clínicos */}
                    <section className="dashboard-card alert-card">
                        <div className="card-header">
                            <h2>Alertas clínicos</h2>
                            <span className="card-badge danger">
                                {dashboardData?.alertas?.length ?? 0} alertas
                            </span>
                        </div>
                        <div className="card-content">
                            {dashboardData?.alertas?.length > 0 ? (
                                dashboardData.alertas.map((alerta) => (
                                    <div
                                        key={alerta.id}
                                        className="alert-item-wrapper"
                                    >
                                        <div
                                            className={`alert-item ${alerta.nivel?.toLowerCase()}`}
                                        >
                                            <div className="alert-icon">
                                                {alerta.nivel === "CRITICAL"
                                                    ? "⚠️"
                                                    : "⚡"}
                                            </div>
                                            <div className="alert-info">
                                                <h3>{alerta.nomePaciente}</h3>
                                                <p>{alerta.descricao}</p>
                                                <span className="alert-time">
                                                    {alerta.data}
                                                </span>
                                            </div>
                                        </div>
                                        <button
                                            className={
                                                alerta.nivel === "CRITICAL"
                                                    ? "button"
                                                    : "button-secondary"
                                            }
                                        >
                                            {alerta.nivel === "CRITICAL"
                                                ? "Verificar"
                                                : "Contatar"}
                                        </button>
                                    </div>
                                ))
                            ) : (
                                <p>Nenhum alerta clínico no momento.</p>
                            )}
                        </div>
                        <div className="card-footer">
                            <button className="button">
                                Ver todos os alertas
                            </button>
                        </div>
                    </section>

                    {/* Pacientes Recentes */}
                    <section className="dashboard-card">
                        <div className="card-header">
                            <h2>Pacientes recentes</h2>
                            <span className="card-badge">
                                Últimas atividades
                            </span>
                        </div>
                        <div className="card-content">
                            {dashboardData?.pacientesRecentes?.length > 0 ? (
                                dashboardData.pacientesRecentes.map(
                                    (paciente) => (
                                        <div
                                            key={paciente.id}
                                            className="paciente-item-wrapper"
                                        >
                                            <div className="paciente-item">
                                                <div className="paciente-avatar">
                                                    {paciente.iniciais}
                                                </div>
                                                <div className="paciente-info">
                                                    <h3>{paciente.nome}</h3>
                                                    <p>
                                                        Última consulta:{" "}
                                                        {
                                                            paciente.ultimaConsulta
                                                        }
                                                    </p>
                                                    <span
                                                        className={`paciente-status ${paciente.status?.toLowerCase().replace(" ", "-")}`}
                                                    >
                                                        {paciente.status}
                                                    </span>
                                                </div>
                                            </div>
                                            <button className="button-secondary">
                                                Ver prontuário
                                            </button>
                                        </div>
                                    ),
                                )
                            ) : (
                                <p>Nenhuma atividade recente de pacientes.</p>
                            )}
                        </div>
                        <div className="card-footer">
                            <button className="button" onClick={handlePaciente}>
                                Ver todos os pacientes
                            </button>
                        </div>
                    </section>
                </div>
            </main>
            <InvitePatientModal show={showModal} onClose={() => setShowModal(false)}/>
        </div>
    );
}
