import {useEffect, useState} from "react";
import {useLocation, useNavigate} from "react-router";
import {FiAlertCircle, FiBell, FiCalendar, FiInbox, FiUsers} from "react-icons/fi";
import Card from "../../components/card/card.jsx";
import InvitePatientModal from "../../components/invite-patient-modal/invite-patient-modal.jsx";
import ItemList, {ListItem} from "../../components/item-list/item-list.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import ShownOfTotal from "../../components/shown-of-total/shown-of-total.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, errorMessageOf, getLoggedUser} from "../../services/api.js";
import {modalityLabelOf} from "../../utils/appointment-labels.js";
import {formatDate, formatDateTime, formatTime, isInTheFuture} from "../../utils/date-format.js";
import {firstNameOf} from "../../utils/initials.js";
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

    useEffect(() => {
        if (!loggedUser) {
            navigate("/entrar");
            return;
        }
        apiService.get("/dashboard/prescriber/" + loggedUser.id)
            .then(setPanel)
            .catch((requestError) => {
                setLoadError(errorMessageOf(requestError,
                    "Não foi possível carregar o painel. Confira sua internet e tente de novo."));
            });
    }, [loggedUser, navigate]);

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!panel) {
        return <SkeletonPage cards={3} stats={4}/>;
    }

    const firstName = firstNameOf(loggedUser.name);

    return (
        <section className={styles.page}>
            <PageHeader
                title={"Olá, " + firstName}
                subtitle="O que precisa de você hoje."
                actions={(
                    <>
                        {/* no celular a barra de baixo n tem Indicadores, entao o caminho eh por aqui */}
                        <button type="button" className="button-tertiary" onClick={() => navigate("/indicadores")}>
                            Indicadores
                        </button>
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
                {statsOf(panel).map((stat) => (
                    <StatTile key={stat.label} stat={stat}/>
                ))}
            </div>

            <div className={styles.grid}>
                <TodaysAppointmentsCard appointments={panel.todaysAppointments}/>
                <WaitingRequestsCard requests={panel.waitingRequests} total={panel.waitingRequestsTotal}/>
                <LateScalesCard scales={panel.lateScales} total={panel.lateScalesTotal}/>
            </div>

            <InvitePatientModal show={isInviteOpen} onClose={() => setIsInviteOpen(false)}/>
        </section>
    );
}

// as listas vem com no maximo cinco entao o numero eh sempre o total q a api manda
function statsOf(panel) {
    return [
        {label: "Pacientes ativos", value: panel.activePatients, icon: FiUsers, path: "/lista-paciente"},
        {label: "Consultas hoje", value: panel.todaysAppointments.length, icon: FiCalendar, path: "/agendamento-prescritor"},
        {
            label: "Pedidos esperando",
            value: panel.waitingRequestsTotal,
            icon: FiInbox,
            path: "/agendamento-prescritor",
            needsAttention: panel.waitingRequestsTotal > 0,
        },
        {
            label: "Escalas vencidas",
            value: panel.lateScalesTotal,
            icon: FiAlertCircle,
            needsAttention: panel.lateScalesTotal > 0,
        },
        {
            label: "Avisos não lidos",
            value: panel.unreadNotifications,
            icon: FiBell,
            path: "/notificacoes",
            needsAttention: panel.unreadNotifications > 0,
        },
    ];
}

// o numero q leva pra alguma tela vira botao e o q pede atencao ganha o destaque
function StatTile({stat}) {
    const navigate = useNavigate();
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
            <button type="button" className={tileClass} onClick={() => navigate(stat.path)}>
                {tileContent}
            </button>
        );
    }
    return <div className={tileClass}>{tileContent}</div>;
}

function TodaysAppointmentsCard({appointments}) {
    const navigate = useNavigate();

    return (
        <Card
            title="Consultas de hoje"
            count={appointments.length}
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
            {appointments.length === 0 ? (
                <p className={styles.empty}>Nenhuma consulta marcada para hoje.</p>
            ) : (
                <ItemList>
                    {appointments.map((appointment) => (
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
    );
}

function WaitingRequestsCard({requests, total}) {
    const navigate = useNavigate();

    return (
        <Card
            title="Pedidos esperando resposta"
            count={total}
            footer={(
                <button
                    type="button"
                    className="button-secondary button-small"
                    onClick={() => navigate("/agendamento-prescritor")}
                >
                    Ver todos os pedidos
                </button>
            )}
        >
            {requests.length === 0 ? (
                <p className={styles.empty}>Nenhum pedido esperando resposta.</p>
            ) : (
                <ItemList>
                    {requests.map((request) => (
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
            <ShownOfTotal shown={requests.length} total={total}/>
        </Card>
    );
}

// n tem tela com todas as vencidas entao o cartao diz qnd mostra so uma parte
function LateScalesCard({scales, total}) {
    const navigate = useNavigate();

    return (
        <Card title="Escalas vencidas" count={total}>
            {scales.length === 0 ? (
                <p className={styles.empty}>Nenhuma escala vencida sem resposta.</p>
            ) : (
                <ItemList>
                    {scales.map((scale) => (
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
            <ShownOfTotal shown={scales.length} total={total}/>
        </Card>
    );
}
