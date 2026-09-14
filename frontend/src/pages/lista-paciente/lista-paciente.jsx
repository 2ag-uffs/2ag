import {useEffect, useState} from "react";
import {useNavigate} from "react-router";
import {FiSearch, FiUsers} from "react-icons/fi";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import EmptyState from "../../components/empty-state/empty-state.jsx";
import InvitePatientModal from "../../components/invite-patient-modal/invite-patient-modal.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import {SkeletonBlock} from "../../components/skeleton/skeleton.jsx";
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

// as iniciais do primeiro e do ultimo nome, pro circulo ao lado do nome
function initialsOf(name) {
    const words = name.split(" ").filter((word) => word !== "");
    if (words.length === 0) {
        return "?";
    }
    const lastWord = words.length > 1 ? words[words.length - 1] : "";
    return (words[0].charAt(0) + lastWord.charAt(0)).toUpperCase();
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

        apiService.get("/patients?archived=" + showArchived)
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
            await apiService.put("/patients/" + patient.id + "/archive");
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
            await apiService.put("/patients/" + patient.id + "/reactivate");
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
            : "Você ainda não tem pacientes ativos. Envie o link de cadastro para o primeiro.";
    }

    return (
        <section className={styles.page}>
            <PageHeader
                title="Meus pacientes"
                subtitle="Abra o histórico para ver consultas, prescrições, anamnese e escalas do paciente."
                actions={(
                    <button type="button" className="button" onClick={() => setIsInviteOpen(true)}>
                        Convidar paciente
                    </button>
                )}
            />

            <div className={styles.toolbar}>
                <div className={styles.tabs} role="group" aria-label="Quais pacientes mostrar">
                    <button
                        type="button"
                        className={showArchived ? styles.tab : styles.tab + " " + styles.tabActive}
                        aria-pressed={!showArchived}
                        onClick={() => changeList(false)}
                    >
                        Ativos
                    </button>
                    <button
                        type="button"
                        className={showArchived ? styles.tab + " " + styles.tabActive : styles.tab}
                        aria-pressed={showArchived}
                        onClick={() => changeList(true)}
                    >
                        Arquivados
                    </button>
                </div>
                <label className={styles.searchBox}>
                    <FiSearch className={styles.searchIcon} aria-hidden="true"/>
                    <input
                        type="search"
                        className={styles.search}
                        placeholder="Buscar pelo nome"
                        aria-label="Buscar paciente pelo nome"
                        value={searchTerm}
                        onChange={(event) => setSearchTerm(event.target.value)}
                    />
                </label>
            </div>

            {notice && <p className="aviso" role="status">{notice}</p>}
            {actionError && <p className="aviso aviso--atencao" role="alert">{actionError}</p>}

            {isLoading && (
                <ul className={styles.list} role="status" aria-label="Carregando pacientes">
                    {[0, 1, 2, 3].map((index) => (
                        <li key={index} className={styles.row}>
                            <div className={styles.patient}>
                                <SkeletonBlock width="2.75rem" height="2.75rem" isRound={true}/>
                                <div className={styles.patientText}>
                                    <SkeletonBlock width="12rem" height="1rem"/>
                                    <SkeletonBlock width="7rem" height="0.875rem"/>
                                </div>
                            </div>
                        </li>
                    ))}
                </ul>
            )}

            {!isLoading && loadError && <p className="aviso aviso--atencao">{loadError}</p>}

            {!isLoading && !loadError && visiblePatients.length === 0 && (
                <EmptyState
                    icon={FiUsers}
                    message={emptyMessage}
                    action={patients.length === 0 && !showArchived ? (
                        <button type="button" className="button" onClick={() => setIsInviteOpen(true)}>
                            Convidar paciente
                        </button>
                    ) : null}
                />
            )}

            {!isLoading && !loadError && visiblePatients.length > 0 && (
                <ul className={styles.list}>
                    {visiblePatients.map((patient) => (
                        <li key={patient.id} className={styles.row}>
                            <div className={styles.patient}>
                                <span
                                    className={patient.archived ? styles.avatar + " " + styles.avatarArchived : styles.avatar}
                                    aria-hidden="true"
                                >
                                    {initialsOf(patient.name)}
                                </span>
                                <div className={styles.patientText}>
                                    <h2 className={styles.patientName}>{patient.name}</h2>
                                    <p className={styles.details}>{patientDetailsOf(patient)}</p>
                                </div>
                            </div>
                            <div className={styles.actions}>
                                <button
                                    type="button"
                                    className="button button-small"
                                    onClick={() => navigate("/paciente/" + patient.id + "/historico")}
                                >
                                    Abrir histórico
                                </button>
                                {!patient.archived && (
                                    <>
                                        <button
                                            type="button"
                                            className="button-secondary button-small"
                                            onClick={() => navigate("/paciente/" + patient.id + "/consulta/nova")}
                                        >
                                            Nova consulta
                                        </button>
                                        <button
                                            type="button"
                                            className="button-tertiary button-small"
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
                                        className="button-secondary button-small"
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

            <ConfirmModal
                show={patientToArchive !== null}
                title="Arquivar paciente"
                message={patientToArchive
                    ? patientToArchive.name + " sai da lista de ativos e o acompanhamento automático é encerrado."
                    + " O prontuário fica guardado, as escalas que você enviar continuam chegando"
                    + " e você pode reativar quando quiser."
                    : ""}
                confirmText="Arquivar"
                cancelText="Voltar"
                onConfirm={archivePatient}
                onCancel={() => setPatientToArchive(null)}
            />
            <InvitePatientModal show={isInviteOpen} onClose={() => setIsInviteOpen(false)}/>
        </section>
    );
}
