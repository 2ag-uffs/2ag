import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import {formatDate, formatDateTime} from "../../utils/date-format.js";
import {formatAnswer} from "../../utils/scale-answers.js";
import styles from "./resposta-escala.module.css";

// uma escala respondida, so pra leitura (RF12 e RF13)
//
// serve pro prescritor conferir o q o paciente respondeu e pro paciente
// reler uma resposta antiga, item por item
export default function RespostaEscala() {
    const {responseId} = useParams();
    const navigate = useNavigate();
    const loggedUser = getLoggedUser();

    const [response, setResponse] = useState(null);
    const [definition, setDefinition] = useState(null);
    const [loadError, setLoadError] = useState(null);

    useEffect(() => {
        let isCurrentRequest = true;

        apiService.get("/escalas/respostas/" + responseId)
            .then((loadedResponse) => {
                if (!isCurrentRequest) {
                    return null;
                }
                setResponse(loadedResponse);
                return apiService.get("/escalas/definicoes/" + loadedResponse.slug);
            })
            .then((loadedDefinition) => {
                if (isCurrentRequest && loadedDefinition) {
                    setDefinition(loadedDefinition);
                    setLoadError(null);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível abrir a resposta. Confira sua internet e tente de novo.");
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [responseId]);

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!response || !definition) {
        return <p>Carregando a resposta...</p>;
    }

    const periodText = response.periodStart === response.periodEnd
        ? formatDate(response.periodStart)
        : formatDate(response.periodStart) + " a " + formatDate(response.periodEnd);

    return (
        <section className={styles.page}>
            <header>
                <h1>{response.scaleName}</h1>
                <p className={styles.meta}>
                    {response.patientName} · {periodText}
                    {response.prescriberName ? " · aplicada por " + response.prescriberName : ""}
                </p>
            </header>

            {response.result && <p className={styles.result}>{response.result}</p>}

            {response.annulled && (
                <p className="aviso aviso--atencao">
                    Anulada em {formatDateTime(response.annulledAt)} por {response.annulledByName}.
                    Motivo: {response.annulmentReason}
                </p>
            )}

            {response.reviewed && !response.annulled && (
                <p className={styles.meta}>Analisada pelo prescritor em {formatDateTime(response.reviewedAt)}.</p>
            )}

            <dl className={styles.answers}>
                {definition.items.map((item) => (
                    <div key={item.key} className={styles.answer}>
                        <dt>{item.label}</dt>
                        <dd>{formatAnswer(item, response.answers[item.key])}</dd>
                    </div>
                ))}
            </dl>

            <div className={styles.actions}>
                <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                    Voltar
                </button>
                {/* o paciente corrige enquanto o prescritor n analisou e a tela de responder ja abre no dia certo */}
                {loggedUser.role === "PATIENT" && response.editableByPatient && (
                    <button
                        type="button"
                        className="button"
                        onClick={() => navigate("/escalas/" + response.slug + "?data=" + response.periodStart)}
                    >
                        Corrigir respostas
                    </button>
                )}
            </div>
        </section>
    );
}
