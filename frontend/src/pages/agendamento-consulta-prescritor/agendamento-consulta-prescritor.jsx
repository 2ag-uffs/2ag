import {useEffect, useRef, useState} from "react";
import {useNavigate} from "react-router";
import {FiChevronLeft, FiChevronRight, FiClock} from "react-icons/fi";
import AppointmentStatusBadge from "../../components/appointment-status-badge/appointment-status-badge.jsx";
import Card from "../../components/card/card.jsx";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import {LoadError} from "../../components/error-card/error-card.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, errorMessageOf, loadErrorOf} from "../../services/api.js";
import {appointmentTypeLabelOf, modalityLabelOf} from "../../utils/appointment-labels.js";
import {ARCHIVED_ATTENDANCE_NOTICE} from "../../utils/archived-patient.js";
import {
    addDays,
    formatDate,
    formatDateTime,
    formatTime,
    formatWeekdayAndDate,
    hasEnded,
    isInTheFuture,
    mondayOf,
    toApiDateTime,
    toIsoDate,
} from "../../utils/date-format.js";
import {emptyToNull, numberOrNull} from "../../utils/form-values.js";
import {isPhoneWidth} from "../../utils/phone-width.js";
import AppointmentFormModal from "./appointment-form-modal.jsx";
import DeclineRequestModal from "./decline-request-modal.jsx";
import styles from "./agendamento-consulta-prescritor.module.css";

// tipo e duracao vazios vao nulos e a api decide pelo historico na nova e mantendo o q era na remarcacao
function scheduleFieldsOf(values) {
    return {
        dateTime: toApiDateTime(values.date, values.time),
        modality: values.modality,
        durationMinutes: numberOrNull(values.durationMinutes),
        appointmentType: emptyToNull(values.appointmentType),
    };
}

// T00:00:00 p/ ler o dia no horario local e n em utc
function mondayOfDay(dayIso) {
    return mondayOf(new Date(dayIso + "T00:00:00"));
}

// os sete dias da semana a partir da segunda
function weekDaysFrom(monday) {
    const days = [];
    for (let offset = 0; offset < 7; offset++) {
        days.push(toIsoDate(addDays(monday, offset)));
    }
    return days;
}

// pedido recusado e consulta cancelada n tem botao nenhum e enchiam a grade da semana, entao ficam escondidos ate
// o prescritor pedir pra ver (decisao de 17/11/2026). a anulada continua aparecendo com o selo dela
function isRefusedOrCanceled(appointment) {
    return !appointment.annulled && (appointment.status === "RECUSADA" || appointment.status === "CANCELADA");
}

