import {useEffect, useRef, useState} from "react";
import {FiCalendar, FiUserX} from "react-icons/fi";
import AppointmentStatusBadge from "../../components/appointment-status-badge/appointment-status-badge.jsx";
import Card from "../../components/card/card.jsx";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import EmptyState from "../../components/empty-state/empty-state.jsx";
import BusyButton from "../../components/form/busy-button.jsx";
import {useFocusFirstError} from "../../components/form/focus-first-error.js";
import SelectField from "../../components/form/select-field.jsx";
import TextAreaField from "../../components/form/text-area-field.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import UrgencyNotice from "../../components/urgency-notice/urgency-notice.jsx";
import {apiService, ApiError, errorMessageOf, getLoggedUser} from "../../services/api.js";
import {appointmentTypeLabelOf, MODALITY_OPTIONS, modalityLabelOf} from "../../utils/appointment-labels.js";
import {ARCHIVED_APPOINTMENT_NOTICE} from "../../utils/archived-patient.js";
import {
    addDays,
    brasiliaTimeNote,
    formatDateTime,
    formatDateTimeWithZone,
    formatTime,
    formatWeekdayAndDate,
    minutesUntil,
    toIsoDate,
} from "../../utils/date-format.js";
import {coveredTopOf} from "../../utils/focus-above-bars.js";
import {emptyToNull} from "../../utils/form-values.js";
import styles from "./agendamento-consulta-paciente.module.css";

// quantos dias pra frente o paciente ve horario livre
const DAYS_AHEAD = 30;

// o paciente cancela sozinho a consulta marcada so com essa antecedencia
const PATIENT_CANCELLATION_HOURS = 24;

// e pede so com essa antecedencia, igual a api, pro prescritor ter tempo de responder
const MIN_REQUEST_HOURS = 24;

// a mesma mensagem q a api manda no campo
const MODALITY_MISSING_MESSAGE = "Escolha se a consulta é presencial ou remota";

// a api ja manda so o q da pra pedir, mas a tela aberta ha horas ainda mostrava horario em cima da hora
function isFarEnoughAhead(slot) {
    return minutesUntil(slot.start) >= MIN_REQUEST_HOURS * 60;
}

// os pedidos q ainda esperam a resposta do prescritor. o paciente tem um por vez e o novo substitui ele,
// mais de um so sobra de antes dessa regra
function findPendingRequests(upcomingAppointments) {
    return upcomingAppointments.filter(
        (appointment) => appointment.status === "SOLICITADA" && !appointment.annulled);
}

// "x", "x e y" ou "x, y e z"
function dateTimesText(appointments) {
    const dateTimes = appointments.map((appointment) => formatDateTimeWithZone(appointment.dateTime));
    if (dateTimes.length === 1) {
        return dateTimes[0];
    }
    return dateTimes.slice(0, -1).join(", ") + " e " + dateTimes[dateTimes.length - 1];
}

function replacementQuestion(replacement) {
    const newRequestText = " O novo pedido é para " + formatDateTimeWithZone(replacement.slotStart) + ". Quer trocar?";
    if (replacement.replacedRequests.length === 1) {
        return "O pedido de " + dateTimesText(replacement.replacedRequests)
            + " será cancelado e o horário dele volta a ficar livre." + newRequestText;
    }
    return "Os pedidos de " + dateTimesText(replacement.replacedRequests)
        + " serão cancelados e os horários deles voltam a ficar livres." + newRequestText;
}

function sentNoticeOf(replacedRequests, prescriberName) {
    if (replacedRequests.length === 0) {
        return "Pedido enviado para " + prescriberName + ".";
    }
    if (replacedRequests.length === 1) {
        return "Pedido trocado. O pedido de " + dateTimesText(replacedRequests)
            + " foi cancelado e o novo foi enviado para " + prescriberName + ".";
    }
    return "Pedido trocado. Os pedidos de " + dateTimesText(replacedRequests)
        + " foram cancelados e o novo foi enviado para " + prescriberName + ".";
}

