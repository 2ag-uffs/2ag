import {useEffect, useState} from "react";
import {useNavigate} from "react-router";
import {FiPlus, FiTrash2} from "react-icons/fi";
import FormSection, {FormActions} from "../../components/form-section/form-section.jsx";
import SelectField from "../../components/form/select-field.jsx";
import TextField from "../../components/form/text-field.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {DURATION_OPTIONS, WEEKDAYS} from "../../utils/appointment-labels.js";
import {formatDate, formatWeekdayAndDate, toIsoDate} from "../../utils/date-format.js";
import styles from "./agenda-disponibilidade.module.css";

const CONNECTION_ERROR_MESSAGE = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

// a api manda a hora com segundos e o input de hora usa so hora e minuto
function toInputTime(apiTime) {
    return apiTime ? apiTime.slice(0, 5) : "";
}

// cada periodo ganha uma chave so da tela pro react saber qual linha eh qual
let lastPeriodKey = 0;

function newPeriod(dayOfWeek, startTime, endTime) {
    lastPeriodKey = lastPeriodKey + 1;
    return {key: lastPeriodKey, dayOfWeek, startTime, endTime};
}

// a folga n cancela nada, entao o prescritor precisa saber o q ficou marcado no dia
function bookingsOnTheDayWarning(count) {
    if (count === 1) {
        return "Você tem 1 consulta ou pedido nesse dia. Ele continua marcado; remarque ou cancele na agenda.";
    }
    return "Você tem " + count + " consultas ou pedidos nesse dia. Eles continuam marcados; remarque ou cancele na agenda.";
}

