import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
import {FiSearch} from "react-icons/fi";
import ChoiceCard from "../../components/choice-card/choice-card.jsx";
import EmptyState from "../../components/empty-state/empty-state.jsx";
import BusyButton from "../../components/form/busy-button.jsx";
import {FormActions} from "../../components/form-section/form-section.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError, CONNECTION_ERROR_MESSAGE, errorMessageOf} from "../../services/api.js";
import {ARCHIVED_INITIAL_ASSESSMENT_NOTICE} from "../../utils/archived-patient.js";
import {matchesSearch} from "../../utils/search-text.js";
import styles from "./selecao-escalas.module.css";

// escala ja enviada, bloqueada ou recusada mostra o motivo no lugar da descricao
function descriptionOf(scale, alreadySent, blockedReason, refusal) {
    if (alreadySent) {
        return "Já enviada. Esperando o paciente responder.";
    }
    if (blockedReason) {
        return blockedReason;
    }
    if (refusal) {
        return refusal.endsWith(".") ? refusal : refusal + ".";
    }
    return scale.description;
}

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
    // as q o servidor recusou, com o motivo, tipo a escala q ja foi respondida hoje
    const [refused, setRefused] = useState({});

    useEffect(() => {
        let isCurrentRequest = true;

        Promise.all([
            apiService.get("/patients/" + patientId),
            apiService.get("/patients/" + patientId + "/scales"),
            apiService.get("/scales/assignable"),
        ])
            .then(([patient, tasks, assignable]) => {
                if (isCurrentRequest) {
                    setPage({
                        patientName: patient.name,
                        isArchived: Boolean(patient.archived),
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
                    setLoadError(errorMessageOf(requestError,
                        "Não foi possível carregar as escalas. Confira sua internet e tente de novo."));
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
        return <SkeletonPage cards={3}/>;
    }

    // o arquivado n preenche a avaliacao inicial e a api recusa o envio, entao o cartao ja vem bloqueado
    // com o motivo e as outras escalas vao num clique so (decisao da dona do projeto, novembro de 2026)
    const blockedReasonOf = (scale) => (
        page.isArchived && scale.type === "ANAMNESE" ? ARCHIVED_INITIAL_ASSESSMENT_NOTICE : null);

    const visibleScales = page.scales.filter((scale) => matchesSearch(scale.name, search));
    const scalesToSend = page.scales.filter((scale) => chosen[scale.type] && !page.waiting.includes(scale.type)
        && !refused[scale.type] && !blockedReasonOf(scale));

    const send = async () => {
        setActionError(null);
        setIsSending(true);
        const sent = [];
        try {
            // uma de cada vez pra saber o q chegou ao paciente se alguma falhar
            for (const scale of scalesToSend) {
                await apiService.post("/patients/" + patientId + "/scales", {scaleType: scale.type});
                sent.push(scale.type);
            }
            navigate("/paciente/" + patientId + "/historico", {
                state: {
                    notice: sent.length + " escala(s) enviada(s) para " + page.patientName
                        + ". O paciente recebeu o aviso e a escala já aparece no painel dele.",
                },
            });
        } catch (requestError) {
            const failedScale = scalesToSend[sent.length];
            let detail = CONNECTION_ERROR_MESSAGE;
            if (requestError instanceof ApiError) {
                detail = failedScale.name + ": " + requestError.message;
                // a recusa vale so pra essa escala: ela fica marcada com o motivo e as outras podem ir
                if (requestError.status === 400) {
                    setRefused((current) => ({...current, [failedScale.type]: requestError.message}));
                }
            }
            setPage((current) => ({...current, waiting: [...current.waiting, ...sent]}));
            setActionError(sent.length > 0
                ? "Só parte das escalas foi enviada. " + detail
                : "Nenhuma escala foi enviada. " + detail);
            setIsSending(false);
        }
    };

    return (
        <section className={styles.page}>
            <PageHeader
                title={"Enviar escalas para " + page.patientName}
                subtitle="Marque o que o paciente vai responder. Ele recebe um aviso no sistema e a escala aparece no painel dele, com prazo."
            />

            {actionError && <p className="aviso aviso--atencao" role="alert">{actionError}</p>}

            <label className={styles.searchBox}>
                <FiSearch className={styles.searchIcon} aria-hidden="true"/>
                <input
                    type="search"
                    className={styles.search}
                    placeholder="Buscar escala pelo nome"
                    aria-label="Buscar escala pelo nome"
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                />
            </label>

            {visibleScales.length === 0 ? (
                <EmptyState icon={FiSearch} message="Nenhuma escala com esse nome." isCompact={true}/>
            ) : (
                <ul className={styles.list}>
                    {visibleScales.map((scale) => {
                        const alreadySent = page.waiting.includes(scale.type);
                        const refusal = refused[scale.type];
                        const blockedReason = blockedReasonOf(scale);
                        const isBlocked = Boolean(blockedReason) || Boolean(refusal);
                        return (
                            <li key={scale.type}>
                                <ChoiceCard
                                    title={scale.name}
                                    description={descriptionOf(scale, alreadySent, blockedReason, refusal)}
                                    checked={alreadySent || (!isBlocked && Boolean(chosen[scale.type]))}
                                    disabled={alreadySent || isBlocked || isSending}
                                    onChange={() => toggleScale(scale.type)}
                                />
                            </li>
                        );
                    })}
                </ul>
            )}

            <FormActions>
                <p className={styles.counter}>
                    {scalesToSend.length === 0
                        ? "Nenhuma escala marcada."
                        : scalesToSend.length + " escala(s) para enviar."}
                </p>
                <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                    Voltar
                </button>
                <BusyButton
                    type="button"
                    className="button"
                    onClick={send}
                    disabled={scalesToSend.length === 0}
                    isBusy={isSending}
                    busyText="Enviando..."
                >
                    Enviar ao paciente
                </BusyButton>
            </FormActions>
        </section>
    );
}
