import {useEffect, useState} from "react";
import {useLocation, useNavigate} from "react-router";
import AnamnesisView from "../../components/anamnesis-view/anamnesis-view.jsx";
import ConsultationCard from "../../components/consultation-card/consultation-card.jsx";
import ExportModal from "../../components/export-modal/export-modal.jsx";
import PrescriptionCard from "../../components/prescription-card/prescription-card.jsx";
import ScaleSummary from "../../components/scale-summary/scale-summary.jsx";
import SectionLinks from "../../components/section-links/section-links.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import {isInTheFuture} from "../../utils/date-format.js";
import styles from "./historico-clinico-paciente.module.css";

// historico clinico do proprio paciente (RF12)
// mostra so o q foi registrado de verdade e o registro anulado aparece com o motivo
export default function HistoricoClinicoPaciente() {
    const navigate = useNavigate();
    const location = useLocation();
    const loggedUser = getLoggedUser();
    // quem acabou de corrigir a anamnese chega aqui com esse aviso
    const notice = location.state && location.state.notice;
    const [history, setHistory] = useState(null);
    const [loadError, setLoadError] = useState(null);
    // o painel de exportacao dos proprios dados (RF33)
    const [isExportOpen, setIsExportOpen] = useState(false);

    useEffect(() => {
        let isCurrentRequest = true;
        const patientPath = "/pacientes/" + loggedUser.id;

        Promise.all([
            apiService.get(patientPath + "/consultas"),
            apiService.get(patientPath + "/prescricoes"),
            apiService.get(patientPath + "/anamneses"),
            apiService.get(patientPath + "/escalas/central"),
        ])
            .then(([appointments, prescriptions, anamneses, scalesPage]) => {
                if (isCurrentRequest) {
                    setHistory({appointments, prescriptions, anamneses, scalesPage});
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível carregar seu histórico. Confira sua internet e tente de novo.");
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [loggedUser.id]);

    if (loadError) {
        return <p className="aviso aviso--atencao">{loadError}</p>;
    }

    if (history === null) {
        return <p className={styles.status}>Carregando...</p>;
    }

    const {appointments, prescriptions, anamneses, scalesPage} = history;
    // consulta marcada pro futuro aparece no inicio e aqui entra so a q ja aconteceu
    const pastAppointments = appointments.filter((appointment) => !isInTheFuture(appointment.dateTime));
    // a vigente aparece primeiro
    const orderedPrescriptions = prescriptions.filter((prescription) => prescription.current)
        .concat(prescriptions.filter((prescription) => !prescription.current));

    return (
        <section className={styles.page}>
            <div className={styles.header}>
                <div>
                    <h1>Meu histórico clínico</h1>
                    <p className={styles.subtitle}>
                        Tudo o que foi registrado no seu acompanhamento. Registros anulados continuam aqui,
                        com o motivo.
                    </p>
                </div>
                <button type="button" className="button-secondary" onClick={() => setIsExportOpen(true)}>
                    Exportar
                </button>
            </div>

            {notice && <p className="aviso" role="status">{notice}</p>}

            {isExportOpen && (
                <ExportModal
                    patientId={loggedUser.id}
                    printPath="/impressao"
                    canAnonymize={false}
                    onClose={() => setIsExportOpen(false)}
                />
            )}

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
                            <ConsultationCard key={appointment.id} appointment={appointment}/>
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
                            <PrescriptionCard key={prescription.id} prescription={prescription}/>
                        ))}
                    </div>
                )}
            </section>

            <section id="anamnese" className={styles.section}>
                <h2 className={styles.sectionTitle}>Anamnese</h2>
                {anamneses.length === 0 ? (
                    <div className={styles.emptyAnamnesis}>
                        <p className={styles.status}>Você ainda não preencheu a anamnese.</p>
                        <button type="button" className={styles.secondaryButton} onClick={() => navigate("/anamnese")}>
                            Preencher anamnese
                        </button>
                    </div>
                ) : (
                    <div className={styles.list}>
                        {anamneses.map((anamnesis) => (
                            <AnamnesisView
                                key={anamnesis.id}
                                anamnesis={anamnesis}
                                actions={anamnesis.annulled ? null : (
                                    <button
                                        type="button"
                                        className="button-tertiary"
                                        onClick={() => navigate("/anamnese?id=" + anamnesis.id)}
                                    >
                                        Corrigir
                                    </button>
                                )}
                            />
                        ))}
                    </div>
                )}
            </section>

            <section id="escalas" className={styles.section}>
                <h2 className={styles.sectionTitle}>Escalas</h2>
                <ScaleSummary
                    scalesPage={scalesPage}
                    pendingTitle="Para responder"
                    onOpenResponse={(response) => navigate("/escalas/resposta/" + response.id)}
                    pendingAction={(
                        <button
                            type="button"
                            className={styles.secondaryButton}
                            onClick={() => navigate("/pacientes/" + loggedUser.id + "/escalas")}
                        >
                            Responder agora
                        </button>
                    )}
                />
            </section>
        </section>
    );
}
