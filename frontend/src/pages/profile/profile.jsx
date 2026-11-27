import {useCallback, useEffect, useState} from "react";
import {useBlocker} from "react-router";
import {FiBell, FiBookOpen, FiLock, FiMail, FiUser} from "react-icons/fi";
import Card from "../../components/card/card.jsx";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import BusyButton from "../../components/form/busy-button.jsx";
import {useFocusFirstError} from "../../components/form/focus-first-error.js";
import {useFocusWhenShown} from "../../components/form/focus-when-shown.js";
import Modal from "../../components/modal/modal.jsx";
import PasswordChecklist from "../../components/form/password-checklist.jsx";
import PasswordField from "../../components/form/password-field.jsx";
import SelectField from "../../components/form/select-field.jsx";
import TextField from "../../components/form/text-field.jsx";
import {isStrongPassword} from "../../components/form/password-rules.js";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {
    apiService,
    checkPendingTerm,
    CONNECTION_ERROR_MESSAGE,
    getLoggedUser,
    readRequestError,
    setLoggedUser,
} from "../../services/api.js";
import {BRAZIL_STATE_OPTIONS} from "../../utils/brazil-states.js";
import {toIsoDate} from "../../utils/date-format.js";
import {guardianBody, guardianFieldsFrom, isMinorPatient} from "../../utils/guardian.js";
import {useForgetBlockedExit, useLeaveWarning} from "../../utils/leave-warning.js";
import {formatCpf, formatPhone, onlyDigits} from "../../utils/masks.js";
import {RESEARCH_REVOKED_TEXT, researchStatusText, showsResearch} from "../../utils/research-consent.js";
import styles from "./profile.module.css";

const EMPTY_PASSWORD_FORM = {currentPassword: "", newPassword: "", newPasswordConfirmation: ""};

// erro de campo fica no campo, com o foco nele, e o resto vira o aviso do cartao
function showRequestError(requestError, setFieldErrors, setMessage, focusFirstError) {
    const errorInfo = readRequestError(requestError);
    setFieldErrors(errorInfo.fieldErrors);
    if (errorInfo.message) {
        setMessage({isError: true, text: errorInfo.message});
    }
    focusFirstError();
}

// onde n tem campo pra marcar o erro todo vira aviso
function generalErrorText(requestError) {
    return readRequestError(requestError).message || CONNECTION_ERROR_MESSAGE;
}

// campos do formulario a partir do perfil q veio da api
function personalDataFrom(profile) {
    const address = profile.address || {};
    return {
        name: profile.name || "",
        socialName: profile.socialName || "",
        birthDate: profile.birthDate || "",
        phone: formatPhone(profile.phone || ""),
        street: address.street || "",
        number: address.number || "",
        city: address.city || "",
        state: address.state || "",
        ...guardianFieldsFrom(profile.guardian),
    };
}

function profileRequestBody(formData, isPatient) {
    // endereco todo em branco vai como sem endereco
    const hasAddress = Boolean(formData.street || formData.number || formData.city || formData.state);
    return {
        name: formData.name,
        // nome social so existe na conta do paciente
        socialName: isPatient ? (formData.socialName.trim() || null) : null,
        birthDate: formData.birthDate || null,
        phone: onlyDigits(formData.phone) || null,
        address: hasAddress
            ? {street: formData.street, number: formData.number, city: formData.city, state: formData.state}
            : null,
        guardian: isPatient ? guardianBody(formData) : null,
    };
}

// cada cartao avisa a tela qnd tem algo digitado e n salvo, pra ela perguntar antes de sair
// o cartao q sai da tela deixa de contar
function useReportUnsaved(cardName, hasUnsaved, onUnsavedChange) {
    useEffect(() => {
        onUnsavedChange(cardName, hasUnsaved);
        return () => onUnsavedChange(cardName, false);
    }, [cardName, hasUnsaved, onUnsavedChange]);
}

// aviso de sucesso ou de erro dentro de cada cartao
// o noticeRef eh pro aviso de sucesso q recebe o foco qnd o botao usado some
function CardMessage({message, noticeRef}) {
    if (!message) {
        return null;
    }
    if (message.isError) {
        return <p className={"aviso aviso--atencao " + styles.cardMessage} role="alert">{message.text}</p>;
    }
    return (
        <p className={"aviso " + styles.cardMessage} role="status" tabIndex={noticeRef ? -1 : undefined}
           ref={noticeRef}>
            {message.text}
        </p>
    );
}

