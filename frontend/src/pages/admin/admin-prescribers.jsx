import {useCallback, useEffect, useState} from "react";
import {FiUserPlus, FiUsers} from "react-icons/fi";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import EmptyState from "../../components/empty-state/empty-state.jsx";
import BusyButton from "../../components/form/busy-button.jsx";
import {useFocusFirstError} from "../../components/form/focus-first-error.js";
import PasswordField from "../../components/form/password-field.jsx";
import Modal from "../../components/modal/modal.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import {SkeletonBlock} from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError, errorMessageOf, readRequestError} from "../../services/api.js";
import {isValidCpf} from "../../utils/cpf.js";
import {initialsOf} from "../../utils/initials.js";
import {onlyDigits} from "../../utils/masks.js";
import styles from "./admin-prescribers.module.css";

// conselhos profissionais aceitos no cadastro
const REGISTRY_TYPES = ["CRBM", "CRM", "COREN", "CRP", "CRF", "CRN"];

// a msm msg da api pro cpf q n fecha a conta dos dois digitos
const INVALID_CPF_MESSAGE = "Confira o CPF: os 11 números precisam ser iguais aos do documento";

// o envio tira tudo q n eh numero, entao o cpf so com letras chegava vazio: no cadastro a api dizia q era
// obrigatorio e na correcao ele virava null e ficava o guardado. aqui ele eh conferido como foi digitado
// em branco segue como antes: no cadastro a api diz q falta e na correcao mantem o guardado
function isWrongCpf(cpfText) {
    return cpfText.trim() !== "" && !isValidCpf(cpfText);
}

const MINUTES_PER_HOUR = 60;

const EMPTY_FORM = {
    name: "",
    email: "",
    cpf: "",
    birthDate: "",
    phone: "",
    profession: "",
    registryType: "CRBM",
    registryNumber: "",
};

// correcao de registro, cpf e profissao: o cpf comeca em branco pq a lista n mostra ele
const EMPTY_FIX = {
    cpf: "",
    profession: "",
    registryType: "CRBM",
    registryNumber: "",
};

// 30 vira 30 minutos e 2880 vira 48 horas
// 60 continua 60 minutos pra n virar 1 horas
function validityText(minutes) {
    if (minutes >= 2 * MINUTES_PER_HOUR && minutes % MINUTES_PER_HOUR === 0) {
        return minutes / MINUTES_PER_HOUR + " horas";
    }
    return minutes + " minutos";
}

// desativar n fecha nada dos pacientes: a api diz quantas escalas esperando resposta e quantos pedidos e
// consultas por vir ficaram, e os lembretes delas so voltam com a conta reativada (#148)
function pendingText(result) {
    const openTasks = result && result.openTasks ? result.openTasks : 0;
    const futureAppointments = result && result.futureAppointments ? result.futureAppointments : 0;
    if (openTasks === 0 && futureAppointments === 0) {
        return " Nenhuma escala nem consulta dos pacientes ficou esperando.";
    }
    const taskText = openTasks === 1 ? "1 escala esperando resposta" : openTasks + " escalas esperando resposta";
    const appointmentText = futureAppointments === 1
        ? "1 consulta ou pedido de consulta por vir"
        : futureAppointments + " consultas ou pedidos de consulta por vir";
    return " Ficaram com os pacientes " + taskText + " e " + appointmentText
        + ". Nada foi fechado, e os lembretes só voltam quando a conta for reativada.";
}

// o link de senha foi por e-mail ou aparece aqui pro administrador entregar
function LinkResult({link, sentText, handText, isCopied, onCopy, onClose}) {
    if (!link) {
        return (
            <div className={styles.form}>
                <p className={styles.fieldWide}>{sentText}</p>
                <div className={styles.formActions}>
                    <button type="button" className="button" onClick={onClose}>
                        Fechar
                    </button>
                </div>
            </div>
        );
    }
    return (
        <div className={styles.form}>
            <p className={styles.fieldWide}>{handText}</p>
            <input className={styles.linkBox} value={link} readOnly={true} aria-label="Link de senha"/>
            <div className={styles.formActions}>
                <button type="button" className="button-secondary" onClick={() => onCopy(link)}>
                    {isCopied ? "Link copiado" : "Copiar link"}
                </button>
                <button type="button" className="button" onClick={onClose}>
                    Fechar
                </button>
            </div>
        </div>
    );
}

