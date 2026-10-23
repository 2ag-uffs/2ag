import {useEffect, useRef, useState} from "react";
import {Link, useNavigate, useSearchParams} from "react-router";
import {homePathFor} from "../../app/role-home.js";
import AuthLayout from "../../components/auth-layout/auth-layout.jsx";
import PasswordChecklist from "../../components/form/password-checklist.jsx";
import PasswordField from "../../components/form/password-field.jsx";
import SelectField from "../../components/form/select-field.jsx";
import TextField from "../../components/form/text-field.jsx";
import TrapField from "../../components/form/trap-field.jsx";
import {isStrongPassword} from "../../components/form/password-rules.js";
import Modal from "../../components/modal/modal.jsx";
import {apiService, ApiError, getLoggedUser, logout, setLoggedUser} from "../../services/api.js";
import {BRAZIL_STATE_OPTIONS} from "../../utils/brazil-states.js";
import {ACCEPT_BUTTON_TEXT, ageBandOf, findGuardianErrors, guardianBody} from "../../utils/guardian.js";
import {formatCpf, formatPhone, onlyDigits} from "../../utils/masks.js";
import styles from "./sign-up.module.css";

const EMPTY_FORM = {
    name: "",
    socialName: "",
    cpf: "",
    birthDate: "",
    phone: "",
    street: "",
    number: "",
    city: "",
    state: "",
    guardianName: "",
    guardianRelationship: "",
    guardianPhone: "",
    email: "",
    password: "",
    passwordConfirmation: "",
};

// qm aceita o termo muda com a faixa de idade (Codigo Civil arts. 3 e 4)
const CONSENT_ERRORS = {
    "adult": "Para criar a conta, leia e aceite o termo de consentimento",
    "16-17": "Para criar a conta, o paciente e o responsável legal precisam ler e aceitar o termo de consentimento",
    "under-16": "Para criar a conta, o responsável legal precisa ler e aceitar o termo de consentimento",
};

