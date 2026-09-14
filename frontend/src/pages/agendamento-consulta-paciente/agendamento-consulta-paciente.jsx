import {useEffect, useState} from "react";
import AppointmentStatusBadge from "../../components/appointment-status-badge/appointment-status-badge.jsx";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import SelectField from "../../components/form/select-field.jsx";
import TextAreaField from "../../components/form/text-area-field.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import {MODALITY_OPTIONS, modalityLabelOf} from "../../utils/appointment-labels.js";
import {addDays, formatDateTime, formatTime, formatWeekdayAndDate, toIsoDate} from "../../utils/date-format.js";
import styles from "./agendamento-consulta-paciente.module.css";

const CONNECTION_ERROR_MESSAGE = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

// quantos dias pra frente o paciente ve horario livre
const DAYS_AHEAD = 30;

// o paciente cancela sozinho a consulta marcada so com essa antecedencia
const PATIENT_CANCELLATION_HOURS = 24;

// separa os horarios livres pelo dia
function groupSlotsByDay(freeSlots) {
    const slotsByDay = {};
    freeSlots.forEach((slot) => {
        const dayIso = slot.start.slice(0, 10);
        if (!slotsByDay[dayIso]) {
            slotsByDay[dayIso] = [];
        }
        slotsByDay[dayIso].push(slot);
    });
    return slotsByDay;
}

// o pedido o paciente cancela a qualquer hora e a consulta marcada com 24 horas de antecedencia
function canPatientCancel(appointment) {
    if (appointment.annulled) {
        return false;
    }
    if (appointment.status === "SOLICITADA") {
        return true;
    }
    if (appointment.status !== "AGENDADA") {
        return false;
    }
    const millisecondsUntil = new Date(appointment.dateTime.slice(0, 19)).getTime() - Date.now();
    return millisecondsUntil >= PATIENT_CANCELLATION_HOURS * 60 * 60 * 1000;
}