// titulo do cartao com o icone do lado
function cardTitle(Icon, text) {
    return (
        <>
            <Icon className={styles.cardIcon} aria-hidden="true"/>
            {text}
        </>
    );
}

// o q so a clinica corrige fica fora do formulario
function AccountInfo({profile, isPatient, isAdmin}) {
    return (
        <>
            <dl className={styles.readOnlyInfo}>
                {isAdmin && (
                    <>
                        <dt>E-mail de acesso</dt>
                        <dd>{profile.email}</dd>
                    </>
                )}
                {!isAdmin && (
                    <>
                        <dt>CPF</dt>
                        <dd>{profile.cpf ? formatCpf(profile.cpf) : "Não informado"}</dd>
                    </>
                )}
                {isPatient && (
                    <>
                        <dt>Quem acompanha você</dt>
                        <dd>{profile.prescriberName || "Nenhum prescritor vinculado"}</dd>
                    </>
                )}
                {!isPatient && !isAdmin && profile.registryType && (
                    <>
                        <dt>Registro profissional</dt>
                        <dd>
                            {profile.registryType} {profile.registryNumber}
                            {profile.profession ? " · " + profile.profession : ""}
                        </dd>
                    </>
                )}
            </dl>
            <p className={styles.hint}>
                {isAdmin
                    ? "O e-mail da conta administrativa é definido no servidor."
                    : "Para corrigir esses dados, fale com a clínica."}
            </p>
        </>
    );
}

// nascimento telefone e endereco obrigatorios so pro paciente
function ContactFields({formData, fieldErrors, isPatient, onFieldChange}) {
    return (
        <>
            <div className={styles.row}>
                <TextField
                    label="Data de nascimento"
                    name="birthDate"
                    type="date"
                    autoComplete="bday"
                    max={toIsoDate(new Date())}
                    value={formData.birthDate}
                    onChange={(event) => onFieldChange("birthDate", event.target.value)}
                    error={fieldErrors.birthDate}
                    required={isPatient}
                />
                <TextField
                    label="Telefone com DDD"
                    name="phone"
                    type="tel"
                    autoComplete="tel-national"
                    placeholder="(49) 99999-9999"
                    value={formData.phone}
                    onChange={(event) => onFieldChange("phone", formatPhone(event.target.value))}
                    error={fieldErrors.phone}
                    required={isPatient}
                />
            </div>
            <div className={styles.rowWide}>
                <TextField
                    label="Rua"
                    name="address.street"
                    autoComplete="address-line1"
                    autoCapitalize="words"
                    value={formData.street}
                    onChange={(event) => onFieldChange("street", event.target.value)}
                    error={fieldErrors["address.street"]}
                    required={isPatient}
                />
                <TextField
                    label="Número"
                    name="address.number"
                    value={formData.number}
                    onChange={(event) => onFieldChange("number", event.target.value)}
                    error={fieldErrors["address.number"]}
                    required={isPatient}
                />
            </div>
            <div className={styles.cityRow}>
                <TextField
                    label="Cidade"
                    name="address.city"
                    autoComplete="address-level2"
                    autoCapitalize="words"
                    value={formData.city}
                    onChange={(event) => onFieldChange("city", event.target.value)}
                    error={fieldErrors["address.city"]}
                    required={isPatient}
                />
                <SelectField
                    label="Estado"
                    name="address.state"
                    autoComplete="address-level1"
                    options={BRAZIL_STATE_OPTIONS}
                    placeholder="Escolha"
                    value={formData.state}
                    onChange={(event) => onFieldChange("state", event.target.value)}
                    error={fieldErrors["address.state"]}
                    required={isPatient}
                />
            </div>
        </>
    );
}

