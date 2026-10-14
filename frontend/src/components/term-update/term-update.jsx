import {useEffect, useState} from "react";
import PageHeader from "../page-header/page-header.jsx";
import {apiService, ApiError, setLoggedUser} from "../../services/api.js";
import styles from "./term-update.module.css";

const CONNECTION_ERROR = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

// paciente q aceitou uma versao antiga do termo le e aceita a nova antes de seguir (#58)
export default function TermUpdate({onAccepted}) {
    const [term, setTerm] = useState(null);
    const [loadError, setLoadError] = useState(null);
    const [errorMessage, setErrorMessage] = useState(null);
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        let isCurrentPage = true;
        apiService.get("/consent-term")
            .then((currentTerm) => {
                if (isCurrentPage) {
                    setTerm(currentTerm);
                }
            })
            .catch(() => {
                if (isCurrentPage) {
                    setLoadError(CONNECTION_ERROR);
                }
            });
        return () => {
            isCurrentPage = false;
        };
    }, []);

    const handleAccept = async () => {
        setIsSaving(true);
        setErrorMessage(null);
        try {
            const user = await apiService.post("/consent-term/accept", {termVersion: term.version});
            setLoggedUser(user);
            onAccepted();
        } catch (requestError) {
            setErrorMessage(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR);
            setIsSaving(false);
        }
    };

    return (
        <section className={styles.page}>
            <PageHeader
                title="Termo de consentimento atualizado"
                subtitle="O texto mudou desde o seu último aceite"
            />
            <p className="aviso">
                Leia a versão nova e aceite para continuar usando o 2AG. Se não concordar, fale com a clínica.
            </p>

            {loadError && <p className="aviso aviso--atencao" role="alert">{loadError}</p>}
            {!term && !loadError && <p className={styles.statusText}>Carregando o termo...</p>}

            {term && (
                <>
                    <div className={styles.termText} tabIndex={0} aria-label="Texto do termo">
                        {term.text}
                    </div>
                    {errorMessage && <p className="aviso aviso--atencao" role="alert">{errorMessage}</p>}
                    <div className={styles.actions}>
                        <button type="button" className="button" onClick={handleAccept} disabled={isSaving}>
                            {isSaving ? "Salvando..." : "Li e aceito"}
                        </button>
                    </div>
                </>
            )}
        </section>
    );
}
