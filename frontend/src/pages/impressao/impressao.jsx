import {useEffect, useMemo, useState} from "react";
import {useNavigate, useParams} from "react-router";
import {FiPrinter} from "react-icons/fi";
import AnamnesisView from "../../components/anamnesis-view/anamnesis-view.jsx";
import ConsultationCard from "../../components/consultation-card/consultation-card.jsx";
import PrescriptionCard from "../../components/prescription-card/prescription-card.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, errorMessageOf, getLoggedUser} from "../../services/api.js";
import {belongsToHistory} from "../../utils/appointment-labels.js";
import {addDays, ageFrom, formatDate, formatNow, formatPeriod, toIsoDate} from "../../utils/date-format.js";
import {lastPrescriptionTitle} from "../../utils/prescription-labels.js";
import styles from "./impressao.module.css";

// o tratamento todo n tem data de corte
const WHOLE_TREATMENT = 0;

const PERIODS = [
    {value: 90, label: "Últimos 90 dias"},
    {value: 180, label: "Últimos 6 meses"},
    {value: 365, label: "Último ano"},
    {value: WHOLE_TREATMENT, label: "Todo o tratamento"},
];

const DEFAULT_PERIOD_DAYS = 90;

// versao para impressao do historico (RF33)
//
// o navegador imprime ou salva em pdf, entao n precisa de biblioteca de
// pdf no servidor. o cabecalho identifica paciente, prescritor, periodo
// e a data de emissao
export default function Impressao() {
    const navigate = useNavigate();
    const {patientId: patientIdFromUrl} = useParams();
    const loggedUser = getLoggedUser();
    const patientId = patientIdFromUrl || (loggedUser && loggedUser.id);

    const [history, setHistory] = useState(null);
    const [loadError, setLoadError] = useState(null);
    const [periodDays, setPeriodDays] = useState(DEFAULT_PERIOD_DAYS);

    useEffect(() => {
        if (!patientId) {
            return;
        }
        const patientPath = "/patients/" + patientId;

        Promise.all([
            apiService.get(patientPath),
            apiService.get(patientPath + "/appointments"),
            apiService.get(patientPath + "/prescriptions"),
            apiService.get(patientPath + "/anamneses"),
            apiService.get(patientPath + "/scales/responses"),
        ])
            .then(([patient, appointments, prescriptions, anamneses, scales]) => {
                setHistory({patient, appointments, prescriptions, anamneses, scales});
                setLoadError(null);
            })
            .catch((requestError) => {
                setLoadError(errorMessageOf(requestError,
                    "Não foi possível carregar o histórico. Confira sua internet e tente de novo."));
            });
    }, [patientId]);

    const startDate = useMemo(() => {
        if (periodDays === WHOLE_TREATMENT) {
            return null;
        }
        // pelo toISOString a data voltaria um dia no fuso de brasilia
        return toIsoDate(addDays(new Date(), -periodDays));
    }, [periodDays]);

    const insidePeriod = (isoDate) => !startDate || String(isoDate).slice(0, 10) >= startDate;

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!history) {
        return <SkeletonPage cards={3}/>;
    }

    const {patient, appointments, prescriptions, anamneses, scales} = history;
    const age = ageFrom(patient.birthDate);
    // consulta marcada pro futuro e pedido recusado ou cancelado n entram na impressao
    const appointmentsInPeriod = appointments.filter(
        (appointment) => belongsToHistory(appointment) && insidePeriod(appointment.dateTime));
    const prescriptionsInPeriod = prescriptions.filter(
        (prescription) => insidePeriod(prescription.appointmentDateTime));
    // a receita em uso eh estado, n historico: sai sempre, msm emitida antes do periodo, senao
    // o medico q recebe a impressao de 90 dias conclui q o paciente n toma nada
    const currentPrescription = prescriptions.find((prescription) => prescription.current);
    const anamnesesInPeriod = anamneses.filter((anamnesis) => insidePeriod(anamnesis.assessmentDate));
    // a ficha mais recente tbm sai sempre: o remedio em uso e a alergia de maio continuam valendo em novembro
    // a api manda da mais recente pra mais antiga, e a anulada n vale como relato (revisao das regras R10)
    const latestAnamnesis = anamneses.find((anamnesis) => !anamnesis.annulled);
    const scalesInPeriod = scales.filter((scale) => insidePeriod(scale.periodStart));

    return (
        <section className={styles.page}>
            <div className={styles.toolbar}>
                <div className={styles.filter}>
                    <label htmlFor="periodo">Período</label>
                    <select
                        id="periodo"
                        className={styles.select}
                        value={periodDays}
                        onChange={(event) => setPeriodDays(Number(event.target.value))}
                    >
                        {PERIODS.map((period) => (
                            <option key={period.value} value={period.value}>{period.label}</option>
                        ))}
                    </select>
                </div>
                <div className={styles.toolbarActions}>
                    <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                        Voltar
                    </button>
                    <button type="button" className="button" onClick={() => window.print()}>
                        <FiPrinter aria-hidden="true"/>
                        Imprimir ou salvar em PDF
                    </button>
                </div>
            </div>

            <header className={styles.head}>
                <h1>Histórico clínico</h1>
                <dl className={styles.headData}>
                    <div>
                        <dt>Paciente</dt>
                        <dd>
                            {patient.name}
                            {patient.civilName ? " (nome civil: " + patient.civilName + ")" : ""}
                            {age !== null ? " · " + age + " anos" : ""}
                        </dd>
                    </div>
                    <div>
                        <dt>Prescritor</dt>
                        <dd>{patient.prescriberName || "sem prescritor vinculado"}</dd>
                    </div>
                    <div>
                        <dt>Período</dt>
                        <dd>{PERIODS.find((period) => period.value === periodDays).label}</dd>
                    </div>
                    <div>
                        <dt>Emitido em</dt>
                        <dd>{formatNow()}</dd>
                    </div>
                </dl>
            </header>

            <PrintSection title="Consultas" count={appointmentsInPeriod.length} emptyText="Nenhuma consulta no período.">
                {appointmentsInPeriod.map((appointment) => (
                    <ConsultationCard key={appointment.id} appointment={appointment}/>
                ))}
            </PrintSection>

            <section className={styles.section}>
                {/* a do paciente arquivado n esta mais em uso (revisao das regras R2) */}
                <h2>
                    {currentPrescription && currentPrescription.followUpEndedAt
                        ? lastPrescriptionTitle(currentPrescription.followUpEndedAt)
                        : "Prescrição em uso"}
                </h2>
                {currentPrescription
                    ? <PrescriptionCard prescription={currentPrescription}/>
                    : <p className={styles.empty}>Nenhuma prescrição em uso.</p>}
            </section>

            <section className={styles.section}>
                <h2>Anamnese mais recente</h2>
                {latestAnamnesis ? (
                    <>
                        <p className={styles.empty}>
                            Sai mesmo quando foi preenchida antes do período: é o relato do paciente na data de
                            preenchimento.
                        </p>
                        <AnamnesisView anamnesis={latestAnamnesis}/>
                    </>
                ) : <p className={styles.empty}>Nenhuma anamnese preenchida.</p>}
            </section>

            <PrintSection
                title="Prescrições"
                count={prescriptionsInPeriod.length}
                emptyText="Nenhuma prescrição no período."
            >
                {prescriptionsInPeriod.map((prescription) => (
                    <PrescriptionCard key={prescription.id} prescription={prescription}/>
                ))}
            </PrintSection>

            <PrintSection
                title="Escalas respondidas"
                count={scalesInPeriod.length}
                emptyText="Nenhuma escala respondida no período."
            >
                <div className={styles.tableWrap}>
                    <table className={styles.table}>
                        <thead>
                        <tr>
                            <th>Escala</th>
                            <th>Período</th>
                            <th>Resultado</th>
                        </tr>
                        </thead>
                        <tbody>
                        {scalesInPeriod.map((scale) => (
                            <tr key={scale.id}>
                                <td>{scale.scaleName}</td>
                                <td>{formatPeriod(scale.periodStart, scale.periodEnd)}</td>
                                <td>
                                    {scale.annulled ? "anulada" : scale.result}
                                    {/* a api so manda a correcao pra prescritora (revisao das regras R4) */}
                                    {scale.correctedAt && (
                                        <span className={styles.corrected}>
                                            corrigida em {formatDate(scale.correctedAt)} · antes: {scale.previousResult}
                                        </span>
                                    )}
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            </PrintSection>

            <PrintSection title="Anamnese" count={anamnesesInPeriod.length} emptyText="Nenhuma anamnese no período.">
                {anamnesesInPeriod.map((anamnesis) => (
                    <AnamnesisView key={anamnesis.id} anamnesis={anamnesis}/>
                ))}
            </PrintSection>
        </section>
    );
}

function PrintSection({title, count, emptyText, children}) {
    return (
        <section className={styles.section}>
            <h2>{title} ({count})</h2>
            {count === 0 ? <p className={styles.empty}>{emptyText}</p> : children}
        </section>
    );
}
