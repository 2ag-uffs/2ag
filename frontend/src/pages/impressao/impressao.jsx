import {useEffect, useMemo, useState} from "react";
import {useNavigate, useParams} from "react-router";
import AnamnesisView from "../../components/anamnesis-view/anamnesis-view.jsx";
import ConsultationCard from "../../components/consultation-card/consultation-card.jsx";
import PrescriptionCard from "../../components/prescription-card/prescription-card.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import {ageFrom, formatDate, formatDateTime} from "../../utils/date-format.js";
import styles from "./impressao.module.css";

const PERIODS = [
    {value: 90, label: "Últimos 90 dias"},
    {value: 180, label: "Últimos 6 meses"},
    {value: 365, label: "Último ano"},
    {value: 0, label: "Todo o tratamento"},
];

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
    const [periodDays, setPeriodDays] = useState(90);

    useEffect(() => {
        if (!patientId) {
            return;
        }
        const patientPath = "/pacientes/" + patientId;

        Promise.all([
            apiService.get("/paciente/" + patientId),
            apiService.get(patientPath + "/consultas"),
            apiService.get(patientPath + "/prescricoes"),
            apiService.get(patientPath + "/anamneses"),
            apiService.get(patientPath + "/escalas/respostas"),
        ])
            .then(([patient, appointments, prescriptions, anamneses, scales]) => {
                setHistory({patient, appointments, prescriptions, anamneses, scales});
                setLoadError(null);
            })
            .catch((requestError) => {
                setLoadError(requestError instanceof ApiError
                    ? requestError.message
                    : "Não foi possível carregar o histórico. Confira sua internet e tente de novo.");
            });
    }, [patientId]);

    // a data de corte do periodo escolhido, ou nada quando eh tudo
    const startDate = useMemo(() => {
        if (periodDays === 0) {
            return null;
        }
        const start = new Date();
        start.setDate(start.getDate() - periodDays);
        return start.toISOString().slice(0, 10);
    }, [periodDays]);

    const insidePeriod = (isoDate) => !startDate || String(isoDate).slice(0, 10) >= startDate;

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!history) {
        return <p>Carregando o histórico...</p>;
    }

    const {patient, appointments, prescriptions, anamneses, scales} = history;
    const appointmentsInPeriod = appointments.filter((appointment) => insidePeriod(appointment.dateTime));
    const prescriptionsInPeriod = prescriptions.filter(
        (prescription) => insidePeriod(prescription.appointmentDateTime));
    const anamnesesInPeriod = anamneses.filter((anamnesis) => insidePeriod(anamnesis.assessmentDate));
    const scalesInPeriod = scales.filter((scale) => insidePeriod(scale.periodStart));

    return (
        <section className={styles.page}>
            <div className={styles.toolbar}>
                <div className={styles.filter}>
                    <label htmlFor="periodo">Período</label>
                    <select
                        id="periodo"
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
                            {patient.birthDate ? " · " + ageFrom(patient.birthDate) + " anos" : ""}
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
                        <dd>{formatDateTime(new Date().toISOString().slice(0, 19))}</dd>
                    </div>
                </dl>
            </header>

            <section className={styles.section}>
                <h2>Consultas ({appointmentsInPeriod.length})</h2>
                {appointmentsInPeriod.length === 0 ? (
                    <p className={styles.empty}>Nenhuma consulta no período.</p>
                ) : (
                    appointmentsInPeriod.map((appointment) => (
                        <ConsultationCard key={appointment.id} appointment={appointment}/>
                    ))
                )}
            </section>

            <section className={styles.section}>
                <h2>Prescrições ({prescriptionsInPeriod.length})</h2>
                {prescriptionsInPeriod.length === 0 ? (
                    <p className={styles.empty}>Nenhuma prescrição no período.</p>
                ) : (
                    prescriptionsInPeriod.map((prescription) => (
                        <PrescriptionCard key={prescription.id} prescription={prescription}/>
                    ))
                )}
            </section>

            <section className={styles.section}>
                <h2>Escalas respondidas ({scalesInPeriod.length})</h2>
                {scalesInPeriod.length === 0 ? (
                    <p className={styles.empty}>Nenhuma escala respondida no período.</p>
                ) : (
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
                                <td>
                                    {scale.periodStart === scale.periodEnd
                                        ? formatDate(scale.periodStart)
                                        : formatDate(scale.periodStart) + " a " + formatDate(scale.periodEnd)}
                                </td>
                                <td>{scale.annulled ? "anulada" : scale.result}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </section>

            <section className={styles.section}>
                <h2>Anamnese ({anamnesesInPeriod.length})</h2>
                {anamnesesInPeriod.length === 0 ? (
                    <p className={styles.empty}>Nenhuma anamnese no período.</p>
                ) : (
                    anamnesesInPeriod.map((anamnesis) => (
                        <AnamnesisView key={anamnesis.id} anamnesis={anamnesis}/>
                    ))
                )}
            </section>
        </section>
    );
}
