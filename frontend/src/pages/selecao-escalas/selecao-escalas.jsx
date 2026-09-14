import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
import {apiService, ApiError} from "../../services/api.js";
import styles from "./selecao-escalas.module.css";

const CONNECTION_ERROR_MESSAGE = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

// envio avulso de escalas pro paciente (RF09)
//
// a lista do q da pra enviar vem do servidor, entao o mini-exame nem
// aparece aqui: quem aplica eh o prescritor na consulta (RN09)
export default function SelecaoEscalas() {
    const navigate = useNavigate();
    const {patientId} = useParams();

    const [page, setPage] = useState(null);
    const [loadError, setLoadError] = useState(null);
    const [chosen, setChosen] = useState({});
    const [search, setSearch] = useState("");
    const [actionError, setActionError] = useState(null);
    const [isSending, setIsSending] = useState(false);

    useEffect(() => {
        let isCurrentRequest = true;

        Promise.all([
            apiService.get("/paciente/" + patientId),
            apiService.get("/pacientes/" + patientId + "/escalas"),
            apiService.get("/escalas/designaveis"),
        ])
            .then(([patient, tasks, assignable]) => {
                if (isCurrentRequest) {
                    setPage({
                        patientName: patient.name,
                        // as q ainda esperam resposta n entram de novo
                        waiting: tasks.filter((task) => task.status === "PENDENTE")
                            .map((task) => task.scaleType),
                        scales: assignable,
                    });
                    setLoadError(null);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível carregar as escalas. Confira sua internet e tente de novo.");
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [patientId]);

    const toggleScale = (type) => {
        setChosen((current) => ({...current, [type]: !current[type]}));
        setActionError(null);
    };

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!page) {
        return <p>Carregando escalas...</p>;
    }

    const visibleScales = page.scales.filter((scale) =>
        scale.name.toLowerCase().includes(search.trim().toLowerCase()));
    const scalesToSend = page.scales.filter((scale) => chosen[scale.type] && !page.waiting.includes(scale.type));

    const send = async () => {
        setActionError(null);
        setIsSending(true);
        const sent = [];
        try {
            // uma de cada vez pra saber o q chegou ao paciente se alguma falhar
            for (const scale of scalesToSend) {
                await apiService.post("/pacientes/" + patientId + "/escalas", {scaleType: scale.type});
                sent.push(scale.type);
            }
            navigate("/paciente/" + patientId + "/historico", {
                state: {
                    notice: sent.length + " escala(s) enviada(s) para " + page.patientName
                        + ". O paciente recebeu o aviso e a escala já aparece no painel dele.",
                },
            });
        } catch (requestError) {
            const detail = requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE;
            setPage((current) => ({...current, waiting: [...current.waiting, ...sent]}));
            setActionError(sent.length > 0
                ? "Só parte das escalas foi enviada. " + detail
                : "Nenhuma escala foi enviada. " + detail);
            setIsSending(false);
        }
    };

    return (
        <section className={styles.page}>
            <header>
                <h1>Enviar escalas para {page.patientName}</h1>
                <p className={styles.subtitle}>
                    Marque o que o paciente vai responder. Ele recebe um aviso no sistema e a escala aparece no
                    painel dele, com prazo.
                </p>
            </header>

            {actionError && <p className="aviso aviso--atencao" role="alert">{actionError}</p>}

            <label className={styles.search}>
                <span>Buscar escala</span>
                <input
                    type="text"
                    placeholder="Nome da escala"
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                />
            </label>

            <ul className={styles.list}>
                {visibleScales.map((scale) => {
                    const alreadySent = page.waiting.includes(scale.type);
                    return (
                        <li key={scale.type} className={styles.item}>
                            <label className={styles.choice}>
                                <input
                                    type="checkbox"
                                    checked={alreadySent || Boolean(chosen[scale.type])}
                                    disabled={alreadySent || isSending}
                                    onChange={() => toggleScale(scale.type)}
                                />
                                <span>
                                    <strong>{scale.name}</strong>
                                    <span className={styles.help}>
                                        {alreadySent
                                            ? "Já enviada. Esperando o paciente responder."
                                            : scale.description}
                                    </span>
                                </span>
                            </label>
                        </li>
                    );
                })}
            </ul>

            <footer className={styles.footer}>
                <p className={styles.subtitle}>
                    {scalesToSend.length === 0
                        ? "Nenhuma escala marcada."
                        : scalesToSend.length + " escala(s) para enviar."}
                </p>
                <div className={styles.actions}>
                    <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                        Voltar
                    </button>
                    <button
                        type="button"
                        className="button"
                        onClick={send}
                        disabled={scalesToSend.length === 0 || isSending}
                    >
                        {isSending ? "Enviando..." : "Enviar ao paciente"}
                    </button>
                </div>
            </footer>
        </section>
    );
}
