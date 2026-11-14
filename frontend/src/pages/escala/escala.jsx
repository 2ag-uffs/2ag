import {useEffect, useMemo, useRef, useState} from "react";
import {useBlocker, useLocation, useNavigate, useParams, useSearchParams} from "react-router";
import {FiCheck, FiEdit2, FiMoon} from "react-icons/fi";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import {FormActions} from "../../components/form-section/form-section.jsx";
import BusyButton from "../../components/form/busy-button.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import ScaleForm from "../../components/scale-form/scale-form.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {ApiError, apiService, errorMessageOf, getLoggedUser} from "../../services/api.js";
import {addDays, formatDate, formatWeekdayAndDate, mondayOf, shortTime, toIsoDate} from "../../utils/date-format.js";
import {useLeaveWarning} from "../../utils/leave-warning.js";
import {answersPayload, answersToValues, missingItemsOf, requiredItemsOf} from "../../utils/scale-answers.js";
import styles from "./escala.module.css";

// os acompanhamentos semanais falam da ultima semana
const PERIOD_DAYS = 7;

const SLEEP_DIARY_SLUG = "diario-sono";

// pode ter duas pendentes da mesma escala (a antiga q voltou pra pendente e a atual)
// vale a q cobre o dia pq o servidor liga cada dia na tarefa q cobre ele e sem nenhuma a q comecou por ultimo
function openTaskOf(pendingTasks, slug, dayIso) {
    const tasksOfScale = pendingTasks.filter((task) => task.path === "/escalas/" + slug);
    const taskOfDay = tasksOfScale.find((task) => task.periodStart <= dayIso && task.periodEnd >= dayIso);
    if (taskOfDay) {
        return taskOfDay;
    }
    let latestTask = null;
    for (const task of tasksOfScale) {
        if (latestTask === null || task.periodStart > latestTask.periodStart) {
            latestTask = task;
        }
    }
    return latestTask;
}

// c/ a hora no texto a data eh lida no fuso local e n volta um dia
function localDateOf(dayIso) {
    return new Date(dayIso + "T00:00:00");
}

// a semana q fechou ontem continua na grade ate o fim de hoje, e o dia preenchido nela conta como atraso
// (decisao de 14/11/2026, revisao das regras R6). a pendente q venceu ontem e o job ainda n fechou ja eh a aberta
function closedTaskOfYesterday(tasks, slug, today) {
    const yesterday = toIsoDate(addDays(localDateOf(today), -1));
    return tasks.find((task) => task.path === "/escalas/" + slug && task.status !== "PENDENTE"
        && task.periodEnd === yesterday) || null;
}

function daysBetween(firstDay, lastDay) {
    const days = [];
    let dayIso = firstDay;
    while (dayIso <= lastDay) {
        days.push(dayIso);
        const nextDay = toIsoDate(addDays(localDateOf(dayIso), 1));
        // se a conta do dia seguinte n andar a grade para aqui em vez de travar a tela
        if (nextDay <= dayIso) {
            break;
        }
        dayIso = nextDay;
    }
    return days;
}

