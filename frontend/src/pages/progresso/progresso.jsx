import {useEffect, useMemo, useState} from "react";
import {Link, useLocation, useNavigate, useParams} from "react-router";
import {FiDownload, FiFileText, FiTrendingUp, FiUsers} from "react-icons/fi";
import {
    CartesianGrid,
    Legend,
    Line,
    LineChart,
    ReferenceLine,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts";
import Card from "../../components/card/card.jsx";
import EmptyState from "../../components/empty-state/empty-state.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import {SkeletonBlock} from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import {formatDate} from "../../utils/date-format.js";
import styles from "./progresso.module.css";

// o recharts precisa das cores como valor e n como var do css
// entao a gente le o token no momento de montar a tela
const colorOf = (name, fallback) =>
    getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;

const CHART_COLORS = {
    line: colorOf("--color-chart-line", "#006633"),
    grid: colorOf("--color-chart-grid", "#dbe1dd"),
    appointment: colorOf("--color-error", "#a44819"),
    dose: colorOf("--color-secondary", "#77954f"),
    text: colorOf("--color-text-secondary", "#575e59"),
};

// aaaa-mm-dd vira dd/mm q eh como o eixo do grafico mostra
const dayAndMonth = (isoDate) => isoDate.slice(8, 10) + "/" + isoDate.slice(5, 7);

const PERIODS = [
    {value: "DIAS_15", label: "Últimos 15 dias"},
    {value: "DIAS_30", label: "Últimos 30 dias"},
    {value: "DIAS_60", label: "Últimos 60 dias"},
    {value: "DIAS_90", label: "Últimos 90 dias"},
];

// o periodo todo o tempo eh so do prescritor (RF28)
const ALL_TIME_PERIOD = {value: "TUDO", label: "Todo o tempo"};

// a ficha de acompanhamento eh a unica q registra a dose do dia
const FOLLOW_UP_SCALE = "ACOMPANHAMENTO_SEMANAL";

// evolucao do tratamento (RF07 RF27 e RF28)
//
// serve pras telas do paciente e do prescritor
// o paciente ve o proprio grafico e o prescritor escolhe de qual paciente
// e ainda ganha o periodo todo o tempo
export default function Progresso() {
    const {patientId: patientIdFromUrl} = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    const loggedUser = getLoggedUser();
    const isPrescriber = Boolean(loggedUser && loggedUser.role === "PRESCRIBER");
    const patientId = isPrescriber ? patientIdFromUrl : (loggedUser && loggedUser.id);

    // quem troca de paciente continua com os mesmos filtros
    const keptFilters = location.state || {};

    const [patients, setPatients] = useState(null);
    const [currentPatient, setCurrentPatient] = useState(null);
    const [isPatientLoading, setIsPatientLoading] = useState(isPrescriber && Boolean(patientId));

    const [attributes, setAttributes] = useState([]);
    const [chosenAttribute, setChosenAttribute] = useState(keptFilters.attribute || "");
    const [period, setPeriod] = useState(keptFilters.period || "DIAS_30");

    const [points, setPoints] = useState([]);
    const [dosePoints, setDosePoints] = useState([]);
    const [showDose, setShowDose] = useState(Boolean(keptFilters.showDose));
    const [appointments, setAppointments] = useState([]);
    const [showAppointments, setShowAppointments] = useState(keptFilters.showAppointments !== false);
    const [comments, setComments] = useState([]);
    const [loadError, setLoadError] = useState(null);
    // a serie do grafico lembra de qual paciente atributo e periodo ela eh
    const [loadedSeriesKey, setLoadedSeriesKey] = useState(null);
    const [seriesError, setSeriesError] = useState(null);

    const periods = isPrescriber ? [...PERIODS, ALL_TIME_PERIOD] : PERIODS;

    // o prescritor escolhe numa lista com os pacientes ativos dele
    useEffect(() => {
        if (!isPrescriber) {
            return;
        }
        apiService.get("/patients?archived=false")
            .then(setPatients)
            .catch(() => {
                setPatients([]);
                setLoadError("Não foi possível carregar seus pacientes.");
            });
    }, [isPrescriber]);

    // o nome vai no topo pra n ficar duvida de quem eh o grafico
    useEffect(() => {
        if (!isPrescriber || !patientId) {
            return;
        }
        apiService.get("/patients/" + patientId)
            .then(setCurrentPatient)
            .catch(() => setCurrentPatient(null))
            .finally(() => setIsPatientLoading(false));
    }, [isPrescriber, patientId]);

    // o catalogo vem do backend entao a tela n tem uma lista de escalas
    // e atributos repetida aqui dentro pra ficar desatualizada
    useEffect(() => {
        if (!patientId) {
            return;
        }
        apiService.get("/progress/attributes")
            .then((loadedAttributes) => {
                setAttributes(loadedAttributes);
                // o atributo q veio da troca de paciente continua se ainda existir
                setChosenAttribute((currentName) => {
                    if (loadedAttributes.some((attribute) => attribute.name === currentName)) {
                        return currentName;
                    }
                    return loadedAttributes.length > 0 ? loadedAttributes[0].name : "";
                });
            })
            .catch((requestError) => {
                setLoadError(requestError instanceof ApiError
                    ? requestError.message
                    : "Não foi possível carregar as escalas.");
            });
    }, [patientId]);

    // as escalas q aparecem no seletor sem repetir
    const scales = useMemo(() => {
        const seen = new Map();
        attributes.forEach((attribute) => seen.set(attribute.scaleType, attribute.scaleName));
        return Array.from(seen, ([value, label]) => ({value, label}));
    }, [attributes]);

    // a escala sai do atributo escolhido entao as duas nunca ficam desencontradas
    const currentAttribute = attributes.find((attribute) => attribute.name === chosenAttribute);
    const chosenScale = currentAttribute ? currentAttribute.scaleType : "";
    const isFollowUpScale = chosenScale === FOLLOW_UP_SCALE;

    const attributesOfScale = attributes.filter((attribute) => attribute.scaleType === chosenScale);

    // cada serie eh de um paciente atributo e periodo
    // enquanto a serie da chave atual n chega a tela mostra o esqueleto
    const seriesKey = patientId + "|" + chosenAttribute + "|" + period;
    const isLoading = Boolean(patientId && chosenAttribute) && loadedSeriesKey !== seriesKey;
    const pageError = loadError || (seriesError && seriesError.key === seriesKey ? seriesError.message : null);

    useEffect(() => {
        if (!patientId || !chosenAttribute) {
            return;
        }
        const requestKey = patientId + "|" + chosenAttribute + "|" + period;
        // resposta de um filtro antigo q chega atrasada n pode passar por cima da atual
        let isCurrentRequest = true;

        apiService.get("/patients/" + patientId + "/progress?attribute=" + chosenAttribute + "&period=" + period)
            .then((series) => {
                if (isCurrentRequest) {
                    setPoints(series);
                    setSeriesError(null);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setPoints([]);
                    setSeriesError({
                        key: requestKey,
                        message: requestError instanceof ApiError
                            ? requestError.message
                            : "Não foi possível carregar o progresso.",
                    });
                }
            })
            .finally(() => {
                if (isCurrentRequest) {
                    setLoadedSeriesKey(requestKey);
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [patientId, chosenAttribute, period]);

    // a dose do dia entra como segunda linha pra dar pra ler se o
    // sintoma mudou depois de mexer na dose
    useEffect(() => {
        // gota escondida n precisa buscar e o grafico ja ignora o q ficou guardado
        if (!patientId || !showDose || !isFollowUpScale) {
            return;
        }
        const url = "/patients/" + patientId + "/progress?period=" + period + "&attribute=";
        Promise.all([apiService.get(url + "GOTAS_MANHA"), apiService.get(url + "GOTAS_TARDE")])
            .then(([morning, afternoon]) => setDosePoints(sumByDate(morning, afternoon)))
            .catch(() => setDosePoints([]));
    }, [patientId, period, showDose, isFollowUpScale]);

    // as consultas e os comentarios n dependem da escala escolhida so
    // do periodo entao ficam em efeitos separados
    useEffect(() => {
        if (!patientId) {
            return;
        }
        apiService.get("/patients/" + patientId + "/progress/appointments?period=" + period)
            // se as consultas falharem o grafico ainda serve pq elas sao contexto e n o dado principal
            .then(setAppointments)
            .catch(() => setAppointments([]));

        apiService.get("/patients/" + patientId + "/progress/comments?period=" + period)
            .then(setComments)
            .catch(() => setComments([]));
    }, [patientId, period]);

    // a gota so entra no grafico qdo esta marcada e a escala eh o acompanhamento semanal
    const shownDosePoints = showDose && isFollowUpScale ? dosePoints : [];
    const doseByDate = new Map(shownDosePoints.map((point) => [point.date, point.value]));
    const chartRows = points.map((point) => ({
        label: dayAndMonth(point.date),
        valor: point.value,
        gotas: doseByDate.has(point.date) ? doseByDate.get(point.date) : null,
    }));

    // o eixo x eh categorico entao so da pra marcar consulta em dia q tem
    // ponto no grafico senao o recharts n sabe onde por a linha
    const daysWithPoint = new Set(chartRows.map((row) => row.label));
    const markedDays = new Set();
    const appointmentMarks = !showAppointments ? [] : appointments
        .map((appointment) => ({...appointment, label: dayAndMonth(appointment.data)}))
        .filter((appointment) => {
            if (!daysWithPoint.has(appointment.label) || markedDays.has(appointment.label)) {
                return false;
            }
            markedDays.add(appointment.label);
            return true;
        });

    // a faixa interpretativa vira linha de corte no grafico pq escore
    // sem faixa n quer dizer nada (RN14)
    const bandLines = currentAttribute && currentAttribute.bands
        ? currentAttribute.bands.filter((band) => band.minScore > 0)
        : [];

    // trocar a escala abre o primeiro atributo dela
    const changeScale = (newScale) => {
        const firstOfScale = attributes.find((attribute) => attribute.scaleType === newScale);
        setChosenAttribute(firstOfScale ? firstOfScale.name : "");
    };

    // cada paciente tem o proprio endereco entao o voltar do navegador funciona
    const changePatient = (newPatientId) => {
        if (!newPatientId) {
            return;
        }
        navigate("/paciente/" + newPatientId + "/progresso", {
            state: {
                period: period,
                attribute: chosenAttribute,
                showDose: showDose,
                showAppointments: showAppointments,
            },
        });
    };

    // paciente arquivado n vem na lista mas pode ter sido aberto pelo historico
    const patientList = patients || [];
    const isCurrentInList = patientList.some((patient) => String(patient.id) === String(patientId));
    const patientOptions = currentPatient && !isCurrentInList ? [...patientList, currentPatient] : patientList;

    const patientFilter = (
        <div className={styles.filter + " " + styles.patientFilter}>
            <label htmlFor="paciente">Paciente</label>
            <select
                id="paciente"
                className={styles.select}
                value={patientId || ""}
                onChange={(event) => changePatient(event.target.value)}
                disabled={patients === null}
            >
                <option value="" disabled={Boolean(patientId)}>
                    {patients === null ? "Carregando pacientes…" : "Escolha um paciente"}
                </option>
                {patientOptions.map((patient) => (
                    <option key={patient.id} value={patient.id}>
                        {patient.archived ? patient.name + " (no arquivo)" : patient.name}
                    </option>
                ))}
            </select>
        </div>
    );

    // o prescritor abriu o progresso pelo menu e ainda n escolheu ninguem
    if (isPrescriber && !patientId) {
        const hasNoPatients = patients !== null && patients.length === 0 && !loadError;
        return (
            <section className={styles.page}>
                <PageHeader
                    title="Progresso"
                    subtitle="Escolha um paciente para ver como o tratamento está evoluindo."
                />

                <div className={styles.filters}>{patientFilter}</div>

                {loadError && <p className="aviso aviso--atencao" role="alert">{loadError}</p>}

                {hasNoPatients ? (
                    <EmptyState
                        icon={FiUsers}
                        message="Você ainda não tem pacientes ativos."
                        action={<Link to="/lista-paciente" className="button-secondary">Ver pacientes</Link>}
                    />
                ) : (
                    <EmptyState
                        icon={FiTrendingUp}
                        message="O gráfico aparece aqui assim que você escolher um paciente."
                    />
                )}
            </section>
        );
    }

    if (!patientId) {
        return <p className="aviso aviso--atencao">Não foi possível identificar o paciente.</p>;
    }

    let pageTitle = "Evolução do tratamento";
    if (isPrescriber && isPatientLoading) {
        pageTitle = <SkeletonBlock width="14rem" height="1.75rem"/>;
    } else if (isPrescriber && currentPatient) {
        pageTitle = currentPatient.name;
    }

    const historyLink = isPrescriber ? (
        <Link to={"/paciente/" + patientId + "/historico"} className="button-tertiary">
            <FiFileText aria-hidden="true"/>
            Histórico clínico
        </Link>
    ) : null;

    const csvLink = chartRows.length > 0 ? (
        <a
            className="button-secondary"
            href={"/api/patients/" + patientId + "/export/progress.csv?attribute="
                + chosenAttribute + "&period=" + period}
            download={true}
        >
            <FiDownload aria-hidden="true"/>
            Baixar esta série em CSV
        </a>
    ) : null;

    return (
        <section className={styles.page}>
            <PageHeader
                title={pageTitle}
                subtitle={isPrescriber
                    ? "Evolução do tratamento. Cada ponto é um preenchimento e dia sem resposta não aparece no gráfico."
                    : "Cada ponto é um preenchimento. Dia sem resposta não aparece no gráfico, em vez de aparecer como zero."}
                actions={historyLink || csvLink ? <>{historyLink}{csvLink}</> : null}
            />

            <div className={styles.filters}>
                {isPrescriber && patientFilter}

                <div className={styles.filter}>
                    <label htmlFor="periodo">Período</label>
                    <select
                        id="periodo"
                        className={styles.select}
                        value={period}
                        onChange={(event) => setPeriod(event.target.value)}
                    >
                        {periods.map((option) => (
                            <option key={option.value} value={option.value}>{option.label}</option>
                        ))}
                    </select>
                </div>

                <div className={styles.filter}>
                    <label htmlFor="escala">Escala</label>
                    <select
                        id="escala"
                        className={styles.select}
                        value={chosenScale}
                        onChange={(event) => changeScale(event.target.value)}
                    >
                        {scales.map((scale) => (
                            <option key={scale.value} value={scale.value}>{scale.label}</option>
                        ))}
                    </select>
                </div>

                <div className={styles.filter}>
                    <label htmlFor="atributo">O que acompanhar</label>
                    <select
                        id="atributo"
                        className={styles.select}
                        value={chosenAttribute}
                        onChange={(event) => setChosenAttribute(event.target.value)}
                    >
                        {attributesOfScale.map((attribute) => (
                            <option key={attribute.name} value={attribute.name}>{attribute.displayName}</option>
                        ))}
                    </select>
                </div>

                <div className={styles.checks}>
                    <label className={styles.check}>
                        <input
                            type="checkbox"
                            checked={showAppointments}
                            onChange={(event) => setShowAppointments(event.target.checked)}
                        />
                        Marcar as consultas
                    </label>
                    {isFollowUpScale && (
                        <label className={styles.check}>
                            <input
                                type="checkbox"
                                checked={showDose}
                                onChange={(event) => setShowDose(event.target.checked)}
                            />
                            Mostrar as gotas do dia
                        </label>
                    )}
                </div>
            </div>

            {pageError && <p className="aviso aviso--atencao" role="alert">{pageError}</p>}

            <Card title={currentAttribute ? currentAttribute.displayName : "Gráfico"}>
                {isLoading && <SkeletonBlock height="320px"/>}

                {!isLoading && chartRows.length === 0 && !pageError && (
                    <EmptyState icon={FiTrendingUp} message="Nenhum registro preenchido neste período." isCompact={true}/>
                )}

                {!isLoading && chartRows.length > 0 && (
                    <div className={styles.chart}>
                        <ResponsiveContainer width="100%" height={320}>
                            <LineChart data={chartRows} margin={{top: 16, right: 24, bottom: 8, left: 0}}>
                                {/* as cores saem da paleta da marca e n do padrao do recharts */}
                                <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid}/>
                                <XAxis dataKey="label" tick={{fontSize: 12, fill: CHART_COLORS.text}}/>
                                {/* a faixa vem do backend pq 0 a 10 e 0 a 56 n podem
                                    dividir o mesmo eixo */}
                                <YAxis
                                    domain={[
                                        currentAttribute && currentAttribute.minValue !== null
                                            ? currentAttribute.minValue : 0,
                                        currentAttribute && currentAttribute.maxValue !== null
                                            ? currentAttribute.maxValue : "auto",
                                    ]}
                                    allowDecimals={false}
                                    tick={{fontSize: 12, fill: CHART_COLORS.text}}
                                />
                                <Tooltip/>
                                <Legend/>

                                {/* cada faixa do instrumento vira uma linha de corte */}
                                {bandLines.map((band) => (
                                    <ReferenceLine
                                        key={band.label}
                                        y={band.minScore}
                                        stroke={CHART_COLORS.grid}
                                        strokeDasharray="6 3"
                                        label={{value: band.label, position: "insideTopRight", fontSize: 11}}
                                    />
                                ))}

                                {/* a consulta vira uma linha vertical no dia em q
                                    aconteceu pra dar pra ler a curva junto com a
                                    conduta do atendimento */}
                                {appointmentMarks.map((appointment) => (
                                    <ReferenceLine
                                        key={appointment.id}
                                        x={appointment.label}
                                        stroke={CHART_COLORS.appointment}
                                        strokeDasharray="4 4"
                                        label={{
                                            value: appointment.geraPrescricao ? "consulta + receita" : "consulta",
                                            position: "top",
                                            fontSize: 11,
                                            fill: CHART_COLORS.appointment,
                                        }}
                                    />
                                ))}

                                <Line
                                    type="monotone"
                                    dataKey="valor"
                                    name={currentAttribute ? currentAttribute.displayName : "valor"}
                                    stroke={CHART_COLORS.line}
                                    strokeWidth={2}
                                    dot={{r: 4, fill: CHART_COLORS.line}}
                                    activeDot={{r: 6}}
                                />

                                {showDose && isFollowUpScale && (
                                    <Line
                                        type="monotone"
                                        dataKey="gotas"
                                        name="Gotas no dia"
                                        stroke={CHART_COLORS.dose}
                                        strokeWidth={2}
                                        strokeDasharray="5 3"
                                        dot={{r: 3, fill: CHART_COLORS.dose}}
                                        connectNulls={true}
                                    />
                                )}
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                )}
            </Card>

            {showAppointments && appointments.length > 0 && (
                <Card title="Consultas no período" count={appointments.length}>
                    <ul className={styles.list}>
                        {appointments.map((appointment) => (
                            <li key={appointment.id}>
                                <strong>{formatDate(appointment.data)}</strong>
                                {appointment.diagnosis ? " — " + appointment.diagnosis : ""}
                                {appointment.geraPrescricao && (
                                    <span className={styles.mark}>receita emitida</span>
                                )}
                            </li>
                        ))}
                    </ul>
                    {/* so vira linha no grafico a consulta q caiu num dia
                        com preenchimento entao a lista avisa */}
                    {chartRows.length > 0 && appointmentMarks.length < appointments.length && (
                        <p className={styles.note}>
                            Consulta em dia sem preenchimento aparece aqui, mas não no gráfico: não há ponto onde marcar.
                        </p>
                    )}
                </Card>
            )}

            <Card title="O que o paciente escreveu" count={comments.length}>
                {comments.length === 0 ? (
                    <p className={styles.empty}>Nenhum comentário nas escalas deste período.</p>
                ) : (
                    <ul className={styles.comments}>
                        {comments.map((comment) => (
                            <li key={comment.responseId + "-" + comment.itemLabel} className={styles.comment}>
                                <p className={styles.commentMeta}>
                                    {formatDate(comment.date)} · {comment.scaleName}
                                </p>
                                <p className={styles.commentText}>{comment.text}</p>
                            </li>
                        ))}
                    </ul>
                )}
            </Card>
        </section>
    );
}

// as gotas da manha e as da tarde viram a dose do dia
// dia q so tem uma das duas conta o q tem em vez de sumir
function sumByDate(morningPoints, afternoonPoints) {
    const totalByDate = new Map();
    [...morningPoints, ...afternoonPoints].forEach((point) => {
        const current = totalByDate.get(point.date);
        totalByDate.set(point.date, current === undefined ? point.value : current + point.value);
    });
    return Array.from(totalByDate, ([date, value]) => ({date, value}));
}