// familiar ou cuidador q vira obrigatorio qnd o paciente eh menor
// o celular poria o nome e o telefone do paciente aqui, entao os campos ficam sem preenchimento automatico
function GuardianFieldset({formData, fieldErrors, isMinor, onFieldChange}) {
    return (
        <fieldset className={styles.formSection}>
            <legend className={styles.formSubtitle}>
                {isMinor ? "Responsável legal" : "Familiar ou cuidador (opcional)"}
            </legend>
            {isMinor && (
                <p className={styles.hint}>
                    O paciente tem menos de 18 anos e o responsável legal participa do aceite do termo, por
                    isso o nome e o parentesco não podem ficar em branco.
                </p>
            )}
            <TextField
                label="Nome"
                name="guardian.name"
                autoComplete="off"
                autoCapitalize="words"
                value={formData.guardianName}
                onChange={(event) => onFieldChange("guardianName", event.target.value)}
                error={fieldErrors["guardian.name"]}
                required={isMinor}
            />
            <div className={styles.row}>
                <TextField
                    label="Parentesco"
                    name="guardian.relationship"
                    autoComplete="off"
                    placeholder={isMinor ? "Mãe, pai, avó, tutor..." : "Filha, marido, cuidadora..."}
                    value={formData.guardianRelationship}
                    onChange={(event) => onFieldChange("guardianRelationship", event.target.value)}
                    error={fieldErrors["guardian.relationship"]}
                    required={isMinor}
                />
                <TextField
                    label="Telefone com DDD"
                    name="guardian.phone"
                    type="tel"
                    autoComplete="off"
                    placeholder="(49) 99999-9999"
                    value={formData.guardianPhone}
                    onChange={(event) => onFieldChange("guardianPhone", formatPhone(event.target.value))}
                    error={fieldErrors["guardian.phone"]}
                />
            </div>
        </fieldset>
    );
}