// horarios de atendimento do prescritor (RF11)
// os pacientes so pedem consulta nos periodos daqui, em horarios do tamanho da primeira consulta ou do retorno,
// e nunca num dia sem atendimento
export default function AgendaDisponibilidade() {
    const navigate = useNavigate();
    const [firstDurationMinutes, setFirstDurationMinutes] = useState("90");
    const [returnDurationMinutes, setReturnDurationMinutes] = useState("60");
    const [periods, setPeriods] = useState([]);
    const [daysOff, setDaysOff] = useState([]);
    const [newDayOff, setNewDayOff] = useState("");
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [isSavingDayOff, setIsSavingDayOff] = useState(false);
    const [loadError, setLoadError] = useState(null);
    const [formError, setFormError] = useState(null);
    const [notice, setNotice] = useState(null);
    const [dayOffError, setDayOffError] = useState(null);
    const [dayOffNotice, setDayOffNotice] = useState(null);
    const [dayOffWarning, setDayOffWarning] = useState(null);

    useEffect(() => {
        let isCurrentRequest = true;

        Promise.all([
            apiService.get("/availability"),
            apiService.get("/availability/days-off"),
        ])
            .then(([availability, savedDaysOff]) => {
                if (isCurrentRequest) {
                    setReturnDurationMinutes(String(availability.appointmentDurationMinutes));
                    setFirstDurationMinutes(String(availability.firstAppointmentDurationMinutes));
                    setPeriods(availability.periods.map((period) =>
                        newPeriod(period.dayOfWeek, toInputTime(period.startTime), toInputTime(period.endTime))));
                    setDaysOff(savedDaysOff);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível carregar seus horários. Confira sua internet e tente de novo.");
                }
            })
            .finally(() => {
                if (isCurrentRequest) {
                    setIsLoading(false);
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, []);

    const addPeriod = (dayOfWeek) => {
        setNotice(null);
        setPeriods((currentPeriods) => [...currentPeriods, newPeriod(dayOfWeek, "08:00", "12:00")]);
    };

    const updatePeriod = (periodKey, fieldName, value) => {
        setNotice(null);
        setPeriods((currentPeriods) => currentPeriods.map((period) =>
            period.key === periodKey ? {...period, [fieldName]: value} : period));
    };

    const removePeriod = (periodKey) => {
        setNotice(null);
        setPeriods((currentPeriods) => currentPeriods.filter((period) => period.key !== periodKey));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setFormError(null);
        setNotice(null);

        // hora no formato hora e minuto da pra comparar como texto
        const hasInvalidPeriod = periods.some((period) =>
            !period.startTime || !period.endTime || period.startTime >= period.endTime);
        if (hasInvalidPeriod) {
            setFormError("Em cada período, o horário de fim precisa ser depois do início.");
            return;
        }

        setIsSaving(true);
        try {
            await apiService.put("/availability", {
                appointmentDurationMinutes: Number(returnDurationMinutes),
                firstAppointmentDurationMinutes: Number(firstDurationMinutes),
                periods: periods.map((period) => ({
                    dayOfWeek: period.dayOfWeek,
                    startTime: period.startTime,
                    endTime: period.endTime,
                })),
            });
            // salvar sem periodo nenhum eh permitido de proposito, mas a pessoa
            // precisa saber q isso fecha a agenda dela pros pacientes
            setNotice(periods.length === 0
                ? "Horários salvos. Sem nenhum período, seus pacientes não conseguem pedir consulta."
                : "Horários salvos. Seus pacientes já podem pedir consulta nesses períodos.");
        } catch (requestError) {
            setFormError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
        } finally {
            setIsSaving(false);
        }
    };

    const clearDayOffMessages = () => {
        setDayOffError(null);
        setDayOffNotice(null);
        setDayOffWarning(null);
    };

    // a folga grava na hora, sem esperar o botao de salvar os horarios
    const addDayOff = async (event) => {
        event.preventDefault();
        clearDayOffMessages();
        if (newDayOff === "") {
            setDayOffError("Escolha o dia.");
            return;
        }

        setIsSavingDayOff(true);
        try {
            const dayOff = await apiService.post("/availability/days-off", {date: newDayOff});
            setDaysOff((currentDaysOff) => [...currentDaysOff, dayOff]
                .sort((first, second) => first.date.localeCompare(second.date)));
            setNewDayOff("");
            setDayOffNotice(formatDate(dayOff.date) + " ficou sem atendimento. Seus pacientes não veem horários nesse dia.");
            if (dayOff.appointmentsOnTheDay > 0) {
                setDayOffWarning(bookingsOnTheDayWarning(dayOff.appointmentsOnTheDay));
            }
        } catch (requestError) {
            setDayOffError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
        } finally {
            setIsSavingDayOff(false);
        }
    };

    const removeDayOff = async (date) => {
        clearDayOffMessages();
        setIsSavingDayOff(true);
        try {
            await apiService.delete("/availability/days-off?date=" + date);
            setDaysOff((currentDaysOff) => currentDaysOff.filter((dayOff) => dayOff.date !== date));
            setDayOffNotice(formatDate(date) + " voltou a ter atendimento.");
        } catch (requestError) {
            setDayOffError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
        } finally {
            setIsSavingDayOff(false);
        }
    };

    if (isLoading) {
        return <SkeletonPage cards={2}/>;
    }

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    return (
        <section className={styles.page}>
            <PageHeader
                title="Horários de atendimento"
                subtitle="Seus pacientes pedem consulta só nestes períodos. Quem ainda não foi atendido vê horários do tamanho da primeira consulta, e os outros, do retorno."
                actions={(
                    <button type="button" className="button-secondary" onClick={() => navigate("/agendamento-prescritor")}>
                        Ver a agenda
                    </button>
                )}
            />

            {notice && <p className="aviso" role="status">{notice}</p>}
            {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}

            <form className={styles.form} onSubmit={handleSubmit}>
                <FormSection title="Duração das consultas" disabled={isSaving}>
                    <div className={styles.durationFields}>
                        <SelectField
                            label="Primeira consulta"
                            name="firstAppointmentDurationMinutes"
                            options={DURATION_OPTIONS}
                            value={firstDurationMinutes}
                            onChange={(event) => {
                                setNotice(null);
                                setFirstDurationMinutes(event.target.value);
                            }}
                        />
                        <SelectField
                            label="Retorno"
                            name="appointmentDurationMinutes"
                            options={DURATION_OPTIONS}
                            value={returnDurationMinutes}
                            onChange={(event) => {
                                setNotice(null);
                                setReturnDurationMinutes(event.target.value);
                            }}
                        />
                    </div>
                </FormSection>

                <FormSection
                    title="Períodos da semana"
                    description="Adicione um ou mais períodos em cada dia em que você atende."
                    disabled={isSaving}
                >
                    <ul className={styles.days}>
                        {WEEKDAYS.map((weekday) => {
                            const dayPeriods = periods.filter((period) => period.dayOfWeek === weekday.value);
                            return (
                                <li key={weekday.value} className={styles.day}>
                                    <div className={styles.dayHeader}>
                                        <h3 className={styles.dayName}>{weekday.label}</h3>
                                        {dayPeriods.length === 0 && <span className={styles.dayStatus}>Sem atendimento</span>}
                                        <button
                                            type="button"
                                            className={"button-tertiary button-small " + styles.addButton}
                                            onClick={() => addPeriod(weekday.value)}
                                        >
                                            <FiPlus aria-hidden="true"/>
                                            Adicionar período
                                        </button>
                                    </div>
                                    {dayPeriods.map((period) => (
                                        <div key={period.key} className={styles.period}>
                                            <label className={styles.timeField}>
                                                <span>Das</span>
                                                <input
                                                    type="time"
                                                    step="300"
                                                    value={period.startTime}
                                                    aria-label={"Início do período de " + weekday.label}
                                                    onChange={(event) => updatePeriod(period.key, "startTime", event.target.value)}
                                                />
                                            </label>
                                            <label className={styles.timeField}>
                                                <span>às</span>
                                                <input
                                                    type="time"
                                                    step="300"
                                                    value={period.endTime}
                                                    aria-label={"Fim do período de " + weekday.label}
                                                    onChange={(event) => updatePeriod(period.key, "endTime", event.target.value)}
                                                />
                                            </label>
                                            <button
                                                type="button"
                                                className={"button-tertiary button-small " + styles.removeButton}
                                                onClick={() => removePeriod(period.key)}
                                                aria-label={"Remover período de " + weekday.label}
                                                title="Remover período"
                                            >
                                                <FiTrash2 aria-hidden="true"/>
                                            </button>
                                        </div>
                                    ))}
                                </li>
                            );
                        })}
                    </ul>
                </FormSection>

                <FormActions>
                    <button type="submit" className="button" disabled={isSaving}>
                        {isSaving ? "Salvando..." : "Salvar horários"}
                    </button>
                </FormActions>
            </form>

            <form className={styles.form} onSubmit={addDayOff} noValidate>
                <FormSection
                    title="Dias sem atendimento"
                    description="Feriado, folga ou viagem: o dia some dos horários livres dos seus pacientes. O que já estava marcado nele continua na agenda."
                    disabled={isSavingDayOff}
                >
                    {dayOffNotice && <p className="aviso" role="status">{dayOffNotice}</p>}
                    {dayOffWarning && <p className="aviso aviso--atencao" role="alert">{dayOffWarning}</p>}
                    {dayOffError && <p className="aviso aviso--atencao" role="alert">{dayOffError}</p>}

                    <div className={styles.dayOffForm}>
                        <TextField
                            label="Dia"
                            name="dayOffDate"
                            type="date"
                            min={toIsoDate(new Date())}
                            value={newDayOff}
                            onChange={(event) => setNewDayOff(event.target.value)}
                        />
                        <button type="submit" className="button-secondary">
                            Marcar folga
                        </button>
                    </div>

                    {daysOff.length === 0 ? (
                        <p className={styles.dayStatus}>Nenhum dia sem atendimento marcado.</p>
                    ) : (
                        <ul className={styles.daysOff}>
                            {daysOff.map((dayOff) => (
                                <li key={dayOff.date} className={styles.dayOff}>
                                    <div className={styles.dayOffInfo}>
                                        <span className={styles.dayOffDate}>{formatWeekdayAndDate(dayOff.date)}</span>
                                        {dayOff.appointmentsOnTheDay > 0 && (
                                            <span className={styles.dayOffBookings}>
                                                {dayOff.appointmentsOnTheDay === 1
                                                    ? "1 consulta ou pedido ainda marcado"
                                                    : dayOff.appointmentsOnTheDay + " consultas ou pedidos ainda marcados"}
                                            </span>
                                        )}
                                    </div>
                                    <button
                                        type="button"
                                        className={"button-tertiary button-small " + styles.removeButton}
                                        onClick={() => removeDayOff(dayOff.date)}
                                        aria-label={"Voltar a atender em " + formatDate(dayOff.date)}
                                        title="Voltar a atender nesse dia"
                                    >
                                        <FiTrash2 aria-hidden="true"/>
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                </FormSection>
            </form>
        </section>
    );
}
