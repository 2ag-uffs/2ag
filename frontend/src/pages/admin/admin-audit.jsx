import AuditTrail from "../../components/audit-trail/audit-trail.jsx";
import styles from "./admin-audit.module.css";

// auditoria do administrador (RF31)
// mostra o q prescritores e sistema fizeram sem nome de paciente e sem conteudo clinico
export default function AdminAudit() {
    return (
        <section className={styles.page}>
            <div>
                <h1>Auditoria</h1>
                <p className={styles.subtitle}>
                    Quem abriu ou alterou prontuários, e quando. O paciente aparece só pelo número, sem nome e sem
                    conteúdo clínico.
                </p>
            </div>
            <AuditTrail endpoint="/admin/audit-events" showPatientNumber={true}/>
        </section>
    );
}
