import {useEffect, useId, useState} from "react";
import {useNavigate, useParams} from "react-router";
import Card from "../../components/card/card.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, errorMessageOf, getLoggedUser} from "../../services/api.js";
import {formatDateTime, formatPeriod} from "../../utils/date-format.js";
import {formatAnswer} from "../../utils/scale-answers.js";
import styles from "./resposta-escala.module.css";

// as versoes q as correcoes do paciente substituiram, da mais nova pra mais velha, cada uma so com o q
// mudou dela pra seguinte. a api so manda pra prescritora (decisao de 14/11/2026, revisao das regras R4)
function previousVersionsOf(items, response) {
    const corrections = response.corrections || [];
    const versions = corrections.map((correction, index) => {
        const next = index + 1 < corrections.length ? corrections[index + 1] : response;
        return {
            correction,
            changedItems: items.filter((item) => formatAnswer(item, correction.answers[item.key])
                !== formatAnswer(item, next.answers[item.key])),
            resultChanged: correction.result !== next.result,
            periodChanged: correction.periodStart !== next.periodStart || correction.periodEnd !== next.periodEnd,
        };
    });
    return versions.reverse();
}

function PreviousVersion({version}) {
    const titleId = useId();
    const correction = version.correction;

    return (
        <section className={styles.version} aria-labelledby={titleId}>
            <h3 id={titleId} className={styles.versionTitle}>
                Antes da correção de {formatDateTime(correction.correctedAt)}
            </h3>
            {version.resultChanged && correction.result && <p>Resultado: {correction.result}</p>}
            {version.periodChanged && <p>Período: {formatPeriod(correction.periodStart, correction.periodEnd)}</p>}
            {version.changedItems.length > 0 && (
                <dl className={styles.answers}>
                    {version.changedItems.map((item) => (
                        <div key={item.key} className={styles.answer}>
                            <dt>{item.label}</dt>
                            <dd>{formatAnswer(item, correction.answers[item.key])}</dd>
                        </div>
                    ))}
                </dl>
            )}
        </section>
    );
}

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
                    setLoadError(errorMessageOf(requestError,
                        "Não foi possível abrir a resposta. Confira sua internet e tente de novo."));
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

    const periodText = formatPeriod(response.periodStart, response.periodEnd);

    // o paciente corrige enquanto o prescritor n analisou e a tela de responder ja abre no dia certo
    const canCorrect = loggedUser.role === "PATIENT" && response.editableByPatient;
    const previousVersions = previousVersionsOf(definition.items, response);

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

            {previousVersions.length > 0 && (
                <p className="aviso">
                    Corrigida pelo paciente em {formatDateTime(previousVersions[0].correction.correctedAt)}. O que
                    estava antes de cada correção aparece no fim da página.
                </p>
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

            {previousVersions.length > 0 && (
                <Card title="Antes das correções">
                    <div className={styles.versions}>
                        {previousVersions.map((version) => (
                            <PreviousVersion key={version.correction.correctedAt} version={version}/>
                        ))}
                    </div>
                </Card>
            )}
        </section>
    );
}