// um campo do formulario com rotulo e mensagem de erro
// o erro fica ligado ao campo, entao o leitor de tela le os dois juntos qnd o foco chega nele
function FormField({
    label, fieldName, value, error, onChange,
    type = "text", autoComplete = "off", isWide = false, isRequired = true,
}) {
    const errorId = fieldName + "-error";
    return (
        <div className={isWide ? styles.field + " " + styles.fieldWide : styles.field}>
            <label htmlFor={fieldName}>{label}</label>
            <input
                id={fieldName}
                name={fieldName}
                type={type}
                value={value}
                autoComplete={autoComplete}
                required={isRequired}
                aria-invalid={error ? "true" : undefined}
                aria-describedby={error ? errorId : undefined}
                onChange={(event) => onChange(fieldName, event.target.value)}
            />
            {error && <span id={errorId} className={styles.fieldError}>{error}</span>}
        </div>
    );
}

function LoadingList() {
    return (
        <ul className={styles.list} role="status" aria-label="Carregando prescritores">
            {[0, 1, 2].map((index) => (
                <li key={index} className={styles.row}>
                    <SkeletonBlock width="2.75rem" height="2.75rem" isRound={true}/>
                    <div className={styles.rowText}>
                        <SkeletonBlock width="12rem" height="1rem"/>
                        <SkeletonBlock width="9rem" height="0.875rem"/>
                    </div>
                </li>
            ))}
        </ul>
    );
}

// uma conta de prescritor com os botoes de senha nova, e-mail, dados e acesso
// enquanto a api desativa ou reativa a conta a linha fica ocupada e nenhum botao dela aceita clique
function PrescriberRow({prescriber, isBusy, onResetPassword, onChangeEmail, onFixData, onToggle}) {
    return (
        <li className={styles.row} aria-busy={isBusy ? "true" : undefined}>
            <span
                className={prescriber.active ? styles.avatar : styles.avatar + " " + styles.avatarInactive}
                aria-hidden="true"
            >
                {initialsOf(prescriber.name)}
            </span>
            <div className={styles.rowText}>
                <strong className={styles.name}>{prescriber.name}</strong>
                <span className={styles.details}>{prescriber.email}</span>
                <span className={styles.details}>
                    {prescriber.registryType} {prescriber.registryNumber} · {prescriber.profession}
                </span>
            </div>
            <div className={styles.rowActions}>
                <span className={prescriber.active ? styles.badgeActive : styles.badgeInactive}>
                    {prescriber.active ? "Ativo" : "Desativado"}
                </span>
                {prescriber.active && (
                    <BusyButton
                        type="button"
                        className="button-secondary button-small"
                        isBusy={isBusy}
                        onClick={() => onResetPassword(prescriber)}
                    >
                        Senha nova
                    </BusyButton>
                )}
                {/* enquanto a pessoa n cria a senha o e-mail errado ainda pode ser corrigido */}
                {prescriber.active && !prescriber.passwordCreated && (
                    <BusyButton
                        type="button"
                        className="button-secondary button-small"
                        isBusy={isBusy}
                        onClick={() => onChangeEmail(prescriber)}
                    >
                        Corrigir e-mail
                    </BusyButton>
                )}
                {/* registro, cpf ou profissao digitados errado (#149) */}
                <BusyButton
                    type="button"
                    className="button-secondary button-small"
                    isBusy={isBusy}
                    onClick={() => onFixData(prescriber)}
                >
                    Corrigir dados
                </BusyButton>
                <BusyButton
                    type="button"
                    className={prescriber.active ? "button-danger button-small" : "button-secondary button-small"}
                    isBusy={isBusy}
                    busyText={prescriber.active ? "Desativando..." : "Reativando..."}
                    onClick={() => onToggle(prescriber)}
                >
                    {prescriber.active ? "Desativar" : "Reativar"}
                </BusyButton>
            </div>
        </li>
    );
}

