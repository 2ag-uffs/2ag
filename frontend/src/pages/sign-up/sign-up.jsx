import {useEffect, useState} from "react";
import {Link, useNavigate, useSearchParams} from "react-router";
import AuthLayout from "../../components/auth-layout/auth-layout.jsx";
import PasswordChecklist from "../../components/form/password-checklist.jsx";
import PasswordField from "../../components/form/password-field.jsx";
import SelectField from "../../components/form/select-field.jsx";
import TextField from "../../components/form/text-field.jsx";
import {isStrongPassword} from "../../components/form/password-rules.js";
import Modal from "../../components/modal/modal.jsx";
import {apiService, ApiError, setLoggedUser} from "../../services/api.js";
import {BRAZIL_STATE_OPTIONS} from "../../utils/brazil-states.js";
import {formatCpf, formatPhone, onlyDigits} from "../../utils/masks.js";
import styles from "./sign-up.module.css";

const EMPTY_FORM = {
    name: "",
    cpf: "",
    birthDate: "",
    phone: "",
    street: "",
    number: "",
    city: "",
    state: "",
    email: "",
    password: "",
    passwordConfirmation: "",
};

// confere no navegador o q da pra conferir antes de mandar pra api
function findFormErrors(formData, hasAcceptedConsent) {
    const errors = {};
    if (onlyDigits(formData.cpf).length !== 11) {
        errors.cpf = "O CPF tem 11 números";
    }
    if (onlyDigits(formData.phone).length < 10) {
        errors.phone = "Informe o telefone com DDD";
    }
    if (!isStrongPassword(formData.password)) {
        errors.password = "A senha ainda não cumpre todas as regras";
    }
    if (formData.password !== formData.passwordConfirmation) {
        errors.passwordConfirmation = "As senhas não são iguais";
    }
    if (!hasAcceptedConsent) {
        errors.consentTermVersion = "Para criar a conta, leia e aceite o termo de consentimento";
    }
    return errors;
}

// leva a pessoa ate o primeiro campo com erro
// no celular o formulario eh comprido e o erro pode estar fora da tela
function focusFirstInvalidField() {
    setTimeout(() => {
        const firstInvalidField = document.querySelector("[aria-invalid='true']");
        if (firstInvalidField) {
            firstInvalidField.focus();
        }
    }, 0);
}

