import {useEffect, useMemo, useState} from "react";
import {useNavigate, useParams} from "react-router";
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
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import {formatDate} from "../../utils/date-format.js";
import styles from "./progresso.module.css";

// o recharts precisa das cores como valor, n como var() do css.
// entao a gente le o token no momento de montar a tela
const colorOf = (name, fallback) =>
    getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;

const CHART_COLORS = {
    line: colorOf("--color-chart-line", "#006633"),
    grid: colorOf("--color-chart-grid", "#dbe1dd"),
    appointment: colorOf("--color-error", "#a44819"),
    dose: colorOf("--color-secondary", "#77954f"),
};

// aaaa-mm-dd vira dd/mm, que eh como o eixo do grafico mostra
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

// evolucao do tratamento (RF07, RF27 e RF28)
//
// serve pras duas telas: a do paciente e a do prescritor. a diferenca
// eh de onde vem o id do paciente, e o prescritor ainda ganha o periodo
// todo o tempo
export default function Progresso() {
    const navigate = useNavigate();
    const {patientId: patientIdFromUrl} = useParams();
    const loggedUser = getLoggedUser();
    const patientId = patientIdFromUrl || (loggedUser && loggedUser.id);
    const isPrescriber = Boolean(patientIdFromUrl);

    const [attributes, setAttributes] = useState([]);
    const [chosenScale, setChosenScale] = useState("");
    const [chosenAttribute, setChosenAttribute] = useState("");
    const [period, setPeriod] = useState("DIAS_30");

    const [points, setPoints] = useState([]);
    const [dosePoints, setDosePoints] = useState([]);
    const [showDose, setShowDose] = useState(false);
    const [appointments, setAppointments] = useState([]);
    const [showAppointments, setShowAppointments] = useState(true);
    const [comments, setComments] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [loadError, setLoadError] = useState(null);

    const periods = isPrescriber ? [...PERIODS, ALL_TIME_PERIOD] : PERIODS;

    // o catalogo vem do backend, entao a tela n tem uma lista de escalas
    // e atributos repetida aqui dentro pra ficar desatualizada
    useEffect(() => {
        apiService.get("/progress/attributes")
            .then((loadedAttributes) => {
                setAttributes(loadedAttributes);
                if (loadedAttributes.length > 0) {
                    setChosenScale(loadedAttributes[0].scaleType);
                    setChosenAttribute(loadedAttributes[0].name);
                }
            })
            .catch((requestError) => {
                setLoadError(requestError instanceof ApiError
                    ? requestError.message
                    : "Não foi possível carregar as escalas.");
            });
    }, []);

    // as escalas que aparecem no seletor, sem repetir
    const scales = useMemo(() => {
        const seen = new Map();
        attributes.forEach((attribute) => seen.set(attribute.scaleType, attribute.scaleName));
        return Array.from(seen, ([value, label]) => ({value, label}));
    }, [attributes]);

    const attributesOfScale = useMemo(
        () => attributes.filter((attribute) => attribute.scaleType === chosenScale),
        [attributes, chosenScale]);

    const currentAttribute = attributes.find((attribute) => attribute.name === chosenAttribute);
    const isFollowUpScale = chosenScale === FOLLOW_UP_SCALE;

    useEffect(() => {
        if (!patientId || !chosenAttribute) {
            return;
        }
        setIsLoading(true);
        setLoadError(null);

        apiService.get("/patients/" + patientId + "/progress?attribute=" + chosenAttribute + "&period=" + period)
            .then((series) => setPoints(series))
            .catch((requestError) => {
                setLoadError(requestError instanceof ApiError
                    ? requestError.message
                    : "Não foi possível carregar o progresso.");
                setPoints([]);
            })
            .finally(() => setIsLoading(false));
    }, [patientId, chosenAttribute, period]);

    // a dose do dia entra como segunda linha, pra dar pra ler se o
    // sintoma mudou depois de mexer na dose
    useEffect(() => {
        if (!patientId || !showDose || !isFollowUpScale) {
            setDosePoints([]);
            return;
        }
        const url = "/patients/" + patientId + "/progress?period=" + period + "&attribute=";
        Promise.all([apiService.get(url + "GOTAS_MANHA"), apiService.get(url + "GOTAS_TARDE")])
            .then(([morning, afternoon]) => setDosePoints(sumByDate(morning, afternoon)))
            .catch(() => setDosePoints([]));
    }, [patientId, period, showDose, isFollowUpScale]);

    // as consultas e os comentarios n dependem da escala escolhida, so
    // do periodo, entao ficam em efeitos separados
    useEffect(() => {
        if (!patientId) {
            return;
        }
        apiService.get("/patients/" + patientId + "/progress/appointments?period=" + period)
            // se as consultas falharem o grafico ainda serve: elas sao
            // contexto, n o dado principal
            .then(setAppointments)
            .catch(() => setAppointments([]));

        apiService.get("/patients/" + patientId + "/progress/comments?period=" + period)
            .then(setComments)
            .catch(() => setComments([]));
    }, [patientId, period]);

    const chartRows = useMemo(() => {
        const doseByDate = new Map(dosePoints.map((point) => [point.date, point.value]));
        return points.map((point) => ({
            label: dayAndMonth(point.date),
            valor: point.value,
            gotas: doseByDate.has(point.date) ? doseByDate.get(point.date) : null,
        }));
    }, [points, dosePoints]);

    // o eixo x eh categorico: so da pra marcar consulta em dia que tem
    // ponto no grafico, senao o recharts n sabe onde por a linha
    const appointmentMarks = useMemo(() => {
        if (!showAppointments) {
            return [];
        }
        const daysWithPoint = new Set(chartRows.map((row) => row.label));
        const seen = new Set();
        return appointments
            .map((appointment) => ({...appointment, label: dayAndMonth(appointment.data)}))
            .filter((appointment) => {
                if (!daysWithPoint.has(appointment.label) || seen.has(appointment.label)) {
                    return false;
                }
                seen.add(appointment.label);
                return true;
            });
    }, [appointments, chartRows, showAppointments]);

    // a faixa interpretativa vira linha de corte no grafico, pq escore
    // sem faixa n quer dizer nada (RN14)
    const bandLines = currentAttribute && currentAttribute.bands
        ? currentAttribute.bands.filter((band) => band.minScore > 0)
        : [];

    const changeScale = (newScale) => {
        setChosenScale(newScale);
        const firstOfScale = attributes.find((attribute) => attribute.scaleType === newScale);
        setChosenAttribute(firstOfScale ? firstOfScale.name : "");
    };

    if (!patientId) {
        return <p className="aviso aviso--atencao">Não foi possível identificar o paciente.</p>;
    }

    return (
        <section className={styles.page}>
            <header>
                <h1>Evolução do tratamento</h1>
                <p className={styles.subtitle}>
                    Cada ponto é um preenchimento. Dia sem resposta não aparece no gráfico, em vez de aparecer
                    como zero.
                </p>
            </header>

            <section className={styles.filters}>
                <div className={styles.filter}>
                    <label htmlFor="periodo">Período</label>
                    <select id="periodo" value={period} onChange={(event) => setPeriod(event.target.value)}>
                        {periods.map((option) => (
                            <option key={option.value} value={option.value}>{option.label}</option>
                        ))}
                    </select>
                </div>

                <div className={styles.filter}>
                    <label htmlFor="escala">Escala</label>
                    <select id="escala" value={chosenScale} onChange={(event) => changeScale(event.target.value)}>
                        {scales.map((scale) => (
                            <option key={scale.value} value={scale.value}>{scale.label}</option>
                        ))}
                    </select>
                </div>

                <div className={styles.filter}>
                    <label htmlFor="atributo">O que acompanhar</label>
                    <select
                        id="atributo"
                        value={chosenAttribute}
                        onChange={(event) => setChosenAttribute(event.target.value)}
                    >
                        {attributesOfScale.map((attribute) => (
                            <option key={attribute.name} value={attribute.name}>{attribute.displayName}</option>
                        ))}
                    </select>
                </div>

                <div className={styles.checks}>
                    <label>
                        <input
                            type="checkbox"
                            checked={showAppointments}
                            onChange={(event) => setShowAppointments(event.target.checked)}
                        />
                        Marcar as consultas
                    </label>
                    {isFollowUpScale && (
                        <label>
                            <input
                                type="checkbox"
                                checked={showDose}
                                onChange={(event) => setShowDose(event.target.checked)}
                            />
                            Mostrar as gotas do dia
                        </label>
                    )}
                </div>
            </section>

            {loadError && <p className="aviso aviso--atencao" role="alert">{loadError}</p>}

            <section className={styles.chartBox}>
                {isLoading && <p>Carregando...</p>}

                {!isLoading && chartRows.length === 0 && !loadError && (
                    <p className={styles.empty}>Nenhum registro preenchido neste período.</p>
                )}

                {!isLoading && chartRows.length > 0 && (
                    <>
                        <h2 className={styles.chartTitle}>
                            {currentAttribute ? currentAttribute.displayName : ""}
                        </h2>
                        <ResponsiveContainer width="100%" height={320}>
                            <LineChart data={chartRows} margin={{top: 16, right: 24, bottom: 8, left: 0}}>
                                {/* as cores saem da paleta da marca, n do padrao do recharts */}
                                <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid}/>
                                <XAxis dataKey="label"/>
                                {/* a faixa vem do backend: 0 a 10 e 0 a 56 n podem
                                    dividir o mesmo eixo */}
                                <YAxis
                                    domain={[
                                        currentAttribute && currentAttribute.minValue !== null
                                            ? currentAttribute.minValue : 0,
                                        currentAttribute && currentAttribute.maxValue !== null
                                            ? currentAttribute.maxValue : "auto",
                                    ]}
                                    allowDecimals={false}
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

                                {/* a consulta vira uma linha vertical no dia em que
                                    aconteceu, pra dar pra ler a curva junto com a
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

                        {showAppointments && appointments.length > 0 && (
                            <section className={styles.appointments}>
                                <h3 className={styles.blockTitle}>Consultas no período</h3>
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
                                {/* so vira linha no grafico a consulta que caiu num dia
                                    com preenchimento, entao a lista avisa */}
                                {appointmentMarks.length < appointments.length && (
                                    <p className={styles.note}>
                                        Consulta em dia sem preenchimento aparece aqui, mas não no gráfico:
                                        não há ponto onde marcar.
                                    </p>
                                )}
                            </section>
                        )}
                    </>
                )}
            </section>

            <section>
                <h2 className={styles.blockTitle}>O que o paciente escreveu</h2>
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
            </section>

            <div className={styles.footerActions}>
                <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                    Voltar
                </button>
                {chartRows.length > 0 && (
                    <a
                        className="button-secondary"
                        href={"/api/patients/" + patientId + "/export/progress.csv?attribute="
                            + chosenAttribute + "&period=" + period}
                        download={true}
                    >
                        Baixar esta série em CSV
                    </a>
                )}
            </div>
        </section>
    );
}

// as gotas da manha e as da tarde viram a dose do dia
// dia q so tem uma das duas conta o q tem, em vez de sumir
function sumByDate(morningPoints, afternoonPoints) {
    const totalByDate = new Map();
    [...morningPoints, ...afternoonPoints].forEach((point) => {
        const current = totalByDate.get(point.date);
        totalByDate.set(point.date, current === undefined ? point.value : current + point.value);
    });
    return Array.from(totalByDate, ([date, value]) => ({date, value}));
}
