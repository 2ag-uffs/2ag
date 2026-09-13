import {useCallback, useEffect, useState} from "react";
import Modal from "../../components/modal/modal.jsx";
import ModalConfirmacao from "../../components/modal/modal-confirmacao.jsx";
import {apiService, ApiError} from "../../services/api.js";
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

        // a api ainda chama a senha de senha e espera cpf e telefone so com numeros
        // campo opcional vazio vai como null
        const requestBody = {
            name: formData.name,
            email: formData.email,
            senha: formData.password,
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
                "Conta de " + createdPrescriber.name + " criada. Código de vínculo para os pacientes: "
                + createdPrescriber.professionalCode,
            );
            await loadPrescribers();
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                const errorsByField = requestError.fieldErrors();
                // o formulario chama o campo de password e a api chama de senha
                if (errorsByField.senha) {
                    errorsByField.password = errorsByField.senha;
                }
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
            <div className={styles.pageHeader}>
                <div>
                    <h1>Prescritores</h1>
                    <p className={styles.subtitle}>
                        Contas de quem atende na clínica. Desativar tira o acesso sem apagar nenhum histórico.
                    </p>
                </div>
                <button type="button" className={styles.primaryButton} onClick={openForm}>
                    Novo prescritor
                </button>
            </div>

            {notice && <p className="aviso">{notice}</p>}
            {pageError && <p className="aviso aviso--atencao">{pageError}</p>}
            {isLoading && <p>Carregando...</p>}

            {!isLoading && !pageError && prescribers.length === 0 && (
                <p className={styles.emptyState}>Nenhum prescritor cadastrado ainda.</p>
            )}

            <ul className={styles.list}>
                {prescribers.map((prescriber) => (
                    <li key={prescriber.id} className={styles.card}>
                        <div className={styles.cardInfo}>
                            <strong>{prescriber.name}</strong>
                            <span>{prescriber.email}</span>
                            <span>
                                {prescriber.registryType} {prescriber.registryNumber} · {prescriber.profession}
                            </span>
                            <span>
                                Código de vínculo: <strong>{prescriber.professionalCode}</strong>
                            </span>
                        </div>
                        <div className={styles.cardActions}>
                            <span className={prescriber.active ? styles.badgeActive : styles.badgeInactive}>
                                {prescriber.active ? "Ativo" : "Desativado"}
                            </span>
                            <button
                                type="button"
                                className={styles.secondaryButton}
                                onClick={() => setPrescriberToToggle(prescriber)}
                            >
                                {prescriber.active ? "Desativar" : "Reativar"}
                            </button>
                        </div>
                    </li>
                ))}
            </ul>

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

                    <p className={styles.formHint}>
                        A senha precisa ter pelo menos 8 caracteres, com letra maiúscula, letra minúscula,
                        número e um destes símbolos: @ $ ! % * ? &amp;
                    </p>

                    <div className={styles.formActions}>
                        <button type="button" className={styles.secondaryButton} onClick={closeForm} disabled={isSaving}>
                            Cancelar
                        </button>
                        <button type="submit" className={styles.primaryButton} disabled={isSaving}>
                            {isSaving ? "Criando..." : "Criar conta"}
                        </button>
                    </div>
                </form>
            </Modal>

            <ModalConfirmacao
                show={prescriberToToggle !== null}
                titulo={isDeactivating ? "Desativar prescritor" : "Reativar prescritor"}
                mensagem={
                    isDeactivating
                        ? "A pessoa perde o acesso na hora. Os pacientes e o histórico continuam guardados."
                        : "A pessoa volta a conseguir entrar no sistema."
                }
                textoConfirmar={isDeactivating ? "Sim, desativar" : "Sim, reativar"}
                textoCancelar="Voltar"
                onConfirmar={confirmToggle}
                onCancelar={() => setPrescriberToToggle(null)}
            />
        </section>
    );
}
