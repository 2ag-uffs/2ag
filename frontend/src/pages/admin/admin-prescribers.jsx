import {useCallback, useEffect, useState} from "react";
import {FiUserPlus, FiUsers} from "react-icons/fi";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import EmptyState from "../../components/empty-state/empty-state.jsx";
import PasswordChecklist from "../../components/form/password-checklist.jsx";
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
    password: "",
};

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

    const [prescriberToToggle, setPrescriberToToggle] = useState(null);

    const loadPrescribers = useCallback(async () => {
        try {
            const prescriberList = await apiService.get("/admin/prescribers");
            setPrescribers(prescriberList);
            setPageError(null);
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                setPageError(requestError.message);
            } else {
                setPageError("Não foi possível carregar os prescritores.");
            }
        } finally {
            setIsLoading(false);
        }
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
            password: formData.password,
            cpf: formData.cpf.replace(/\D/g, ""),
            birthDate: formData.birthDate || null,
            phone: formData.phone.replace(/\D/g, "") || null,
            profession: formData.profession,
            registryType: formData.registryType,
            registryNumber: formData.registryNumber,
        };

        try {
            const createdPrescriber = await apiService.post("/admin/prescribers", requestBody);
            setIsFormOpen(false);
            setNotice(
                "Conta de " + createdPrescriber.name + " criada. Passe o e-mail e a senha inicial para a pessoa entrar.",
            );
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
                    <FormField label="Senha inicial" fieldName="password" type="password" value={formData.password}
                               error={fieldErrors.password} onChange={updateField} autoComplete="new-password"
                               isWide={true}/>

                    <div className={styles.fieldWide}>
                        <PasswordChecklist password={formData.password}/>
                    </div>

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
