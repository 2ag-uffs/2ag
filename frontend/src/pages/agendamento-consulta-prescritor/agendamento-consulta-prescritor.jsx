import {useEffect, useState} from "react";
import {useNavigate} from "react-router";
import {FiChevronLeft, FiChevronRight, FiClock} from "react-icons/fi";
import AppointmentStatusBadge from "../../components/appointment-status-badge/appointment-status-badge.jsx";
import Card from "../../components/card/card.jsx";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {modalityLabelOf} from "../../utils/appointment-labels.js";
import {
    addDays,
    formatDate,
    formatDateTime,
    formatTime,
    formatWeekdayAndDate,
    isInTheFuture,
    mondayOf,
    toIsoDate,
} from "../../utils/date-format.js";
import AppointmentFormModal from "./appointment-form-modal.jsx";
import DeclineRequestModal from "./decline-request-modal.jsx";
import styles from "./agendamento-consulta-prescritor.module.css";

const CONNECTION_ERROR_MESSAGE = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

// data e hora do formulario no formato q a api espera
function toApiDateTime(date, time) {
    return date + "T" + time + ":00";
}

// os sete dias da semana a partir da segunda
function weekDaysFrom(monday) {
    const days = [];
    for (let offset = 0; offset < 7; offset++) {
        days.push(toIsoDate(addDays(monday, offset)));
    }
    return days;
}

