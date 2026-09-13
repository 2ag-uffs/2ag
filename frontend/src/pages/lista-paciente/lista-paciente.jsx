import {useEffect, useState} from "react";
import {useNavigate} from "react-router";
import InvitePatientModal from "../../components/invite-patient-modal/invite-patient-modal.jsx";
import ModalConfirmacao from "../../components/modal/modal-confirmacao.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {ageFrom, formatDate} from "../../utils/date-format.js";
import styles from "./lista-paciente.module.css";

const CONNECTION_ERROR_MESSAGE = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

function patientDetailsOf(patient) {
    const age = ageFrom(patient.birthDate);
    const ageText = age !== null ? age + " anos" : "Idade não informada";
    if (patient.archived) {
        return ageText + " · no arquivo desde " + formatDate(patient.archivedAt);
    }
    return ageText;
}

// carteira de pacientes do prescritor
// os ativos aparecem primeiro e os arquivados ficam numa aba separada com o prontuario guardado
// o resto das acoes do paciente fica no historico dele
export default function ListaPacientes() {
    const navigate = useNavigate();
    const [showArchived, setShowArchived] = useState(false);
    const [patients, setPatients] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState(null);
    const [notice, setNotice] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [searchTerm, setSearchTerm] = useState("");
    const [isInviteOpen, setIsInviteOpen] = useState(false);
    // paciente q o prescritor escolheu arquivar e q abre a confirmacao
    const [patientToArchive, setPatientToArchive] = useState(null);
    // paciente com pedido em andamento pra n mandar duas vezes
    const [busyPatientId, setBusyPatientId] = useState(null);
    // somar um aqui busca a lista de novo depois de arquivar ou reativar
    const [reloadCount, setReloadCount] = useState(0);

    useEffect(() => {
        let isCurrentRequest = true;

        apiService.get("/paciente?arquivados=" + showArchived)
            .then((patientList) => {
                if (isCurrentRequest) {
                    setPatients(patientList);
                    setLoadError(null);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível carregar seus pacientes. Confira sua internet e tente de novo.");
                }
            })
            .finally(() => {
                if (isCurrentRequest) {
                    setIsLoading(false);
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [showArchived, reloadCount]);

    const changeList = (archived) => {
        if (archived === showArchived) {
            return;
        }
        setIsLoading(true);
        setShowArchived(archived);
    };

    const archivePatient = async () => {
        const patient = patientToArchive;
        setPatientToArchive(null);
        setNotice(null);
        setActionError(null);
        setBusyPatientId(patient.id);
        try {
            await apiService.put("/paciente/" + patient.id + "/arquivamento");
            setNotice(patient.name + " saiu da lista de ativos. O prontuário continua guardado na aba Arquivados.");
            setReloadCount((currentCount) => currentCount + 1);
        } catch (requestError) {
            setActionError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
        } finally {
            setBusyPatientId(null);
        }
    };

    const reactivatePatient = async (patient) => {
        setNotice(null);
        setActionError(null);
        setBusyPatientId(patient.id);
        try {
            await apiService.put("/paciente/" + patient.id + "/reativacao");
            setNotice(patient.name + " voltou para a lista de ativos.");
            setReloadCount((currentCount) => currentCount + 1);
        } catch (requestError) {
            setActionError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
        } finally {
            setBusyPatientId(null);
        }
    };

    const normalizedSearch = searchTerm.trim().toLowerCase();
    const visiblePatients = patients.filter((patient) => patient.name.toLowerCase().includes(normalizedSearch));

    let emptyMessage = "Nenhum paciente encontrado com esse nome.";
    if (patients.length === 0) {
        emptyMessage = showArchived
            ? "Nenhum paciente no arquivo."
            : "Você ainda não tem pacientes ativos. Use Convidar paciente para enviar o link de cadastro.";
    }

    return (
        <section className={styles.page}>
            <div className={styles.header}>
                <div>
                    <h1>Meus pacientes</h1>
                    <p className={styles.subtitle}>
                        Abra o histórico para ver consultas, prescrições, anamnese e escalas do paciente.
                    </p>
                </div>
                <button type="button" className={styles.primaryButton} onClick={() => setIsInviteOpen(true)}>
                    Convidar paciente
                </button>
            </div>

            <div className={styles.toolbar}>
                <div className={styles.filters} role="group" aria-label="Quais pacientes mostrar">
                    <button
                        type="button"
                        className={showArchived ? styles.filterButton : styles.filterButtonActive}
                        aria-pressed={!showArchived}
                        onClick={() => changeList(false)}
                    >
                        Ativos
                    </button>
                    <button
                        type="button"
                        className={showArchived ? styles.filterButtonActive : styles.filterButton}
                        aria-pressed={showArchived}
                        onClick={() => changeList(true)}
                    >
                        Arquivados
                    </button>
                </div>
                <input
                    type="search"
                    className={styles.search}
                    placeholder="Buscar pelo nome"
                    aria-label="Buscar paciente pelo nome"
                    value={searchTerm}
                    onChange={(event) => setSearchTerm(event.target.value)}
                />
            </div>

            {notice && <p className="aviso" role="status">{notice}</p>}
            {actionError && <p className="aviso aviso--atencao" role="alert">{actionError}</p>}

            {isLoading && <p className={styles.status}>Carregando...</p>}
            {!isLoading && loadError && <p className="aviso aviso--atencao">{loadError}</p>}

            {!isLoading && !loadError && visiblePatients.length === 0 && (
                <p className={styles.status}>{emptyMessage}</p>
            )}

            {!isLoading && !loadError && visiblePatients.length > 0 && (
                <ul className={styles.list}>
                    {visiblePatients.map((patient) => (
                        <li key={patient.id} className={styles.card}>
                            <div className={styles.patientInfo}>
                                <span className={styles.avatar} aria-hidden="true">{patient.name.charAt(0)}</span>
                                <div>
                                    <h2 className={styles.patientName}>{patient.name}</h2>
                                    <p className={styles.status}>{patientDetailsOf(patient)}</p>
                                </div>
                            </div>
                            <div className={styles.actions}>
                                <button
                                    type="button"
                                    className={styles.primaryButton}
                                    onClick={() => navigate("/paciente/" + patient.id + "/historico")}
                                >
                                    Abrir histórico
                                </button>
                                {!patient.archived && (
                                    <>
                                        <button
                                            type="button"
                                            className={styles.secondaryButton}
                                            onClick={() => navigate("/paciente/" + patient.id + "/consulta/nova")}
                                        >
                                            Nova consulta
                                        </button>
                                        <button
                                            type="button"
                                            className={styles.secondaryButton}
                                            onClick={() => setPatientToArchive(patient)}
                                            disabled={busyPatientId === patient.id}
                                        >
                                            {busyPatientId === patient.id ? "Arquivando..." : "Arquivar"}
                                        </button>
                                    </>
                                )}
                                {patient.archived && (
                                    <button
                                        type="button"
                                        className={styles.secondaryButton}
                                        onClick={() => reactivatePatient(patient)}
                                        disabled={busyPatientId === patient.id}
                                    >
                                        {busyPatientId === patient.id ? "Reativando..." : "Reativar"}
                                    </button>
                                )}
                            </div>
                        </li>
                    ))}
                </ul>
            )}

            <ModalConfirmacao
                show={patientToArchive !== null}
                titulo="Arquivar paciente"
                mensagem={patientToArchive
                    ? patientToArchive.name + " sai da lista de ativos e o acompanhamento automático é encerrado."
                    + " O prontuário fica guardado, as escalas que você enviar continuam chegando"
                    + " e você pode reativar quando quiser."
                    : ""}
                textoConfirmar="Arquivar"
                textoCancelar="Voltar"
                onConfirmar={archivePatient}
                onCancelar={() => setPatientToArchive(null)}
            />
            <InvitePatientModal show={isInviteOpen} onClose={() => setIsInviteOpen(false)}/>
        </section>
    );
}