// consultas do paciente (RF10)
// o paciente pede um horario livre na agenda do prescritor e acompanha os pedidos e as consultas marcadas
export default function AgendamentoConsultaPaciente() {
    const loggedUser = getLoggedUser();
    const [page, setPage] = useState(null);
    const [loadError, setLoadError] = useState(null);
    const [notice, setNotice] = useState(null);
    const [actionError, setActionError] = useState(null);
    // somar um aqui busca tudo de novo sem esconder a tela
    const [reloadCount, setReloadCount] = useState(0);
    const [selectedDay, setSelectedDay] = useState(null);
    const [selectedSlot, setSelectedSlot] = useState(null);
    const [modality, setModality] = useState("PRESENCIAL");
    const [patientNote, setPatientNote] = useState("");
    const [isSending, setIsSending] = useState(false);
    const [cancelTarget, setCancelTarget] = useState(null);
    const [busyAppointmentId, setBusyAppointmentId] = useState(null);

    useEffect(() => {
        let isCurrentRequest = true;
        const firstDay = toIsoDate(new Date());
        const lastDay = toIsoDate(addDays(new Date(), DAYS_AHEAD - 1));

        Promise.all([
            apiService.get("/paciente/" + loggedUser.id),
            apiService.get("/consulta/minhas"),
            apiService.get("/consulta/horarios-livres?inicio=" + firstDay + "&fim=" + lastDay),
        ])
            .then(([patient, upcomingAppointments, freeSlots]) => {
                if (isCurrentRequest) {
                    setPage({patient, upcomingAppointments, freeSlots});
                    setLoadError(null);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível carregar suas consultas. Confira sua internet e tente de novo.");
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [loggedUser.id, reloadCount]);

    const reload = () => {
        setReloadCount((currentCount) => currentCount + 1);
    };

    const showActionError = (requestError) => {
        setActionError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
    };

    const chooseDay = (dayIso) => {
        setSelectedDay(dayIso);
        setSelectedSlot(null);
        setNotice(null);
        setActionError(null);
    };

    const chooseSlot = (slotStart) => {
        setSelectedSlot(slotStart);
        setNotice(null);
        setActionError(null);
    };

    const sendRequest = async (slotStart) => {
        setIsSending(true);
        setNotice(null);
        setActionError(null);
        try {
            await apiService.post("/consulta/agendamento", {
                dateTime: slotStart,
                modality,
                patientNote: patientNote.trim() === "" ? null : patientNote.trim(),
            });
            setNotice("Pedido enviado para " + page.patient.prescriberName
                + ". O horário fica reservado e você recebe um aviso quando o pedido for respondido.");
            setSelectedSlot(null);
            setPatientNote("");
        } catch (requestError) {
            showActionError(requestError);
        } finally {
            setIsSending(false);
            // mesmo com erro vale buscar de novo pq o horario pode ter sido pedido por outra pessoa
            reload();
        }
    };

    const cancelAppointment = async () => {
        const appointment = cancelTarget;
        setCancelTarget(null);
        setNotice(null);
        setActionError(null);
        setBusyAppointmentId(appointment.id);
        try {
            await apiService.put("/consulta/" + appointment.id + "/cancelar");
            setNotice(appointment.status === "SOLICITADA"
                ? "Pedido cancelado. O horário voltou a ficar livre."
                : "Consulta cancelada. Seu prescritor recebeu o aviso.");
            reload();
        } catch (requestError) {
            showActionError(requestError);
        } finally {
            setBusyAppointmentId(null);
        }
    };

    if (loadError && page === null) {
        return <p className="aviso aviso--atencao">{loadError}</p>;
    }

    if (page === null) {
        return <p className={styles.status}>Carregando...</p>;
    }

    const {patient, upcomingAppointments, freeSlots} = page;
    const hasPrescriber = patient.prescriberId !== null;
    const slotsByDay = groupSlotsByDay(freeSlots);
    const availableDays = Object.keys(slotsByDay).sort();
    // o dia escolhido pode ter ficado sem horario depois de buscar de novo
    const currentDay = selectedDay !== null && slotsByDay[selectedDay] ? selectedDay : availableDays[0];
    const daySlots = currentDay ? slotsByDay[currentDay] : [];
    const currentSlot = daySlots.some((slot) => slot.start === selectedSlot) ? selectedSlot : null;

    return (
        <section className={styles.page}>
            <div>
                <h1>Consultas</h1>
                <p className={styles.subtitle}>
                    {hasPrescriber
                        ? "Peça um horário na agenda de " + patient.prescriberName
                        + ". O horário fica reservado até o pedido ser respondido."
                        : "Você ainda não tem um prescritor vinculado. Fale com a clínica."}
                </p>
            </div>

            {notice && <p className="aviso" role="status">{notice}</p>}
            {actionError && <p className="aviso aviso--atencao" role="alert">{actionError}</p>}
            {loadError && <p className="aviso aviso--atencao">{loadError}</p>}

            {hasPrescriber && (
                <section className={styles.card} aria-labelledby="pedir-consulta">
                    <h2 id="pedir-consulta" className={styles.sectionTitle}>Pedir consulta</h2>

                    {availableDays.length === 0 ? (
                        <p className={styles.status}>
                            Não há horário livre nos próximos {DAYS_AHEAD} dias. Tente de novo mais tarde ou fale com
                            a clínica.
                        </p>
                    ) : (
                        <>
                            <div className={styles.step}>
                                <h3 className={styles.stepTitle}>Dia</h3>
                                <div className={styles.days} role="group" aria-label="Dias com horário livre">
                                    {availableDays.map((dayIso) => (
                                        <button
                                            key={dayIso}
                                            type="button"
                                            className={dayIso === currentDay ? styles.dayButtonSelected : styles.dayButton}
                                            aria-pressed={dayIso === currentDay}
                                            onClick={() => chooseDay(dayIso)}
                                        >
                                            <span className={styles.weekday}>{formatWeekdayAndDate(dayIso)}</span>
                                            <span className={styles.slotCount}>
                                                {slotsByDay[dayIso].length === 1
                                                    ? "1 horário"
                                                    : slotsByDay[dayIso].length + " horários"}
                                            </span>
                                        </button>
                                    ))}
                                </div>
                            </div>

                            <div className={styles.step}>
                                <h3 className={styles.stepTitle}>Horário</h3>
                                <div className={styles.slots} role="group" aria-label="Horários livres do dia">
                                    {daySlots.map((slot) => (
                                        <button
                                            key={slot.start}
                                            type="button"
                                            className={slot.start === currentSlot ? styles.slotButtonSelected : styles.slotButton}
                                            aria-pressed={slot.start === currentSlot}
                                            onClick={() => chooseSlot(slot.start)}
                                        >
                                            {formatTime(slot.start)}
                                        </button>
                                    ))}
                                </div>
                            </div>

                            {currentSlot && (
                                <div className={styles.step}>
                                    <div className={styles.formRow}>
                                        <SelectField
                                            label="Modalidade"
                                            name="modality"
                                            options={MODALITY_OPTIONS}
                                            value={modality}
                                            onChange={(event) => setModality(event.target.value)}
                                            disabled={isSending}
                                        />
                                    </div>
                                    <TextAreaField
                                        label="Motivo da consulta (opcional)"
                                        name="patientNote"
                                        hint="Conte em poucas palavras o que você quer tratar. Isso ajuda seu prescritor a se preparar."
                                        rows={3}
                                        maxLength={1000}
                                        value={patientNote}
                                        onChange={(event) => setPatientNote(event.target.value)}
                                        disabled={isSending}
                                    />
                                    <p className={styles.summary}>
                                        Pedido para {formatDateTime(currentSlot)} com {patient.prescriberName}.
                                    </p>
                                    <div className={styles.actions}>
                                        <button
                                            type="button"
                                            className={styles.primaryButton}
                                            onClick={() => sendRequest(currentSlot)}
                                            disabled={isSending}
                                        >
                                            {isSending ? "Enviando..." : "Pedir consulta"}
                                        </button>
                                    </div>
                                </div>
                            )}
                        </>
                    )}
                </section>
            )}

            <section className={styles.section} aria-labelledby="proximas-consultas">
                <h2 id="proximas-consultas" className={styles.sectionTitle}>Próximas consultas e pedidos</h2>
                {upcomingAppointments.length === 0 ? (
                    <p className={styles.status}>Nenhuma consulta ou pedido pela frente.</p>
                ) : (
                    <ul className={styles.list}>
                        {upcomingAppointments.map((appointment) => (
                            <li key={appointment.id} className={styles.appointment}>
                                <div className={styles.appointmentInfo}>
                                    <div className={styles.appointmentTitle}>
                                        <strong>{formatDateTime(appointment.dateTime)}</strong>
                                        <AppointmentStatusBadge appointment={appointment}/>
                                    </div>
                                    <p className={styles.meta}>
                                        {modalityLabelOf(appointment.modality)} com {appointment.prescriberName}
                                    </p>
                                    {appointment.patientNote && (
                                        <p className={styles.meta}>Motivo: {appointment.patientNote}</p>
                                    )}
                                    {appointment.status === "AGENDADA" && !appointment.annulled
                                        && !canPatientCancel(appointment) && (
                                        <p className={styles.meta}>
                                            Faltam menos de 24 horas. Para cancelar, fale com o seu prescritor.
                                        </p>
                                    )}
                                </div>
                                {canPatientCancel(appointment) && (
                                    <button
                                        type="button"
                                        className={styles.dangerButton}
                                        onClick={() => setCancelTarget(appointment)}
                                        disabled={busyAppointmentId === appointment.id}
                                    >
                                        {appointment.status === "SOLICITADA" ? "Cancelar pedido" : "Cancelar consulta"}
                                    </button>
                                )}
                            </li>
                        ))}
                    </ul>
                )}
            </section>

            <ConfirmModal
                show={cancelTarget !== null}
                title={cancelTarget !== null && cancelTarget.status === "SOLICITADA" ? "Cancelar pedido" : "Cancelar consulta"}
                message={cancelTarget !== null
                    ? "O horário de " + formatDateTime(cancelTarget.dateTime) + " volta a ficar livre na agenda. Quer cancelar?"
                    : ""}
                confirmText="Sim, cancelar"
                cancelText="Voltar"
                onConfirm={cancelAppointment}
                onCancel={() => setCancelTarget(null)}
            />
        </section>
    );
}
