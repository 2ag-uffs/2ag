import {useEffect, useState} from "react";
import {useLocation, useNavigate} from "react-router";
import AppointmentStatusBadge from "../../components/appointment-status-badge/appointment-status-badge.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import {modalityLabelOf} from "../../utils/appointment-labels.js";
import {formatDate, formatDateTime} from "../../utils/date-format.js";
import styles from "./dashboard-paciente.module.css";

const SHORTCUTS = [
    {path: "/agendamento-consulta", label: "Agendar consulta"},
    {path: "/progresso", label: "Meu progresso"},
    {path: "/minhas-prescricoes", label: "Minhas prescrições"},
    {path: "/historico-paciente", label: "Meu histórico"},
];

// painel do paciente (RF03)
//
// mostra so o q o resto do sistema grava: as proximas consultas, as
// escalas esperando resposta, a prescricao q esta valendo e os avisos
// q ele ainda n leu
export default function DashboardPaciente() {
    const navigate = useNavigate();
    const location = useLocation();
    const loggedUser = getLoggedUser();
    // quem acabou de salvar um formulario chega aqui com esse aviso
    const notice = location.state && location.state.aviso;

    const [panel, setPanel] = useState(null);
    const [loadError, setLoadError] = useState(null);

    useEffect(() => {
        if (!loggedUser) {
            navigate("/login");
            return;
        }
        apiService.get("/dashboard/paciente/" + loggedUser.id)
            .then(setPanel)
            .catch((requestError) => {
                setLoadError(requestError instanceof ApiError
                    ? requestError.message
                    : "Não foi possível carregar o painel. Confira sua internet e tente de novo.");
            });
    }, [loggedUser, navigate]);

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!panel) {
        return <p>Carregando o painel...</p>;
    }

    return (
        <section className={styles.page}>
            <header>
                <h1>Olá, {loggedUser.name}</h1>
                <p className={styles.subtitle}>
                    Acompanhe o seu tratamento e o que está esperando resposta.
                </p>
            </header>

            {notice && <p className="aviso" role="status">{notice}</p>}

            <div className={styles.shortcuts}>
                {SHORTCUTS.map((shortcut) => (
                    <button
                        key={shortcut.path}
                        type="button"
                        className="button-secondary"
                        onClick={() => navigate(shortcut.path)}
                    >
                        {shortcut.label}
                    </button>
                ))}
            </div>

            <div className={styles.cards}>
                <section className={styles.card}>
                    <h2 className={styles.cardTitle}>Próximas consultas</h2>
                    {panel.upcomingAppointments.length === 0 ? (
                        <p className={styles.empty}>Nenhuma consulta marcada. Você pode pedir um horário.</p>
                    ) : (
                        <ul className={styles.list}>
                            {panel.upcomingAppointments.map((appointment) => (
                                <li key={appointment.appointmentId} className={styles.item}>
                                    <div>
                                        <strong>{formatDateTime(appointment.dateTime)}</strong>
                                        <p className={styles.meta}>
                                            {modalityLabelOf(appointment.modality)}
                                            {appointment.prescriberName ? " com " + appointment.prescriberName : ""}
                                        </p>
                                    </div>
                                    <AppointmentStatusBadge appointment={appointment}/>
                                </li>
                            ))}
                        </ul>
                    )}
                    <button type="button" className="button" onClick={() => navigate("/agendamento-consulta")}>
                        Ver minhas consultas
                    </button>
                </section>

                <section className={styles.card}>
                    <h2 className={styles.cardTitle}>Esperando resposta ({panel.pendingScales.length})</h2>
                    {panel.pendingScales.length === 0 ? (
                        <p className={styles.empty}>Nenhuma escala esperando resposta agora.</p>
                    ) : (
                        <ul className={styles.list}>
                            {panel.pendingScales.map((scale) => (
                                <li key={scale.taskId} className={styles.item}>
                                    <div>
                                        <strong>{scale.name}</strong>
                                        <p className={styles.meta}>
                                            {scale.late
                                                ? "O prazo era " + formatDate(scale.deadline)
                                                : "Responda até " + formatDate(scale.deadline)}
                                        </p>
                                    </div>
                                    <button type="button" className="button" onClick={() => navigate(scale.path)}>
                                        Preencher
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                    <button
                        type="button"
                        className="button-secondary"
                        onClick={() => navigate("/pacientes/" + loggedUser.id + "/escalas")}
                    >
                        Ver todas as avaliações
                    </button>
                </section>

                <section className={styles.card}>
                    <h2 className={styles.cardTitle}>Prescrição de agora</h2>
                    {panel.currentPrescription === null ? (
                        <p className={styles.empty}>Nenhuma prescrição vigente.</p>
                    ) : (
                        <div className={styles.prescription}>
                            <strong>{panel.currentPrescription.productDescription}</strong>
                            <p className={styles.posology}>{panel.currentPrescription.posology}</p>
                            <p className={styles.meta}>
                                Prescrita em {formatDate(panel.currentPrescription.prescribedAt)}
                            </p>
                        </div>
                    )}
                    <button type="button" className="button-secondary" onClick={() => navigate("/minhas-prescricoes")}>
                        Ver prescrições
                    </button>
                </section>

                <section className={styles.card}>
                    <h2 className={styles.cardTitle}>Avisos não lidos ({panel.latestNotifications.length})</h2>
                    {panel.latestNotifications.length === 0 ? (
                        <p className={styles.empty}>Nenhum aviso novo.</p>
                    ) : (
                        <ul className={styles.list}>
                            {panel.latestNotifications.map((notification) => (
                                <li key={notification.id} className={styles.notification}>
                                    <strong>{notification.title}</strong>
                                    <p className={styles.meta}>{notification.message}</p>
                                </li>
                            ))}
                        </ul>
                    )}
                    <button
                        type="button"
                        className="button-secondary"
                        onClick={() => navigate("/notificacoes")}
                    >
                        Ver avisos
                    </button>
                </section>
            </div>
        </section>
    );
}
