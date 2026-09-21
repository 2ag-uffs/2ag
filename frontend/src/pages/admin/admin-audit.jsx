import AuditTrail from "../../components/audit-trail/audit-trail.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import styles from "./admin-audit.module.css";

// auditoria do administrador (RF31)
// mostra o q prescritores e sistema fizeram sem nome de paciente e sem conteudo clinico
export default function AdminAudit() {
    return (
        <section className={styles.page}>
            <PageHeader
                title="Auditoria"
                subtitle="Quem abriu ou alterou prontuários, quem mexeu em conta de prescritor, e quando. O paciente aparece só pelo número, sem nome e sem conteúdo clínico."
            />
            <AuditTrail endpoint="/admin/audit-events" showPatientNumber={true}/>
        </section>
    );
}
