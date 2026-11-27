import {useEffect, useRef, useState} from "react";
import BusyButton from "../form/busy-button.jsx";
import {useFocusFirstError} from "../form/focus-first-error.js";
import {useFocusWhenShown} from "../form/focus-when-shown.js";
import TextField from "../form/text-field.jsx";
import PageHeader from "../page-header/page-header.jsx";
import {
    apiService,
    ApiError,
    CONNECTION_ERROR_MESSAGE,
    readRequestError,
    setLoggedUser,
} from "../../services/api.js";
import {formatDate, toIsoDate} from "../../utils/date-format.js";
import {
    ACCEPT_BUTTON_TEXT,
    ageBandOf,
    findGuardianErrors,
    guardianBody,
    guardianFieldsFrom,
} from "../../utils/guardian.js";
import {RESEARCH_REVOKED_TEXT, researchStatusText, showsResearch} from "../../utils/research-consent.js";
import styles from "./term-update.module.css";

const EMPTY_GUARDIAN = {guardianName: "", guardianRelationship: "", guardianPhone: ""};

// nome e parentesco do responsavel q vao no fim do aviso da idade
function guardianTextOf(guardian) {
    if (!guardian) {
        return "";
    }
    return " Responsável informado: " + guardian.name
        + (guardian.relationship ? " (" + guardian.relationship + ")" : "") + ".";
}

// conta de menor sem o responsavel completo preenche o nome e o parentesco aqui
function MissingGuardianFields({guardian, guardianData, fieldErrors, onFieldChange, fieldsRef}) {
    return (
        <fieldset className={styles.guardianFields} ref={fieldsRef}>
            <legend className={styles.guardianTitle}>Responsável legal</legend>
            <p className={styles.statusText}>
                {guardian
                    ? "Falta o parentesco do responsável legal. Confira o nome e preencha o parentesco "
                    + "para aceitar o termo."
                    : "O responsável legal ainda não foi informado. Preencha o nome e o parentesco "
                    + "dele para aceitar o termo."}
            </p>
            {/* o celular poria o nome do paciente aqui */}
            <TextField
                label="Nome"
                name="guardian.name"
                autoComplete="off"
                autoCapitalize="words"
                value={guardianData.guardianName}
                onChange={(event) => onFieldChange("guardianName", event.target.value)}
                error={fieldErrors["guardian.name"]}
                required={true}
            />
            <TextField
                label="Parentesco"
                name="guardian.relationship"
                autoComplete="off"
                placeholder="Mãe, pai, avó, tutor..."
                value={guardianData.guardianRelationship}
                onChange={(event) => onFieldChange("guardianRelationship", event.target.value)}
                error={fieldErrors["guardian.relationship"]}
                required={true}
            />
        </fieldset>
    );
}

// o perfil vai igual ao salvo, so com a data nova
function profileBodyWith(profile, birthDate) {
    return {
        name: profile.name,
        socialName: profile.socialName,
        birthDate: birthDate || null,
        phone: profile.phone,
        address: profile.address,
        guardian: profile.guardian,
    };
}

// aqui so tem o campo da data, entao o erro de outro campo do perfil aparece nele tbm
function birthDateErrorOf(requestError) {
    const errorInfo = readRequestError(requestError);
    const fieldMessages = Object.values(errorInfo.fieldErrors);
    return fieldMessages.length > 0 ? fieldMessages[0] : errorInfo.message;
}

