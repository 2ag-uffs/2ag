import {useLocation, useParams} from "react-router";
import AuditTrail from "../../components/audit-trail/audit-trail.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import styles from "./patient-audit.module.css";

// historico de acesso ao prontuario de um paciente do prescritor (RF31)
export default function PatientAudit() {
    const {patientId} = useParams();
    const location = useLocation();
    // o nome vem da lista de pacientes pra essa tela n precisar abrir o prontuario so pra mostrar o nome
    const patientName = location.state && location.state.patientName;

    return (
        <section className={styles.page}>
            <PageHeader
                title="Histórico de acesso"
                subtitle={(patientName ? "Prontuário de " + patientName + ". " : "")
                    + "Quem criou, alterou ou abriu os registros, e quando."}
            />
            <AuditTrail endpoint={"/patients/" + patientId + "/audit-events"} showPatientNumber={false}/>
        </section>
    );
}
