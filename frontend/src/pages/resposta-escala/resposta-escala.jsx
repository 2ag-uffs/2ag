import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
import Card from "../../components/card/card.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
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

        apiService.get("/scales/responses/" + responseId)
            .then((loadedResponse) => {
                if (!isCurrentRequest) {
                    return null;
                }
                setResponse(loadedResponse);
                return apiService.get("/scales/definitions/" + loadedResponse.slug);
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
        return <SkeletonPage cards={1}/>;
    }

    const periodText = response.periodStart === response.periodEnd
        ? formatDate(response.periodStart)
        : formatDate(response.periodStart) + " a " + formatDate(response.periodEnd);

    // o paciente corrige enquanto o prescritor n analisou e a tela de responder ja abre no dia certo
    const canCorrect = loggedUser.role === "PATIENT" && response.editableByPatient;

    return (
        <section className={styles.page}>
            <PageHeader
                title={response.scaleName}
                subtitle={response.patientName + " · " + periodText
                    + (response.prescriberName ? " · aplicada por " + response.prescriberName : "")}
                actions={canCorrect ? (
                    <button
                        type="button"
                        className="button"
                        onClick={() => navigate("/escalas/" + response.slug + "?data=" + response.periodStart)}
                    >
                        Corrigir respostas
                    </button>
                ) : null}
            />

            {response.result && (
                <div className={styles.result}>
                    <span className={styles.resultLabel}>Resultado</span>
                    <strong>{response.result}</strong>
                </div>
            )}

            {response.annulled && (
                <p className="aviso aviso--atencao">
                    Anulada em {formatDateTime(response.annulledAt)} por {response.annulledByName}.
                    Motivo: {response.annulmentReason}
                </p>
            )}

            {response.reviewed && !response.annulled && (
                <p className={styles.meta}>Analisada pelo prescritor em {formatDateTime(response.reviewedAt)}.</p>
            )}

            <Card title="Respostas">
                <dl className={styles.answers}>
                    {definition.items.map((item) => (
                        <div key={item.key} className={styles.answer}>
                            <dt>{item.label}</dt>
                            <dd>{formatAnswer(item, response.answers[item.key])}</dd>
                        </div>
                    ))}
                </dl>
            </Card>
        </section>
    );
}