function DayGrid({days, responses, selectedDay, unsavedDays, isInsideTask, lateUntil, onChooseDay}) {
    const stripRef = useRef(null);

    // no celular a faixa rola de lado e o dia escolhido abria cortado, entao ela anda ate ele
    // mexe so na faixa: o scrollIntoView rolaria a pagina tbm e o aviso perto do salvar sairia da vista
    useEffect(() => {
        const strip = stripRef.current;
        const chosenDay = strip ? strip.querySelector("[aria-pressed='true']") : null;
        if (!chosenDay) {
            return;
        }
        const stripBox = strip.getBoundingClientRect();
        const dayBox = chosenDay.getBoundingClientRect();
        if (dayBox.left < stripBox.left || dayBox.right > stripBox.right) {
            strip.scrollLeft += dayBox.left - stripBox.left;
        }
    }, [days, selectedDay]);

    return (
        <section className={styles.daysSection}>
            <h2 className={styles.sectionTitle}>{isInsideTask ? "Dias do período" : "Dia da semana"}</h2>
            {isInsideTask && (
                <p className={styles.daysHint}>
                    Esqueceu algum dia? Escolha o dia na grade e preencha agora.
                </p>
            )}
            {lateUntil && (
                <p className={styles.daysHint}>
                    Os dias até {formatDate(lateUntil)} são do período que já fechou: dá para preencher até o fim
                    de hoje, e eles contam como preenchidos com atraso.
                </p>
            )}
            <div
                ref={stripRef}
                className={styles.days}
                role="group"
                aria-label={isInsideTask ? "Dias do período" : "Dias da semana"}
            >
                {days.map((dayIso) => {
                    // dia so com resposta anulada volta a ficar em branco
                    const isFilled = responses.some((response) => response.periodStart === dayIso
                        && !response.annulled);
                    // dia mexido e ainda n salvo avisa na grade, senao a pessoa sai achando q salvou
                    const isUnsaved = unsavedDays.includes(dayIso);
                    let markClass = styles.dayMark;
                    if (isUnsaved) {
                        markClass = styles.dayMark + " " + styles.dayUnsaved;
                    } else if (isFilled) {
                        markClass = styles.dayMark + " " + styles.dayFilled;
                    }
                    return (
                        <button
                            key={dayIso}
                            type="button"
                            className={dayIso === selectedDay ? styles.day + " " + styles.dayChosen : styles.day}
                            aria-pressed={dayIso === selectedDay}
                            onClick={() => onChooseDay(dayIso)}
                        >
                            <span className={styles.dayName}>{formatWeekdayAndDate(dayIso)}</span>
                            <span className={markClass}>
                                {isUnsaved && <FiEdit2 aria-hidden="true"/>}
                                {!isUnsaved && isFilled && <FiCheck aria-hidden="true"/>}
                                {isUnsaved ? "não salvo" : (isFilled ? "preenchido" : "em branco")}
                            </span>
                        </button>
                    );
                })}
            </div>
        </section>
    );
}

