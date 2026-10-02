import {useCallback, useEffect, useState} from "react";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {initialsOf} from "../../utils/initials.js";
import accountStyles from "./admin-prescribers.module.css";
import styles from "./admin-patients.module.css";

// uma conta de paciente com o botao de ligar ou desligar o acesso
function PatientRow({patient, onToggle}) {
    return (
        <li className={accountStyles.row}>
            <span
                className={patient.active
                    ? accountStyles.avatar
                    : accountStyles.avatar + " " + accountStyles.avatarInactive}
                aria-hidden="true"
            >
                {initialsOf(patient.name)}
            </span>
            <div className={accountStyles.rowText}>
                <strong className={accountStyles.name}>{patient.name}</strong>
                <span className={accountStyles.details}>{patient.email}</span>
            </div>
            <div className={accountStyles.rowActions}>
                <span className={patient.active ? accountStyles.badgeActive : accountStyles.badgeInactive}>
                    {patient.active ? "Ativa" : "Desativada"}
                </span>
                <button
                    type="button"
                    className={patient.active ? "button-danger button-small" : "button-secondary button-small"}
                    onClick={() => onToggle(patient)}
                >
                    {patient.active ? "Desativar" : "Reativar"}
                </button>
            </div>
        </li>
    );
}

// contas de paciente pro administrador
// ele acha a conta pelo e-mail e liga ou desliga o acesso, sem ver nada do prontuario
export default function AdminPatients() {
    const [email, setEmail] = useState("");
    const [foundPatient, setFoundPatient] = useState(null);
    const [searchError, setSearchError] = useState(null);
    const [isSearching, setIsSearching] = useState(false);

    const [deactivatedPatients, setDeactivatedPatients] = useState([]);
    const [notice, setNotice] = useState(null);
    const [pageError, setPageError] = useState(null);
    const [patientToToggle, setPatientToToggle] = useState(null);

    const loadDeactivated = useCallback(() => {
        return apiService.get("/admin/patients/deactivated")
            .then((patientList) => {
                setDeactivatedPatients(patientList);
                setPageError(null);
            })
            .catch((requestError) => {
                if (requestError instanceof ApiError) {
                    setPageError(requestError.message);
                } else {
                    setPageError("Não foi possível carregar as contas desativadas.");
                }
            });
    }, []);

    useEffect(() => {
        loadDeactivated();
    }, [loadDeactivated]);

    const handleSearch = async (event) => {
        event.preventDefault();
        setIsSearching(true);
        setSearchError(null);
        setFoundPatient(null);
        setNotice(null);
        try {
            const patient = await apiService.post("/admin/patients/lookup", {email: email});
            setFoundPatient(patient);
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                setSearchError(requestError.fieldErrors().email || requestError.message);
            } else {
                setSearchError("Não foi possível falar com o servidor.");
            }
        } finally {
            setIsSearching(false);
        }
    };

    const confirmToggle = async () => {
        const patient = patientToToggle;
        setPatientToToggle(null);
        try {
            const savedPatient = await apiService.put("/admin/patients/" + patient.id + "/active", {
                active: !patient.active,
            });
            if (patient.active) {
                setNotice("Acesso de " + patient.name + " desativado.");
            } else {
                setNotice("Acesso de " + patient.name + " reativado.");
            }
            if (foundPatient && foundPatient.id === savedPatient.id) {
                setFoundPatient(savedPatient);
            }
            await loadDeactivated();
        } catch (requestError) {
            setNotice(null);
            if (requestError instanceof ApiError) {
                setPageError(requestError.message);
            } else {
                setPageError("Não foi possível alterar a conta.");
            }
        }
    };

    const isDeactivating = patientToToggle !== null && patientToToggle.active;
    // a conta achada na busca n aparece duas vezes na tela
    const otherDeactivated = deactivatedPatients.filter(
        (patient) => !foundPatient || patient.id !== foundPatient.id,
    );

    return (
        <section className={styles.page}>
            <PageHeader
                title="Pacientes"
                subtitle="Desativar tira o acesso da conta na hora. O prontuário continua guardado, e arquivar o paciente continua sendo com o prescritor."
            />

            {notice && <p className="aviso" role="status">{notice}</p>}
            {pageError && <p className="aviso aviso--atencao" role="alert">{pageError}</p>}

            <form className={styles.search} onSubmit={handleSearch}>
                <div className={accountStyles.field + " " + styles.searchField}>
                    <label htmlFor="patientEmail">E-mail da conta do paciente</label>
                    <input
                        id="patientEmail"
                        name="patientEmail"
                        type="email"
                        autoComplete="off"
                        value={email}
                        required={true}
                        aria-invalid={searchError ? "true" : undefined}
                        onChange={(event) => setEmail(event.target.value)}
                    />
                </div>
                <button type="submit" className="button" disabled={isSearching}>
                    {isSearching ? "Buscando..." : "Buscar"}
                </button>
            </form>
            {searchError && <p className="aviso aviso--atencao" role="alert">{searchError}</p>}

            {foundPatient && (
                <ul className={accountStyles.list}>
                    <PatientRow patient={foundPatient} onToggle={setPatientToToggle}/>
                </ul>
            )}

            {otherDeactivated.length > 0 && (
                <div className={styles.section}>
                    <h2 className={styles.sectionTitle}>Contas desativadas</h2>
                    <ul className={accountStyles.list}>
                        {otherDeactivated.map((patient) => (
                            <PatientRow key={patient.id} patient={patient} onToggle={setPatientToToggle}/>
                        ))}
                    </ul>
                </div>
            )}

            <ConfirmModal
                show={patientToToggle !== null}
                title={isDeactivating ? "Desativar conta de paciente" : "Reativar conta de paciente"}
                message={
                    isDeactivating
                        ? "A pessoa sai do sistema na hora e não consegue mais entrar. O prontuário continua guardado."
                        : "A pessoa volta a conseguir entrar com a mesma senha."
                }
                confirmText={isDeactivating ? "Sim, desativar" : "Sim, reativar"}
                cancelText="Voltar"
                onConfirm={confirmToggle}
                onCancel={() => setPatientToToggle(null)}
            />
        </section>
    );
}
