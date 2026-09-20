import {useCallback, useEffect, useState} from "react";
import {useLocation, useNavigate} from "react-router";
import {FiAlertCircle, FiCalendar, FiInbox, FiUsers} from "react-icons/fi";
import Card from "../../components/card/card.jsx";
import InvitePatientModal from "../../components/invite-patient-modal/invite-patient-modal.jsx";
import ItemList, {ListItem} from "../../components/item-list/item-list.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import {modalityLabelOf} from "../../utils/appointment-labels.js";
import {formatDate, formatDateTime, formatTime, isInTheFuture} from "../../utils/date-format.js";
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
        return <SkeletonPage cards={3} stats={4}/>;
    }

    // o painel chama a pessoa pelo primeiro nome e pula titulo tipo dra.
    const firstName = loggedUser.name.split(" ").find((word) => !word.endsWith(".")) || loggedUser.name;

    // os numeros do dia. o q leva pra alguma tela vira botao e o q pede
    // atencao ganha o destaque
    const stats = [
        {label: "Pacientes ativos", value: panel.activePatients, icon: FiUsers, path: "/lista-paciente"},
        {label: "Consultas hoje", value: panel.todaysAppointments.length, icon: FiCalendar, path: "/agendamento-prescritor"},
        {
            label: "Pedidos esperando",
            value: panel.waitingRequests.length,
            icon: FiInbox,
            path: "/agendamento-prescritor",
            needsAttention: panel.waitingRequests.length > 0,
        },
        {
            label: "Escalas vencidas",
            value: panel.lateScales.length,
            icon: FiAlertCircle,
            needsAttention: panel.lateScales.length > 0,
        },
    ];

    return (
        <section className={styles.page}>
            <PageHeader
                title={"Olá, " + firstName}
                subtitle="O que precisa de você hoje."
                actions={(
                    <>
                        <button type="button" className="button-secondary" onClick={() => navigate("/lista-paciente")}>
                            Nova consulta
                        </button>
                        <button type="button" className="button" onClick={() => setIsInviteOpen(true)}>
                            Convidar paciente
                        </button>
                    </>
                )}
            />

            {notice && <p className="aviso" role="status">{notice}</p>}

            <div className={styles.stats}>
                {stats.map((stat) => {
                    const Icon = stat.icon;
                    const tileClass = stat.needsAttention ? styles.stat + " " + styles.statAttention : styles.stat;
                    const tileContent = (
                        <>
                            <span className={styles.statIcon}><Icon/></span>
                            <span className={styles.statNumber}>{stat.value}</span>
                            <span className={styles.statLabel}>{stat.label}</span>
                        </>
                    );
                    if (stat.path) {
                        return (
                            <button key={stat.label} type="button" className={tileClass} onClick={() => navigate(stat.path)}>
                                {tileContent}
                            </button>
                        );
                    }
                    return <div key={stat.label} className={tileClass}>{tileContent}</div>;
                })}
            </div>

            <div className={styles.grid}>
                <Card
                    title="Consultas de hoje"
                    count={panel.todaysAppointments.length}
                    footer={(
                        <>
                            <button
                                type="button"
                                className="button-secondary button-small"
                                onClick={() => navigate("/agendamento-prescritor")}
                            >
                                Ver a agenda
                            </button>
                            <button
                                type="button"
                                className="button-tertiary button-small"
                                onClick={() => navigate("/agenda/disponibilidade")}
                            >
                                Horários de atendimento
                            </button>
                        </>
                    )}
                >
                    {panel.todaysAppointments.length === 0 ? (
                        <p className={styles.empty}>Nenhuma consulta marcada para hoje.</p>
                    ) : (
                        <ItemList>
                            {panel.todaysAppointments.map((appointment) => (
                                <ListItem
                                    key={appointment.appointmentId}
                                    title={formatTime(appointment.dateTime) + " · " + appointment.patientName}
                                    details={modalityLabelOf(appointment.modality)}
                                    aside={isInTheFuture(appointment.dateTime) ? null : (
                                        <button
                                            type="button"
                                            className="button button-small"
                                            onClick={() => navigate("/consulta/" + appointment.appointmentId + "/registro")}
                                        >
                                            Registrar
                                        </button>
                                    )}
                                />
                            ))}
                        </ItemList>
                    )}
                </Card>

                <Card title="Pedidos esperando resposta" count={panel.waitingRequests.length}>
                    {panel.waitingRequests.length === 0 ? (
                        <p className={styles.empty}>Nenhum pedido esperando resposta.</p>
                    ) : (
                        <ItemList>
                            {panel.waitingRequests.map((request) => (
                                <ListItem
                                    key={request.appointmentId}
                                    title={request.patientName}
                                    details={(
                                        <>
                                            {formatDateTime(request.dateTime)} · {modalityLabelOf(request.modality)}
                                            {request.patientNote && (
                                                <span className={styles.note}>Motivo: {request.patientNote}</span>
                                            )}
                                        </>
                                    )}
                                    aside={(
                                        <button
                                            type="button"
                                            className="button button-small"
                                            onClick={() => navigate("/agendamento-prescritor")}
                                        >
                                            Responder
                                        </button>
                                    )}
                                />
                            ))}
                        </ItemList>
                    )}
                </Card>

                <Card title="Escalas vencidas" count={panel.lateScales.length}>
                    {panel.lateScales.length === 0 ? (
                        <p className={styles.empty}>Nenhuma escala vencida sem resposta.</p>
                    ) : (
                        <ItemList>
                            {panel.lateScales.map((scale) => (
                                <ListItem
                                    key={scale.taskId}
                                    title={scale.patientName}
                                    details={scale.scaleName + " · prazo em " + formatDate(scale.deadline)}
                                    aside={(
                                        <button
                                            type="button"
                                            className="button-secondary button-small"
                                            onClick={() => navigate("/paciente/" + scale.patientId + "/historico")}
                                        >
                                            Ver histórico
                                        </button>
                                    )}
                                />
                            ))}
                        </ItemList>
                    )}
                </Card>
            </div>

            <InvitePatientModal show={isInviteOpen} onClose={() => setIsInviteOpen(false)}/>
        </section>
    );
}
