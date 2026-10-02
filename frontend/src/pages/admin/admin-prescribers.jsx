import {useCallback, useEffect, useState} from "react";
import {FiUserPlus, FiUsers} from "react-icons/fi";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import EmptyState from "../../components/empty-state/empty-state.jsx";
import Modal from "../../components/modal/modal.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import {SkeletonBlock} from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {initialsOf} from "../../utils/initials.js";
import styles from "./admin-prescribers.module.css";

// conselhos profissionais aceitos no cadastro
const REGISTRY_TYPES = ["CRBM", "CRM", "COREN", "CRP", "CRF", "CRN"];

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

// 30 vira 30 minutos e 2880 vira 48 horas
function validityText(minutes) {
    if (minutes >= 120 && minutes % 60 === 0) {
        return minutes / 60 + " horas";
    }
    return minutes + " minutos";
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
function FormField({
    label, fieldName, value, error, onChange,
    type = "text", autoComplete = "off", isWide = false, isRequired = true,
}) {
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
                onChange={(event) => onChange(fieldName, event.target.value)}
            />
            {error && <span className={styles.fieldError}>{error}</span>}
        </div>
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

    // conta recem criada com o link de primeiro acesso, ou sem ele qnd foi por e-mail
    const [firstAccess, setFirstAccess] = useState(null);

    const [prescriberToToggle, setPrescriberToToggle] = useState(null);

    // senha nova de um prescritor: pede a senha do admin e o link vem na resposta ou vai por e-mail
    const [resetTarget, setResetTarget] = useState(null);
    const [adminPassword, setAdminPassword] = useState("");
    const [resetError, setResetError] = useState(null);
    const [isResetting, setIsResetting] = useState(false);
    const [newLink, setNewLink] = useState(null);
    const [isLinkCopied, setIsLinkCopied] = useState(false);

    // devolve a promessa pra quem cria ou desativa prescritor poder esperar a lista nova
    const loadPrescribers = useCallback(() => {
        return apiService.get("/admin/prescribers")
            .then((prescriberList) => {
                setPrescribers(prescriberList);
                setPageError(null);
            })
            .catch((requestError) => {
                if (requestError instanceof ApiError) {
                    setPageError(requestError.message);
                } else {
                    setPageError("Não foi possível carregar os prescritores.");
                }
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
        setIsSaving(true);
        setFieldErrors({});
        setFormError(null);

        // a api espera cpf e telefone so com numeros
        // campo opcional vazio vai como null
        const requestBody = {
            name: formData.name,
            email: formData.email,
            cpf: formData.cpf.replace(/\D/g, ""),
            birthDate: formData.birthDate || null,
            phone: formData.phone.replace(/\D/g, "") || null,
            profession: formData.profession,
            registryType: formData.registryType,
            registryNumber: formData.registryNumber,
        };

        try {
            const createdAccount = await apiService.post("/admin/prescribers", requestBody);
            setIsFormOpen(false);
            setNotice(null);
            setIsLinkCopied(false);
            setFirstAccess({...createdAccount, email: formData.email.trim()});
            await loadPrescribers();
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                const errorsByField = requestError.fieldErrors();
                setFieldErrors(errorsByField);
                if (Object.keys(errorsByField).length === 0) {
                    setFormError(requestError.message);
                }
            } else {
                setFormError("Não foi possível falar com o servidor.");
            }
        } finally {
            setIsSaving(false);
        }
    };

    const confirmToggle = async () => {
        const prescriber = prescriberToToggle;
        setPrescriberToToggle(null);
        try {
            await apiService.put("/admin/prescribers/" + prescriber.id + "/active", {active: !prescriber.active});
            if (prescriber.active) {
                setNotice("Acesso de " + prescriber.name + " desativado.");
            } else {
                setNotice("Acesso de " + prescriber.name + " reativado.");
            }
            await loadPrescribers();
        } catch (requestError) {
            setNotice(null);
            if (requestError instanceof ApiError) {
                setPageError(requestError.message);
            } else {
                setPageError("Não foi possível alterar a conta.");
            }
        }
    };

    const openReset = (prescriber) => {
        setResetTarget(prescriber);
        setAdminPassword("");
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
        setResetError(null);
        try {
            const link = await apiService.post("/admin/prescribers/" + resetTarget.id + "/password-reset", {
                adminPassword,
            });
            setNewLink(link);
            setAdminPassword("");
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                setResetError(requestError.fieldErrors().adminPassword || requestError.message);
            } else {
                setResetError("Não foi possível falar com o servidor.");
            }
        } finally {
            setIsResetting(false);
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

            {isLoading && (
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
            )}

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
                        <li key={prescriber.id} className={styles.row}>
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
                                    <button
                                        type="button"
                                        className="button-secondary button-small"
                                        onClick={() => openReset(prescriber)}
                                    >
                                        Senha nova
                                    </button>
                                )}
                                <button
                                    type="button"
                                    className={prescriber.active ? "button-danger button-small" : "button-secondary button-small"}
                                    onClick={() => setPrescriberToToggle(prescriber)}
                                >
                                    {prescriber.active ? "Desativar" : "Reativar"}
                                </button>
                            </div>
                        </li>
                    ))}
                </ul>
            )}

            <Modal show={isFormOpen} title="Novo prescritor" onClickClose={closeForm}>
                <form className={styles.form} onSubmit={handleCreate}>
                    {formError && <p className={"aviso aviso--atencao " + styles.fieldWide}>{formError}</p>}

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
                        <button type="submit" className="button" disabled={isSaving}>
                            {isSaving ? "Criando..." : "Criar conta"}
                        </button>
                    </div>
                </form>
            </Modal>

            <Modal
                show={resetTarget !== null}
                title={resetTarget === null ? "" : "Senha nova de " + resetTarget.name}
                onClickClose={closeReset}
            >
                {newLink === null ? (
                    <form className={styles.form} onSubmit={handleReset}>
                        <p className={styles.fieldWide}>
                            Isso cria um link de senha nova e derruba os links anteriores dessa pessoa.
                            A senha atual dela continua valendo até ela abrir o link.
                        </p>
                        {resetError && <p className={"aviso aviso--atencao " + styles.fieldWide}>{resetError}</p>}

                        <FormField
                            label="Sua senha de administrador"
                            fieldName="adminPassword"
                            type="password"
                            value={adminPassword}
                            onChange={(fieldName, value) => setAdminPassword(value)}
                            autoComplete="current-password"
                            isWide={true}
                        />

                        <div className={styles.formActions}>
                            <button type="button" className="button-secondary" onClick={closeReset}
                                    disabled={isResetting}>
                                Cancelar
                            </button>
                            <button type="submit" className="button" disabled={isResetting}>
                                {isResetting ? "Gerando..." : "Gerar link"}
                            </button>
                        </div>
                    </form>
                ) : (
                    <LinkResult
                        link={newLink.resetLink}
                        sentText={"O link de senha nova foi enviado para " + resetTarget.email + ". Ele vale "
                            + validityText(newLink.validMinutes) + " e serve uma vez só. Se não chegar, peça para"
                            + " a pessoa conferir a caixa de spam."}
                        handText={"Entregue este link para " + newLink.prescriberName + ". Ele vale "
                            + validityText(newLink.validMinutes) + " e serve uma vez só."}
                        isCopied={isLinkCopied}
                        onCopy={copyLink}
                        onClose={closeReset}
                    />
                )}
            </Modal>

            <Modal show={firstAccess !== null} title="Conta criada" onClickClose={() => setFirstAccess(null)}>
                {firstAccess !== null && (
                    <LinkResult
                        link={firstAccess.resetLink}
                        sentText={"A conta de " + firstAccess.prescriberName + " foi criada e o link para criar a"
                            + " senha foi enviado para " + firstAccess.email + ". Ele vale "
                            + validityText(firstAccess.validMinutes) + " e serve uma vez só. Se não chegar ou"
                            + " vencer, gere outro em Senha nova."}
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
                        ? "A pessoa perde o acesso na hora. Os pacientes e o histórico continuam guardados."
                        : "A pessoa volta a conseguir entrar no sistema."
                }
                confirmText={isDeactivating ? "Sim, desativar" : "Sim, reativar"}
                cancelText="Voltar"
                onConfirm={confirmToggle}
                onCancel={() => setPrescriberToToggle(null)}
            />
        </section>
    );
}
