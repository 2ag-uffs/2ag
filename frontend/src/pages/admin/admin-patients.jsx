import {useCallback, useEffect, useState} from "react";
import {FiSearch} from "react-icons/fi";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import EmptyState from "../../components/empty-state/empty-state.jsx";
import BusyButton from "../../components/form/busy-button.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import {apiService, ApiError, CONNECTION_ERROR_MESSAGE, errorMessageOf} from "../../services/api.js";
import {initialsOf} from "../../utils/initials.js";
import accountStyles from "./admin-prescribers.module.css";
import styles from "./admin-patients.module.css";

// uma conta de paciente com o botao de ligar ou desligar o acesso
// enquanto a api responde a linha fica ocupada e o botao n aceita outro clique
function PatientRow({patient, isBusy, onToggle}) {
    return (
        <li className={accountStyles.row} aria-busy={isBusy ? "true" : undefined}>
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
                <span className={accountStyles.details}>
                    {patient.civilName ? "Nome civil: " + patient.civilName + " · " : ""}
                    {patient.email}
                </span>
            </div>
            <div className={accountStyles.rowActions}>
                <span className={patient.active ? "etiqueta etiqueta--ok" : "etiqueta etiqueta--anulada"}>
                    {patient.active ? "Ativa" : "Desativada"}
                </span>
                <BusyButton
                    type="button"
                    className={patient.active ? "button-danger button-small" : "button-secondary button-small"}
                    isBusy={isBusy}
                    busyText={patient.active ? "Desativando..." : "Reativando..."}
                    onClick={() => onToggle(patient)}
                >
                    {patient.active ? "Desativar" : "Reativar"}
                </BusyButton>
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
    // o campo so fica vermelho qnd o e-mail esta mal digitado
    const [isEmailWrong, setIsEmailWrong] = useState(false);
    // busca sem resultado n eh erro de quem digitou, entao vira o aviso neutro da lista vazia
    const [notFoundText, setNotFoundText] = useState(null);
    const [isSearching, setIsSearching] = useState(false);

    const [deactivatedPatients, setDeactivatedPatients] = useState([]);
    const [notice, setNotice] = useState(null);
    const [pageError, setPageError] = useState(null);
    const [patientToToggle, setPatientToToggle] = useState(null);
    // contas com o pedido de desativar ou reativar ainda sem resposta
    const [busyIds, setBusyIds] = useState([]);

    const loadDeactivated = useCallback(() => {
        return apiService.get("/admin/patients/deactivated")
            .then((patientList) => {
                setDeactivatedPatients(patientList);
                setPageError(null);
            })
            .catch((requestError) => {
                setPageError(errorMessageOf(requestError,
                    "Não foi possível carregar as contas desativadas. Confira sua internet e tente de novo."));
            });
    }, []);

    useEffect(() => {
        loadDeactivated();
    }, [loadDeactivated]);

    const handleSearch = async (event) => {
        event.preventDefault();
        setIsSearching(true);
        setSearchError(null);
        setIsEmailWrong(false);
        setNotFoundText(null);
        setFoundPatient(null);
        setNotice(null);
        // a busca nova limpa o aviso da acao anterior
        setPageError(null);
        try {
            const patient = await apiService.post("/admin/patients/lookup", {email: email});
            setFoundPatient(patient);
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                const emailError = requestError.fieldErrors().email;
                if (emailError) {
                    setSearchError(emailError);
                    setIsEmailWrong(true);
                } else if (requestError.status === 404) {
                    setNotFoundText(requestError.message);
                } else {
                    setSearchError(requestError.message);
                }
            } else {
                setSearchError(CONNECTION_ERROR_MESSAGE);
            }
        } finally {
            setIsSearching(false);
        }
    };

    const confirmToggle = async () => {
        const patient = patientToToggle;
        setPatientToToggle(null);
        setBusyIds((currentIds) => [...currentIds, patient.id]);
        try {
            const savedPatient = await apiService.put("/admin/patients/" + patient.id + "/active", {
                active: !patient.active,
            });
            if (patient.active) {
                setNotice("Acesso de " + patient.name + " desativado.");
            } else {
                setNotice("Acesso de " + patient.name + " reativado.");
            }
            // a busca fica livre enquanto a linha esta ocupada, entao a conta achada eh a de agora
            setFoundPatient((currentPatient) => {
                if (currentPatient && currentPatient.id === savedPatient.id) {
                    return savedPatient;
                }
                return currentPatient;
            });
            await loadDeactivated();
        } catch (requestError) {
            setNotice(null);
            if (requestError instanceof ApiError) {
                // o 400 dessa rota eh a conta q ja esta no estado pedido (outra janela ou outra pessoa
                // mudou antes), entao a linha passa a mostrar ele. a lista recarrega em qualquer recusa
                if (requestError.status === 400) {
                    setFoundPatient((currentPatient) => {
                        if (currentPatient && currentPatient.id === patient.id) {
                            return {...currentPatient, active: !patient.active};
                        }
                        return currentPatient;
                    });
                }
                await loadDeactivated();
            }
            setPageError(errorMessageOf(requestError,
                "Não foi possível alterar a conta. Confira sua internet e tente de novo."));
        } finally {
            setBusyIds((currentIds) => currentIds.filter((busyId) => busyId !== patient.id));
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
                subtitle="Desativar tira o acesso da conta na hora. O prontuário continua guardado, e arquivar o paciente continua sendo com o prescritor. A busca pelo e-mail e a lista das contas desativadas ficam registradas na auditoria."
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
                        aria-invalid={isEmailWrong ? "true" : undefined}
                        onChange={(event) => setEmail(event.target.value)}
                    />
                </div>
                <BusyButton type="submit" className="button" isBusy={isSearching} busyText="Buscando...">
                    Buscar
                </BusyButton>
            </form>
            {searchError && <p className="aviso aviso--atencao" role="alert">{searchError}</p>}
            {notFoundText && (
                <div role="status">
                    <EmptyState icon={FiSearch} message={notFoundText}/>
                </div>
            )}

            {foundPatient && (
                <ul className={accountStyles.list}>
                    <PatientRow
                        patient={foundPatient}
                        isBusy={busyIds.includes(foundPatient.id)}
                        onToggle={setPatientToToggle}
                    />
                </ul>
            )}

            {otherDeactivated.length > 0 && (
                <div className={styles.section}>
                    <h2 className={styles.sectionTitle}>Contas desativadas</h2>
                    <ul className={accountStyles.list}>
                        {otherDeactivated.map((patient) => (
                            <PatientRow
                                key={patient.id}
                                patient={patient}
                                isBusy={busyIds.includes(patient.id)}
                                onToggle={setPatientToToggle}
                            />
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
                danger={isDeactivating}
                cancelText="Voltar"
                onConfirm={confirmToggle}
                onCancel={() => setPatientToToggle(null)}
            />
        </section>
    );
}
