import {useEffect, useState} from "react";
import PrescriptionCard from "../../components/prescription-card/prescription-card.jsx";
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

        apiService.get("/pacientes/" + loggedUser.id + "/prescricoes")
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

    const currentPrescription = prescriptions.find((prescription) => prescription.current);
    const previousPrescriptions = prescriptions.filter((prescription) => !prescription.current);

    return (
        <section className={styles.page}>
            <div>
                <h1>Minhas prescrições</h1>
                <p className={styles.subtitle}>
                    A prescrição vigente é a que vale agora. As anteriores ficam guardadas no histórico.
                </p>
            </div>

            {isLoading && <p className={styles.status}>Carregando...</p>}
            {errorMessage && <p className="aviso aviso--atencao">{errorMessage}</p>}

            {!isLoading && !errorMessage && (
                <>
                    <h2 className={styles.sectionTitle}>Vigente</h2>
                    {currentPrescription
                        ? <PrescriptionCard prescription={currentPrescription}/>
                        : <p className={styles.status}>Você não tem prescrição vigente no momento.</p>}

                    {previousPrescriptions.length > 0 && (
                        <>
                            <h2 className={styles.sectionTitle}>Histórico</h2>
                            <div className={styles.list}>
                                {previousPrescriptions.map((prescription) => (
                                    <PrescriptionCard key={prescription.id} prescription={prescription}/>
                                ))}
                            </div>
                        </>
                    )}
                </>
            )}
        </section>
    );
}
