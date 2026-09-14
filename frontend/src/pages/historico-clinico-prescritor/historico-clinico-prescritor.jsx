import {useEffect, useState} from "react";
import {useLocation, useNavigate, useParams} from "react-router";
import AnamnesisView from "../../components/anamnesis-view/anamnesis-view.jsx";
import AnnulmentModal from "../../components/annulment-modal/annulment-modal.jsx";
import ConsultationCard from "../../components/consultation-card/consultation-card.jsx";
import ExportModal from "../../components/export-modal/export-modal.jsx";
import PrescriptionCard from "../../components/prescription-card/prescription-card.jsx";
import ScaleSummary from "../../components/scale-summary/scale-summary.jsx";
import SectionLinks from "../../components/section-links/section-links.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {ageFrom, formatDate, formatDateTime, isInTheFuture} from "../../utils/date-format.js";
import styles from "./historico-clinico-prescritor.module.css";

// historico clinico de um paciente do prescritor (RF13)
// o registro feito por engano eh anulado com motivo e continua aparecendo aqui
export default function HistoricoClinicoPrescritor() {
    const navigate = useNavigate();
    const location = useLocation();
    const {patientId} = useParams();

    const [history, setHistory] = useState(null);
    const [loadError, setLoadError] = useState(null);
    // quem salvou algo em outra tela chega aqui com esse aviso
    const [notice, setNotice] = useState(location.state && location.state.notice);
    // o registro q o prescritor escolheu anular e q abre o modal
    const [annulmentTarget, setAnnulmentTarget] = useState(null);
    // o painel de exportacao do paciente (RF33)
    const [isExportOpen, setIsExportOpen] = useState(false);
    // somar um aqui busca o historico de novo sem esconder a tela
    const [reloadCount, setReloadCount] = useState(0);

    useEffect(() => {
        let isCurrentRequest = true;
        const patientPath = "/patients/" + patientId;

        Promise.all([
            apiService.get("/patients/" + patientId),
            apiService.get(patientPath + "/appointments"),
            apiService.get(patientPath + "/prescriptions"),
            apiService.get(patientPath + "/anamneses"),
            apiService.get(patientPath + "/scales/overview"),
        ])
            .then(([patient, appointments, prescriptions, anamneses, scalesPage]) => {
                if (isCurrentRequest) {
                    setHistory({patient, appointments, prescriptions, anamneses, scalesPage});
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível carregar o histórico. Confira sua internet e tente de novo.");
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [patientId, reloadCount]);

    // marcar como analisada trava a correcao do paciente naquela resposta
    const reviewScale = async (response) => {
        try {
            await apiService.put("/scales/responses/" + response.id + "/review");
            setNotice("Escala marcada como analisada. O paciente não corrige mais essa resposta.");
            setReloadCount((currentCount) => currentCount + 1);
        } catch (requestError) {
            setNotice(requestError instanceof ApiError
                ? requestError.message
                : "Não foi possível falar com o servidor. Confira sua internet e tente de novo.");
        }
    };

    const handleAnnulled = () => {
        setAnnulmentTarget(null);
        setNotice("Registro anulado. Ele continua no histórico com o motivo da anulação.");
        setReloadCount((currentCount) => currentCount + 1);
    };

    if (loadError) {
        return <p className="aviso aviso--atencao">{loadError}</p>;
    }

    if (history === null) {
        return <p className={styles.status}>Carregando...</p>;
    }

    const {patient, appointments, prescriptions, anamneses, scalesPage} = history;
    const age = ageFrom(patient.birthDate);

    // a lista vem da consulta mais recente pra mais antiga
    // entao a proxima consulta marcada eh a ultima futura da lista
    const pastAppointments = appointments.filter((appointment) => !isInTheFuture(appointment.dateTime));
    const upcomingAppointments = appointments.filter((appointment) => isInTheFuture(appointment.dateTime)
        && !appointment.annulled && appointment.status === "AGENDADA");
    const nextAppointment = upcomingAppointments.length > 0
        ? upcomingAppointments[upcomingAppointments.length - 1]
        : null;

    // a vigente aparece primeiro e as outras seguem da mais nova pra mais antiga
    const orderedPrescriptions = prescriptions.filter((prescription) => prescription.current)
        .concat(prescriptions.filter((prescription) => !prescription.current));

    // so a consulta confirmada e n anulada recebe registro prescricao e anulacao
    const consultationActions = (appointment) => {
        const isConfirmed = appointment.status === "AGENDADA" || appointment.status === "EM_ANDAMENTO"
            || appointment.status === "CONCLUIDA";
        if (appointment.annulled || !isConfirmed) {
            return null;
        }
        return (
            <>
                <button
                    type="button"
                    className={styles.secondaryButton}
                    onClick={() => navigate("/consulta/" + appointment.id + "/registro")}
                >
                    Editar registro
                </button>
                <button
                    type="button"
                    className={styles.secondaryButton}
                    onClick={() => navigate("/consulta/" + appointment.id + "/prescricao")}
                >
                    Emitir prescrição
                </button>
                <button
                    type="button"
                    className={styles.secondaryButton}
                    onClick={() => navigate("/consulta/" + appointment.id + "/mini-exame")}
                >
                    Mini-exame
                </button>
                <button
                    type="button"
                    className={styles.dangerButton}
                    onClick={() => setAnnulmentTarget({
                        title: "Anular consulta",
                        recordDescription: "Consulta de " + formatDateTime(appointment.dateTime),
                        endpoint: "/appointments/" + appointment.id + "/annul",
                    })}
                >
                    Anular
                </button>
            </>
        );
    };

    const prescriptionActions = (prescription) => {
        if (prescription.annulled) {
            return null;
        }
        return (
            <button
                type="button"
                className={styles.dangerButton}
                onClick={() => setAnnulmentTarget({
                    title: "Anular prescrição",
                    recordDescription: prescription.productDescription + ", emitida em "
                        + formatDate(prescription.appointmentDateTime),
                    endpoint: "/prescriptions/" + prescription.id + "/annul",
                })}
            >
                Anular
            </button>
        );
    };

    const anamnesisActions = (anamnesis) => {
        if (anamnesis.annulled) {
            return null;
        }
        return (
            <button
                type="button"
                className={styles.dangerButton}
                onClick={() => setAnnulmentTarget({
                    title: "Anular anamnese",
                    recordDescription: "Anamnese preenchida em " + formatDate(anamnesis.assessmentDate),
                    endpoint: "/anamneses/" + anamnesis.id + "/annul",
                })}
            >
                Anular
            </button>
        );
    };

    return (
        <section className={styles.page}>
            <div className={styles.header}>
                <div>
                    <h1>{patient.name}</h1>
                    <p className={styles.subtitle}>
                        Histórico clínico{age !== null ? " · " + age + " anos" : ""}
                    </p>
                    {nextAppointment && (
                        <p className={styles.subtitle}>
                            Próxima consulta: {formatDateTime(nextAppointment.dateTime)}
                        </p>
                    )}
                    {patient.archived && (
                        <p className={styles.subtitle}>
                            No arquivo desde {formatDate(patient.archivedAt)}. O acompanhamento automático foi
                            encerrado.
                        </p>
                    )}
                </div>
                <div className={styles.headerActions}>
                    <button
                        type="button"
                        className={styles.primaryButton}
                        onClick={() => navigate("/paciente/" + patientId + "/consulta/nova")}
                    >
                        Nova consulta
                    </button>
                    <button
                        type="button"
                        className={styles.secondaryButton}
                        onClick={() => setIsExportOpen(true)}
                    >
                        Exportar
                    </button>
                    <button
                        type="button"
                        className={styles.secondaryButton}
                        onClick={() => navigate("/paciente/" + patientId + "/selecao-escalas")}
                    >
                        Enviar escalas
                    </button>
                    <button
                        type="button"
                        className={styles.secondaryButton}
                        onClick={() => navigate("/paciente/" + patientId + "/acompanhamento")}
                    >
                        Acompanhamento
                    </button>
                    <button
                        type="button"
                        className={styles.secondaryButton}
                        onClick={() => navigate("/paciente/" + patientId + "/progresso")}
                    >
                        Progresso
                    </button>
                    <button
                        type="button"
                        className={styles.secondaryButton}
                        onClick={() => navigate("/paciente/" + patientId + "/auditoria",
                            {state: {patientName: patient.name}})}
                    >
                        Histórico de acesso
                    </button>
                </div>
            </div>

            {notice && <p className="aviso" role="status">{notice}</p>}

            <SectionLinks
                label="Partes do histórico"
                sections={[
                    {id: "consultas", label: "Consultas (" + pastAppointments.length + ")"},
                    {id: "prescricoes", label: "Prescrições (" + prescriptions.length + ")"},
                    {id: "anamnese", label: "Anamnese (" + anamneses.length + ")"},
                    {id: "escalas", label: "Escalas"},
                ]}
            />

            <section id="consultas" className={styles.section}>
                <h2 className={styles.sectionTitle}>Consultas</h2>
                {pastAppointments.length === 0 ? (
                    <p className={styles.status}>Nenhuma consulta realizada ainda.</p>
                ) : (
                    <div className={styles.list}>
                        {pastAppointments.map((appointment) => (
                            <ConsultationCard
                                key={appointment.id}
                                appointment={appointment}
                                actions={consultationActions(appointment)}
                            />
                        ))}
                    </div>
                )}
            </section>

            <section id="prescricoes" className={styles.section}>
                <h2 className={styles.sectionTitle}>Prescrições</h2>
                {orderedPrescriptions.length === 0 ? (
                    <p className={styles.status}>Nenhuma prescrição emitida ainda.</p>
                ) : (
                    <div className={styles.list}>
                        {orderedPrescriptions.map((prescription) => (
                            <PrescriptionCard
                                key={prescription.id}
                                prescription={prescription}
                                actions={prescriptionActions(prescription)}
                            />
                        ))}
                    </div>
                )}
            </section>

            <section id="anamnese" className={styles.section}>
                <h2 className={styles.sectionTitle}>Anamnese</h2>
                {anamneses.length === 0 ? (
                    <p className={styles.status}>
                        O paciente ainda não preencheu a anamnese. Ela pode ser enviada em Enviar escalas.
                    </p>
                ) : (
                    <div className={styles.list}>
                        {anamneses.map((anamnesis) => (
                            <AnamnesisView
                                key={anamnesis.id}
                                anamnesis={anamnesis}
                                actions={anamnesisActions(anamnesis)}
                            />
                        ))}
                    </div>
                )}
            </section>

            <section id="escalas" className={styles.section}>
                <h2 className={styles.sectionTitle}>Escalas</h2>
                <ScaleSummary
                    scalesPage={scalesPage}
                    pendingTitle="Aguardando o paciente"
                    onOpenResponse={(response) => navigate("/escalas/resposta/" + response.id)}
                    onReview={reviewScale}
                    onAnnul={(response) => setAnnulmentTarget({
                        title: "Anular escala respondida",
                        recordDescription: response.scaleName + " de " + formatDate(response.periodStart),
                        endpoint: "/scales/responses/" + response.id + "/annul",
                    })}
                />
            </section>

            {isExportOpen && (
                <ExportModal
                    patientId={patientId}
                    printPath={"/paciente/" + patientId + "/impressao"}
                    canAnonymize={true}
                    onClose={() => setIsExportOpen(false)}
                />
            )}

            {annulmentTarget && (
                <AnnulmentModal
                    title={annulmentTarget.title}
                    recordDescription={annulmentTarget.recordDescription}
                    endpoint={annulmentTarget.endpoint}
                    onClose={() => setAnnulmentTarget(null)}
                    onAnnulled={handleAnnulled}
                />
            )}
        </section>
    );
}
