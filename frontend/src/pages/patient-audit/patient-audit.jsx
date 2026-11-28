import {useEffect, useState} from "react";
import {useParams} from "react-router";
import AuditTrail from "../../components/audit-trail/audit-trail.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import {apiService} from "../../services/api.js";
import styles from "./patient-audit.module.css";

// historico de acesso ao prontuario de um paciente do prescritor (RF31)
export default function PatientAudit() {
    const {patientId} = useParams();
    // o nome vem da api, q confere o vinculo. ele n viaja no historico do navegador,
    // senao o voltar mostrava o nome pra outro prescritor no msm computador
    const [patient, setPatient] = useState(null);

    useEffect(() => {
        let isCurrentRequest = true;

        apiService.get("/patients/" + patientId)
            .then((loadedPatient) => {
                if (isCurrentRequest) {
                    setPatient(loadedPatient);
                }
            })
            .catch(() => {
                // sem o nome a tela segue, e a trilha mostra o erro dela
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [patientId]);

    // o nome de um paciente n fica na tela qnd o endereco troca pra outro
    const patientName = patient && String(patient.id) === patientId ? patient.name : null;

    return (
        <section className={styles.page}>
            <PageHeader
                title="Histórico de acesso"
                subtitle={(patientName ? "Prontuário de " + patientName + ". " : "")
                    + "Quem criou, alterou, abriu ou baixou os registros, e quando."}
            />
            <AuditTrail endpoint={"/patients/" + patientId + "/audit-events"} showPatientNumber={false}/>
        </section>
    );
}