// confere no navegador o q da pra conferir antes de mandar pra api
function findFormErrors(formData, hasAcceptedConsent, ageBand) {
    const errors = findGuardianErrors(formData, ageBand !== "adult");
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
        errors.consentTermVersion = CONSENT_ERRORS[ageBand];
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

// volta pra tela inicial da conta q ja estava aberta, sem sair dela
function BackToOpenAccount({account}) {
    return (
        <Link to={homePathFor(account)} replace={true} className={"button button-secondary " + styles.linkButton}>
            Voltar para a conta aberta
        </Link>
    );
}

// cadastro do paciente pelo link de convite (RF02.1) com aceite do termo (RF36)
export default function SignUp() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const tokenInUrl = searchParams.get("convite");
    // o codigo do convite fica guardado na tela depois de sair da barra de endereco
    const [inviteToken] = useState(tokenInUrl);
    // o link de convite aberto com outra conta logada explica q precisa sair dela antes
    const [otherAccount, setOtherAccount] = useState(() => getLoggedUser());

    const [inviteStatus, setInviteStatus] = useState(inviteToken ? "checking" : "missing");
    const [inviteInfo, setInviteInfo] = useState(null);
    const [consentTerm, setConsentTerm] = useState(null);
    const [hasAcceptedConsent, setHasAcceptedConsent] = useState(false);
    const [isTermOpen, setIsTermOpen] = useState(false);
    // pesquisa eh opcional e separada do termo do cadastro (RF37)
    const [researchTerm, setResearchTerm] = useState(null);
    const [acceptsResearch, setAcceptsResearch] = useState(false);
    const [isResearchOpen, setIsResearchOpen] = useState(false);
    const [formData, setFormData] = useState(EMPTY_FORM);
    const [site, setSite] = useState("");
    const [fieldErrors, setFieldErrors] = useState({});
    const [errorMessage, setErrorMessage] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const messageRef = useRef(null);
    // menor de 16: o responsavel legal aceita o termo em nome do paciente
    // com 16 e 17: o paciente aceita junto com o responsavel (LGPD art. 14 e Codigo Civil)
    // a pesquisa fica escondida pro menor ate o comite de etica definir o assentimento dele
    const ageBand = ageBandOf(formData.birthDate);
    const isMinor = ageBand !== "adult";

    // erro q volta da api: leva a pessoa ate o campo marcado ou ate o aviso la em cima
    // eh um efeito pq o erro so esta na tela depois q ela desenha de novo
    useEffect(() => {
        if (!errorMessage) {
            return;
        }
        const firstInvalidField = document.querySelector("[aria-invalid='true']");
        if (firstInvalidField) {
            firstInvalidField.focus();
        } else if (messageRef.current) {
            messageRef.current.scrollIntoView({block: "center"});
        }
    }, [errorMessage]);

    // tira o codigo da barra de endereco e do historico do navegador
    // com outra conta aberta ele fica, pq sem o convite a rota manda pra tela inicial dessa conta
    useEffect(() => {
        if (tokenInUrl && !otherAccount) {
            navigate("/cadastro", {replace: true});
        }
    }, [tokenInUrl, otherAccount, navigate]);

    // confere o convite e busca o termo antes de mostrar o formulario
    // com outra conta aberta tbm, pra so pedir pra sair dela se o convite ainda vale
    useEffect(() => {
        if (!inviteToken) {
            return;
        }

        let isCurrentPage = true;
        Promise.all([
            apiService.post("/invites/lookup", {token: inviteToken}),
            apiService.get("/consent-term"),
            apiService.get("/consent-term/research"),
        ])
            .then(([invite, term, research]) => {
                if (isCurrentPage) {
                    setInviteInfo(invite);
                    setConsentTerm(term);
                    setResearchTerm(research);
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

    // sai da conta q estava aberta e segue o cadastro com o mesmo convite
    const logOutOtherAccount = async () => {
        await logout();
        setOtherAccount(null);
    };

    // se o termo mudou com a tela aberta a versao nova entra no lugar e o aceite desmarca pra ler de novo
    const reloadChangedTerms = async () => {
        try {
            const [term, research] = await Promise.all([
                apiService.get("/consent-term"),
                apiService.get("/consent-term/research"),
            ]);
            if (term.version !== consentTerm.version) {
                setConsentTerm(term);
                setHasAcceptedConsent(false);
            }
            if (research.version !== researchTerm.version) {
                setResearchTerm(research);
                setAcceptsResearch(false);
            }
        } catch {
            // o erro do cadastro ja esta na tela
        }
    };

    const updateField = (fieldName, value) => {
        setFormData((currentData) => ({...currentData, [fieldName]: value}));
    };

    // mudou a faixa de idade, mudou qm marca o aceite: as caixas desmarcam pra pessoa certa marcar o texto certo
    const changeBirthDate = (birthDate) => {
        if (ageBandOf(birthDate) !== ageBand) {
            setHasAcceptedConsent(false);
            setAcceptsResearch(false);
        }
        updateField("birthDate", birthDate);
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

        const formErrors = findFormErrors(formData, hasAcceptedConsent, ageBand);
        setFieldErrors(formErrors);
        if (Object.keys(formErrors).length > 0) {
            focusFirstInvalidField();
            return;
        }

        setIsSubmitting(true);
        const requestBody = {
            inviteToken: inviteToken,
            name: formData.name,
            socialName: formData.socialName.trim() || null,
            cpf: onlyDigits(formData.cpf),
            birthDate: formData.birthDate,
            phone: onlyDigits(formData.phone),
            address: {
                street: formData.street,
                number: formData.number,
                city: formData.city,
                state: formData.state,
            },
            guardian: guardianBody(formData),
            email: formData.email,
            password: formData.password,
            consentTermVersion: consentTerm.version,
            researchTermVersion: acceptsResearch && !isMinor ? researchTerm.version : null,
            site: site,
        };

        try {
            const user = await apiService.post("/auth/register", requestBody);
            setLoggedUser(user);
            navigate("/painel-paciente", {replace: true});
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                const errorsByField = requestError.fieldErrors();
                setFieldErrors(errorsByField);
                if (Object.keys(errorsByField).length > 0) {
                    setErrorMessage("Confira os campos destacados.");
                } else {
                    // tipo o termo q foi atualizado: a mensagem so aparece com o texto novo ja na tela
                    await reloadChangedTerms();
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

    if (otherAccount && inviteStatus === "valid") {
        return (
            <AuthLayout title="Criar conta">
                <p className={"aviso aviso--atencao " + styles.message}>
                    Você está conectado como <strong>{otherAccount.name}</strong>. Este convite cria uma conta nova,
                    então saia dessa conta para continuar o cadastro.
                </p>
                <button type="button" className="button" onClick={logOutOtherAccount}>
                    Sair e continuar o cadastro
                </button>
                <BackToOpenAccount account={otherAccount}/>
            </AuthLayout>
        );
    }

    // convite q n vale mais com uma conta aberta, tipo o paciente q abre de novo o link q ja usou:
    // ele continua logado e volta pra conta dele
    if (otherAccount && inviteStatus === "invalid") {
        return (
            <AuthLayout title="Criar conta">
                <p className={"aviso aviso--atencao " + styles.message}>
                    Este convite não vale mais. Ele pode ter vencido ou já ter sido usado. Você continua conectado
                    como <strong>{otherAccount.name}</strong>. Para criar outra conta, peça um novo link ao seu
                    prescritor.
                </p>
                <BackToOpenAccount account={otherAccount}/>
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
                {otherAccount ? <BackToOpenAccount account={otherAccount}/> : (
                    <Link to="/entrar" className={"button button-secondary " + styles.linkButton}>
                        Já tenho conta
                    </Link>
                )}
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
                <p className={"aviso aviso--atencao " + styles.message} role="alert" ref={messageRef}>
                    {errorMessage}
                </p>
            )}

            <form className={styles.form} onSubmit={handleSubmit}>
                <TrapField value={site} onChange={setSite}/>
                <fieldset className={styles.section}>
                    <legend className={styles.sectionTitle}>{isMinor ? "Dados do paciente" : "Seus dados"}</legend>
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
                        label="Nome social (opcional)"
                        name="socialName"
                        maxLength={255}
                        hint="Se você usa um nome social, é ele que aparece no sistema e nos avisos."
                        value={formData.socialName}
                        onChange={(event) => updateField("socialName", event.target.value)}
                        error={fieldErrors.socialName}
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
                            onChange={(event) => changeBirthDate(event.target.value)}
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
                    <legend className={styles.sectionTitle}>
                        {isMinor ? "Responsável legal" : "Familiar ou cuidador (opcional)"}
                    </legend>
                    {ageBand === "adult" && (
                        <p className={styles.sectionHint}>
                            Se alguém ajuda você no tratamento, deixe o contato. A clínica pode ligar para essa pessoa
                            quando precisar.
                        </p>
                    )}
                    {ageBand === "16-17" && (
                        <p className={styles.sectionHint}>
                            O paciente tem 16 ou 17 anos. Ele preenche o cadastro e aceita o termo junto com o pai, a
                            mãe ou o responsável legal.
                        </p>
                    )}
                    {ageBand === "under-16" && (
                        <p className={styles.sectionHint}>
                            O paciente tem menos de 16 anos. O pai, a mãe ou o responsável legal preenche o cadastro
                            e aceita o termo em nome dele.
                        </p>
                    )}
                    <TextField
                        label="Nome"
                        name="guardian.name"
                        value={formData.guardianName}
                        onChange={(event) => updateField("guardianName", event.target.value)}
                        error={fieldErrors["guardian.name"]}
                        required={isMinor}
                    />
                    <div className={styles.row}>
                        <TextField
                            label="Parentesco"
                            name="guardian.relationship"
                            placeholder={isMinor ? "Mãe, pai, avó, tutor..." : "Filha, marido, cuidadora..."}
                            value={formData.guardianRelationship}
                            onChange={(event) => updateField("guardianRelationship", event.target.value)}
                            error={fieldErrors["guardian.relationship"]}
                            required={isMinor}
                        />
                        <TextField
                            label="Telefone com DDD"
                            name="guardian.phone"
                            type="tel"
                            placeholder="(49) 99999-9999"
                            value={formData.guardianPhone}
                            onChange={(event) => updateField("guardianPhone", formatPhone(event.target.value))}
                            error={fieldErrors["guardian.phone"]}
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
                        {ageBand === "adult" && (
                            <span>
                                Li e aceito o{" "}
                                <button type="button" className={styles.termButton} onClick={() => setIsTermOpen(true)}>
                                    termo de consentimento
                                </button>
                                {" "}para o uso dos meus dados de saúde no acompanhamento.
                            </span>
                        )}
                        {ageBand === "16-17" && (
                            <span>
                                Eu, paciente, e meu responsável legal lemos e aceitamos o{" "}
                                <button type="button" className={styles.termButton} onClick={() => setIsTermOpen(true)}>
                                    termo de consentimento
                                </button>
                                {" "}para o uso dos meus dados de saúde no acompanhamento.
                            </span>
                        )}
                        {ageBand === "under-16" && (
                            <span>
                                Sou o responsável legal pelo paciente e aceito, em nome dele, o{" "}
                                <button type="button" className={styles.termButton} onClick={() => setIsTermOpen(true)}>
                                    termo de consentimento
                                </button>
                                {" "}para o uso dos dados de saúde dele no acompanhamento.
                            </span>
                        )}
                    </label>
                    {fieldErrors.consentTermVersion && (
                        <span className={styles.consentError}>{fieldErrors.consentTermVersion}</span>
                    )}
                </div>

                {!isMinor && (
                    <label className={styles.researchLabel}>
                        <input
                            type="checkbox"
                            name="researchTermVersion"
                            className={styles.consentCheckbox}
                            checked={acceptsResearch}
                            onChange={(event) => setAcceptsResearch(event.target.checked)}
                        />
                        <span>
                            Aceito participar da pesquisa da clínica com os meus dados sem identificação. É opcional e
                            não muda o atendimento.{" "}
                            <button type="button" className={styles.termButton} onClick={() => setIsResearchOpen(true)}>
                                Ler o termo de pesquisa
                            </button>
                        </span>
                    </label>
                )}

                <button type="submit" className={styles.submitButton} disabled={isSubmitting}>
                    {isSubmitting ? "Criando conta..." : "Criar conta"}
                </button>
            </form>

            <p className={styles.loginNote}>
                Já tem conta? <Link to="/entrar">Entrar</Link>
            </p>

            <Modal show={isTermOpen} title="Termo de consentimento" onClickClose={() => setIsTermOpen(false)}>
                <div className={styles.termText}>{consentTerm.text}</div>
                <div className={styles.termActions}>
                    <button type="button" className={styles.termAcceptButton} onClick={acceptTerm}>
                        {ACCEPT_BUTTON_TEXT[ageBand]}
                    </button>
                </div>
            </Modal>

            <Modal show={isResearchOpen} title="Termo de pesquisa" onClickClose={() => setIsResearchOpen(false)}>
                <div className={styles.termText}>{researchTerm.text}</div>
                <div className={styles.termActions}>
                    <button
                        type="button"
                        className={styles.termAcceptButton}
                        onClick={() => {
                            setAcceptsResearch(true);
                            setIsResearchOpen(false);
                        }}
                    >
                        Aceito participar
                    </button>
                </div>
            </Modal>
        </AuthLayout>
    );
}