// a contagem so aparece qnd a semana tem algum escondido
function toggleLabelOf(showsRefusedAndCanceled, refusedAndCanceledCount) {
    if (showsRefusedAndCanceled) {
        return "Esconder recusados e cancelados";
    }
    if (refusedAndCanceledCount === 0) {
        return "Mostrar recusados e cancelados";
    }
    return "Mostrar recusados e cancelados (" + refusedAndCanceledCount + ")";
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
    const [noShowTarget, setNoShowTarget] = useState(null);
    // consulta com pedido em andamento pra n mandar duas vezes
    const [busyAppointmentId, setBusyAppointmentId] = useState(null);
    // a escolha vale enquanto a tela esta aberta, tbm qnd troca de semana
    const [showsRefusedAndCanceled, setShowsRefusedAndCanceled] = useState(false);

    const weekStartIso = toIsoDate(weekStart);
    const weekEndIso = toIsoDate(addDays(weekStart, 6));
    const todayIso = toIsoDate(new Date());
    const isCurrentWeek = weekStartIso === toIsoDate(mondayOf(new Date()));
    const todayRef = useRef(null);
    const hasOpenedOnToday = useRef(false);

    useEffect(() => {
        let isCurrentRequest = true;

        Promise.all([
            apiService.get("/appointments?from=" + weekStartIso + "&to=" + weekEndIso),
            apiService.get("/appointments/requests"),
            apiService.get("/availability"),
            apiService.get("/patients"),
            apiService.get("/availability/days-off"),
        ])
            .then(([weekAppointments, waitingRequests, availability, patients, daysOff]) => {
                if (isCurrentRequest) {
                    setAgenda({weekAppointments, waitingRequests, availability, patients, daysOff});
                    setLoadError(null);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(loadErrorOf(requestError,
                        "Não foi possível carregar a agenda. Confira sua internet e tente de novo."));
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [weekStartIso, weekEndIso, reloadCount]);

    // no celular os dias ficam um embaixo do outro e hoje podia estar la no fim: a semana abre rolada ate o
    // dia de hoje. so na primeira vez q a agenda chega, pra n puxar a tela dps de cada acao
    // (decisao da dona do projeto, novembro de 2026)
    useEffect(() => {
        if (agenda === null || hasOpenedOnToday.current) {
            return;
        }
        hasOpenedOnToday.current = true;
        if (isPhoneWidth() && todayRef.current) {
            todayRef.current.scrollIntoView({block: "start"});
        }
    }, [agenda]);

    const finishAction = (message) => {
        setNotice(message);
        setActionError(null);
        setReloadCount((currentCount) => currentCount + 1);
    };

    const showActionError = (requestError) => {
        setActionError(errorMessageOf(requestError));
    };

    const clearMessages = () => {
        setNotice(null);
        setActionError(null);
    };

    // confirmar cancelar e marcar falta so mudam o fim do endereco e o aviso
    const runAppointmentAction = async (appointment, action, successMessage) => {
        clearMessages();
        setBusyAppointmentId(appointment.id);
        try {
            await apiService.put("/appointments/" + appointment.id + "/" + action);
            finishAction(successMessage);
        } catch (requestError) {
            showActionError(requestError);
        } finally {
            setBusyAppointmentId(null);
        }
    };

    const confirmRequest = async (request) => {
        await runAppointmentAction(request, "confirm",
            "Consulta de " + request.patientName + " confirmada. O paciente recebeu o aviso.");
    };

    const cancelAppointment = async () => {
        const appointment = cancelTarget;
        setCancelTarget(null);
        await runAppointmentAction(appointment, "cancel",
            "Consulta de " + appointment.patientName + " cancelada. O paciente recebeu o aviso.");
    };

    const markNoShow = async () => {
        const appointment = noShowTarget;
        setNoShowTarget(null);
        await runAppointmentAction(appointment, "no-show",
            "Falta de " + appointment.patientName + " registrada. O paciente recebeu o aviso.");
    };

    // sem try pq se a api recusar o erro volta pro modal mostrar
    const createAppointment = async (values) => {
        await apiService.post("/appointments", {patientId: Number(values.patientId), ...scheduleFieldsOf(values)});
        setIsCreating(false);
        setWeekStart(mondayOfDay(values.date));
        finishAction("Consulta marcada. O paciente recebeu o aviso.");
    };

    const rescheduleAppointment = async (values) => {
        const patientName = rescheduleTarget.patientName;
        await apiService.put("/appointments/" + rescheduleTarget.id, scheduleFieldsOf(values));
        setRescheduleTarget(null);
        setWeekStart(mondayOfDay(values.date));
        finishAction("Consulta de " + patientName + " remarcada. O paciente recebeu o aviso.");
    };

    const handleDeclined = () => {
        const patientName = declineTarget.patientName;
        setDeclineTarget(null);
        finishAction("Pedido de " + patientName + " recusado. O paciente recebeu o aviso.");
    };

    const openForm = (setTarget, target) => {
        clearMessages();
        setTarget(target);
    };

    if (loadError && agenda === null) {
        return <LoadError error={loadError}/>;
    }

    if (agenda === null) {
        return <SkeletonPage cards={2}/>;
    }

    const {weekAppointments, waitingRequests, availability, patients, daysOff} = agenda;
    const daysOffDates = daysOff.map((dayOff) => dayOff.date);
    const refusedAndCanceledCount = weekAppointments.filter(isRefusedOrCanceled).length;
    const shownAppointments = showsRefusedAndCanceled
        ? weekAppointments
        : weekAppointments.filter((appointment) => !isRefusedOrCanceled(appointment));
    // a duracao q o modal preenche qnd o prescritor escolhe o tipo
    const durationsByType = {
        PRIMEIRA_CONSULTA: availability.firstAppointmentDurationMinutes,
        RETORNO: availability.appointmentDurationMinutes,
    };

    const requestActions = (request, isBusy) => (
        <>
            {isInTheFuture(request.dateTime) && (
                <button
                    type="button"
                    className="button button-small"
                    onClick={() => confirmRequest(request)}
                    disabled={isBusy}
                >
                    Confirmar
                </button>
            )}
            <button
                type="button"
                className="button-secondary button-small"
                onClick={() => openForm(setDeclineTarget, request)}
                disabled={isBusy}
            >
                Recusar
            </button>
        </>
    );

    const scheduledActions = (appointment, isBusy) => {
        const hasStarted = !isInTheFuture(appointment.dateTime);
        const isOver = hasEnded(appointment.dateTime, appointment.durationMinutes);
        // a falta so depois q a hora marcada terminou, igual a api pede
        const isOverAndScheduled = appointment.status === "AGENDADA" && isOver;
        return (
            <>
                {hasStarted && appointment.patientArchived && (
                    <span className={styles.meta}>{ARCHIVED_ATTENDANCE_NOTICE}</span>
                )}
                {hasStarted && !appointment.patientArchived && (
                    <button
                        type="button"
                        className="button button-small"
                        onClick={() => navigate("/consulta/" + appointment.id + "/registro")}
                    >
                        Registrar atendimento
                    </button>
                )}
                {!hasStarted && (
                    <button
                        type="button"
                        className="button-secondary button-small"
                        onClick={() => openForm(setRescheduleTarget, appointment)}
                        disabled={isBusy}
                    >
                        Remarcar
                    </button>
                )}
                {isOverAndScheduled && (
                    <button
                        type="button"
                        className="button-secondary button-small"
                        onClick={() => openForm(setNoShowTarget, appointment)}
                        disabled={isBusy}
                    >
                        Marcar falta
                    </button>
                )}
                {/* dps do fim a api n cancela mais, sobram registrar e marcar falta (decisao de 17/11/2026) */}
                {!isOver && (
                    <button
                        type="button"
                        className="button-danger button-small"
                        onClick={() => openForm(setCancelTarget, appointment)}
                        disabled={isBusy}
                    >
                        Cancelar
                    </button>
                )}
            </>
        );
    };

    // o botao de cada consulta depende da situacao dela
    const appointmentActions = (appointment) => {
        const isBusy = busyAppointmentId === appointment.id;
        if (appointment.annulled) {
            return null;
        }
        if (appointment.status === "SOLICITADA") {
            return requestActions(appointment, isBusy);
        }
        if (appointment.status === "AGENDADA" || appointment.status === "EM_ANDAMENTO") {
            return scheduledActions(appointment, isBusy);
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
        // falta marcada por engano: a api aceita o registro e ele desfaz a falta e conclui a consulta
        // a do arquivado fica como falta ate ele ser reativado (decisao de 17/11/2026)
        if (appointment.status === "NAO_COMPARECEU" && appointment.patientArchived) {
            return <span className={styles.meta}>{ARCHIVED_ATTENDANCE_NOTICE}</span>;
        }
        if (appointment.status === "NAO_COMPARECEU") {
            return (
                <button
                    type="button"
                    className="button-secondary button-small"
                    onClick={() => navigate("/consulta/" + appointment.id + "/registro")}
                >
                    Desfazer falta e registrar atendimento
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
                    {/* o paciente n ve a confirmacao nem recebe lembrete, mas a consulta continua valendo (R25) */}
                    {appointment.patientAccountActive === false && (
                        <span className="etiqueta etiqueta--anulada">Conta desativada</span>
                    )}
                </div>
                <p className={styles.meta}>
                    {[appointmentTypeLabelOf(appointment.appointmentType), modalityLabelOf(appointment.modality)]
                        .filter((text) => text !== "")
                        .join(" · ")}
                </p>
                {appointment.patientNote && <p className={styles.patientNote}>Motivo: {appointment.patientNote}</p>}
            </div>
            <div className={styles.appointmentActions}>{appointmentActions(appointment)}</div>
        </li>
    );

    const renderDay = (dayIso) => {
        const dayAppointments = shownAppointments.filter((appointment) => appointment.dateTime.slice(0, 10) === dayIso);
        const isToday = dayIso === todayIso;
        const isDayOff = daysOffDates.includes(dayIso);
        let dayClass = styles.day;
        if (isToday) {
            dayClass = dayClass + " " + styles.dayToday;
        }
        // o dia sem consulta eh uma linha curta, q n estica ate a altura do dia cheio do lado
        if (dayAppointments.length === 0) {
            dayClass = dayClass + " " + styles.dayWithoutAppointments;
        }
        return (
            <div key={dayIso} ref={isToday ? todayRef : null} className={dayClass}>
                <h3 className={styles.dayName}>
                    {formatWeekdayAndDate(dayIso)}
                    <span className={styles.dayTags}>
                        {isToday && <span className="etiqueta etiqueta--ok">hoje</span>}
                        {/* folga marcada nos horarios de atendimento: o paciente n pede, mas o q ja estava marcado fica */}
                        {isDayOff && <span className="etiqueta etiqueta--atencao">sem atendimento</span>}
                    </span>
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
    };

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
            {loadError && <p className="aviso aviso--atencao" role="alert">{loadError.message}</p>}

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
                <div>
                    <button
                        type="button"
                        className="button-secondary button-small"
                        onClick={() => setShowsRefusedAndCanceled(!showsRefusedAndCanceled)}
                    >
                        {toggleLabelOf(showsRefusedAndCanceled, refusedAndCanceledCount)}
                    </button>
                </div>
                <div className={styles.days}>
                    {weekDaysFrom(weekStart).map((dayIso) => renderDay(dayIso))}
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
                        modality: "",
                        appointmentType: "",
                        durationMinutes: "",
                    }}
                    durationsByType={durationsByType}
                    typePlaceholder="Pelo histórico do paciente"
                    durationPlaceholder="Padrão do tipo"
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
                        modality: rescheduleTarget.modality || "",
                        appointmentType: rescheduleTarget.appointmentType || "",
                        durationMinutes: String(rescheduleTarget.durationMinutes),
                    }}
                    durationsByType={durationsByType}
                    typePlaceholder="Manter como está"
                    durationPlaceholder="Manter como está"
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
                danger={true}
                cancelText="Voltar"
                onConfirm={cancelAppointment}
                onCancel={() => setCancelTarget(null)}
            />

            <ConfirmModal
                show={noShowTarget !== null}
                title="Marcar falta"
                message={noShowTarget !== null
                    ? "A consulta de " + noShowTarget.patientName + " em " + formatDateTime(noShowTarget.dateTime)
                    + " vai ficar como falta e o paciente recebe um aviso. Se ele foi atendido, registre o"
                    + " atendimento em vez disso."
                    : ""}
                confirmText="Marcar falta"
                danger={true}
                cancelText="Voltar"
                onConfirm={markNoShow}
                onCancel={() => setNoShowTarget(null)}
            />
        </section>
    );
}
