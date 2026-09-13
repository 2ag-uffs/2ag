import AnnulmentNotice from "../annulment-notice/annulment-notice.jsx";
import {formatDateTime} from "../../utils/date-format.js";
import styles from "./consultation-card.module.css";

const MODALITY_LABELS = {
    PRESENCIAL: "Presencial",
    REMOTA: "Remota",
};

const STATUS_LABELS = {
    AGENDADA: "Agendada",
    EM_ANDAMENTO: "Em andamento",
    CONCLUIDA: "Concluída",
    CANCELADA: "Cancelada",
};

// campos do registro clinico na ordem em q o prescritor preenche
const CLINICAL_FIELDS = [
    {name: "clinicalObservation", label: "Queixa principal e observações"},
    {name: "physicalExam", label: "Exame físico"},
    {name: "evolution", label: "Evolução do quadro"},
    {name: "diagnosis", label: "Hipótese diagnóstica"},
    {name: "therapeuticPlan", label: "Conduta e plano terapêutico"},
    {name: "complementaryExams", label: "Exames complementares"},
];

function statusLabelOf(appointment) {
    if (appointment.annulled) {
        return "Anulada";
    }
    return STATUS_LABELS[appointment.status] || appointment.status;
}

function badgeClassOf(appointment) {
    if (appointment.annulled) {
        return styles.badgeAnnulled;
    }
    return appointment.status === "CONCLUIDA" ? styles.badgeDone : styles.badgeOther;
}

// junta pressao peso e altura numa linha so
function vitalSignsOf(appointment) {
    const vitalSigns = [];
    if (appointment.bloodPressure) {
        vitalSigns.push("PA " + appointment.bloodPressure);
    }
    if (appointment.weight) {
        vitalSigns.push(appointment.weight.toLocaleString("pt-BR") + " kg");
    }
    if (appointment.height) {
        vitalSigns.push(appointment.height + " cm");
    }
    return vitalSigns.join(" · ");
}

// cartao de uma consulta usado no historico do paciente e do prescritor
// o actions deixa quem usa colocar botoes como o de anular
export default function ConsultationCard({appointment, actions}) {
    const vitalSigns = vitalSignsOf(appointment);
    const filledFields = CLINICAL_FIELDS.filter((field) => appointment[field.name]);

    return (
        <article className={styles.card}>
            <header className={styles.header}>
                <div>
                    <h3>{formatDateTime(appointment.dateTime)}</h3>
                    <p className={styles.meta}>
                        {MODALITY_LABELS[appointment.modality] || "Modalidade não informada"}
                        {appointment.prescriberName ? " com " + appointment.prescriberName : ""}
                    </p>
                </div>
                <span className={badgeClassOf(appointment)}>{statusLabelOf(appointment)}</span>
            </header>

            {vitalSigns && <p className={styles.vitalSigns}>{vitalSigns}</p>}

            {filledFields.length > 0 ? (
                <dl className={styles.details}>
                    {filledFields.map((field) => (
                        <div key={field.name}>
                            <dt>{field.label}</dt>
                            <dd>{appointment[field.name]}</dd>
                        </div>
                    ))}
                </dl>
            ) : (
                <p className={styles.meta}>Nenhum registro clínico nesta consulta.</p>
            )}

            {appointment.annulled && <AnnulmentNotice record={appointment}/>}

            {actions && <div className={styles.actions}>{actions}</div>}
        </article>
    );
}
