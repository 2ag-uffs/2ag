import AuditTrail from "../../components/audit-trail/audit-trail.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import styles from "./admin-audit.module.css";

// auditoria do administrador (RF31)
// mostra o q prescritores e sistema fizeram sem nome de paciente e sem conteudo clinico
// o numero do paciente so vem nos eventos de conta (revisao das regras R20)
export default function AdminAudit() {
    return (
        <section className={styles.page}>
            <PageHeader
                title="Auditoria"
                subtitle="Quem abriu, alterou ou baixou prontuários, quem criou, buscou, desativou ou reativou uma conta, e quando. O paciente aparece só pelo número, sem nome, e só nos eventos de conta: os registros clínicos vêm sem o número e sem conteúdo clínico."
            />
            <AuditTrail endpoint="/admin/audit-events" showPatientNumber={true}/>
        </section>
    );
}
