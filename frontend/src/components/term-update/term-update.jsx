import {useEffect, useState} from "react";
import TextField from "../form/text-field.jsx";
import PageHeader from "../page-header/page-header.jsx";
import {apiService, ApiError, setLoggedUser} from "../../services/api.js";
import {
    ACCEPT_BUTTON_TEXT,
    ageBandOf,
    findGuardianErrors,
    guardianBody,
    guardianFieldsFrom,
} from "../../utils/guardian.js";
import styles from "./term-update.module.css";

const CONNECTION_ERROR = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

const EMPTY_GUARDIAN = {guardianName: "", guardianRelationship: "", guardianPhone: ""};

// paciente q aceitou uma versao antiga do termo le e aceita a nova antes de seguir (#58)
export default function TermUpdate({onAccepted}) {
    const [term, setTerm] = useState(null);
    const [profile, setProfile] = useState(null);
    const [guardianData, setGuardianData] = useState(EMPTY_GUARDIAN);
    const [fieldErrors, setFieldErrors] = useState({});
    const [loadError, setLoadError] = useState(null);
    const [errorMessage, setErrorMessage] = useState(null);
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        let isCurrentPage = true;
        // o perfil traz a idade, pq pelo menor de 18 o responsavel legal participa do aceite
        // GET passa mesmo com o termo pendente
        Promise.all([apiService.get("/consent-term"), apiService.get("/profile")])
            .then(([currentTerm, loadedProfile]) => {
                if (isCurrentPage) {
                    setTerm(currentTerm);
                    setProfile(loadedProfile);
                    // o q ja esta salvo vem preenchido e o telefone salvo vai junto no aceite
                    setGuardianData(guardianFieldsFrom(loadedProfile.guardian));
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

    // menor de 16: o responsavel aceita em nome do paciente. com 16 e 17 os dois aceitam juntos
    const ageBand = profile ? ageBandOf(profile.birthDate) : "adult";
    const guardian = profile ? profile.guardian : null;
    // conta de menor sem responsavel salvo, ou com ele sem o parentesco (de antes da regra), informa ele aqui,
    // pq o termo pendente trava o perfil e o aceite guarda o nome e o parentesco
    const needsGuardian = ageBand !== "adult" && (!guardian || !guardian.relationship);

    const updateGuardianField = (fieldName, value) => {
        setGuardianData((currentData) => ({...currentData, [fieldName]: value}));
    };

    const handleAccept = async () => {
        setErrorMessage(null);
        const requestBody = {termVersion: term.version};
        if (needsGuardian) {
            const errors = findGuardianErrors(guardianData, true);
            setFieldErrors(errors);
            if (Object.keys(errors).length > 0) {
                return;
            }
            requestBody.guardian = guardianBody(guardianData);
        }

        setIsSaving(true);
        try {
            const user = await apiService.post("/consent-term/accept", requestBody);
            setLoggedUser(user);
            onAccepted();
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                // erro de campo ja aparece embaixo do campo
                const errorsByField = requestError.fieldErrors();
                setFieldErrors(errorsByField);
                setErrorMessage(Object.keys(errorsByField).length > 0 ? null : requestError.message);
            } else {
                setErrorMessage(CONNECTION_ERROR);
            }
            setIsSaving(false);
        }
    };

    let guardianText = "";
    if (guardian) {
        guardianText = " Responsável informado: " + guardian.name
            + (guardian.relationship ? " (" + guardian.relationship + ")" : "") + ".";
    }

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
                    {ageBand === "under-16" && (
                        <p className="aviso">
                            Paciente com menos de 16 anos: quem aceita é o responsável legal, em nome do paciente.
                            {guardianText}
                        </p>
                    )}
                    {ageBand === "16-17" && (
                        <p className="aviso">
                            Paciente com 16 ou 17 anos: o paciente e o responsável legal leem e aceitam juntos.
                            {guardianText}
                        </p>
                    )}
                    <div className={styles.termText} tabIndex={0} aria-label="Texto do termo">
                        {term.text}
                    </div>
                    {needsGuardian && (
                        <fieldset className={styles.guardianFields}>
                            <legend className={styles.guardianTitle}>Responsável legal</legend>
                            <p className={styles.statusText}>
                                {guardian
                                    ? "Falta o parentesco do responsável legal. Confira o nome e preencha o parentesco "
                                    + "para aceitar o termo."
                                    : "O responsável legal ainda não foi informado. Preencha o nome e o parentesco "
                                    + "dele para aceitar o termo."}
                            </p>
                            <TextField
                                label="Nome"
                                name="guardian.name"
                                value={guardianData.guardianName}
                                onChange={(event) => updateGuardianField("guardianName", event.target.value)}
                                error={fieldErrors["guardian.name"]}
                                required={true}
                            />
                            <TextField
                                label="Parentesco"
                                name="guardian.relationship"
                                placeholder="Mãe, pai, avó, tutor..."
                                value={guardianData.guardianRelationship}
                                onChange={(event) => updateGuardianField("guardianRelationship", event.target.value)}
                                error={fieldErrors["guardian.relationship"]}
                                required={true}
                            />
                        </fieldset>
                    )}
                    {errorMessage && <p className="aviso aviso--atencao" role="alert">{errorMessage}</p>}
                    <div className={styles.actions}>
                        <button type="button" className="button" onClick={handleAccept} disabled={isSaving}>
                            {isSaving ? "Salvando..." : ACCEPT_BUTTON_TEXT[ageBand]}
                        </button>
                    </div>
                </>
            )}
        </section>
    );
}
