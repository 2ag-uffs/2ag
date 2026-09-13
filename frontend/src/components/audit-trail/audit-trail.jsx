import {useEffect, useState} from "react";
import TextField from "../form/text-field.jsx";
import {apiService, ApiError} from "../../services/api.js";
import styles from "./audit-trail.module.css";

// a trilha abre mostrando os ultimos 30 dias
const DEFAULT_PERIOD_DAYS = 30;

const ROLE_LABELS = {
    PATIENT: "Paciente",
    PRESCRIBER: "Prescritor",
    ADMIN: "Administrador",
};

// data no formato do input date sem passar por utc
// o toISOString troca o dia depois das 21h no horario de brasilia
function toInputDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return year + "-" + month + "-" + day;
}

function daysAgo(days) {
    const date = new Date();
    date.setDate(date.getDate() - days);
    return date;
}

function formatMoment(isoDateTime) {
    return new Date(isoDateTime).toLocaleString("pt-BR", {dateStyle: "short", timeStyle: "short"});
}

// lista da trilha de auditoria com filtro de periodo e paginas
// quem usa passa o endereco da api e diz se cada linha mostra o numero do paciente
export default function AuditTrail({endpoint, showPatientNumber}) {
    const [fromDate, setFromDate] = useState(() => toInputDate(daysAgo(DEFAULT_PERIOD_DAYS - 1)));
    const [toDate, setToDate] = useState(() => toInputDate(new Date()));
    const [period, setPeriod] = useState({from: fromDate, to: toDate});
    const [page, setPage] = useState(0);
    const [trail, setTrail] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState(null);

    useEffect(() => {
        // resposta de um filtro antigo q chega atrasada n pode sobrescrever a nova
        let isCurrentRequest = true;
        const path = endpoint + "?from=" + period.from + "&to=" + period.to + "&page=" + page;

        apiService.get(path)
            .then((trailPage) => {
                if (isCurrentRequest) {
                    setTrail(trailPage);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setTrail(null);
                    setErrorMessage(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível carregar a trilha. Confira sua internet e tente de novo.");
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
    }, [endpoint, period, page]);

    const handleFilter = (event) => {
        event.preventDefault();
        if (!fromDate || !toDate) {
            setErrorMessage("Escolha as duas datas do período.");
            return;
        }
        if (fromDate > toDate) {
            setErrorMessage("A data inicial precisa ser igual ou anterior à data final.");
            return;
        }
        setErrorMessage(null);
        setIsLoading(true);
        setPage(0);
        setPeriod({from: fromDate, to: toDate});
    };

    const goToPage = (newPage) => {
        setErrorMessage(null);
        setIsLoading(true);
        setPage(newPage);
    };

    const events = trail ? trail.events : [];

    return (
        <div className={styles.trail}>
            <form className={styles.filter} onSubmit={handleFilter}>
                <TextField
                    label="De"
                    name="audit-from"
                    type="date"
                    value={fromDate}
                    max={toDate || undefined}
                    onChange={(event) => setFromDate(event.target.value)}
                    required={true}
                />
                <TextField
                    label="Até"
                    name="audit-to"
                    type="date"
                    value={toDate}
                    min={fromDate || undefined}
                    onChange={(event) => setToDate(event.target.value)}
                    required={true}
                />
                <button type="submit" className={styles.filterButton}>Filtrar</button>
            </form>

            {errorMessage && <p className="aviso aviso--atencao" role="alert">{errorMessage}</p>}

            {isLoading && <p className={styles.status}>Carregando...</p>}

            {!isLoading && trail && events.length === 0 && (
                <p className={styles.status}>Nenhum registro nesse período.</p>
            )}

            {!isLoading && events.length > 0 && (
                <ul className={styles.list}>
                    {events.map((auditEvent, index) => (
                        <li key={auditEvent.occurredAt + "-" + index} className={styles.item}>
                            <time className={styles.moment} dateTime={auditEvent.occurredAt}>
                                {formatMoment(auditEvent.occurredAt)}
                            </time>
                            <div className={styles.details}>
                                <strong>{auditEvent.operationLabel} · {auditEvent.recordLabel}</strong>
                                <span>
                                    {auditEvent.actorName}
                                    {auditEvent.actorRole && " (" + ROLE_LABELS[auditEvent.actorRole] + ")"}
                                    {showPatientNumber && " · paciente nº " + auditEvent.patientId}
                                </span>
                            </div>
                        </li>
                    ))}
                </ul>
            )}

            {!isLoading && trail && trail.totalPages > 1 && (
                <nav className={styles.pagination} aria-label="Páginas da trilha">
                    <button
                        type="button"
                        className={styles.pageButton}
                        onClick={() => goToPage(page - 1)}
                        disabled={page === 0}
                    >
                        Mais recentes
                    </button>
                    <span>Página {page + 1} de {trail.totalPages}</span>
                    <button
                        type="button"
                        className={styles.pageButton}
                        onClick={() => goToPage(page + 1)}
                        disabled={page + 1 >= trail.totalPages}
                    >
                        Mais antigos
                    </button>
                </nav>
            )}
        </div>
    );
}
