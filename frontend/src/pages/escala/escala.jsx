import {useEffect, useMemo, useState} from "react";
import {useNavigate, useParams, useSearchParams} from "react-router";
import {FiCheck, FiMoon} from "react-icons/fi";
import {FormActions} from "../../components/form-section/form-section.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import ScaleForm from "../../components/scale-form/scale-form.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import {addDays, formatDate, formatWeekdayAndDate, mondayOf, toIsoDate} from "../../utils/date-format.js";
import {answersPayload, answersToValues} from "../../utils/scale-answers.js";
import styles from "./escala.module.css";

const CONNECTION_ERROR_MESSAGE = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

// os acompanhamentos semanais falam da ultima semana
const PERIOD_DAYS = 7;

// tela unica de preenchimento de escala (RF08 e RF21 a RF26)
//
// o formulario inteiro vem do servidor: titulo, itens, ancoras e
// opcoes. escala nova aparece aqui sem tela nova
export default function Escala() {
    const {slug} = useParams();
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const loggedUser = getLoggedUser();

    const today = toIsoDate(new Date());
    const chosenDate = searchParams.get("data");

    const [definition, setDefinition] = useState(null);
    const [responses, setResponses] = useState([]);
    const [sleepSchedule, setSleepSchedule] = useState(null);
    const [loadError, setLoadError] = useState(null);
    // o q a paciente mexeu fica guardado por dia ate salvar
    const [editsByDay, setEditsByDay] = useState({});
    const [selectedDay, setSelectedDay] = useState(chosenDate && chosenDate <= today ? chosenDate : today);
    const [periodEndByDay, setPeriodEndByDay] = useState({});
    const [notice, setNotice] = useState(null);
    const [formError, setFormError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const [reloadCount, setReloadCount] = useState(0);

    useEffect(() => {
        let isCurrentRequest = true;

        Promise.all([
            apiService.get("/scales/definitions/" + slug),
            apiService.get("/scales/" + slug + "/responses"),
        ])
            .then(([loadedDefinition, loadedResponses]) => {
                if (isCurrentRequest) {
                    setDefinition(loadedDefinition);
                    setResponses(loadedResponses);
                    setLoadError(null);
                    // o acompanhamento semanal fala da ultima semana, entao
                    // o periodo ja abre nela
                    if (loadedDefinition.fillMode === "PERIODO" && !chosenDate) {
                        setSelectedDay(toIsoDate(addDays(new Date(), -(PERIOD_DAYS - 1))));
                    }
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível abrir a escala. Confira sua internet e tente de novo.");
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [slug, chosenDate, reloadCount]);

    // a programacao de horarios do diario do sono, q o prescritor define
    // no acompanhamento. paciente sem acompanhamento n tem programacao
    useEffect(() => {
        if (slug !== "diario-sono") {
            return;
        }
        apiService.get("/patients/" + loggedUser.id + "/treatment-protocol")
            .then(setSleepSchedule)
            .catch(() => setSleepSchedule(null));
    }, [slug, loggedUser.id]);

    const isDiary = definition ? definition.fillMode === "DIARIO" : false;
    const isPeriod = definition ? definition.fillMode === "PERIODO" : false;

    // a semana do dia escolhido, como a grade do formulario em papel
    const weekDays = useMemo(() => {
        const monday = mondayOf(new Date(selectedDay + "T00:00:00"));
        const days = [];
        for (let dayNumber = 0; dayNumber < 7; dayNumber = dayNumber + 1) {
            const dayIso = toIsoDate(addDays(monday, dayNumber));
            if (dayIso <= today) {
                days.push(dayIso);
            }
        }
        return days;
    }, [selectedDay, today]);

    const currentResponse = useMemo(
        () => responses.find((response) => response.periodStart === selectedDay) || null,
        [responses, selectedDay]);

    // cada dia mostra o q ja foi respondido nele ou o q a paciente esta mexendo
    // dia em branco abre em branco e a tela nunca sugere valor (RN10)
    const savedValues = currentResponse ? answersToValues(currentResponse.answers) : {};
    const values = editsByDay[selectedDay] || savedValues;
    // o periodo ja respondido abre com o fim q foi salvo e n com hoje
    const periodEnd = periodEndByDay[selectedDay] || (currentResponse ? currentResponse.periodEnd : today);

    // o recado de um dia n vale pro outro entao trocar de dia limpa os avisos
    // salvar n passa por aqui e o recado de salvo continua na tela
    const changeDay = (dayIso) => {
        setSelectedDay(dayIso);
        setNotice(null);
        setFormError(null);
    };

    const changePeriodEnd = (dayIso) => {
        setPeriodEndByDay((current) => ({...current, [selectedDay]: dayIso}));
    };

    const isReadOnly = currentResponse !== null && !currentResponse.editableByPatient;

    const changeAnswer = (key, value) => {
        setEditsByDay((current) => ({
            ...current,
            [selectedDay]: {...(current[selectedDay] || savedValues), [key]: value},
        }));
    };

    const save = async (event) => {
        event.preventDefault();
        setFormError(null);
        setNotice(null);
        setIsSaving(true);
        try {
            const saved = await apiService.post("/scales/" + slug + "/responses", {
                periodStart: selectedDay,
                periodEnd: isPeriod ? periodEnd : selectedDay,
                answers: answersPayload(definition.items, values),
            });
            setNotice(saved.result ? "Respostas salvas. Resultado: " + saved.result : "Respostas salvas.");
            // a resposta salva entra na lista na hora e o rascunho daquele dia sai
            setResponses((current) => [saved, ...current.filter((response) => response.periodStart !== saved.periodStart)]);
            setEditsByDay((current) => ({...current, [selectedDay]: null}));
            setPeriodEndByDay((current) => ({...current, [selectedDay]: null}));
            setReloadCount((currentCount) => currentCount + 1);
        } catch (requestError) {
            setFormError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
        } finally {
            setIsSaving(false);
        }
    };

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!definition) {
        return <SkeletonPage cards={2}/>;
    }

    return (
        <section className={styles.page}>
            <PageHeader title={definition.title} subtitle={definition.instruction}/>

            {sleepSchedule && (sleepSchedule.sleepBedTime || sleepSchedule.sleepWakeTime) && (
                <p className={styles.schedule}>
                    <FiMoon aria-hidden="true"/>
                    {/* a api manda hh:mm:ss e aqui so interessa hh:mm */}
                    Programação do seu prescritor: dormir às {sleepSchedule.sleepBedTime
                        ? sleepSchedule.sleepBedTime.slice(0, 5) : "—"} e
                    levantar às {sleepSchedule.sleepWakeTime ? sleepSchedule.sleepWakeTime.slice(0, 5) : "—"}.
                </p>
            )}

            {isDiary && (
                <section className={styles.daysSection}>
                    <h2 className={styles.sectionTitle}>Dia da semana</h2>
                    <div className={styles.days} role="group" aria-label="Dias da semana">
                        {weekDays.map((dayIso) => {
                            const isFilled = responses.some((response) => response.periodStart === dayIso);
                            return (
                                <button
                                    key={dayIso}
                                    type="button"
                                    className={dayIso === selectedDay ? styles.day + " " + styles.dayChosen : styles.day}
                                    aria-pressed={dayIso === selectedDay}
                                    onClick={() => changeDay(dayIso)}
                                >
                                    <span className={styles.dayName}>{formatWeekdayAndDate(dayIso)}</span>
                                    <span className={isFilled ? styles.dayMark + " " + styles.dayFilled : styles.dayMark}>
                                        {isFilled && <FiCheck aria-hidden="true"/>}
                                        {isFilled ? "preenchido" : "em branco"}
                                    </span>
                                </button>
                            );
                        })}
                    </div>
                </section>
            )}

            <form className={styles.form} onSubmit={save}>
                {!isDiary && (
                    <div className={styles.period}>
                        <div className={styles.dateField}>
                            <label htmlFor="periodStart">
                                {isPeriod ? "Início do período" : "Data da avaliação"}
                            </label>
                            <input
                                id="periodStart"
                                type="date"
                                max={today}
                                value={selectedDay}
                                disabled={isReadOnly}
                                onChange={(event) => changeDay(event.target.value)}
                            />
                        </div>
                        {isPeriod && (
                            <div className={styles.dateField}>
                                <label htmlFor="periodEnd">Fim do período</label>
                                <input
                                    id="periodEnd"
                                    type="date"
                                    value={periodEnd}
                                    disabled={isReadOnly}
                                    onChange={(event) => changePeriodEnd(event.target.value)}
                                />
                            </div>
                        )}
                    </div>
                )}

                {isReadOnly && (
                    <p className="aviso">
                        {currentResponse.annulled
                            ? "Esta resposta foi anulada pelo prescritor e fica no histórico como está."
                            : "O prescritor já analisou esta resposta. Para corrigir, fale com ele."}
                    </p>
                )}

                {currentResponse && currentResponse.result && (
                    <div className={styles.result}>
                        <span className={styles.resultLabel}>Resultado</span>
                        <strong>{currentResponse.result}</strong>
                    </div>
                )}

                {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}
                {notice && <p className="aviso" role="status">{notice}</p>}

                <ScaleForm
                    items={definition.items}
                    values={values}
                    onChange={changeAnswer}
                    disabled={isReadOnly || isSaving}
                />

                {isPeriod && currentResponse && (
                    <p className={styles.periodNote}>
                        Período respondido: {formatDate(currentResponse.periodStart)} a {formatDate(currentResponse.periodEnd)}.
                    </p>
                )}

                <FormActions>
                    <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                        Voltar
                    </button>
                    {!isReadOnly && (
                        <button type="submit" className="button" disabled={isSaving}>
                            {isSaving ? "Salvando..." : "Salvar respostas"}
                        </button>
                    )}
                </FormActions>
            </form>
        </section>
    );
}