// tela unica de preenchimento de escala (RF08 e RF21 a RF26)
//
// o formulario inteiro vem do servidor: titulo, itens, ancoras e
// opcoes. escala nova aparece aqui sem tela nova
export default function Escala() {
    const {slug} = useParams();
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const location = useLocation();
    const loggedUser = getLoggedUser();

    const today = toIsoDate(new Date());
    const chosenDate = searchParams.get("data");
    // a data da url so vale se o dia ja chegou
    const startingDay = chosenDate && chosenDate <= today ? chosenDate : today;

    const [definition, setDefinition] = useState(null);
    const [responses, setResponses] = useState([]);
    const [sleepSchedule, setSleepSchedule] = useState(null);
    const [openTask, setOpenTask] = useState(null);
    // o periodo q fechou ontem, q ainda aparece na grade ate o fim de hoje
    const [lateTask, setLateTask] = useState(null);
    const [loadError, setLoadError] = useState(null);
    // o q a paciente mexeu fica guardado por dia ate salvar
    const [editsByDay, setEditsByDay] = useState({});
    const [selectedDay, setSelectedDay] = useState(startingDay);
    const [periodEndByDay, setPeriodEndByDay] = useState({});
    // o inicio do periodo q a paciente mexeu na correcao, antes de salvar
    const [periodStartByDay, setPeriodStartByDay] = useState({});
    const [notice, setNotice] = useState(null);
    const [formError, setFormError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    // itens da escala pontuada q ficaram em branco no ultimo envio
    const [missingKeys, setMissingKeys] = useState([]);
    // o erro de campo q a api mandou no ultimo envio, por dia, como o horario de levantar igual ao de dormir
    const [fieldErrorsByDay, setFieldErrorsByDay] = useState({});
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
                    setLoadError(errorMessageOf(requestError,
                        "Não foi possível abrir a escala. Confira sua internet e tente de novo."));
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [slug, chosenDate, reloadCount]);

    // a programacao de horarios do diario do sono, q o prescritor define
    // no acompanhamento. paciente sem acompanhamento n tem programacao
    useEffect(() => {
        if (slug !== SLEEP_DIARY_SLUG) {
            return;
        }
        apiService.get("/patients/" + loggedUser.id + "/treatment-protocol")
            .then(setSleepSchedule)
            .catch(() => setSleepSchedule(null));
    }, [slug, loggedUser.id]);

    const isDiary = definition ? definition.fillMode === "DIARIO" : false;
    const isPeriod = definition ? definition.fillMode === "PERIODO" : false;

    // a tarefa aberta dessa escala, pq a grade do diario precisa seguir a janela dela
    // com a data na url (o aviso da anulacao abre no dia anulado) vale a pendente q cobre esse dia, q eh a
    // semana reaberta e n a de hoje
    useEffect(() => {
        if (!isDiary) {
            return;
        }
        const patientPath = "/patients/" + loggedUser.id;
        Promise.all([
            apiService.get(patientPath + "/scales/overview"),
            apiService.get(patientPath + "/scales").catch(() => []),
        ])
            .then(([page, tasks]) => {
                const task = openTaskOf(page.pending, slug, startingDay);
                const closedTask = closedTaskOfYesterday(tasks, slug, today);
                setOpenTask(task);
                setLateTask(closedTask);
                // a pendente q ja venceu ainda aceita os dias dela (antes do job das 8h, ou qnd volta pela
                // anulacao), entao a tela abre no ultimo dia dela e a grade mostra o periodo dela
                // sem nenhuma aberta, a tela abre no ultimo dia do periodo q fechou ontem
                const lastDayToOpen = task ? task.periodEnd : (closedTask ? closedTask.periodEnd : today);
                if (lastDayToOpen < today && !chosenDate) {
                    setSelectedDay((currentDay) => (currentDay === today ? lastDayToOpen : currentDay));
                }
            })
            .catch(() => setOpenTask(null));
    }, [isDiary, slug, loggedUser.id, today, chosenDate, startingDay]);

    const insideOpenTask = openTask !== null
        && selectedDay >= openTask.periodStart
        && selectedDay <= openTask.periodEnd;
    const insideLateTask = lateTask !== null
        && selectedDay >= lateTask.periodStart
        && selectedDay <= lateTask.periodEnd;
    // o periodo q fechou ontem vem junto do aberto, e escolher um dia dele deixa a mesma grade
    const showsLateTask = lateTask !== null && (insideLateTask || insideOpenTask);

    // a grade cobre o periodo inteiro da tarefa aberta, pro paciente preencher de uma vez os dias q
    // esqueceu, mesmo qnd o periodo passa de uma semana (issue 71). fora da tarefa fica a semana do
    // dia escolhido, como no papel, pq eh por ela q o "ver e corrigir" do historico chega no dia antigo
    // dia q ainda n chegou nunca aparece
    const gridDays = useMemo(() => {
        let firstDay = toIsoDate(mondayOf(localDateOf(selectedDay)));
        let periodLastDay = toIsoDate(addDays(localDateOf(firstDay), 6));
        if (insideOpenTask || showsLateTask) {
            const shownTasks = [openTask, showsLateTask ? lateTask : null]
                .filter((task) => task !== null && task.periodStart <= today);
            firstDay = shownTasks.map((task) => task.periodStart).sort()[0];
            periodLastDay = shownTasks.map((task) => task.periodEnd).sort()[shownTasks.length - 1];
        }
        const lastDay = periodLastDay < today ? periodLastDay : today;
        return daysBetween(firstDay, lastDay);
    }, [selectedDay, today, openTask, lateTask, insideOpenTask, showsLateTask]);

    // resposta anulada fica so no historico: o dia dela abre em branco pro paciente responder de novo
    const currentResponse = useMemo(
        () => responses.find((response) => response.periodStart === selectedDay && !response.annulled) || null,
        [responses, selectedDay]);
    const annulledResponse = currentResponse === null
        ? responses.find((response) => response.periodStart === selectedDay && response.annulled) || null
        : null;
    const hasAnnulledResponse = annulledResponse !== null;

    // cada dia mostra o q ja foi respondido nele ou o q a paciente esta mexendo
    // dia em branco abre em branco e a tela nunca sugere valor (RN10)
    const savedValues = currentResponse ? answersToValues(currentResponse.answers) : {};
    const values = editsByDay[selectedDay] || savedValues;
    // dia mexido com alguma resposta e ainda n salvo. o rascunho todo limpo n tem o q salvar
    const hasDraft = (dayIso) => Boolean(definition && editsByDay[dayIso])
        && Object.keys(answersPayload(definition.items, editsByDay[dayIso])).length > 0;
    // no diario sao os dias mexidos, em ordem de data. fora dele so o dia da tela, pq o rascunho de outra
    // data some da tela e n vai no salvar
    const unsavedDays = isDiary ? Object.keys(editsByDay).filter(hasDraft).sort() : [selectedDay].filter(hasDraft);
    // o periodo ja respondido abre com o fim q foi salvo e n com hoje. o so com resposta anulada abre com o
    // fim dela: o aviso da anulacao chega pela data, e com o fim em hoje a resposta nova caia na pendencia
    // mais nova da escala em vez da reaberta
    const savedResponse = currentResponse || annulledResponse;
    const periodEnd = periodEndByDay[selectedDay] || (savedResponse ? savedResponse.periodEnd : today);

    // o recado de um dia n vale pro outro entao trocar de dia limpa os avisos
    // salvar n passa por aqui e o recado de salvo continua na tela
    const changeDay = (dayIso) => {
        setSelectedDay(dayIso);
        setNotice(null);
        setFormError(null);
        setMissingKeys([]);
    };

    const changePeriodEnd = (dayIso) => {
        setPeriodEndByDay((current) => ({...current, [selectedDay]: dayIso}));
    };

    const isReadOnly = currentResponse !== null && !currentResponse.editableByPatient;

    // a correcao de escala de periodo abre pela data (?data=) e mexe na resposta q ja existe:
    // as datas viram campo dela e salvar atualiza ela, em vez de criar outra no dia novo
    // e deixar a errada no historico. no diario o dia eh o da grade e a correcao segue
    // pelo envio do dia, q o servidor ja trata como correcao
    const isCorrecting = !isDiary && chosenDate !== null && currentResponse !== null && !isReadOnly;
    const periodStart = isCorrecting
        ? (periodStartByDay[selectedDay] || currentResponse.periodStart)
        : selectedDay;

    const changePeriodStart = (dayIso) => {
        setPeriodStartByDay((current) => ({...current, [selectedDay]: dayIso}));
    };

    const changeAnswer = (key, value) => {
        setEditsByDay((current) => ({
            ...current,
            [selectedDay]: {...(current[selectedDay] || savedValues), [key]: value},
        }));
        setMissingKeys((currentKeys) => currentKeys.filter((currentKey) => currentKey !== key));
    };

    // manda a resposta de um dia do diario, ou do periodo q esta na tela
    const sendResponse = (dayIso) => {
        const dayValues = dayIso === selectedDay ? values : editsByDay[dayIso];
        const body = {
            periodStart: isDiary ? dayIso : periodStart,
            periodEnd: isDiary ? dayIso : (isPeriod ? periodEnd : periodStart),
            answers: answersPayload(definition.items, dayValues),
        };
        return isCorrecting
            ? apiService.put("/scales/responses/" + currentResponse.id, body)
            : apiService.post("/scales/" + slug + "/responses", body);
    };

    const save = async (event) => {
        event.preventDefault();
        setFormError(null);
        setNotice(null);
        setFieldErrorsByDay({});

        // no diario salvar manda cada dia editado, em ordem de data, pro dia preenchido n ficar pra tras
        // sem nenhum dia editado vai o dia da tela, como sempre foi
        const daysToSave = isDiary && unsavedDays.length > 0 ? unsavedDays : [selectedDay];
        for (const dayIso of daysToSave) {
            const dayValues = dayIso === selectedDay ? values : editsByDay[dayIso];
            const missingItems = missingItemsOf(requiredItemsOf(definition), dayValues);
            if (missingItems.length > 0) {
                setSelectedDay(dayIso);
                setMissingKeys(missingItems.map((item) => item.key));
                return;
            }
        }

        setIsSaving(true);
        const savedDays = [];
        const savedResponses = [];
        const failures = [];
        const fieldErrors = {};
        for (const dayIso of daysToSave) {
            try {
                savedResponses.push(await sendResponse(dayIso));
                savedDays.push(dayIso);
            } catch (requestError) {
                // o dia recusado continua como rascunho e o erro diz qual dia foi
                const message = errorMessageOf(requestError);
                failures.push(isDiary
                    ? formatWeekdayAndDate(dayIso) + ": a resposta não foi salva. " + message
                    : message);
                if (requestError instanceof ApiError) {
                    fieldErrors[dayIso] = requestError.fieldErrors();
                }
            }
        }
        setIsSaving(false);
        setFieldErrorsByDay(fieldErrors);
        if (failures.length > 0) {
            setFormError(failures.join(" "));
        }
        if (savedResponses.length === 0) {
            return;
        }

        const lastSaved = savedResponses[savedResponses.length - 1];
        if (savedResponses.length > 1) {
            setNotice("Respostas de " + savedResponses.length + " dias salvas.");
        } else {
            setNotice(lastSaved.result ? "Respostas salvas. Resultado: " + lastSaved.result : "Respostas salvas.");
        }
        // as respostas salvas entram na lista na hora e o rascunho dos dias salvos sai
        setResponses((current) => [...savedResponses, ...current.filter((response) => !savedResponses.some(
            (saved) => response.id === saved.id || response.periodStart === saved.periodStart))]);
        const withoutSavedDays = (byDay) => {
            const next = {...byDay};
            savedDays.forEach((dayIso) => {
                next[dayIso] = null;
            });
            return next;
        };
        setEditsByDay(withoutSavedDays);
        setPeriodEndByDay(withoutSavedDays);
        setPeriodStartByDay(withoutSavedDays);
        // a tela segue na resposta salva ja em modo de correcao: mexer na data dps
        // de salvar corrige essa resposta, em vez de abrir outro dia em branco
        if (!isDiary && chosenDate !== lastSaved.periodStart) {
            setSelectedDay(lastSaved.periodStart);
            navigate("/escalas/" + slug + "?data=" + lastSaved.periodStart, {replace: true});
        } else {
            setReloadCount((currentCount) => currentCount + 1);
        }
    };

    // sair da tela com dia mexido e n salvo pergunta antes, pra resposta n sumir calada
    // vale pra qqr saida: o Voltar do formulario, a seta do topo, o Voltar de cima e o menu
    // trocar so a data na url continua na tela, e sair da conta n pergunta pq a sessao ja acabou
    const exitBlocker = useBlocker(({currentLocation, nextLocation}) => unsavedDays.length > 0
        && getLoggedUser() !== null
        && currentLocation.pathname !== nextLocation.pathname);
    useLeaveWarning(unsavedDays.length > 0);

    // a primeira tela da aba (endereco, favorito ou link colado, msm dps de um F5) n tem tela do app atras e o
    // navigate(-1) saia do app sem o blocker ver, entao o voltar vai pra central de escalas e o modal pergunta
    // o idx q o router guarda no history.state segue 0 qnd salvar com outra data troca o endereco, ja a key muda
    // o F5 na escala aberta pelo app deixa a tela de antes na aba, entao ali o voltar volta pra ela
    const goBack = () => {
        if (location.key === "default" || window.history.state?.idx === 0) {
            navigate("/pacientes/" + loggedUser.id + "/escalas");
        } else {
            navigate(-1);
        }
    };

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!definition) {
        return <SkeletonPage cards={2}/>;
    }

    const hasRequiredItems = requiredItemsOf(definition).length > 0;
    const missingLabels = definition.items
        .filter((item) => missingKeys.includes(item.key))
        .map((item) => item.label);

    return (
        <section className={styles.page}>
            <PageHeader title={definition.title} subtitle={definition.instruction}/>

            {sleepSchedule && (sleepSchedule.sleepBedTime || sleepSchedule.sleepWakeTime) && (
                <p className={styles.schedule}>
                    <FiMoon aria-hidden="true"/>
                    Programação do seu prescritor: dormir às {shortTime(sleepSchedule.sleepBedTime) || "—"} e
                    levantar às {shortTime(sleepSchedule.sleepWakeTime) || "—"}.
                </p>
            )}

            {isDiary && (
                <DayGrid
                    days={gridDays}
                    responses={responses}
                    selectedDay={selectedDay}
                    unsavedDays={unsavedDays}
                    isInsideTask={insideOpenTask || showsLateTask}
                    lateUntil={showsLateTask ? lateTask.periodEnd : null}
                    onChooseDay={changeDay}
                />
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
                                value={periodStart}
                                disabled={isReadOnly}
                                onChange={(event) => (isCorrecting
                                    ? changePeriodStart(event.target.value)
                                    : changeDay(event.target.value))}
                            />
                        </div>
                        {isPeriod && (
                            <div className={styles.dateField}>
                                <label htmlFor="periodEnd">Fim do período</label>
                                <input
                                    id="periodEnd"
                                    type="date"
                                    min={periodStart}
                                    max={today}
                                    value={periodEnd}
                                    disabled={isReadOnly}
                                    onChange={(event) => changePeriodEnd(event.target.value)}
                                />
                            </div>
                        )}
                    </div>
                )}

                {isReadOnly && (
                    <p className="aviso">O prescritor já analisou esta resposta. Para corrigir, fale com ele.</p>
                )}

                {hasAnnulledResponse && (
                    <p className="aviso">
                        A resposta desta data foi anulada pelo prescritor e continua no histórico. Você pode responder
                        de novo.
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

                {hasRequiredItems && !isReadOnly && (
                    <p className="aviso">
                        Responda todos os itens antes de salvar. Sem eles a escala pode ficar sem escore e continuar pendente.
                    </p>
                )}

                <ScaleForm
                    items={definition.items}
                    bands={definition.bands}
                    values={values}
                    onChange={changeAnswer}
                    disabled={isReadOnly || isSaving}
                    missingKeys={missingKeys}
                    fieldErrors={fieldErrorsByDay[selectedDay] || {}}
                    showsUrgencyNotice={true}
                />

                {!isDiary && currentResponse && (
                    <p className={styles.periodNote}>
                        {isPeriod
                            ? "Período respondido: " + formatDate(currentResponse.periodStart) + " a "
                                + formatDate(currentResponse.periodEnd) + "."
                            : "Avaliação de " + formatDate(currentResponse.periodStart) + "."}
                        {isCorrecting && " Mudar a data corrige esta resposta, sem criar outra."}
                    </p>
                )}

                {/* perto do botao pq no topo o aviso some num form comprido */}
                {missingLabels.length > 0 && (
                    <p className="aviso aviso--atencao" role="alert">
                        Falta preencher: {missingLabels.join(", ")}.
                    </p>
                )}

                <FormActions>
                    <button type="button" className="button-secondary" onClick={goBack}>
                        Voltar
                    </button>
                    {!isReadOnly && (
                        <BusyButton type="submit" className="button" isBusy={isSaving} busyText="Salvando...">
                            Salvar respostas
                        </BusyButton>
                    )}
                </FormActions>
            </form>

            <ConfirmModal
                show={exitBlocker.state === "blocked"}
                title="Sair sem salvar"
                message={isDiary && unsavedDays.length > 1
                    ? "Você preencheu " + unsavedDays.length + " dias que ainda não foram salvos e vão se perder. "
                        + "Quer mesmo sair?"
                    : "O que você preencheu ainda não foi salvo e vai se perder. Quer mesmo sair?"}
                confirmText="Sim, sair"
                cancelText="Continuar preenchendo"
                onConfirm={() => exitBlocker.proceed()}
                onCancel={() => exitBlocker.reset()}
            />
        </section>
    );
}
