import {useEffect, useState} from "react";
import {FiFileText} from "react-icons/fi";
import EmptyState from "../../components/empty-state/empty-state.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import PrescriptionCard from "../../components/prescription-card/prescription-card.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import styles from "./my-prescriptions.module.css";

// prescricoes do proprio paciente (RF05 e RF12)
// a vigente fica em destaque e as anteriores aparecem como historico
export default function MyPrescriptions() {
    const loggedUser = getLoggedUser();
    const [prescriptions, setPrescriptions] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState(null);

    useEffect(() => {
        let isCurrentRequest = true;

        apiService.get("/patients/" + loggedUser.id + "/prescriptions")
            .then((prescriptionList) => {
                if (isCurrentRequest) {
                    setPrescriptions(prescriptionList);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setErrorMessage(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível carregar suas prescrições. Confira sua internet e tente de novo.");
                }
            })
            .finally(() => {
                if (isCurrentRequest) {
                    setIsLoading(false);
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [loggedUser.id]);

    if (isLoading) {
        return <SkeletonPage cards={2}/>;
    }

    const currentPrescription = prescriptions.find((prescription) => prescription.current);
    const previousPrescriptions = prescriptions.filter((prescription) => !prescription.current);

    return (
        <section className={styles.page}>
            <PageHeader
                title="Minhas prescrições"
                subtitle="A prescrição vigente é a que vale agora. As anteriores ficam guardadas no histórico."
            />

            {errorMessage && <p className="aviso aviso--atencao" role="alert">{errorMessage}</p>}

            {!errorMessage && (
                <>
                    <section className={styles.section}>
                        <h2 className={styles.sectionTitle}>Vigente</h2>
                        {currentPrescription ? (
                            <PrescriptionCard prescription={currentPrescription}/>
                        ) : (
                            <EmptyState
                                icon={FiFileText}
                                message="Você não tem prescrição vigente no momento."
                                isCompact={true}
                            />
                        )}
                    </section>

                    {previousPrescriptions.length > 0 && (
                        <section className={styles.section}>
                            <h2 className={styles.sectionTitle}>Histórico</h2>
                            <div className={styles.list}>
                                {previousPrescriptions.map((prescription) => (
                                    <PrescriptionCard key={prescription.id} prescription={prescription}/>
                                ))}
                            </div>
                        </section>
                    )}
                </>
            )}
        </section>
    );
}