// agenda do prescritor (RF11)
// pedidos dos pacientes pra responder e as consultas da semana com atalho pros horarios de atendimento
export default function AgendamentoPrescritor() {
    const navigate = useNavigate();
    const [weekStart, setWeekStart] = useState(() => mondayOf(new Date()));
    const [agenda, setAgenda] = useState(null);
    const [loadError, setLoadError] = useState(null);
    const [notice, setNotice] = useState(null);
    const [actionError, setActionError] = useState(null);
    // somar um aqui busca a agenda de novo sem esconder a tela
    const [reloadCount, setReloadCount] = useState(0);
    const [isCreating, setIsCreating] = useState(false);
    const [rescheduleTarget, setRescheduleTarget] = useState(null);
    const [declineTarget, setDeclineTarget] = useState(null);
    const [cancelTarget, setCancelTarget] = useState(null);
    // consulta com pedido em andamento pra n mandar duas vezes
    const [busyAppointmentId, setBusyAppointmentId] = useState(null);

    const weekStartIso = toIsoDate(weekStart);
    const weekEndIso = toIsoDate(addDays(weekStart, 6));
    const todayIso = toIsoDate(new Date());
    const isCurrentWeek = weekStartIso === toIsoDate(mondayOf(new Date()));

    useEffect(() => {
        let isCurrentRequest = true;

        Promise.all([
            apiService.get("/appointments?from=" + weekStartIso + "&to=" + weekEndIso),
            apiService.get("/appointments/requests"),
            apiService.get("/availability"),
            apiService.get("/patients"),
        ])
            .then(([weekAppointments, waitingRequests, availability, patients]) => {
                if (isCurrentRequest) {
                    setAgenda({weekAppointments, waitingRequests, availability, patients});
                    setLoadError(null);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível carregar a agenda. Confira sua internet e tente de novo.");
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [weekStartIso, weekEndIso, reloadCount]);

    const finishAction = (message) => {
        setNotice(message);
        setActionError(null);
        setReloadCount((currentCount) => currentCount + 1);
    };

    const showActionError = (requestError) => {
        setActionError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
    };

    const confirmRequest = async (request) => {
        setNotice(null);
        setActionError(null);
        setBusyAppointmentId(request.id);
        try {
            await apiService.put("/appointments/" + request.id + "/confirm");
            finishAction("Consulta de " + request.patientName + " confirmada. O paciente recebeu o aviso.");
        } catch (requestError) {
            showActionError(requestError);
        } finally {
            setBusyAppointmentId(null);
        }
    };

    const cancelAppointment = async () => {
        const appointment = cancelTarget;
        setCancelTarget(null);
        setNotice(null);
        setActionError(null);
        setBusyAppointmentId(appointment.id);
        try {
            await apiService.put("/appointments/" + appointment.id + "/cancel");
            finishAction("Consulta de " + appointment.patientName + " cancelada. O paciente recebeu o aviso.");
        } catch (requestError) {
            showActionError(requestError);
        } finally {
            setBusyAppointmentId(null);
        }
    };

    // se a api recusar o erro volta pro modal mostrar
    const createAppointment = async (values) => {
        await apiService.post("/appointments", {
            patientId: Number(values.patientId),
            dateTime: toApiDateTime(values.date, values.time),
            modality: values.modality,
            durationMinutes: Number(values.durationMinutes),
        });
        setIsCreating(false);
        setWeekStart(mondayOf(new Date(values.date + "T00:00:00")));
        finishAction("Consulta marcada. O paciente recebeu o aviso.");
    };

    const rescheduleAppointment = async (values) => {
        const patientName = rescheduleTarget.patientName;
        await apiService.put("/appointments/" + rescheduleTarget.id, {
            dateTime: toApiDateTime(values.date, values.time),
            modality: values.modality,
            durationMinutes: Number(values.durationMinutes),
        });
        setRescheduleTarget(null);
        setWeekStart(mondayOf(new Date(values.date + "T00:00:00")));
        finishAction("Consulta de " + patientName + " remarcada. O paciente recebeu o aviso.");
    };

    const handleDeclined = () => {
        const patientName = declineTarget.patientName;
        setDeclineTarget(null);
        finishAction("Pedido de " + patientName + " recusado. O paciente recebeu o aviso.");
    };

    const openForm = (setTarget, target) => {
        setNotice(null);
        setActionError(null);
        setTarget(target);
    };

    if (loadError && agenda === null) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (agenda === null) {
        return <SkeletonPage cards={2}/>;
    }

    const {weekAppointments, waitingRequests, availability, patients} = agenda;

    // o botao de cada consulta depende da situacao dela
    const appointmentActions = (appointment) => {
        const isBusy = busyAppointmentId === appointment.id;
        if (appointment.annulled) {
            return null;
        }
        if (appointment.status === "SOLICITADA") {
            return (
                <>
                    {isInTheFuture(appointment.dateTime) && (
                        <button
                            type="button"
                            className="button button-small"
                            onClick={() => confirmRequest(appointment)}
                            disabled={isBusy}
                        >
                            Confirmar
                        </button>
                    )}
                    <button
                        type="button"
                        className="button-secondary button-small"
                        onClick={() => openForm(setDeclineTarget, appointment)}
                        disabled={isBusy}
                    >
                        Recusar
                    </button>
                </>
            );
        }
        if (appointment.status === "AGENDADA" || appointment.status === "EM_ANDAMENTO") {
            const hasStarted = !isInTheFuture(appointment.dateTime);
            return (
                <>
                    {hasStarted ? (
                        <button
                            type="button"
                            className="button button-small"
                            onClick={() => navigate("/consulta/" + appointment.id + "/registro")}
                        >
                            Registrar atendimento
                        </button>
                    ) : (
                        <button
                            type="button"
                            className="button-secondary button-small"
                            onClick={() => openForm(setRescheduleTarget, appointment)}
                            disabled={isBusy}
                        >
                            Remarcar
                        </button>
                    )}
                    <button
                        type="button"
                        className="button-danger button-small"
                        onClick={() => openForm(setCancelTarget, appointment)}
                        disabled={isBusy}
                    >
                        Cancelar
                    </button>
                </>
            );
        }
        if (appointment.status === "CONCLUIDA") {
            return (
                <button
                    type="button"
                    className="button-secondary button-small"
                    onClick={() => navigate("/paciente/" + appointment.patientId + "/historico")}
                >
                    Ver histórico
                </button>
            );
        }
        return null;
    };

    const renderAppointment = (appointment, showDate) => (
        <li key={appointment.id} className={styles.appointment}>
            <div className={styles.appointmentTime}>
                {showDate && <span className={styles.appointmentDate}>{formatDate(appointment.dateTime)}</span>}
                <strong>{formatTime(appointment.dateTime)}</strong>
                <span className={styles.duration}>{appointment.durationMinutes} min</span>
            </div>
            <div className={styles.appointmentInfo}>
                <div className={styles.appointmentTitle}>
                    <span className={styles.patientName}>{appointment.patientName}</span>
                    <AppointmentStatusBadge appointment={appointment}/>
                </div>
                <p className={styles.meta}>{modalityLabelOf(appointment.modality)}</p>
                {appointment.patientNote && <p className={styles.patientNote}>Motivo: {appointment.patientNote}</p>}
            </div>
            <div className={styles.appointmentActions}>{appointmentActions(appointment)}</div>
        </li>
    );

    return (
        <section className={styles.page}>
            <PageHeader
                title="Agenda"
                subtitle="Responda os pedidos dos pacientes e acompanhe as consultas da semana."
                actions={(
                    <>
                        <button
                            type="button"
                            className="button-secondary"
                            onClick={() => navigate("/agenda/disponibilidade")}
                        >
                            <FiClock aria-hidden="true"/>
                            Horários de atendimento
                        </button>
                        <button
                            type="button"
                            className="button"
                            onClick={() => openForm(setIsCreating, true)}
                            disabled={patients.length === 0}
                        >
                            Nova consulta
                        </button>
                    </>
                )}
            />

            {availability.periods.length === 0 && (
                <div className={"aviso aviso--atencao " + styles.warning}>
                    <span>
                        Você ainda não cadastrou horários de atendimento, então seus pacientes não conseguem pedir
                        consulta.
                    </span>
                    <button
                        type="button"
                        className="button-secondary button-small"
                        onClick={() => navigate("/agenda/disponibilidade")}
                    >
                        Cadastrar horários
                    </button>
                </div>
            )}
            {notice && <p className="aviso" role="status">{notice}</p>}
            {actionError && <p className="aviso aviso--atencao" role="alert">{actionError}</p>}
            {loadError && <p className="aviso aviso--atencao">{loadError}</p>}

            <Card title="Pedidos aguardando resposta" count={waitingRequests.length}>
                {waitingRequests.length === 0 ? (
                    <p className={styles.empty}>Nenhum pedido esperando resposta.</p>
                ) : (
                    <ul className={styles.list}>
                        {waitingRequests.map((request) => renderAppointment(request, true))}
                    </ul>
                )}
            </Card>

            <section className={styles.section} aria-labelledby="semana-da-agenda">
                <div className={styles.weekHeader}>
                    <h2 id="semana-da-agenda" className={styles.sectionTitle}>
                        Semana de {formatDate(weekStartIso)} a {formatDate(weekEndIso)}
                    </h2>
                    <div className={styles.weekNavigation}>
                        <button
                            type="button"
                            className="button-secondary button-small"
                            onClick={() => setWeekStart(addDays(weekStart, -7))}
                            aria-label="Semana anterior"
                            title="Semana anterior"
                        >
                            <FiChevronLeft aria-hidden="true"/>
                        </button>
                        <button
                            type="button"
                            className="button-tertiary button-small"
                            onClick={() => setWeekStart(mondayOf(new Date()))}
                            disabled={isCurrentWeek}
                        >
                            Esta semana
                        </button>
                        <button
                            type="button"
                            className="button-secondary button-small"
                            onClick={() => setWeekStart(addDays(weekStart, 7))}
                            aria-label="Próxima semana"
                            title="Próxima semana"
                        >
                            <FiChevronRight aria-hidden="true"/>
                        </button>
                    </div>
                </div>
                <div className={styles.days}>
                    {weekDaysFrom(weekStart).map((dayIso) => {
                        const dayAppointments = weekAppointments.filter((appointment) =>
                            appointment.dateTime.slice(0, 10) === dayIso);
                        const isToday = dayIso === todayIso;
                        return (
                            <div key={dayIso} className={isToday ? styles.day + " " + styles.dayToday : styles.day}>
                                <h3 className={styles.dayName}>
                                    {formatWeekdayAndDate(dayIso)}
                                    {isToday && <span className={styles.todayTag}>hoje</span>}
                                </h3>
                                {dayAppointments.length === 0 ? (
                                    <p className={styles.dayEmpty}>Nenhuma consulta.</p>
                                ) : (
                                    <ul className={styles.list}>
                                        {dayAppointments.map((appointment) => renderAppointment(appointment, false))}
                                    </ul>
                                )}
                            </div>
                        );
                    })}
                </div>
            </section>

            {isCreating && (
                <AppointmentFormModal
                    title="Nova consulta"
                    submitLabel="Marcar consulta"
                    patients={patients}
                    initialValues={{
                        patientId: "",
                        date: toIsoDate(new Date()),
                        time: "",
                        modality: "PRESENCIAL",
                        durationMinutes: String(availability.appointmentDurationMinutes),
                    }}
                    onSubmit={createAppointment}
                    onClose={() => setIsCreating(false)}
                />
            )}

            {rescheduleTarget && (
                <AppointmentFormModal
                    title={"Remarcar consulta de " + rescheduleTarget.patientName}
                    submitLabel="Remarcar"
                    initialValues={{
                        date: rescheduleTarget.dateTime.slice(0, 10),
                        time: rescheduleTarget.dateTime.slice(11, 16),
                        modality: rescheduleTarget.modality,
                        durationMinutes: String(rescheduleTarget.durationMinutes),
                    }}
                    onSubmit={rescheduleAppointment}
                    onClose={() => setRescheduleTarget(null)}
                />
            )}

            {declineTarget && (
                <DeclineRequestModal
                    request={declineTarget}
                    onClose={() => setDeclineTarget(null)}
                    onDeclined={handleDeclined}
                />
            )}

            <ConfirmModal
                show={cancelTarget !== null}
                title="Cancelar consulta"
                message={cancelTarget !== null
                    ? "A consulta de " + cancelTarget.patientName + " em " + formatDateTime(cancelTarget.dateTime)
                    + " vai ser cancelada e o paciente recebe um aviso."
                    : ""}
                confirmText="Cancelar consulta"
                cancelText="Voltar"
                onConfirm={cancelAppointment}
                onCancel={() => setCancelTarget(null)}
            />
        </section>
    );
}