// dados pessoais e endereco
// a conta administrativa so tem nome e o e-mail dela vem do servidor
function PersonalDataCard({profile, onSaved, onUnsavedChange}) {
    const [formData, setFormData] = useState(() => personalDataFrom(profile));
    // como os campos estavam ao abrir ou no ultimo salvar, pra saber se a pessoa mexeu em algo
    const [savedData, setSavedData] = useState(formData);
    const [fieldErrors, setFieldErrors] = useState({});
    const [message, setMessage] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const isPatient = profile.role === "PATIENT";
    const isAdmin = profile.role === "ADMIN";
    // menor de 18 n fica sem o responsavel legal q aceitou o termo por ele (LGPD art. 14)
    const isMinor = isPatient && isMinorPatient(formData.birthDate);
    const [formRef, focusFirstError] = useFocusFirstError();

    // o campo desfeito, q voltou ao q estava salvo, n conta como mudanca
    const hasChanges = Object.keys(formData).some((fieldName) => formData[fieldName] !== savedData[fieldName]);
    useReportUnsaved("personalData", hasChanges, onUnsavedChange);

    const updateField = (fieldName, value) => {
        setFormData((currentData) => ({...currentData, [fieldName]: value}));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setIsSaving(true);
        setFieldErrors({});
        setMessage(null);

        try {
            const savedProfile = await apiService.put("/profile", profileRequestBody(formData, isPatient));
            // o q foi enviado eh o q esta salvo, e o q a pessoa digitar enquanto a api responde continua contando
            setSavedData(formData);
            onSaved(savedProfile);
            setMessage({isError: false, text: "Dados salvos."});
        } catch (requestError) {
            showRequestError(requestError, setFieldErrors, setMessage, focusFirstError);
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <Card title={cardTitle(FiUser, isAdmin ? "Dados da conta" : "Dados pessoais")}>
            <AccountInfo profile={profile} isPatient={isPatient} isAdmin={isAdmin}/>

            <form className={styles.form} onSubmit={handleSubmit} ref={formRef}>
                <TextField
                    label="Nome completo"
                    name="name"
                    autoComplete="name"
                    autoCapitalize="words"
                    value={formData.name}
                    onChange={(event) => updateField("name", event.target.value)}
                    error={fieldErrors.name}
                    required={true}
                />
                {/* o celular poria o nome de registro no nome social */}
                {isPatient && (
                    <TextField
                        label="Nome social (opcional)"
                        name="socialName"
                        autoComplete="off"
                        autoCapitalize="words"
                        maxLength={255}
                        hint="Se você usa um nome social, é ele que aparece no sistema e nos avisos."
                        value={formData.socialName}
                        onChange={(event) => updateField("socialName", event.target.value)}
                        error={fieldErrors.socialName}
                    />
                )}
                {!isAdmin && (
                    <ContactFields
                        formData={formData}
                        fieldErrors={fieldErrors}
                        isPatient={isPatient}
                        onFieldChange={updateField}
                    />
                )}
                {isPatient && (
                    <GuardianFieldset
                        formData={formData}
                        fieldErrors={fieldErrors}
                        isMinor={isMinor}
                        onFieldChange={updateField}
                    />
                )}
                {/* o aviso fica do lado do botao: no celular o topo do cartao fica longe, fora da tela */}
                <CardMessage message={message}/>
                <div className={styles.actions}>
                    <BusyButton type="submit" className="button" isBusy={isSaving} busyText="Salvando...">
                        Salvar dados
                    </BusyButton>
                </div>
            </form>
        </Card>
    );
}

// troca do e-mail de acesso pedindo a senha atual
// o e-mail so muda dps q a pessoa abre o link q vai pro endereco novo (issue 130)
function EmailCard({profile, onSaved, onUnsavedChange}) {
    const [newEmail, setNewEmail] = useState("");
    const [currentPassword, setCurrentPassword] = useState("");
    const [fieldErrors, setFieldErrors] = useState({});
    const [message, setMessage] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const [formRef, focusFirstError] = useFocusFirstError();

    // so o e-mail novo conta: a senha atual o navegador pode preencher sozinho, sem a pessoa digitar nada
    useReportUnsaved("email", newEmail !== "", onUnsavedChange);

    // qnd o pedido some (a senha foi trocada) a mensagem de link enviado sai junto
    const shownMessage = message && !message.isError && !profile.pendingEmail ? null : message;

    const handleSubmit = async (event) => {
        event.preventDefault();
        setIsSaving(true);
        setFieldErrors({});
        setMessage(null);

        try {
            const savedProfile = await apiService.put("/profile/email", {
                newEmail: newEmail,
                currentPassword: currentPassword,
            });
            onSaved(savedProfile);
            setNewEmail("");
            setCurrentPassword("");
            setMessage({isError: false, text: "Enviamos um link para " + savedProfile.pendingEmail
                + ". O e-mail de acesso só muda depois que você abrir esse link. Ele vale por 30 minutos."});
        } catch (requestError) {
            showRequestError(requestError, setFieldErrors, setMessage, focusFirstError);
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <Card title={cardTitle(FiMail, "E-mail de acesso")}>
            {/* sem ponto depois do e-mail pra ele n parecer parte do endereco */}
            <p className={styles.cardText}>Hoje você entra com <strong>{profile.email}</strong></p>
            {profile.pendingEmail && (
                <p className={styles.hint}>
                    Aguardando a confirmação de <strong>{profile.pendingEmail}</strong>. Se o link não chegou,
                    confira a caixa de spam ou peça de novo com o endereço certo.
                </p>
            )}

            <CardMessage message={shownMessage}/>

            <form className={styles.form} onSubmit={handleSubmit} ref={formRef}>
                <TextField
                    label="Novo e-mail"
                    name="newEmail"
                    type="email"
                    inputMode="email"
                    autoComplete="email"
                    value={newEmail}
                    onChange={(event) => setNewEmail(event.target.value)}
                    error={fieldErrors.newEmail}
                    required={true}
                />
                <PasswordField
                    label="Senha atual"
                    name="emailCurrentPassword"
                    autoComplete="current-password"
                    value={currentPassword}
                    onChange={(event) => setCurrentPassword(event.target.value)}
                    error={fieldErrors.currentPassword}
                    required={true}
                />
                <div className={styles.actions}>
                    <BusyButton type="submit" className="button" isBusy={isSaving} busyText="Trocando...">
                        Trocar e-mail
                    </BusyButton>
                </div>
            </form>
        </Card>
    );
}

// confere a senha nova antes de mandar pra api
function findPasswordErrors(formData) {
    const errors = {};
    if (!isStrongPassword(formData.newPassword)) {
        errors.newPassword = "A nova senha ainda não cumpre todas as regras";
    }
    if (formData.newPassword !== formData.newPasswordConfirmation) {
        errors.newPasswordConfirmation = "As senhas não são iguais";
    }
    return errors;
}

// troca de senha pedindo a senha atual
function PasswordCard({onPasswordChanged, onUnsavedChange}) {
    const [formData, setFormData] = useState(EMPTY_PASSWORD_FORM);
    const [fieldErrors, setFieldErrors] = useState({});
    const [message, setMessage] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const [formRef, focusFirstError] = useFocusFirstError();

    // so a senha nova conta, pelo msm motivo do cartao do e-mail
    useReportUnsaved("password", formData.newPassword !== "" || formData.newPasswordConfirmation !== "",
        onUnsavedChange);

    const updateField = (fieldName, value) => {
        setFormData((currentData) => ({...currentData, [fieldName]: value}));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setMessage(null);

        const formErrors = findPasswordErrors(formData);
        setFieldErrors(formErrors);
        if (Object.keys(formErrors).length > 0) {
            focusFirstError();
            return;
        }

        setIsSaving(true);
        try {
            await apiService.put("/profile/password", {
                currentPassword: formData.currentPassword,
                newPassword: formData.newPassword,
            });
            setFormData(EMPTY_PASSWORD_FORM);
            setMessage({
                isError: false,
                text: "Senha trocada. Quem estava logado com a senha antiga em outro aparelho precisa entrar de novo.",
            });
            if (onPasswordChanged) {
                onPasswordChanged();
            }
        } catch (requestError) {
            showRequestError(requestError, setFieldErrors, setMessage, focusFirstError);
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <Card title={cardTitle(FiLock, "Senha")}>
            <form className={styles.form} onSubmit={handleSubmit} ref={formRef}>
                <PasswordField
                    label="Senha atual"
                    name="currentPassword"
                    autoComplete="current-password"
                    value={formData.currentPassword}
                    onChange={(event) => updateField("currentPassword", event.target.value)}
                    error={fieldErrors.currentPassword}
                    required={true}
                />
                <PasswordField
                    label="Nova senha"
                    name="newPassword"
                    autoComplete="new-password"
                    value={formData.newPassword}
                    onChange={(event) => updateField("newPassword", event.target.value)}
                    error={fieldErrors.newPassword}
                    required={true}
                />
                <PasswordChecklist password={formData.newPassword}/>
                <PasswordField
                    label="Repita a nova senha"
                    name="newPasswordConfirmation"
                    autoComplete="new-password"
                    value={formData.newPasswordConfirmation}
                    onChange={(event) => updateField("newPasswordConfirmation", event.target.value)}
                    error={fieldErrors.newPasswordConfirmation}
                    required={true}
                />
                <CardMessage message={message}/>
                <div className={styles.actions}>
                    <BusyButton type="submit" className="button" isBusy={isSaving} busyText="Trocando...">
                        Trocar senha
                    </BusyButton>
                </div>
            </form>
        </Card>
    );
}

// avisos por e-mail ligados ou desligados
function EmailPreferenceCard({profile, onSaved}) {
    const [message, setMessage] = useState(null);
    const [isSaving, setIsSaving] = useState(false);

    const handleChange = async (event) => {
        const isEnabled = event.target.checked;
        setIsSaving(true);
        setMessage(null);

        try {
            const savedProfile = await apiService.put("/profile/email-preference", {
                emailNotificationsEnabled: isEnabled,
            });
            onSaved(savedProfile);
            if (isEnabled) {
                setMessage({isError: false, text: "Você vai receber os avisos por e-mail."});
            } else {
                setMessage({isError: false, text: "Avisos por e-mail desligados. Eles continuam aparecendo no sistema."});
            }
        } catch (requestError) {
            setMessage({isError: true, text: generalErrorText(requestError)});
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <Card title={cardTitle(FiBell, "Avisos por e-mail")}>
            <label className={styles.preference}>
                <input
                    type="checkbox"
                    className={styles.preferenceCheckbox}
                    checked={profile.emailNotificationsEnabled}
                    onChange={handleChange}
                    disabled={isSaving}
                />
                <span>Receber por e-mail os lembretes de formulários e de consultas</span>
            </label>
            <CardMessage message={message}/>
        </Card>
    );
}

// consentimento de pesquisa, opcional e q pode ser retirado a qq hora (RF37)
function ResearchCard({profile, onChanged}) {
    const [term, setTerm] = useState(null);
    const [isTermOpen, setIsTermOpen] = useState(false);
    const [message, setMessage] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    // retirar tira o botao da tela e o foco ia pro body, entao o aviso de retirado recebe o foco (CT-39)
    const [wasRevokedNow, setWasRevokedNow] = useState(false);
    const revokedNoticeRef = useFocusWhenShown(wasRevokedNow);
    const consent = profile.researchConsent;

    const openTerm = async () => {
        setMessage(null);
        setWasRevokedNow(false);
        try {
            setTerm(await apiService.get("/consent-term/research"));
            setIsTermOpen(true);
        } catch (requestError) {
            setMessage({isError: true, text: generalErrorText(requestError)});
        }
    };

    const saveChoice = async (path, body, successText) => {
        setIsSaving(true);
        setMessage(null);
        try {
            const savedConsent = await apiService.post(path, body);
            onChanged(savedConsent);
            setIsTermOpen(false);
            setMessage({isError: false, text: successText});
            return true;
        } catch (requestError) {
            setMessage({isError: true, text: generalErrorText(requestError)});
            return false;
        } finally {
            setIsSaving(false);
        }
    };

    const accept = async () => {
        const isSaved = await saveChoice("/consent-term/research/accept",
            {termVersion: term.version, termHash: term.textHash},
            "Obrigado. Você aceitou participar da pesquisa.");
        if (isSaved) {
            return;
        }
        // se o texto mudou com o modal aberto a versao nova aparece pra ler de novo
        try {
            setTerm(await apiService.get("/consent-term/research"));
        } catch {
            // o erro do aceite ja esta na tela
        }
    };
    const revoke = async () => {
        const isSaved = await saveChoice("/consent-term/research/revoke", undefined, RESEARCH_REVOKED_TEXT);
        setWasRevokedNow(isSaved);
    };

    return (
        <Card title={cardTitle(FiBookOpen, "Pesquisa")}>
            <p className={styles.cardText}>{researchStatusText(consent)}</p>
            {!isTermOpen && <CardMessage message={message} noticeRef={revokedNoticeRef}/>}
            <div className={styles.actions}>
                <button type="button" className="button-secondary" onClick={openTerm} disabled={isSaving}>
                    {consent.active ? "Ler o termo" : "Ler o termo e decidir"}
                </button>
                {consent.active && (
                    <BusyButton type="button" className="button-danger" onClick={revoke} isBusy={isSaving}
                                busyText="Salvando...">
                        Retirar consentimento
                    </BusyButton>
                )}
            </div>

            <Modal show={isTermOpen} title="Termo de pesquisa" onClickClose={() => setIsTermOpen(false)}>
                <div className={styles.termText}>{term && term.text}</div>
                <CardMessage message={message}/>
                {!consent.active && (
                    <div className={styles.actions}>
                        <BusyButton type="button" className="button" onClick={accept} isBusy={isSaving}
                                    busyText="Salvando...">
                            Aceito participar
                        </BusyButton>
                    </div>
                )}
            </Modal>
        </Card>
    );
}

// perfil da propria conta (RF18)
export default function Profile() {
    const [profile, setProfile] = useState(null);
    const [loadError, setLoadError] = useState(null);
    // os cartoes com algo digitado e n salvo (dados, e-mail e senha)
    const [unsavedCards, setUnsavedCards] = useState([]);

    const handleUnsavedChange = useCallback((cardName, hasUnsaved) => {
        setUnsavedCards((currentCards) => {
            if (hasUnsaved === currentCards.includes(cardName)) {
                return currentCards;
            }
            return hasUnsaved
                ? [...currentCards, cardName]
                : currentCards.filter((currentCard) => currentCard !== cardName);
        });
    }, []);

    // sair do perfil com algo digitado e n salvo pergunta antes, como na anamnese e nas escalas
    // (decisao da dona do projeto, novembro de 2026). vale pra qqr saida de dentro do sistema, e recarregar
    // ou fechar a aba fica com o aviso do navegador. o q ja foi salvo e o campo desfeito n perguntam,
    // e sair da conta tbm n pq a sessao ja acabou
    const hasUnsavedChanges = unsavedCards.length > 0;
    const exitBlocker = useBlocker(({currentLocation, nextLocation}) => hasUnsavedChanges
        && getLoggedUser() !== null
        && currentLocation.pathname !== nextLocation.pathname);
    useLeaveWarning(hasUnsavedChanges);
    useForgetBlockedExit(exitBlocker, hasUnsavedChanges);

    useEffect(() => {
        let isCurrentPage = true;
        apiService.get("/profile")
            .then((loadedProfile) => {
                if (isCurrentPage) {
                    setProfile(loadedProfile);
                }
            })
            .catch((requestError) => {
                if (isCurrentPage) {
                    setLoadError(generalErrorText(requestError));
                }
            });

        // se a pessoa sair antes da resposta chegar a resposta eh ignorada
        return () => {
            isCurrentPage = false;
        };
    }, []);

    // o nome novo tbm vale pro cabecalho, e o social vem na frente qnd o paciente tem
    const handleSaved = (savedProfile) => {
        setProfile(savedProfile);
        const loggedUser = getLoggedUser();
        if (loggedUser) {
            setLoggedUser({...loggedUser, name: savedProfile.socialName || savedProfile.name});
        }
    };

    // a data de nascimento q cruza os 18 anos deixa o termo pendente, e o layout abre ele na hora (#163)
    const handlePersonalDataSaved = (savedProfile) => {
        handleSaved(savedProfile);
        if (savedProfile.role === "PATIENT") {
            checkPendingTerm();
        }
    };

    const handleResearchChanged = (researchConsent) => {
        setProfile((currentProfile) => ({...currentProfile, researchConsent: researchConsent}));
    };

    // trocar a senha cancela o pedido de e-mail novo, entao o endereco esperando sai da tela
    const handlePasswordChanged = () => {
        setProfile((currentProfile) => ({...currentProfile, pendingEmail: null}));
    };

    if (loadError) {
        return (
            <section className={styles.page}>
                <PageHeader title="Meu perfil"/>
                <p className="aviso aviso--atencao" role="alert">{loadError}</p>
            </section>
        );
    }

    if (!profile) {
        return <SkeletonPage cards={2}/>;
    }

    // a conta administrativa n troca de e-mail por aqui
    const isAdmin = profile.role === "ADMIN";
    // so o paciente recebe lembrete por e-mail, os avisos do prescritor ficam so no sistema
    const isPatient = profile.role === "PATIENT";
    const showResearch = showsResearch(profile);

    let subtitle = "Seus dados, o e-mail de acesso e a senha.";
    if (isAdmin) {
        subtitle = "O nome e a senha da conta administrativa.";
    }
    if (isPatient) {
        subtitle = "Seus dados, o e-mail de acesso, a senha e os avisos.";
    }

    return (
        <section className={styles.page}>
            <PageHeader title="Meu perfil" subtitle={subtitle}/>

            <div className={styles.grid}>
                <PersonalDataCard profile={profile} onSaved={handlePersonalDataSaved}
                                  onUnsavedChange={handleUnsavedChange}/>
                <div className={styles.column}>
                    {!isAdmin && (
                        <EmailCard profile={profile} onSaved={handleSaved} onUnsavedChange={handleUnsavedChange}/>
                    )}
                    <PasswordCard onPasswordChanged={handlePasswordChanged} onUnsavedChange={handleUnsavedChange}/>
                    {isPatient && <EmailPreferenceCard profile={profile} onSaved={handleSaved}/>}
                    {showResearch && <ResearchCard profile={profile} onChanged={handleResearchChanged}/>}
                </div>
            </div>

            <ConfirmModal
                show={exitBlocker.state === "blocked"}
                title="Sair sem salvar"
                message="O que você mudou ainda não foi salvo e vai se perder. Quer mesmo sair?"
                confirmText="Sim, sair"
                cancelText="Continuar preenchendo"
                onConfirm={() => exitBlocker.proceed()}
                onCancel={() => exitBlocker.reset()}
            />
        </section>
    );
}
