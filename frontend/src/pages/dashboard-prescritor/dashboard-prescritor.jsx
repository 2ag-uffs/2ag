import {useCallback, useEffect, useState} from "react";
import {useLocation, useNavigate} from "react-router";
import InvitePatientModal from "../../components/invite-patient-modal/invite-patient-modal.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import {modalityLabelOf} from "../../utils/appointment-labels.js";
import {formatDate, formatDateTime, formatTime} from "../../utils/date-format.js";
import styles from "./dashboard-prescritor.module.css";

// painel do prescritor (RF03)
//
// mostra o dia de hoje, o q espera resposta dele e o q venceu do lado
// do paciente. cartao sem dado atras saiu: confundia mais do q ajudava
export default function DashboardPrescritor() {
    const navigate = useNavigate();
    const location = useLocation();
    const loggedUser = getLoggedUser();
    // quem salvou algo em outra tela chega aqui com esse aviso
    const notice = location.state && location.state.notice;

    const [panel, setPanel] = useState(null);
    const [loadError, setLoadError] = useState(null);
    const [isInviteOpen, setIsInviteOpen] = useState(false);

    const loadPanel = useCallback(() => {
        if (!loggedUser) {
            navigate("/entrar");
            return;
        }
        apiService.get("/dashboard/prescriber/" + loggedUser.id)
            .then(setPanel)
            .catch((requestError) => {
                setLoadError(requestError instanceof ApiError
                    ? requestError.message
                    : "Não foi possível carregar o painel. Confira sua internet e tente de novo.");
            });
    }, [loggedUser, navigate]);

    useEffect(loadPanel, [loadPanel]);

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
                <p className={styles.subtitle}>O que precisa de você hoje.</p>
            </header>

            {notice && <p className="aviso" role="status">{notice}</p>}

            <div className={styles.stats}>
                <button type="button" className={styles.stat} onClick={() => navigate("/lista-paciente")}>
                    <span className={styles.statNumber}>{panel.activePatients}</span>
                    <span>Pacientes ativos</span>
                </button>
                <div className={styles.stat}>
                    <span className={styles.statNumber}>{panel.todaysAppointments.length}</span>
                    <span>Consultas hoje</span>
                </div>
                <div className={styles.stat}>
                    <span className={styles.statNumber}>{panel.waitingRequests.length}</span>
                    <span>Pedidos esperando</span>
                </div>
                <div className={styles.stat}>
                    <span className={styles.statNumber}>{panel.lateScales.length}</span>
                    <span>Escalas vencidas</span>
                </div>
            </div>

            <div className={styles.shortcuts}>
                <button type="button" className="button-secondary" onClick={() => setIsInviteOpen(true)}>
                    Convidar paciente
                </button>
                <button type="button" className="button-secondary" onClick={() => navigate("/lista-paciente")}>
                    Nova consulta
                </button>
                <button type="button" className="button-secondary" onClick={() => navigate("/agendamento-prescritor")}>
                    Agenda
                </button>
                <button type="button" className="button-secondary" onClick={() => navigate("/agenda/disponibilidade")}>
                    Horários de atendimento
                </button>
            </div>

            <div className={styles.cards}>
                <section className={styles.card}>
                    <h2 className={styles.cardTitle}>Consultas de hoje</h2>
                    {panel.todaysAppointments.length === 0 ? (
                        <p className={styles.empty}>Nenhuma consulta marcada para hoje.</p>
                    ) : (
                        <ul className={styles.list}>
                            {panel.todaysAppointments.map((appointment) => (
                                <li key={appointment.appointmentId} className={styles.item}>
                                    <div>
                                        <strong>{formatTime(appointment.dateTime)} · {appointment.patientName}</strong>
                                        <p className={styles.meta}>{modalityLabelOf(appointment.modality)}</p>
                                    </div>
                                    <button
                                        type="button"
                                        className="button"
                                        onClick={() => navigate("/consulta/" + appointment.appointmentId + "/registro")}
                                    >
                                        Registrar atendimento
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                    <button type="button" className="button-secondary" onClick={() => navigate("/agendamento-prescritor")}>
                        Ver a agenda
                    </button>
                </section>

                <section className={styles.card}>
                    <h2 className={styles.cardTitle}>Pedidos esperando resposta ({panel.waitingRequests.length})</h2>
                    {panel.waitingRequests.length === 0 ? (
                        <p className={styles.empty}>Nenhum pedido esperando resposta.</p>
                    ) : (
                        <ul className={styles.list}>
                            {panel.waitingRequests.map((request) => (
                                <li key={request.appointmentId} className={styles.item}>
                                    <div>
                                        <strong>{request.patientName}</strong>
                                        <p className={styles.meta}>
                                            {formatDateTime(request.dateTime)} · {modalityLabelOf(request.modality)}
                                        </p>
                                        {request.patientNote && (
                                            <p className={styles.note}>Motivo: {request.patientNote}</p>
                                        )}
                                    </div>
                                    <button
                                        type="button"
                                        className="button"
                                        onClick={() => navigate("/agendamento-prescritor")}
                                    >
                                        Responder
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                </section>

                <section className={styles.card}>
                    <h2 className={styles.cardTitle}>Escalas vencidas ({panel.lateScales.length})</h2>
                    {panel.lateScales.length === 0 ? (
                        <p className={styles.empty}>Nenhuma escala vencida sem resposta.</p>
                    ) : (
                        <ul className={styles.list}>
                            {panel.lateScales.map((scale) => (
                                <li key={scale.taskId} className={styles.item}>
                                    <div>
                                        <strong>{scale.patientName}</strong>
                                        <p className={styles.meta}>
                                            {scale.scaleName} · prazo em {formatDate(scale.deadline)}
                                        </p>
                                    </div>
                                    <button
                                        type="button"
                                        className="button-secondary"
                                        onClick={() => navigate("/paciente/" + scale.patientId + "/historico")}
                                    >
                                        Ver histórico
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                </section>
            </div>

            <InvitePatientModal show={isInviteOpen} onClose={() => setIsInviteOpen(false)}/>
        </section>
    );
}