// termo pendente so pela faixa de idade: a data de nascimento errada se corrige aqui, sem gravar antes
// um aceite na faixa errada, como o de uma crianca sem o responsavel (#163)
function BirthDateFix({profile, onFixed}) {
    const [birthDate, setBirthDate] = useState(profile.birthDate || "");
    const [error, setError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const [formRef, focusFirstError] = useFocusFirstError();

    const handleSubmit = async (event) => {
        event.preventDefault();
        setError(null);
        setIsSaving(true);
        try {
            const savedProfile = await apiService.put("/profile", profileBodyWith(profile, birthDate));
            const sessionUser = await apiService.get("/auth/me");
            onFixed(savedProfile, sessionUser);
        } catch (requestError) {
            setError(birthDateErrorOf(requestError));
            focusFirstError();
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <form className={styles.birthDateFix} onSubmit={handleSubmit} ref={formRef}>
            <p className="aviso">
                A data de nascimento salva é {formatDate(profile.birthDate)}, e por ela a sua faixa de idade mudou
                desde o último aceite. Se a data estiver errada, corrija aqui em vez de aceitar: com a data certa, o
                aceite que você já fez continua valendo.
            </p>
            {/* o nome n repete o do perfil, q pode estar montado por baixo */}
            <TextField
                label="Data de nascimento"
                name="correctedBirthDate"
                type="date"
                autoComplete="bday"
                max={toIsoDate(new Date())}
                value={birthDate}
                onChange={(event) => setBirthDate(event.target.value)}
                error={error}
                required={true}
            />
            <div>
                <BusyButton type="submit" className="button-secondary" isBusy={isSaving} busyText="Salvando...">
                    Corrigir a data
                </BusyButton>
            </div>
        </form>
    );
}

// a pesquisa eh separada do termo do cadastro (RF37) e o perfil fica escondido com o termo pendente,
// entao quem n concorda com o termo novo ve aqui como esta a pesquisa e pode retirar o aceite dela
// entrar na pesquisa e ler o termo dela continuam so no perfil, dps do termo do cadastro
function ResearchOnTerm({consent, onRevoked}) {
    const [message, setMessage] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    // retirar tira o botao da tela e o foco ia pro body, entao o aviso de retirado recebe o foco (CT-39)
    const [wasRevokedNow, setWasRevokedNow] = useState(false);
    const revokedNoticeRef = useFocusWhenShown(wasRevokedNow);

    const revoke = async () => {
        setIsSaving(true);
        setMessage(null);
        try {
            const savedConsent = await apiService.post("/consent-term/research/revoke", undefined);
            onRevoked(savedConsent);
            setWasRevokedNow(true);
        } catch (requestError) {
            setMessage(readRequestError(requestError).message || CONNECTION_ERROR_MESSAGE);
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <section className={styles.research} aria-labelledby="term-research-title">
            <h2 id="term-research-title" className={styles.researchTitle}>Pesquisa</h2>
            <p>
                {researchStatusText(consent)}
                {!consent.active && " A pesquisa fica no Perfil, depois de aceitar o termo acima."}
            </p>
            {wasRevokedNow && (
                <p className="aviso" role="status" tabIndex={-1} ref={revokedNoticeRef}>{RESEARCH_REVOKED_TEXT}</p>
            )}
            {message && <p className="aviso aviso--atencao" role="alert">{message}</p>}
            {consent.active && (
                <div>
                    <BusyButton type="button" className="button-danger" onClick={revoke} isBusy={isSaving}
                                busyText="Salvando...">
                        Retirar consentimento
                    </BusyButton>
                </div>
            )}
        </section>
    );
}

// paciente q aceitou uma versao antiga do termo le e aceita a nova antes de seguir (#58)
// a api tbm pede o aceite de novo qnd a idade cruzou os 18 anos: menor aceita com o responsavel e adulto sozinho
export default function TermUpdate({onAccepted, onBirthDateFixed}) {
    const [term, setTerm] = useState(null);
    const [profile, setProfile] = useState(null);
    const [isPendingByAge, setIsPendingByAge] = useState(false);
    // decidido qnd a tela abre: o menor q retira o aceite daqui continua vendo o aviso de retirado
    const [hasResearch, setHasResearch] = useState(false);
    const [guardianData, setGuardianData] = useState(EMPTY_GUARDIAN);
    const [fieldErrors, setFieldErrors] = useState({});
    const [loadError, setLoadError] = useState(null);
    const [errorMessage, setErrorMessage] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const [guardianFieldsRef, focusFirstGuardianError] = useFocusFirstError();
    const introRef = useRef(null);

    // o termo abre no lugar da tela e o foco q estava nela ficava escondido embaixo dele (CT-39)
    // entao, como nos modais, o foco entra no termo, e o layout devolve ele qnd o termo fecha
    useEffect(() => {
        introRef.current.focus();
    }, []);

    useEffect(() => {
        let isCurrentPage = true;
        // o perfil traz a idade, pq pelo menor de 18 o responsavel legal participa do aceite
        // e a sessao diz se o termo esta pendente so pela faixa de idade. GET passa mesmo com o termo pendente
        Promise.all([apiService.get("/consent-term"), apiService.get("/profile"), apiService.get("/auth/me")])
            .then(([currentTerm, loadedProfile, sessionUser]) => {
                if (isCurrentPage) {
                    setTerm(currentTerm);
                    setProfile(loadedProfile);
                    setHasResearch(showsResearch(loadedProfile));
                    setIsPendingByAge(Boolean(sessionUser && sessionUser.termPendingByAge));
                    // o q ja esta salvo vem preenchido e o telefone salvo vai junto no aceite
                    setGuardianData(guardianFieldsFrom(loadedProfile.guardian));
                }
            })
            .catch(() => {
                if (isCurrentPage) {
                    setLoadError(CONNECTION_ERROR_MESSAGE);
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

    // se o termo mudou com a tela aberta o texto novo entra no lugar e o proximo aceite manda o novo
    // o texto pode mudar sem a versao, qnd a clinica troca um dado dela e reinicia a api
    const reloadChangedTerm = async () => {
        try {
            const currentTerm = await apiService.get("/consent-term");
            if (currentTerm.version !== term.version || currentTerm.text !== term.text) {
                setTerm(currentTerm);
            }
        } catch {
            // o erro do aceite ja esta na tela
        }
    };

    const handleAccept = async () => {
        setErrorMessage(null);
        const requestBody = {termVersion: term.version, termHash: term.textHash};
        if (needsGuardian) {
            const errors = findGuardianErrors(guardianData, true);
            setFieldErrors(errors);
            if (Object.keys(errors).length > 0) {
                focusFirstGuardianError();
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
                // erro de campo ja aparece embaixo do campo, e o foco vai pra ele
                const errorsByField = requestError.fieldErrors();
                setFieldErrors(errorsByField);
                focusFirstGuardianError();
                if (Object.keys(errorsByField).length === 0) {
                    // tipo o termo q mudou de novo: a mensagem so aparece com o texto novo ja na tela
                    await reloadChangedTerm();
                    setErrorMessage(requestError.message);
                }
            } else {
                setErrorMessage(CONNECTION_ERROR_MESSAGE);
            }
            setIsSaving(false);
        }
    };

    // com a data certa o aceite q ja existe volta a valer e a tela fecha sem aceitar de novo
    // se a data nova ainda deixa o termo pendente, a tela fica e troca os textos da faixa
    const handleBirthDateFixed = (savedProfile, sessionUser) => {
        setLoggedUser(sessionUser);
        if (!sessionUser.termPending) {
            onBirthDateFixed();
            return;
        }
        setProfile(savedProfile);
        setGuardianData(guardianFieldsFrom(savedProfile.guardian));
        setIsPendingByAge(Boolean(sessionUser.termPendingByAge));
    };

    const handleResearchRevoked = (researchConsent) => {
        setProfile((currentProfile) => ({...currentProfile, researchConsent: researchConsent}));
    };

    const guardianText = guardianTextOf(guardian);

    return (
        <section className={styles.page}>
            <PageHeader
                title="Termo de consentimento"
                subtitle="O texto do termo ou a sua faixa de idade mudou desde o último aceite"
            />
            <p className="aviso" tabIndex={-1} ref={introRef}>
                Leia o termo e aceite de novo para continuar usando o 2AG. Se não concordar, fale com a clínica.
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
                    {isPendingByAge && profile.birthDate && (
                        <BirthDateFix profile={profile} onFixed={handleBirthDateFixed}/>
                    )}
                    {/* a chave faz o texto novo comecar do topo */}
                    <div key={term.version} className={styles.termText} tabIndex={0} aria-label="Texto do termo">
                        {term.text}
                    </div>
                    {needsGuardian && (
                        <MissingGuardianFields
                            guardian={guardian}
                            guardianData={guardianData}
                            fieldErrors={fieldErrors}
                            onFieldChange={updateGuardianField}
                            fieldsRef={guardianFieldsRef}
                        />
                    )}
                    {errorMessage && <p className="aviso aviso--atencao" role="alert">{errorMessage}</p>}
                    <div className={styles.actions}>
                        <BusyButton type="button" className="button" onClick={handleAccept} isBusy={isSaving}
                                    busyText="Salvando...">
                            {ACCEPT_BUTTON_TEXT[ageBand]}
                        </BusyButton>
                    </div>
                    {hasResearch && (
                        <ResearchOnTerm consent={profile.researchConsent} onRevoked={handleResearchRevoked}/>
                    )}
                </>
            )}
        </section>
    );
}
