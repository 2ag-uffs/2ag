import {useEffect, useState} from "react";
import {useNavigate} from "react-router";
import {FiPlus, FiTrash2} from "react-icons/fi";
import FormSection, {FormActions} from "../../components/form-section/form-section.jsx";
import SelectField from "../../components/form/select-field.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {DURATION_OPTIONS, WEEKDAYS} from "../../utils/appointment-labels.js";
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

// horarios de atendimento do prescritor (RF11)
// os pacientes so pedem consulta nos periodos daqui divididos pela duracao da consulta
export default function AgendaDisponibilidade() {
    const navigate = useNavigate();
    const [durationMinutes, setDurationMinutes] = useState("60");
    const [periods, setPeriods] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [loadError, setLoadError] = useState(null);
    const [formError, setFormError] = useState(null);
    const [notice, setNotice] = useState(null);

    useEffect(() => {
        let isCurrentRequest = true;

        apiService.get("/availability")
            .then((availability) => {
                if (isCurrentRequest) {
                    setDurationMinutes(String(availability.appointmentDurationMinutes));
                    setPeriods(availability.periods.map((period) =>
                        newPeriod(period.dayOfWeek, toInputTime(period.startTime), toInputTime(period.endTime))));
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
                appointmentDurationMinutes: Number(durationMinutes),
                periods: periods.map((period) => ({
                    dayOfWeek: period.dayOfWeek,
                    startTime: period.startTime,
                    endTime: period.endTime,
                })),
            });
            setNotice("Horários salvos. Seus pacientes já podem pedir consulta nesses períodos.");
        } catch (requestError) {
            setFormError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
        } finally {
            setIsSaving(false);
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
                subtitle="Seus pacientes pedem consulta só nestes períodos. Cada período é dividido pela duração da consulta."
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
                    <div className={styles.durationField}>
                        <SelectField
                            label="Duração de cada consulta"
                            name="appointmentDurationMinutes"
                            options={DURATION_OPTIONS}
                            value={durationMinutes}
                            onChange={(event) => {
                                setNotice(null);
                                setDurationMinutes(event.target.value);
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
        </section>
    );
}