// cadastro do paciente pelo link de convite (RF02.1) com aceite do termo (RF36)
export default function SignUp() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const inviteToken = searchParams.get("convite");

    const [inviteStatus, setInviteStatus] = useState(inviteToken ? "checking" : "missing");
    const [inviteInfo, setInviteInfo] = useState(null);
    const [consentTerm, setConsentTerm] = useState(null);
    const [hasAcceptedConsent, setHasAcceptedConsent] = useState(false);
    const [isTermOpen, setIsTermOpen] = useState(false);
    const [formData, setFormData] = useState(EMPTY_FORM);
    const [fieldErrors, setFieldErrors] = useState({});
    const [errorMessage, setErrorMessage] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    // confere o convite e busca o termo antes de mostrar o formulario
    useEffect(() => {
        if (!inviteToken) {
            return;
        }

        let isCurrentPage = true;
        Promise.all([
            apiService.get("/invites/" + encodeURIComponent(inviteToken)),
            apiService.get("/consent-term"),
        ])
            .then(([invite, term]) => {
                if (isCurrentPage) {
                    setInviteInfo(invite);
                    setConsentTerm(term);
                    setInviteStatus("valid");
                }
            })
            .catch((requestError) => {
                if (!isCurrentPage) {
                    return;
                }
                if (requestError instanceof ApiError && requestError.status === 404) {
                    setInviteStatus("invalid");
                } else {
                    setInviteStatus("offline");
                }
            });

        // se a pessoa sair antes da resposta chegar a resposta eh ignorada
        return () => {
            isCurrentPage = false;
        };
    }, [inviteToken]);

    const updateField = (fieldName, value) => {
        setFormData((currentData) => ({...currentData, [fieldName]: value}));
    };

    // marcar o aceite tira o aviso de erro do aceite
    const changeConsent = (isAccepted) => {
        setHasAcceptedConsent(isAccepted);
        if (isAccepted) {
            setFieldErrors((currentErrors) => ({...currentErrors, consentTermVersion: undefined}));
        }
    };

    const acceptTerm = () => {
        changeConsent(true);
        setIsTermOpen(false);
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setErrorMessage(null);

        const formErrors = findFormErrors(formData, hasAcceptedConsent);
        setFieldErrors(formErrors);
        if (Object.keys(formErrors).length > 0) {
            focusFirstInvalidField();
            return;
        }

        setIsSubmitting(true);
        const requestBody = {
            inviteToken: inviteToken,
            name: formData.name,
            cpf: onlyDigits(formData.cpf),
            birthDate: formData.birthDate,
            phone: onlyDigits(formData.phone),
            address: {
                street: formData.street,
                number: formData.number,
                city: formData.city,
                state: formData.state,
            },
            email: formData.email,
            password: formData.password,
            consentTermVersion: consentTerm.version,
        };

        try {
            const user = await apiService.post("/auth/register", requestBody);
            setLoggedUser(user);
            navigate("/dashboard-paciente", {replace: true});
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                const errorsByField = requestError.fieldErrors();
                setFieldErrors(errorsByField);
                if (Object.keys(errorsByField).length > 0) {
                    setErrorMessage("Confira os campos destacados.");
                    focusFirstInvalidField();
                } else {
                    setErrorMessage(requestError.message);
                }
            } else {
                setErrorMessage("Não foi possível falar com o servidor. Confira sua internet e tente de novo.");
            }
            setIsSubmitting(false);
        }
    };

    if (inviteStatus === "checking") {
        return (
            <AuthLayout title="Criar conta">
                <p className={styles.statusText}>Conferindo o convite...</p>
            </AuthLayout>
        );
    }

    if (inviteStatus !== "valid") {
        let statusMessage = "O cadastro de paciente é feito pelo link de convite que o seu prescritor envia. "
            + "Peça o link a ele.";
        if (inviteStatus === "invalid") {
            statusMessage = "Este convite não vale mais. Ele pode ter vencido ou já ter sido usado. "
                + "Peça um novo link ao seu prescritor.";
        }
        if (inviteStatus === "offline") {
            statusMessage = "Não foi possível conferir o convite agora. Confira sua internet e abra o link de novo.";
        }

        return (
            <AuthLayout title="Criar conta">
                <p className={"aviso aviso--atencao " + styles.message}>{statusMessage}</p>
                <Link to="/login" className={"button button-secondary " + styles.linkButton}>
                    Já tenho conta
                </Link>
            </AuthLayout>
        );
    }

    return (
        <AuthLayout title="Criar conta">
            <p className={"aviso " + styles.message}>
                Convite de <strong>{inviteInfo.prescriberName}</strong>
                {inviteInfo.prescriberProfession ? " · " + inviteInfo.prescriberProfession : ""}.
                Sua conta fica vinculada a esse acompanhamento.
            </p>

            {errorMessage && (
                <p className={"aviso aviso--atencao " + styles.message} role="alert">{errorMessage}</p>
            )}

            <form className={styles.form} onSubmit={handleSubmit}>
                <fieldset className={styles.section}>
                    <legend className={styles.sectionTitle}>Seus dados</legend>
                    <TextField
                        label="Nome completo"
                        name="name"
                        autoComplete="name"
                        value={formData.name}
                        onChange={(event) => updateField("name", event.target.value)}
                        error={fieldErrors.name}
                        required={true}
                    />
                    <TextField
                        label="CPF"
                        name="cpf"
                        inputMode="numeric"
                        placeholder="000.000.000-00"
                        value={formData.cpf}
                        onChange={(event) => updateField("cpf", formatCpf(event.target.value))}
                        error={fieldErrors.cpf}
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
                            required={true}
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
                            required={true}
                        />
                    </div>
                </fieldset>

                <fieldset className={styles.section}>
                    <legend className={styles.sectionTitle}>Endereço</legend>
                    <div className={styles.rowWide}>
                        <TextField
                            label="Rua"
                            name="address.street"
                            autoComplete="address-line1"
                            value={formData.street}
                            onChange={(event) => updateField("street", event.target.value)}
                            error={fieldErrors["address.street"]}
                            required={true}
                        />
                        <TextField
                            label="Número"
                            name="address.number"
                            value={formData.number}
                            onChange={(event) => updateField("number", event.target.value)}
                            error={fieldErrors["address.number"]}
                            required={true}
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
                            required={true}
                        />
                        <SelectField
                            label="Estado"
                            name="address.state"
                            options={BRAZIL_STATE_OPTIONS}
                            placeholder="Escolha"
                            value={formData.state}
                            onChange={(event) => updateField("state", event.target.value)}
                            error={fieldErrors["address.state"]}
                            required={true}
                        />
                    </div>
                </fieldset>

                <fieldset className={styles.section}>
                    <legend className={styles.sectionTitle}>Acesso</legend>
                    <TextField
                        label="E-mail"
                        name="email"
                        type="email"
                        inputMode="email"
                        autoComplete="email"
                        value={formData.email}
                        onChange={(event) => updateField("email", event.target.value)}
                        error={fieldErrors.email}
                        required={true}
                    />
                    <PasswordField
                        label="Senha"
                        name="password"
                        autoComplete="new-password"
                        value={formData.password}
                        onChange={(event) => updateField("password", event.target.value)}
                        error={fieldErrors.password}
                        required={true}
                    />
                    <PasswordChecklist password={formData.password}/>
                    <PasswordField
                        label="Repita a senha"
                        name="passwordConfirmation"
                        autoComplete="new-password"
                        value={formData.passwordConfirmation}
                        onChange={(event) => updateField("passwordConfirmation", event.target.value)}
                        error={fieldErrors.passwordConfirmation}
                        required={true}
                    />
                </fieldset>

                <div className={styles.consent}>
                    <label className={styles.consentLabel}>
                        <input
                            type="checkbox"
                            name="consentTermVersion"
                            className={styles.consentCheckbox}
                            checked={hasAcceptedConsent}
                            onChange={(event) => changeConsent(event.target.checked)}
                            aria-invalid={fieldErrors.consentTermVersion ? "true" : undefined}
                        />
                        <span>
                            Li e aceito o{" "}
                            <button type="button" className={styles.termButton} onClick={() => setIsTermOpen(true)}>
                                termo de consentimento
                            </button>
                            {" "}para o uso dos meus dados de saúde no acompanhamento.
                        </span>
                    </label>
                    {fieldErrors.consentTermVersion && (
                        <span className={styles.consentError}>{fieldErrors.consentTermVersion}</span>
                    )}
                </div>

                <button type="submit" className={styles.submitButton} disabled={isSubmitting}>
                    {isSubmitting ? "Criando conta..." : "Criar conta"}
                </button>
            </form>

            <p className={styles.loginNote}>
                Já tem conta? <Link to="/login">Entrar</Link>
            </p>

            <Modal show={isTermOpen} title="Termo de consentimento" onClickClose={() => setIsTermOpen(false)}>
                <div className={styles.termText}>{consentTerm.text}</div>
                <div className={styles.termActions}>
                    <button type="button" className={styles.termAcceptButton} onClick={acceptTerm}>
                        Li e aceito
                    </button>
                </div>
            </Modal>
        </AuthLayout>
    );
}
