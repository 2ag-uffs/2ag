import {useEffect, useState} from "react";
import {FiBell, FiLock, FiMail, FiUser} from "react-icons/fi";
import Card from "../../components/card/card.jsx";
import PasswordChecklist from "../../components/form/password-checklist.jsx";
import PasswordField from "../../components/form/password-field.jsx";
import SelectField from "../../components/form/select-field.jsx";
import TextField from "../../components/form/text-field.jsx";
import {isStrongPassword} from "../../components/form/password-rules.js";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError, getLoggedUser, setLoggedUser} from "../../services/api.js";
import {BRAZIL_STATE_OPTIONS} from "../../utils/brazil-states.js";
import {formatCpf, formatPhone, onlyDigits} from "../../utils/masks.js";
import styles from "./profile.module.css";

const CONNECTION_ERROR = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

const EMPTY_PASSWORD_FORM = {currentPassword: "", newPassword: "", newPasswordConfirmation: ""};

// separa o erro da api em erros de campo e mensagem geral
function readRequestError(requestError) {
    if (!(requestError instanceof ApiError)) {
        return {fieldErrors: {}, message: CONNECTION_ERROR};
    }
    const errorsByField = requestError.fieldErrors();
    if (Object.keys(errorsByField).length > 0) {
        return {fieldErrors: errorsByField, message: null};
    }
    return {fieldErrors: {}, message: requestError.message};
}

// campos do formulario a partir do perfil q veio da api
function personalDataFrom(profile) {
    const address = profile.address || {};
    return {
        name: profile.name || "",
        birthDate: profile.birthDate || "",
        phone: formatPhone(profile.phone || ""),
        street: address.street || "",
        number: address.number || "",
        city: address.city || "",
        state: address.state || "",
    };
}