// tela do administrador
// lista os prescritores cria conta nova e ativa ou desativa o acesso
// a senha de cada um quem cria eh a propria pessoa, pelo link de primeiro acesso
export default function AdminPrescribers() {
    const [prescribers, setPrescribers] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [pageError, setPageError] = useState(null);
    const [notice, setNotice] = useState(null);

    const [isFormOpen, setIsFormOpen] = useState(false);
    const [formData, setFormData] = useState(EMPTY_FORM);
    const [fieldErrors, setFieldErrors] = useState({});
    const [formError, setFormError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const [formRef, focusFirstError] = useFocusFirstError();

    // conta recem criada com o link de primeiro acesso, ou sem ele qnd foi por e-mail
    const [firstAccess, setFirstAccess] = useState(null);

    const [prescriberToToggle, setPrescriberToToggle] = useState(null);
    // contas com o pedido de desativar ou reativar ainda sem resposta
    const [busyIds, setBusyIds] = useState([]);

    // senha nova de um prescritor: pede a senha do admin e o link vem na resposta ou vai por e-mail
    const [resetTarget, setResetTarget] = useState(null);
    const [adminPassword, setAdminPassword] = useState("");
    const [resetFieldErrors, setResetFieldErrors] = useState({});
    const [resetError, setResetError] = useState(null);
    const [isResetting, setIsResetting] = useState(false);
    const [newLink, setNewLink] = useState(null);
    const [isLinkCopied, setIsLinkCopied] = useState(false);
    const [resetFormRef, focusFirstResetError] = useFocusFirstError();
    // correcao do e-mail digitado errado, so enquanto a pessoa ainda n criou a senha
    const [emailTarget, setEmailTarget] = useState(null);
    const [newEmail, setNewEmail] = useState("");
    const [emailAdminPassword, setEmailAdminPassword] = useState("");
    const [emailFieldErrors, setEmailFieldErrors] = useState({});
    const [emailError, setEmailError] = useState(null);
    const [isChangingEmail, setIsChangingEmail] = useState(false);
    const [emailLink, setEmailLink] = useState(null);
    const [emailFormRef, focusFirstEmailError] = useFocusFirstError();
    // correcao do registro, cpf ou profissao digitados errado (#149)
    const [fixTarget, setFixTarget] = useState(null);
    const [fixData, setFixData] = useState(EMPTY_FIX);
    const [fixFieldErrors, setFixFieldErrors] = useState({});
    const [fixError, setFixError] = useState(null);
    const [isFixing, setIsFixing] = useState(false);
    const [fixFormRef, focusFirstFixError] = useFocusFirstError();

    // devolve a promessa pra quem cria ou desativa prescritor poder esperar a lista nova
    const loadPrescribers = useCallback(() => {
        return apiService.get("/admin/prescribers")
            .then((prescriberList) => {
                setPrescribers(prescriberList);
                setPageError(null);
            })
            .catch((requestError) => {
                setPageError(errorMessageOf(requestError,
                    "Não foi possível carregar os prescritores. Confira sua internet e tente de novo."));
            })
            .finally(() => setIsLoading(false));
    }, []);

    useEffect(() => {
        loadPrescribers();
    }, [loadPrescribers]);

    const updateField = (fieldName, value) => {
        setFormData((currentData) => ({...currentData, [fieldName]: value}));
    };

    const openForm = () => {
        setFormData(EMPTY_FORM);
        setFieldErrors({});
        setFormError(null);
        setIsFormOpen(true);
    };

    const closeForm = () => {
        if (!isSaving) {
            setIsFormOpen(false);
        }
    };

    const handleCreate = async (event) => {
        event.preventDefault();
        if (isWrongCpf(formData.cpf)) {
            setFieldErrors({cpf: INVALID_CPF_MESSAGE});
            setFormError(null);
            focusFirstError();
            return;
        }
        setIsSaving(true);
        setFieldErrors({});
        setFormError(null);

        // a api espera cpf e telefone so com numeros
        // campo opcional vazio vai como null
        // o numero do registro colado costuma vir com espaco no fim
        const requestBody = {
            name: formData.name,
            email: formData.email,
            cpf: onlyDigits(formData.cpf),
            birthDate: formData.birthDate || null,
            phone: onlyDigits(formData.phone) || null,
            profession: formData.profession,
            registryType: formData.registryType,
            registryNumber: formData.registryNumber.trim(),
        };

        try {
            const createdAccount = await apiService.post("/admin/prescribers", requestBody);
            setIsFormOpen(false);
            setNotice(null);
            setIsLinkCopied(false);
            setFirstAccess({...createdAccount, email: formData.email.trim()});
            await loadPrescribers();
        } catch (requestError) {
            const errorInfo = readRequestError(requestError);
            setFieldErrors(errorInfo.fieldErrors);
            if (errorInfo.message) {
                setFormError(errorInfo.message);
            }
            focusFirstError();
        } finally {
            setIsSaving(false);
        }
    };

    const confirmToggle = async () => {
        const prescriber = prescriberToToggle;
        setPrescriberToToggle(null);
        setBusyIds((currentIds) => [...currentIds, prescriber.id]);
        try {
            const result = await apiService.put("/admin/prescribers/" + prescriber.id + "/active",
                {active: !prescriber.active});
            if (prescriber.active) {
                setNotice("Acesso de " + prescriber.name + " desativado." + pendingText(result));
            } else {
                setNotice("Acesso de " + prescriber.name + " reativado.");
            }
            await loadPrescribers();
        } catch (requestError) {
            setNotice(null);
            // a api recusou: a lista recarrega pra linha mostrar como a conta esta de vdd
            if (requestError instanceof ApiError) {
                await loadPrescribers();
            }
            setPageError(errorMessageOf(requestError,
                "Não foi possível alterar a conta. Confira sua internet e tente de novo."));
        } finally {
            setBusyIds((currentIds) => currentIds.filter((busyId) => busyId !== prescriber.id));
        }
    };

    const openReset = (prescriber) => {
        setResetTarget(prescriber);
        setAdminPassword("");
        setResetFieldErrors({});
        setResetError(null);
        setNewLink(null);
        setIsLinkCopied(false);
    };

    // o resultado sai junto, senao o modal fechado tentaria ler o prescritor q ja foi limpo
    const closeReset = () => {
        if (!isResetting) {
            setResetTarget(null);
            setNewLink(null);
        }
    };

    const handleReset = async (event) => {
        event.preventDefault();
        setIsResetting(true);
        setResetFieldErrors({});
        setResetError(null);
        try {
            const link = await apiService.post("/admin/prescribers/" + resetTarget.id + "/password-reset", {
                adminPassword,
            });
            setNewLink(link);
            setAdminPassword("");
        } catch (requestError) {
            const errorInfo = readRequestError(requestError);
            setResetFieldErrors(errorInfo.fieldErrors);
            if (errorInfo.message) {
                setResetError(errorInfo.message);
            }
            focusFirstResetError();
        } finally {
            setIsResetting(false);
        }
    };

    const openEmailChange = (prescriber) => {
        setEmailTarget(prescriber);
        setNewEmail(prescriber.email);
        setEmailAdminPassword("");
        setEmailFieldErrors({});
        setEmailError(null);
        setEmailLink(null);
        setIsLinkCopied(false);
    };

    const closeEmailChange = () => {
        if (!isChangingEmail) {
            setEmailTarget(null);
            setEmailLink(null);
        }
    };

    const handleEmailChange = async (event) => {
        event.preventDefault();
        setIsChangingEmail(true);
        setEmailFieldErrors({});
        setEmailError(null);
        try {
            const link = await apiService.put("/admin/prescribers/" + emailTarget.id + "/email", {
                email: newEmail,
                adminPassword: emailAdminPassword,
            });
            setNewEmail(newEmail.trim().toLowerCase());
            setEmailLink(link);
            setEmailAdminPassword("");
            // a lista mostra o e-mail certo do jeito q a api guardou
            await loadPrescribers();
        } catch (requestError) {
            const errorInfo = readRequestError(requestError);
            setEmailFieldErrors(errorInfo.fieldErrors);
            if (errorInfo.message) {
                setEmailError(errorInfo.message);
            }
            focusFirstEmailError();
            if (requestError instanceof ApiError) {
                // a pessoa pode ter criado a senha nesse meio tempo, e ai o botao some da linha
                loadPrescribers();
            }
        } finally {
            setIsChangingEmail(false);
        }
    };

    const openFix = (prescriber) => {
        setFixTarget(prescriber);
        setFixData({
            cpf: "",
            profession: prescriber.profession,
            registryType: prescriber.registryType,
            registryNumber: prescriber.registryNumber,
        });
        setFixFieldErrors({});
        setFixError(null);
    };

    const closeFix = () => {
        if (!isFixing) {
            setFixTarget(null);
        }
    };

    const updateFixField = (fieldName, value) => {
        setFixData((currentData) => ({...currentData, [fieldName]: value}));
    };

    const handleFix = async (event) => {
        event.preventDefault();
        if (isWrongCpf(fixData.cpf)) {
            setFixFieldErrors({cpf: INVALID_CPF_MESSAGE});
            setFixError(null);
            focusFirstFixError();
            return;
        }
        setIsFixing(true);
        setFixFieldErrors({});
        setFixError(null);
        // cpf em branco vai como null e a api mantem o q esta guardado
        try {
            await apiService.put("/admin/prescribers/" + fixTarget.id, {
                cpf: onlyDigits(fixData.cpf) || null,
                profession: fixData.profession,
                registryType: fixData.registryType,
                registryNumber: fixData.registryNumber.trim(),
            });
            setFixTarget(null);
            setPageError(null);
            setNotice("Dados de " + fixTarget.name + " corrigidos.");
            await loadPrescribers();
        } catch (requestError) {
            const errorInfo = readRequestError(requestError);
            setFixFieldErrors(errorInfo.fieldErrors);
            if (errorInfo.message) {
                setFixError(errorInfo.message);
            }
            focusFirstFixError();
        } finally {
            setIsFixing(false);
        }
    };

    // navegador sem area de transferencia n quebra a tela, o link fica ali pra copiar na mao
    const copyLink = async (link) => {
        try {
            await navigator.clipboard.writeText(link);
            setIsLinkCopied(true);
        } catch {
            setIsLinkCopied(false);
        }
    };

    const isDeactivating = prescriberToToggle !== null && prescriberToToggle.active;
    // o modal fechado ainda monta o texto, entao sem alvo conta como se a senha existisse
    const resetTargetHasPassword = resetTarget === null || resetTarget.passwordCreated;

    // um conselho guardado fora da lista continua aparecendo selecionado na correcao
    const fixRegistryTypes = REGISTRY_TYPES.includes(fixData.registryType)
        ? REGISTRY_TYPES
        : [fixData.registryType, ...REGISTRY_TYPES];

    return (
        <section className={styles.page}>
            <PageHeader
                title="Prescritores"
                subtitle="Contas de quem atende na clínica. Desativar tira o acesso sem apagar nenhum histórico."
                actions={(
                    <button type="button" className="button" onClick={openForm}>
                        <FiUserPlus aria-hidden="true"/>
                        Novo prescritor
                    </button>
                )}
            />

            {notice && <p className="aviso" role="status">{notice}</p>}
            {pageError && <p className="aviso aviso--atencao" role="alert">{pageError}</p>}

            {isLoading && <LoadingList/>}

            {!isLoading && !pageError && prescribers.length === 0 && (
                <EmptyState
                    icon={FiUsers}
                    message="Nenhum prescritor cadastrado ainda."
                    action={(
                        <button type="button" className="button" onClick={openForm}>
                            Cadastrar o primeiro
                        </button>
                    )}
                />
            )}

            {!isLoading && prescribers.length > 0 && (
                <ul className={styles.list}>
                    {prescribers.map((prescriber) => (
                        <PrescriberRow
                            key={prescriber.id}
                            prescriber={prescriber}
                            isBusy={busyIds.includes(prescriber.id)}
                            onResetPassword={openReset}
                            onChangeEmail={openEmailChange}
                            onFixData={openFix}
                            onToggle={setPrescriberToToggle}
                        />
                    ))}
                </ul>
            )}

            <Modal show={isFormOpen} title="Novo prescritor" onClickClose={closeForm}>
                <form className={styles.form} onSubmit={handleCreate} ref={formRef}>
                    {formError && (
                        <p className={"aviso aviso--atencao " + styles.fieldWide} role="alert">{formError}</p>
                    )}

                    <FormField label="Nome completo" fieldName="name" value={formData.name}
                               error={fieldErrors.name} onChange={updateField} isWide={true}/>
                    <FormField label="E-mail" fieldName="email" type="email" value={formData.email}
                               error={fieldErrors.email} onChange={updateField}/>
                    <FormField label="CPF" fieldName="cpf" value={formData.cpf}
                               error={fieldErrors.cpf} onChange={updateField}/>
                    <FormField label="Data de nascimento (opcional)" fieldName="birthDate" type="date"
                               value={formData.birthDate} error={fieldErrors.birthDate} onChange={updateField}
                               isRequired={false}/>
                    <FormField label="Telefone (opcional)" fieldName="phone" type="tel" value={formData.phone}
                               error={fieldErrors.phone} onChange={updateField} isRequired={false}/>
                    <FormField label="Profissão" fieldName="profession" value={formData.profession}
                               error={fieldErrors.profession} onChange={updateField}/>

                    <div className={styles.field}>
                        <label htmlFor="registryType">Conselho</label>
                        <select
                            id="registryType"
                            className={styles.select}
                            value={formData.registryType}
                            onChange={(event) => updateField("registryType", event.target.value)}
                        >
                            {REGISTRY_TYPES.map((registryType) => (
                                <option key={registryType} value={registryType}>{registryType}</option>
                            ))}
                        </select>
                    </div>

                    <FormField label="Número do registro" fieldName="registryNumber" value={formData.registryNumber}
                               error={fieldErrors.registryNumber} onChange={updateField}/>

                    <p className={styles.details + " " + styles.fieldWide}>
                        Você não escolhe a senha. A pessoa recebe um link e cria a própria senha no primeiro acesso.
                    </p>

                    <div className={styles.formActions}>
                        <button type="button" className="button-secondary" onClick={closeForm} disabled={isSaving}>
                            Cancelar
                        </button>
                        <BusyButton type="submit" className="button" isBusy={isSaving} busyText="Criando...">
                            Criar conta
                        </BusyButton>
                    </div>
                </form>
            </Modal>

            <Modal
                show={resetTarget !== null}
                title={resetTarget === null ? "" : "Senha nova de " + resetTarget.name}
                onClickClose={closeReset}
            >
                {newLink === null ? (
                    <form className={styles.form} onSubmit={handleReset} ref={resetFormRef}>
                        {/* quem nunca criou a senha n tem "senha atual" pra continuar valendo (#154) */}
                        <p className={styles.fieldWide}>
                            {resetTargetHasPassword
                                ? "Isso cria um link de senha nova e derruba os links anteriores dessa pessoa."
                                    + " A senha atual dela continua valendo até ela abrir o link."
                                : "Essa conta ainda não tem senha. Isso cria um link para a pessoa criar a primeira"
                                    + " senha dela e derruba os links anteriores, como o de primeiro acesso."}
                        </p>
                        {resetError && (
                            <p className={"aviso aviso--atencao " + styles.fieldWide} role="alert">{resetError}</p>
                        )}

                        <div className={styles.fieldWide}>
                            <PasswordField
                                label="Sua senha de administrador"
                                name="adminPassword"
                                value={adminPassword}
                                error={resetFieldErrors.adminPassword}
                                onChange={(event) => setAdminPassword(event.target.value)}
                                autoComplete="current-password"
                                required={true}
                            />
                        </div>

                        <div className={styles.formActions}>
                            <button type="button" className="button-secondary" onClick={closeReset}
                                    disabled={isResetting}>
                                Cancelar
                            </button>
                            <BusyButton type="submit" className="button" isBusy={isResetting} busyText="Gerando...">
                                Gerar link
                            </BusyButton>
                        </div>
                    </form>
                ) : (
                    <LinkResult
                        link={newLink.resetLink}
                        sentText={(resetTargetHasPassword
                                ? "O link de senha nova foi enviado para "
                                : "O link para criar a senha foi enviado para ")
                            + resetTarget.email + ". Ele vale "
                            + validityText(newLink.validMinutes) + " e serve uma vez só. Se não chegar, peça para"
                            + " a pessoa conferir a caixa de spam"
                            + (resetTarget.passwordCreated ? "." : " ou, se o endereço estiver errado, use Corrigir e-mail.")}
                        handText={"Entregue este link para " + newLink.prescriberName + ". Ele vale "
                            + validityText(newLink.validMinutes) + " e serve uma vez só."}
                        isCopied={isLinkCopied}
                        onCopy={copyLink}
                        onClose={closeReset}
                    />
                )}
            </Modal>

            <Modal
                show={emailTarget !== null}
                title={emailTarget === null ? "" : "Corrigir e-mail de " + emailTarget.name}
                onClickClose={closeEmailChange}
            >
                {emailLink === null ? (
                    <form className={styles.form} onSubmit={handleEmailChange} ref={emailFormRef}>
                        <p className={styles.fieldWide}>
                            Serve para quando o e-mail foi digitado errado e o link de criar a senha não chegou.
                            O link sai de novo para o endereço certo, e o anterior deixa de valer.
                        </p>
                        {emailError && (
                            <p className={"aviso aviso--atencao " + styles.fieldWide} role="alert">{emailError}</p>
                        )}

                        <FormField
                            label="E-mail certo"
                            fieldName="newEmail"
                            type="email"
                            value={newEmail}
                            error={emailFieldErrors.email}
                            onChange={(fieldName, value) => setNewEmail(value)}
                            isWide={true}
                        />
                        <div className={styles.fieldWide}>
                            <PasswordField
                                label="Sua senha de administrador"
                                name="emailAdminPassword"
                                value={emailAdminPassword}
                                error={emailFieldErrors.adminPassword}
                                onChange={(event) => setEmailAdminPassword(event.target.value)}
                                autoComplete="current-password"
                                required={true}
                            />
                        </div>

                        <div className={styles.formActions}>
                            <button type="button" className="button-secondary" onClick={closeEmailChange}
                                    disabled={isChangingEmail}>
                                Cancelar
                            </button>
                            <BusyButton type="submit" className="button" isBusy={isChangingEmail}
                                        busyText="Corrigindo...">
                                Corrigir e reenviar o link
                            </BusyButton>
                        </div>
                    </form>
                ) : (
                    <LinkResult
                        link={emailLink.resetLink}
                        sentText={"O e-mail foi corrigido e o link para criar a senha foi enviado para " + newEmail
                            + ". Ele vale " + validityText(emailLink.validMinutes) + " e serve uma vez só."}
                        handText={"O e-mail foi corrigido. Entregue este link para " + emailLink.prescriberName
                            + " criar a própria senha. Ele vale " + validityText(emailLink.validMinutes)
                            + " e serve uma vez só."}
                        isCopied={isLinkCopied}
                        onCopy={copyLink}
                        onClose={closeEmailChange}
                    />
                )}
            </Modal>

            <Modal
                show={fixTarget !== null}
                title={fixTarget === null ? "" : "Corrigir dados de " + fixTarget.name}
                onClickClose={closeFix}
            >
                <form className={styles.form} onSubmit={handleFix} ref={fixFormRef}>
                    <p className={styles.fieldWide}>
                        Serve para corrigir o registro profissional, a profissão ou o CPF digitados errado.
                        O CPF guardado não aparece aqui: preencha só se for corrigir.
                    </p>
                    {fixError && <p className={"aviso aviso--atencao " + styles.fieldWide} role="alert">{fixError}</p>}

                    <FormField label="CPF (só se for corrigir)" fieldName="fixCpf" value={fixData.cpf}
                               error={fixFieldErrors.cpf} isRequired={false}
                               onChange={(fieldName, value) => updateFixField("cpf", value)}/>
                    <FormField label="Profissão" fieldName="fixProfession" value={fixData.profession}
                               error={fixFieldErrors.profession}
                               onChange={(fieldName, value) => updateFixField("profession", value)}/>

                    <div className={styles.field}>
                        <label htmlFor="fixRegistryType">Conselho</label>
                        <select
                            id="fixRegistryType"
                            className={styles.select}
                            value={fixData.registryType}
                            onChange={(event) => updateFixField("registryType", event.target.value)}
                        >
                            {fixRegistryTypes.map((registryType) => (
                                <option key={registryType} value={registryType}>{registryType}</option>
                            ))}
                        </select>
                    </div>

                    <FormField label="Número do registro" fieldName="fixRegistryNumber" value={fixData.registryNumber}
                               error={fixFieldErrors.registryNumber}
                               onChange={(fieldName, value) => updateFixField("registryNumber", value)}/>

                    <div className={styles.formActions}>
                        <button type="button" className="button-secondary" onClick={closeFix} disabled={isFixing}>
                            Cancelar
                        </button>
                        <BusyButton type="submit" className="button" isBusy={isFixing} busyText="Salvando...">
                            Salvar correção
                        </BusyButton>
                    </div>
                </form>
            </Modal>

            <Modal show={firstAccess !== null} title="Conta criada" onClickClose={() => setFirstAccess(null)}>
                {firstAccess !== null && (
                    <LinkResult
                        link={firstAccess.resetLink}
                        sentText={"A conta de " + firstAccess.prescriberName + " foi criada e o link para criar a"
                            + " senha foi enviado para " + firstAccess.email + ". Ele vale "
                            + validityText(firstAccess.validMinutes) + " e serve uma vez só. Se não chegar, confira"
                            + " o endereço em Corrigir e-mail. Se vencer, gere outro em Senha nova."}
                        handText={"A conta de " + firstAccess.prescriberName + " foi criada. Entregue este link"
                            + " para a pessoa criar a própria senha. Ele vale "
                            + validityText(firstAccess.validMinutes) + " e serve uma vez só. Se vencer, gere"
                            + " outro em Senha nova."}
                        isCopied={isLinkCopied}
                        onCopy={copyLink}
                        onClose={() => setFirstAccess(null)}
                    />
                )}
            </Modal>

            <ConfirmModal
                show={prescriberToToggle !== null}
                title={isDeactivating ? "Desativar prescritor" : "Reativar prescritor"}
                message={
                    isDeactivating
                        ? "A pessoa perde o acesso na hora e o acompanhamento automático dos pacientes dela fica"
                            + " pausado. As escalas esperando resposta e as consultas marcadas ficam como estão,"
                            + " sem lembrete para os pacientes enquanto a conta estiver desativada. Os pacientes e"
                            + " o histórico continuam guardados."
                        : "A pessoa volta a conseguir entrar no sistema. O acompanhamento automático dos pacientes"
                            + " continua de onde parou, e os lembretes de escala e de consulta voltam a sair."
                }
                confirmText={isDeactivating ? "Sim, desativar" : "Sim, reativar"}
                cancelText="Voltar"
                onConfirm={confirmToggle}
                onCancel={() => setPrescriberToToggle(null)}
            />
        </section>
    );
}