// separa os horarios livres pelo dia
function groupSlotsByDay(freeSlots) {
    const slotsByDay = {};
    freeSlots.filter(isFarEnoughAhead).forEach((slot) => {
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
    // a conta eh no horario de brasilia, igual a api faz
    return minutesUntil(appointment.dateTime) >= PATIENT_CANCELLATION_HOURS * 60;
}

// consulta marcada q ja passou do prazo e so o prescritor cancela
function isTooLateToCancel(appointment) {
    return appointment.status === "AGENDADA" && !appointment.annulled && !canPatientCancel(appointment);
}

function cancelLabelOf(appointment) {
    return appointment.status === "SOLICITADA" ? "Cancelar pedido" : "Cancelar consulta";
}

function slotCountText(count) {
    return count === 1 ? "1 horário" : count + " horários";
}

// o navegador n rola ate o dia q ja aparece um pouco na borda da faixa, entao o tab deixava o dia com foco
// cortado e, no celular, embaixo do esmaecido, q eh o scroll-padding do fim da faixa no css.
// alinha pelo comeco pq eh onde a faixa encaixa os dias, e o encaixe desfazia uma rolagem menor.
// so pelo teclado: no toque a faixa n anda embaixo do dedo
function showWholeDay(event) {
    const day = event.currentTarget;
    if (!day.matches(":focus-visible")) {
        return;
    }
    const strip = day.parentElement;
    const dayBox = day.getBoundingClientRect();
    const stripBox = strip.getBoundingClientRect();
    const fadeWidth = parseFloat(getComputedStyle(strip).scrollPaddingInlineEnd) || 0;
    if (dayBox.left < stripBox.left || dayBox.right > stripBox.right - fadeWidth) {
        day.scrollIntoView({block: "nearest", inline: "start"});
    }
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
    // vem sem nada marcado e o paciente escolhe, pq a que vinha marcada virava pedido presencial sem querer
    // (decisao de 14/11/2026, revisao das regras R24)
    const [modality, setModality] = useState("");
    const [modalityError, setModalityError] = useState(null);
    const [patientNote, setPatientNote] = useState("");
    const [isSending, setIsSending] = useState(false);
    const [cancelTarget, setCancelTarget] = useState(null);
    // o horario escolhido e os pedidos q ele substitui, enquanto a troca espera a confirmacao
    const [replacement, setReplacement] = useState(null);
    const [busyAppointmentId, setBusyAppointmentId] = useState(null);
    const noticeRef = useRef(null);
    const actionErrorRef = useRef(null);
    const [requestStepRef, focusFirstError] = useFocusFirstError();

    // o botao de pedir ou de cancelar some depois do envio e o foco ia pro body, entao ele vai pro aviso (CT-39)
    useEffect(() => {
        if (notice && noticeRef.current) {
            noticeRef.current.focus();
        }
    }, [notice]);

    // o aviso de erro fica no alto da tela e o botao q falhou la embaixo, entao o foco vai pro aviso
    // e o navegador rola ate ele
    useEffect(() => {
        if (actionError && actionErrorRef.current) {
            actionErrorRef.current.focus();
        }
    }, [actionError]);

    // no celular o passo 3 aparece embaixo da grade de horarios e fora da tela, entao a tela rola ate ele.
    // sem rolagem suave, q respeita quem pede menos movimento, e o foco continua no horario.
    // o toque da o foco pro horario e o useFocusAboveBars rola ate ele no proximo quadro, entao a rolagem
    // do passo vai no quadro tbm, dps da dele. e so rola qnd a modalidade, o primeiro campo do passo, fica
    // embaixo das barras ou fora da janela: com o passo ja na tela, como no computador, a pagina n pula
    // e o horario com foco continua a vista
    useEffect(() => {
        if (!selectedSlot || !requestStepRef.current) {
            return;
        }
        const step = requestStepRef.current;
        const frame = requestAnimationFrame(() => {
            const firstField = step.querySelector("select") || step;
            const fieldBox = firstField.getBoundingClientRect();
            if (fieldBox.bottom > coveredTopOf(fieldBox)) {
                step.scrollIntoView({block: "start"});
            }
        });
        return () => cancelAnimationFrame(frame);
    }, [selectedSlot, requestStepRef]);

    useEffect(() => {
        let isCurrentRequest = true;
        const firstDay = toIsoDate(new Date());
        const lastDay = toIsoDate(addDays(new Date(), DAYS_AHEAD - 1));

        Promise.all([
            apiService.get("/patients/" + loggedUser.id),
            apiService.get("/appointments/mine"),
            apiService.get("/appointments/free-slots?from=" + firstDay + "&to=" + lastDay),
        ])
            .then(([patient, upcomingAppointments, freeSlots]) => {
                if (isCurrentRequest) {
                    setPage({patient, upcomingAppointments, freeSlots});
                    setLoadError(null);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(errorMessageOf(requestError,
                        "Não foi possível carregar suas consultas. Confira sua internet e tente de novo."));
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
        setActionError(errorMessageOf(requestError));
    };

    const clearMessages = () => {
        setNotice(null);
        setActionError(null);
    };

    const chooseDay = (dayIso) => {
        setSelectedDay(dayIso);
        setSelectedSlot(null);
        clearMessages();
    };

    const chooseSlot = (slotStart) => {
        setSelectedSlot(slotStart);
        clearMessages();
    };

    // a tela manda os pedidos q mostrou e a api so troca se ainda forem eles, senao recusa sem mudar nada
    // (o prescritor respondeu ou outra aba pediu). entao o aviso pode dizer quais foram cancelados
    const sendRequest = async (slotStart, replacedRequests) => {
        setIsSending(true);
        clearMessages();
        let apiAnswered = true;
        try {
            await apiService.post("/appointments/requests", {
                dateTime: slotStart,
                modality,
                patientNote: emptyToNull(patientNote.trim()),
                replacedRequestIds: replacedRequests.map((request) => request.id),
            });
            setNotice(sentNoticeOf(replacedRequests, page.patient.prescriberName)
                + " O horário fica reservado e você recebe um aviso quando o pedido for respondido.");
            setSelectedSlot(null);
            setModality("");
            setPatientNote("");
        } catch (requestError) {
            showActionError(requestError);
            // sem resposta da api a busca falharia tbm e empilhava um segundo aviso
            apiAnswered = requestError instanceof ApiError;
        } finally {
            setIsSending(false);
            // mesmo com erro da api vale buscar de novo pq o horario pode ter sido pedido por outra pessoa
            if (apiAnswered) {
                reload();
            }
        }
    };

    // com um pedido esperando resposta o novo substitui ele, entao a troca so sai dps de confirmar
    const askForRequest = (slotStart) => {
        if (modality === "") {
            setModalityError(MODALITY_MISSING_MESSAGE);
            focusFirstError();
            return;
        }
        const pendingRequests = findPendingRequests(page.upcomingAppointments);
        if (pendingRequests.length > 0) {
            setReplacement({slotStart, replacedRequests: pendingRequests});
        } else {
            sendRequest(slotStart, []);
        }
    };

    const confirmReplacement = () => {
        const chosen = replacement;
        setReplacement(null);
        sendRequest(chosen.slotStart, chosen.replacedRequests);
    };

    const cancelAppointment = async () => {
        const appointment = cancelTarget;
        setCancelTarget(null);
        clearMessages();
        setBusyAppointmentId(appointment.id);
        try {
            await apiService.put("/appointments/" + appointment.id + "/cancel");
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
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (page === null) {
        return <SkeletonPage cards={2}/>;
    }

    const {patient, upcomingAppointments, freeSlots} = page;
    const hasPrescriber = patient.prescriberId !== null;
    // a api recusa o pedido do arquivado e devolve a agenda vazia, q n eh falta de horario
    const canRequest = hasPrescriber && !patient.archived;
    const slotsByDay = groupSlotsByDay(freeSlots);
    const availableDays = Object.keys(slotsByDay).sort();
    // o dia escolhido pode ter ficado sem horario depois de buscar de novo
    const currentDay = selectedDay !== null && slotsByDay[selectedDay] ? selectedDay : availableDays[0];
    const daySlots = currentDay ? slotsByDay[currentDay] : [];
    const currentSlot = daySlots.some((slot) => slot.start === selectedSlot) ? selectedSlot : null;
    // a primeira consulta dura mais q o retorno, entao o fim do horario mostra quanto tempo reservar
    const currentSlotEnd = currentSlot ? daySlots.find((slot) => slot.start === currentSlot).end : null;
    const pendingRequests = findPendingRequests(upcomingAppointments);
    const sendLabel = pendingRequests.length > 0 ? "Trocar pedido" : "Pedir consulta";

    return (
        <section className={styles.page}>
            <PageHeader
                title="Consultas"
                subtitle={canRequest
                    ? "Peça um horário na agenda de " + patient.prescriberName + ", com pelo menos "
                        + MIN_REQUEST_HOURS + " horas de antecedência. O horário fica reservado até o pedido ser respondido."
                    : "Acompanhe seus pedidos e consultas."}
            />

            {notice && <p className="aviso" role="status" tabIndex={-1} ref={noticeRef}>{notice}</p>}
            {actionError && (
                <p className="aviso aviso--atencao" role="alert" tabIndex={-1} ref={actionErrorRef}>{actionError}</p>
            )}
            {loadError && <p className="aviso aviso--atencao" role="alert">{loadError}</p>}

            {patient.archived && <EmptyState icon={FiUserX} message={ARCHIVED_APPOINTMENT_NOTICE}/>}

            {!patient.archived && !hasPrescriber && (
                <EmptyState
                    icon={FiUserX}
                    message="Você ainda não tem um prescritor vinculado. Fale com a clínica para receber o convite."
                />
            )}

            {canRequest && (
                <Card title="Pedir consulta">
                    {pendingRequests.length === 1 && (
                        <p className="aviso">
                            Você já tem um pedido esperando resposta para {dateTimesText(pendingRequests)}.
                            Se pedir outro horário, ele substitui esse pedido.
                        </p>
                    )}
                    {pendingRequests.length > 1 && (
                        <p className="aviso">
                            Você tem pedidos esperando resposta para {dateTimesText(pendingRequests)}.
                            Se pedir outro horário, ele substitui esses pedidos.
                        </p>
                    )}
                    {availableDays.length === 0 ? (
                        <EmptyState
                            icon={FiCalendar}
                            message={"Não há horário livre nos próximos " + DAYS_AHEAD + " dias. Tente de novo mais tarde ou fale com a clínica."}
                            isCompact={true}
                        />
                    ) : (
                        <div className={styles.steps}>
                            <div className={styles.step}>
                                <h3 className={styles.stepTitle}>
                                    <span className={styles.stepNumber}>1</span>
                                    Escolha o dia
                                </h3>
                                <div className={styles.days} role="group" aria-label="Dias com horário livre">
                                    {availableDays.map((dayIso) => (
                                        <button
                                            key={dayIso}
                                            type="button"
                                            className={dayIso === currentDay ? styles.dayButtonSelected : styles.dayButton}
                                            aria-pressed={dayIso === currentDay}
                                            onClick={() => chooseDay(dayIso)}
                                            onFocus={showWholeDay}
                                        >
                                            <span className={styles.weekday}>{formatWeekdayAndDate(dayIso)}</span>
                                            <span className={styles.slotCount}>
                                                {slotCountText(slotsByDay[dayIso].length)}
                                            </span>
                                        </button>
                                    ))}
                                </div>
                            </div>

                            <div className={styles.step}>
                                <h3 className={styles.stepTitle}>
                                    <span className={styles.stepNumber}>2</span>
                                    Escolha o horário{brasiliaTimeNote()}
                                </h3>
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
                                <div className={styles.step} ref={requestStepRef}>
                                    <h3 className={styles.stepTitle}>
                                        <span className={styles.stepNumber}>3</span>
                                        Confirme o pedido
                                    </h3>
                                    <div className={styles.formRow}>
                                        <SelectField
                                            label="Modalidade"
                                            name="modality"
                                            options={MODALITY_OPTIONS}
                                            placeholder="Escolha a modalidade"
                                            value={modality}
                                            onChange={(event) => {
                                                setModality(event.target.value);
                                                setModalityError(null);
                                            }}
                                            error={modalityError}
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
                                        noticeId="patientNote-urgencia"
                                    />
                                    <UrgencyNotice id="patientNote-urgencia"/>
                                    <p className={styles.summary}>
                                        <FiCalendar aria-hidden="true"/>
                                        Pedido para {formatDateTime(currentSlot)} até {formatTime(currentSlotEnd)}{brasiliaTimeNote()} com {patient.prescriberName}.
                                    </p>
                                    <div className={styles.actions}>
                                        <BusyButton
                                            type="button"
                                            className="button"
                                            onClick={() => askForRequest(currentSlot)}
                                            isBusy={isSending}
                                            busyText="Enviando..."
                                        >
                                            {sendLabel}
                                        </BusyButton>
                                    </div>
                                </div>
                            )}
                        </div>
                    )}
                </Card>
            )}

            <Card title="Próximas consultas e pedidos" count={upcomingAppointments.length}>
                {upcomingAppointments.length === 0 ? (
                    <p className={styles.meta}>Nenhuma consulta ou pedido pela frente.</p>
                ) : (
                    <ul className={styles.list}>
                        {upcomingAppointments.map((appointment) => (
                            <li key={appointment.id} className={styles.appointment}>
                                <div className={styles.appointmentInfo}>
                                    <div className={styles.appointmentTitle}>
                                        <strong>{formatDateTimeWithZone(appointment.dateTime)}</strong>
                                        <AppointmentStatusBadge appointment={appointment}/>
                                    </div>
                                    <p className={styles.meta}>
                                        {appointment.appointmentType && appointmentTypeLabelOf(appointment.appointmentType) + " · "}
                                        {modalityLabelOf(appointment.modality)} com {appointment.prescriberName}
                                    </p>
                                    {/* o texto eh o q o paciente escreveu no pedido, e a recusa vai so no aviso */}
                                    {appointment.patientNote && (
                                        <p className={styles.meta}>Seu motivo: {appointment.patientNote}</p>
                                    )}
                                    {appointment.status === "RECUSADA" && !appointment.annulled && (
                                        <p className={styles.meta}>O motivo da recusa, quando houver, está em Avisos.</p>
                                    )}
                                    {isTooLateToCancel(appointment) && (
                                        <p className={styles.meta}>
                                            Faltam menos de 24 horas. Para cancelar, fale com o seu prescritor.
                                        </p>
                                    )}
                                </div>
                                {canPatientCancel(appointment) && (
                                    <button
                                        type="button"
                                        className="button-danger button-small"
                                        onClick={() => setCancelTarget(appointment)}
                                        disabled={busyAppointmentId === appointment.id}
                                    >
                                        {cancelLabelOf(appointment)}
                                    </button>
                                )}
                            </li>
                        ))}
                    </ul>
                )}
            </Card>

            <ConfirmModal
                show={cancelTarget !== null}
                title={cancelTarget !== null ? cancelLabelOf(cancelTarget) : "Cancelar consulta"}
                message={cancelTarget !== null
                    ? "O horário de " + formatDateTimeWithZone(cancelTarget.dateTime)
                        + " volta a ficar livre na agenda. Quer cancelar?"
                    : ""}
                confirmText="Sim, cancelar"
                danger={true}
                cancelText="Voltar"
                onConfirm={cancelAppointment}
                onCancel={() => setCancelTarget(null)}
            />

            <ConfirmModal
                show={replacement !== null}
                title="Trocar pedido"
                message={replacement !== null ? replacementQuestion(replacement) : ""}
                confirmText="Sim, trocar"
                cancelText="Voltar"
                onConfirm={confirmReplacement}
                onCancel={() => setReplacement(null)}
            />
        </section>
    );
}