// aviso de sucesso ou de erro dentro de cada cartao
function CardMessage({message}) {
    if (!message) {
        return null;
    }
    if (message.isError) {
        return <p className={"aviso aviso--atencao " + styles.cardMessage} role="alert">{message.text}</p>;
    }
    return <p className={"aviso " + styles.cardMessage} role="status">{message.text}</p>;
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

// dados pessoais e endereco
function PersonalDataCard({profile, onSaved}) {
    const [formData, setFormData] = useState(() => personalDataFrom(profile));
    const [fieldErrors, setFieldErrors] = useState({});
    const [message, setMessage] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const isPatient = profile.role === "PATIENT";

    const updateField = (fieldName, value) => {
        setFormData((currentData) => ({...currentData, [fieldName]: value}));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setIsSaving(true);
        setFieldErrors({});
        setMessage(null);

        // endereco todo em branco vai como sem endereco
        const hasAddress = Boolean(formData.street || formData.number || formData.city || formData.state);
        const requestBody = {
            name: formData.name,
            birthDate: formData.birthDate || null,
            phone: onlyDigits(formData.phone) || null,
            address: hasAddress
                ? {street: formData.street, number: formData.number, city: formData.city, state: formData.state}
                : null,
        };

        try {
            const savedProfile = await apiService.put("/profile", requestBody);
            onSaved(savedProfile);
            setMessage({isError: false, text: "Dados salvos."});
        } catch (requestError) {
            const errorInfo = readRequestError(requestError);
            setFieldErrors(errorInfo.fieldErrors);
            if (errorInfo.message) {
                setMessage({isError: true, text: errorInfo.message});
            }
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <Card title={cardTitle(FiUser, "Dados pessoais")}>
            <dl className={styles.readOnlyInfo}>
                <dt>CPF</dt>
                <dd>{profile.cpf ? formatCpf(profile.cpf) : "Não informado"}</dd>
                {isPatient && (
                    <>
                        <dt>Quem acompanha você</dt>
                        <dd>{profile.prescriberName || "Nenhum prescritor vinculado"}</dd>
                    </>
                )}
                {!isPatient && profile.registryType && (
                    <>
                        <dt>Registro profissional</dt>
                        <dd>
                            {profile.registryType} {profile.registryNumber}
                            {profile.profession ? " · " + profile.profession : ""}
                        </dd>
                    </>
                )}
            </dl>
            <p className={styles.hint}>Para corrigir esses dados, fale com a clínica.</p>

            <CardMessage message={message}/>

            <form className={styles.form} onSubmit={handleSubmit}>
                <TextField
                    label="Nome completo"
                    name="name"
                    autoComplete="name"
                    value={formData.name}
                    onChange={(event) => updateField("name", event.target.value)}
                    error={fieldErrors.name}
                    required={true}
                />
                <div className={styles.row}>
                    <TextField
                        label="Data de nascimento"
                        name="birthDate"
                        type="date"
                        autoComplete="bday"
                        value={formData.birthDate}
                        onChange={(event) => updateField("birthDate", event.target.value)}
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
                        onChange={(event) => updateField("phone", formatPhone(event.target.value))}
                        error={fieldErrors.phone}
                        required={isPatient}
                    />
                </div>
                <div className={styles.rowWide}>
                    <TextField
                        label="Rua"
                        name="address.street"
                        autoComplete="address-line1"
                        value={formData.street}
                        onChange={(event) => updateField("street", event.target.value)}
                        error={fieldErrors["address.street"]}
                        required={isPatient}
                    />
                    <TextField
                        label="Número"
                        name="address.number"
                        value={formData.number}
                        onChange={(event) => updateField("number", event.target.value)}
                        error={fieldErrors["address.number"]}
                        required={isPatient}
                    />
                </div>
                <div className={styles.rowWide}>
                    <TextField
                        label="Cidade"
                        name="address.city"
                        autoComplete="address-level2"
                        value={formData.city}
                        onChange={(event) => updateField("city", event.target.value)}
                        error={fieldErrors["address.city"]}
                        required={isPatient}
                    />
                    <SelectField
                        label="Estado"
                        name="address.state"
                        options={BRAZIL_STATE_OPTIONS}
                        placeholder="Escolha"
                        value={formData.state}
                        onChange={(event) => updateField("state", event.target.value)}
                        error={fieldErrors["address.state"]}
                        required={isPatient}
                    />
                </div>
                <div className={styles.actions}>
                    <button type="submit" className="button" disabled={isSaving}>
                        {isSaving ? "Salvando..." : "Salvar dados"}
                    </button>
                </div>
            </form>
        </Card>
    );
}

// troca do e-mail de acesso pedindo a senha atual
function EmailCard({profile, onSaved}) {
    const [newEmail, setNewEmail] = useState("");
    const [currentPassword, setCurrentPassword] = useState("");
    const [fieldErrors, setFieldErrors] = useState({});
    const [message, setMessage] = useState(null);
    const [isSaving, setIsSaving] = useState(false);

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
            // sem ponto depois do e-mail pra ele n parecer parte do endereco
            setMessage({isError: false, text: "Pronto. Agora você entra com " + savedProfile.email});
        } catch (requestError) {
            const errorInfo = readRequestError(requestError);
            setFieldErrors(errorInfo.fieldErrors);
            if (errorInfo.message) {
                setMessage({isError: true, text: errorInfo.message});
            }
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <Card title={cardTitle(FiMail, "E-mail de acesso")}>
            {/* sem ponto depois do e-mail pra ele n parecer parte do endereco */}
            <p className={styles.cardText}>Hoje você entra com <strong>{profile.email}</strong></p>

            <CardMessage message={message}/>

            <form className={styles.form} onSubmit={handleSubmit}>
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
                    <button type="submit" className="button" disabled={isSaving}>
                        {isSaving ? "Trocando..." : "Trocar e-mail"}
                    </button>
                </div>
            </form>
        </Card>
    );
}

// troca de senha pedindo a senha atual
function PasswordCard() {
    const [formData, setFormData] = useState(EMPTY_PASSWORD_FORM);
    const [fieldErrors, setFieldErrors] = useState({});
    const [message, setMessage] = useState(null);
    const [isSaving, setIsSaving] = useState(false);

    const updateField = (fieldName, value) => {
        setFormData((currentData) => ({...currentData, [fieldName]: value}));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setMessage(null);

        const formErrors = {};
        if (!isStrongPassword(formData.newPassword)) {
            formErrors.newPassword = "A nova senha ainda não cumpre todas as regras";
        }
        if (formData.newPassword !== formData.newPasswordConfirmation) {
            formErrors.newPasswordConfirmation = "As senhas não são iguais";
        }
        setFieldErrors(formErrors);
        if (Object.keys(formErrors).length > 0) {
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
        } catch (requestError) {
            const errorInfo = readRequestError(requestError);
            setFieldErrors(errorInfo.fieldErrors);
            if (errorInfo.message) {
                setMessage({isError: true, text: errorInfo.message});
            }
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <Card title={cardTitle(FiLock, "Senha")}>
            <CardMessage message={message}/>

            <form className={styles.form} onSubmit={handleSubmit}>
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
                <div className={styles.actions}>
                    <button type="submit" className="button" disabled={isSaving}>
                        {isSaving ? "Trocando..." : "Trocar senha"}
                    </button>
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
            setMessage({isError: true, text: readRequestError(requestError).message || CONNECTION_ERROR});
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

// perfil da propria conta (RF18)
export default function Profile() {
    const [profile, setProfile] = useState(null);
    const [loadError, setLoadError] = useState(null);

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
                    setLoadError(readRequestError(requestError).message || CONNECTION_ERROR);
                }
            });

        // se a pessoa sair antes da resposta chegar a resposta eh ignorada
        return () => {
            isCurrentPage = false;
        };
    }, []);

    // o nome novo tbm vale pro cabecalho
    const handleSaved = (savedProfile) => {
        setProfile(savedProfile);
        const loggedUser = getLoggedUser();
        if (loggedUser) {
            setLoggedUser({...loggedUser, name: savedProfile.name});
        }
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

    return (
        <section className={styles.page}>
            <PageHeader title="Meu perfil" subtitle="Seus dados, o e-mail de acesso, a senha e os avisos."/>

            <div className={styles.grid}>
                <PersonalDataCard profile={profile} onSaved={handleSaved}/>
                <div className={styles.column}>
                    <EmailCard profile={profile} onSaved={handleSaved}/>
                    <PasswordCard/>
                    <EmailPreferenceCard profile={profile} onSaved={handleSaved}/>
                </div>
            </div>
        </section>
    );
}
