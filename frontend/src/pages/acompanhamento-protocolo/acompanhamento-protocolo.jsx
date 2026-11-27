import {useCallback, useEffect, useRef, useState} from "react";
import {Link, useNavigate, useParams} from "react-router";
import {FiCalendar, FiMoon, FiUsers} from "react-icons/fi";
import Card from "../../components/card/card.jsx";
import ChoiceCard from "../../components/choice-card/choice-card.jsx";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import EmptyState from "../../components/empty-state/empty-state.jsx";
import FormSection, {FieldRow, FormActions} from "../../components/form-section/form-section.jsx";
import BusyButton from "../../components/form/busy-button.jsx";
import TextField from "../../components/form/text-field.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError, CONNECTION_ERROR_MESSAGE, errorMessageOf} from "../../services/api.js";
import {addDays, formatDate, shortTime, toIsoDate} from "../../utils/date-format.js";
import {emptyToNull} from "../../utils/form-values.js";
import styles from "./acompanhamento-protocolo.module.css";

// a api aceita de 7 a 365 dias (TreatmentProtocolCreateDTO)
const MIN_DURATION_DAYS = 7;
const MAX_DURATION_DAYS = 365;
const DEFAULT_DURATION_DAYS = 90;

const PERIODICITIES = [
    {value: "SEMANAL", label: "Toda semana"},
    {value: "QUINZENAL", label: "A cada 14 dias"},
    {value: "MENSAL", label: "Uma vez por mês"},
];

// escala marcada agora comeca toda semana
const DEFAULT_PERIODICITY = "SEMANAL";

// nos ultimos 7 dias a prescritora recebe o aviso e ja pode renovar, igual a api (RENEWAL_DAYS)
const RENEWAL_DAYS = 7;

const MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000;

// so esses campos mostram o proprio erro no formulario
const FIELDS_THAT_SHOW_ERROR = ["startDate", "durationDays"];

// a api recusa o acompanhamento q terminaria antes de hoje, entao o inicio volta no maximo a duracao menos um dia
// duracao vazia ou fora da faixa a api recusa de qualquer jeito, ai vale a menor
function earliestStartDate(durationDays) {
    let days = Number(durationDays);
    if (!(days >= MIN_DURATION_DAYS && days <= MAX_DURATION_DAYS)) {
        days = MIN_DURATION_DAYS;
    }
    return toIsoDate(addDays(new Date(), 1 - days));
}

// quantos dias o acompanhamento durou, contando o primeiro e o ultimo
function durationOf(protocol) {
    const start = Date.parse(protocol.startDate + "T00:00:00Z");
    const end = Date.parse(protocol.endDate + "T00:00:00Z");
    return Math.round((end - start) / MILLISECONDS_PER_DAY) + 1;
}

// a renovacao vem com as escalas, as frequencias, a duracao e os horarios do ultimo acompanhamento
function formValuesOf(protocol) {
    const chosen = {};
    protocol.items.forEach((item) => {
        chosen[item.scaleType] = item.periodicity;
    });
    return {
        chosen,
        durationDays: durationOf(protocol),
        sleepBedTime: shortTime(protocol.sleepBedTime),
        sleepWakeTime: shortTime(protocol.sleepWakeTime),
    };
}

function periodicityLabelOf(periodicity) {
    const option = PERIODICITIES.find((current) => current.value === periodicity);
    if (!option) {
        return null;
    }
    return option.label;
}

// o acompanhamento automatico de 90 dias (RF32)
//
// a prescritora escolhe uma vez quais escalas e de quanto em quanto
// tempo, e o sistema envia sozinho dali em diante. antes ela designava
// formulario por formulario, paciente por paciente
export default function AcompanhamentoProtocolo() {
    const navigate = useNavigate();
    const {patientId} = useParams();

    // o ultimo acompanhamento, valendo ou encerrado: ele preenche a renovacao (revisao das regras R5)
    const [lastProtocol, setLastProtocol] = useState(null);
    const [isArchived, setIsArchived] = useState(false);
    const [scales, setScales] = useState([]);
    const [chosen, setChosen] = useState({});
    const [startDate, setStartDate] = useState(toIsoDate(new Date()));
    const [durationDays, setDurationDays] = useState(DEFAULT_DURATION_DAYS);
    const [sleepBedTime, setSleepBedTime] = useState("");
    const [sleepWakeTime, setSleepWakeTime] = useState("");

    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const [formError, setFormError] = useState(null);
    // conta cada aviso mostrado, pq a msm msg de novo n muda o formError e a tela n rolaria ate ele
    const [formErrorCount, setFormErrorCount] = useState(0);
    const [fieldErrors, setFieldErrors] = useState({});
    const [notice, setNotice] = useState(null);
    const [isEndingOpen, setIsEndingOpen] = useState(false);
    const formErrorRef = useRef(null);

    // o esqueleto so aparece na primeira carga e depois de salvar a tela so atualiza
    const load = useCallback(() => {
        // a avaliacao inicial fica de fora: ela eh da triagem e n repete a cada periodo
        apiService.get("/scales/assignable")
            .then((allScales) => setScales(allScales.filter((scale) => scale.assignablePeriodically)))
            .catch(() => setScales([]));

        // 404 aqui so quer dizer que o paciente nunca teve acompanhamento
        const protocolRequest = apiService.get("/patients/" + patientId + "/treatment-protocol/last")
            .catch((requestError) => {
                if (requestError instanceof ApiError && requestError.status === 404) {
                    return null;
                }
                throw requestError;
            });

        // o paciente vem junto pq a api recusa acompanhamento novo de quem foi arquivado
        // e sem acesso a ele (paciente de outro prescritor) a tela fica so com o aviso, sem formulario
        Promise.all([apiService.get("/patients/" + patientId), protocolRequest])
            .then(([patient, foundProtocol]) => {
                setIsArchived(patient.archived);
                setLastProtocol(foundProtocol);
                if (foundProtocol) {
                    const values = formValuesOf(foundProtocol);
                    setChosen(values.chosen);
                    setDurationDays(values.durationDays);
                    setSleepBedTime(values.sleepBedTime);
                    setSleepWakeTime(values.sleepWakeTime);
                    setStartDate(toIsoDate(new Date()));
                }
            })
            .catch((requestError) => {
                // 403 e 404 querem dizer q o paciente n eh dele, igual no progresso
                const isNotTheirs = requestError instanceof ApiError
                    && (requestError.status === 403 || requestError.status === 404);
                setLoadError(isNotTheirs
                    ? "Acesso negado. Esse paciente não está na sua lista de pacientes."
                    : errorMessageOf(requestError,
                        "Não foi possível abrir o acompanhamento. Confira sua internet e tente de novo."));
            })
            .finally(() => setIsLoading(false));
    }, [patientId]);

    useEffect(load, [load]);

    // o botao de iniciar fica no fim do formulario e o aviso la em cima, entao a tela rola ate ele
    // senao a recusa parece um clique q n fez nada
    useEffect(() => {
        if (formErrorRef.current) {
            formErrorRef.current.scrollIntoView({block: "center"});
        }
    }, [formErrorCount]);

    const showFormError = (message) => {
        setFormError(message);
        setFormErrorCount((count) => count + 1);
    };

    const toggleScale = (type) => {
        setChosen((current) => {
            const copy = {...current};
            if (copy[type]) {
                delete copy[type];
            } else {
                copy[type] = DEFAULT_PERIODICITY;
            }
            return copy;
        });
    };

    const changePeriodicity = (type, periodicity) => {
        setChosen((current) => ({...current, [type]: periodicity}));
    };

    const create = async (event) => {
        event.preventDefault();
        setFormError(null);
        setFieldErrors({});

        const items = Object.entries(chosen).map(([scaleType, periodicity]) => ({scaleType, periodicity}));
        if (items.length === 0) {
            showFormError("Escolha ao menos uma escala para o acompanhamento.");
            return;
        }

        // os horarios do sono so aparecem com o diario marcado, entao sem ele n vao escondidos no envio
        const hasSleepDiary = Boolean(chosen.REGISTRO_SONO);

        setIsSaving(true);
        try {
            await apiService.post("/patients/" + patientId + "/treatment-protocol", {
                startDate,
                durationDays: Number(durationDays),
                sleepBedTime: hasSleepDiary ? emptyToNull(sleepBedTime) : null,
                sleepWakeTime: hasSleepDiary ? emptyToNull(sleepWakeTime) : null,
                items,
            });
            setNotice(lastProtocol !== null
                ? "Acompanhamento renovado. O sistema continua enviando as escalas sozinho."
                : "Acompanhamento iniciado. O sistema vai enviar as escalas sozinho.");
            load();
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                const errorsByField = requestError.fieldErrors();
                setFieldErrors(errorsByField);
                // erro de campo q n aparece no formulario, tipo o limite de escalas, vai pro aviso de cima
                const hiddenErrors = Object.keys(errorsByField)
                    .filter((field) => !FIELDS_THAT_SHOW_ERROR.includes(field))
                    .map((field) => errorsByField[field]);
                showFormError(hiddenErrors.length > 0 ? hiddenErrors.join(". ") + "." : requestError.message);
            } else {
                showFormError(CONNECTION_ERROR_MESSAGE);
            }
        } finally {
            setIsSaving(false);
        }
    };

    const end = async () => {
        setIsEndingOpen(false);
        setFormError(null);
        try {
            await apiService.put("/patients/" + patientId + "/treatment-protocol/end");
            setNotice("Acompanhamento encerrado. O sistema para de enviar escalas para este paciente, "
                + "e as do acompanhamento que esperavam resposta foram fechadas.");
            load();
        } catch (requestError) {
            showFormError(errorMessageOf(requestError));
        }
    };

    if (isLoading) {
        return <SkeletonPage cards={2}/>;
    }

    if (loadError) {
        return (
            <section className={styles.page}>
                <PageHeader title="Acompanhamento automático"/>
                <p className="aviso aviso--atencao" role="alert">{loadError}</p>
                <EmptyState
                    icon={FiUsers}
                    message="Escolha um dos seus pacientes para ver o acompanhamento automático dele."
                    action={<Link to="/lista-paciente" className="button-secondary">Ver pacientes</Link>}
                />
            </section>
        );
    }

    const today = toIsoDate(new Date());
    // so o q esta valendo aparece no quadro, o encerrado so preenche o formulario
    const protocol = lastProtocol !== null && lastProtocol.active ? lastProtocol : null;
    // o job das 8h encerra o q passou do fim, mas ate la ele ja aparece encerrado
    // e o cadastro do novo encerra ele sozinho, entao o formulario de renovar aparece junto
    const hasProtocolEnded = protocol !== null && protocol.endDate < today;
    // nos ultimos 7 dias a renovacao tbm aparece, e o novo comeca hoje no lugar do atual (revisao das regras R5)
    const isEndingSoon = protocol !== null && !hasProtocolEnded
        && protocol.endDate <= toIsoDate(addDays(new Date(), RENEWAL_DAYS));
    const isRenewal = lastProtocol !== null;

    return (
        <section className={styles.page}>
            <PageHeader
                title="Acompanhamento automático"
                subtitle="O sistema envia as escalas na frequência escolhida, sem ninguém precisar lembrar."
            />

            {notice && <p className="aviso" role="status">{notice}</p>}
            {formError && <p className="aviso aviso--atencao" role="alert" ref={formErrorRef}>{formError}</p>}

            {protocol && (
                <Card
                    title={hasProtocolEnded ? "Último acompanhamento" : "Em andamento"}
                    headerAction={hasProtocolEnded
                        ? <span className="etiqueta etiqueta--neutra">Encerrado</span>
                        : <span className="etiqueta etiqueta--ok">Ativo</span>}
                    footer={hasProtocolEnded ? null : (
                        <>
                            <button
                                type="button"
                                className="button-danger button-small"
                                onClick={() => setIsEndingOpen(true)}
                            >
                                Encerrar acompanhamento
                            </button>
                            <button type="button" className="button-secondary button-small" onClick={() => navigate(-1)}>
                                Voltar
                            </button>
                        </>
                    )}
                >
                    <div className={styles.facts}>
                        <span className={styles.fact}>
                            <FiCalendar aria-hidden="true"/>
                            {protocol.patientName}: {formatDate(protocol.startDate)} até {formatDate(protocol.endDate)}
                        </span>
                        {(protocol.sleepBedTime || protocol.sleepWakeTime) && (
                            <span className={styles.fact}>
                                <FiMoon aria-hidden="true"/>
                                Dormir às {shortTime(protocol.sleepBedTime) || "—"} e
                                levantar às {shortTime(protocol.sleepWakeTime) || "—"}
                            </span>
                        )}
                    </div>

                    {isEndingSoon && (
                        <p className="aviso">
                            Este acompanhamento termina em {formatDate(protocol.endDate)}. Renove abaixo para as
                            escalas continuarem chegando: o novo começa hoje, no lugar deste.
                        </p>
                    )}

                    <div className={styles.tableWrap}>
                        <table className={styles.table}>
                            <thead>
                            <tr>
                                <th>Escala</th>
                                <th>Frequência</th>
                            </tr>
                            </thead>
                            <tbody>
                            {protocol.items.map((item) => (
                                <tr key={item.scaleType}>
                                    <td>{item.scaleName}</td>
                                    <td>{periodicityLabelOf(item.periodicity)}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                </Card>
            )}

            {/* o arquivamento encerra o acompanhamento e um novo so comeca dps de reativar */}
            {isArchived && (
                <p className="aviso">Paciente arquivado. Reative o paciente para começar um acompanhamento novo.</p>
            )}

            {(!protocol || hasProtocolEnded || isEndingSoon) && !isArchived && (
                <form className={styles.form} onSubmit={create}>
                    {isRenewal && (
                        <p className="aviso">Preenchido com o último acompanhamento. Confira antes de renovar.</p>
                    )}
                    <FormSection title="Período" disabled={isSaving}>
                        <FieldRow>
                            {/* a renovacao antes do fim encerra o atual, entao o novo comeca no maximo hoje */}
                            <TextField
                                label="Começa em"
                                name="startDate"
                                type="date"
                                min={earliestStartDate(durationDays)}
                                max={isEndingSoon ? today : undefined}
                                value={startDate}
                                required={true}
                                onChange={(event) => setStartDate(event.target.value)}
                                error={fieldErrors.startDate}
                            />
                            <TextField
                                label="Duração em dias"
                                name="durationDays"
                                type="number"
                                min={MIN_DURATION_DAYS}
                                max={MAX_DURATION_DAYS}
                                value={durationDays}
                                required={true}
                                onChange={(event) => setDurationDays(event.target.value)}
                                error={fieldErrors.durationDays}
                            />
                        </FieldRow>
                    </FormSection>

                    <FormSection
                        title="Escalas e frequência"
                        description="Marque as escalas e escolha de quanto em quanto tempo cada uma é enviada."
                        disabled={isSaving}
                    >
                        <ul className={styles.list}>
                            {scales.map((scale) => (
                                <li key={scale.type}>
                                    <ChoiceCard
                                        title={scale.name}
                                        description={scale.description}
                                        checked={Boolean(chosen[scale.type])}
                                        onChange={() => toggleScale(scale.type)}
                                    >
                                        {chosen[scale.type] && (
                                            <select
                                                className={styles.select}
                                                aria-label={"Frequência de " + scale.name}
                                                value={chosen[scale.type]}
                                                onChange={(event) => changePeriodicity(scale.type, event.target.value)}
                                            >
                                                {PERIODICITIES.map((option) => (
                                                    <option key={option.value} value={option.value}>{option.label}</option>
                                                ))}
                                            </select>
                                        )}
                                    </ChoiceCard>
                                </li>
                            ))}
                        </ul>
                    </FormSection>

                    {/* a programacao de horarios q aparece no topo do diario do sono (RF22) */}
                    {chosen.REGISTRO_SONO && (
                        <FormSection
                            title="Programação do diário do sono"
                            description="Aparece no topo do diário, para o paciente lembrar dos horários combinados."
                            disabled={isSaving}
                        >
                            <FieldRow>
                                <TextField
                                    label="Ir dormir às"
                                    name="sleepBedTime"
                                    type="time"
                                    value={sleepBedTime}
                                    onChange={(event) => setSleepBedTime(event.target.value)}
                                />
                                <TextField
                                    label="Levantar às"
                                    name="sleepWakeTime"
                                    type="time"
                                    value={sleepWakeTime}
                                    onChange={(event) => setSleepWakeTime(event.target.value)}
                                />
                            </FieldRow>
                        </FormSection>
                    )}

                    <FormActions>
                        <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                            Cancelar
                        </button>
                        <BusyButton
                            type="submit"
                            className="button"
                            isBusy={isSaving}
                            busyText={isRenewal ? "Renovando..." : "Iniciando..."}
                        >
                            {isRenewal ? "Renovar acompanhamento" : "Iniciar acompanhamento"}
                        </BusyButton>
                    </FormActions>
                </form>
            )}

            <ConfirmModal
                show={isEndingOpen}
                title="Encerrar acompanhamento"
                message={"O sistema para de enviar as escalas para este paciente e fecha na hora as do acompanhamento "
                    + "que ainda esperam resposta: ele não é mais cobrado por elas. O acompanhamento fica guardado "
                    + "como encerrado."}
                confirmText="Sim, encerrar"
                danger={true}
                cancelText="Manter ativo"
                onConfirm={end}
                onCancel={() => setIsEndingOpen(false)}
            />
        </section>
    );
}
