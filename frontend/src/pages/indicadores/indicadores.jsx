import {useEffect, useState} from "react";
import Card from "../../components/card/card.jsx";
import TextField from "../../components/form/text-field.jsx";
import ItemList, {ListItem} from "../../components/item-list/item-list.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {addDays, toIsoDate} from "../../utils/date-format.js";
import styles from "./indicadores.module.css";

// "1 paciente" e "2 pacientes"
function countOf(count, singular, plural) {
    return count + " " + (count === 1 ? singular : plural);
}

function percentText(percent) {
    return percent === null || percent === undefined ? "—" : percent + "%";
}

// indicadores do piloto (RF39)
// so numero somado dos pacientes do prescritor, sem nome nem linha por paciente
export default function Indicadores() {
    // o prazo de hoje ainda esta correndo, entao o periodo vai no maximo ate ontem
    const yesterday = toIsoDate(addDays(new Date(), -1));

    const [fromDate, setFromDate] = useState("");
    const [toDate, setToDate] = useState("");
    // sem periodo a api usa o padrao dela e devolve as datas q usou
    const [period, setPeriod] = useState(null);
    const [indicators, setIndicators] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState(null);

    useEffect(() => {
        // resposta de um filtro antigo q chega atrasada n pode sobrescrever a nova
        let isCurrentRequest = true;
        const path = period ? "/indicators?from=" + period.from + "&to=" + period.to : "/indicators";

        apiService.get(path)
            .then((result) => {
                if (isCurrentRequest) {
                    setIndicators(result);
                    setFromDate(result.from);
                    setToDate(result.to);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setIndicators(null);
                    setErrorMessage(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível carregar os indicadores. Confira sua internet e tente de novo.");
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
    }, [period]);

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
        if (toDate > yesterday) {
            setErrorMessage("Escolha um período que termine até ontem.");
            return;
        }
        setErrorMessage(null);
        setIsLoading(true);
        setPeriod({from: fromDate, to: toDate});
    };

    if (isLoading && !indicators && !errorMessage) {
        return <SkeletonPage cards={2}/>;
    }

    return (
        <section className={styles.page}>
            <PageHeader
                title="Indicadores do piloto"
                subtitle="Números somados dos seus pacientes, sem identificar ninguém."
            />

            <form className={styles.filter} onSubmit={handleFilter}>
                <TextField
                    label="De"
                    name="indicators-from"
                    type="date"
                    value={fromDate}
                    max={yesterday}
                    onChange={(event) => setFromDate(event.target.value)}
                    required={true}
                />
                <TextField
                    label="Até"
                    name="indicators-to"
                    type="date"
                    value={toDate}
                    max={yesterday}
                    onChange={(event) => setToDate(event.target.value)}
                    required={true}
                />
                <button type="submit" className="button-secondary" disabled={isLoading}>Filtrar</button>
            </form>

            {errorMessage && <p className="aviso aviso--atencao" role="alert">{errorMessage}</p>}

            {indicators && <IndicatorCards indicators={indicators}/>}

            <Card title="Como contamos">
                <ul className={styles.rules}>
                    <li>Só entram formulários com o prazo já encerrado, por isso o período vai até ontem.</li>
                    <li>No prazo quer dizer respondido até o último dia do prazo.</li>
                    <li>Um diário conta como respondido com pelo menos um dia preenchido.</li>
                    <li>Resposta anulada não conta.</li>
                    <li>Nos formulários, pacientes arquivados continuam na conta, para a desistência não sumir do número.</li>
                </ul>
            </Card>
        </section>
    );
}

function IndicatorCards({indicators}) {
    const goal = indicators.goalPercent;
    const percent = indicators.onTimePercent;
    const hasPercent = percent !== null && percent !== undefined;
    const invitesGoalMet = indicators.invitesInCycle === indicators.invitesSent;

    return (
        <>
            <div className={styles.grid}>
                <Card title="Pacientes no ciclo de acompanhamento">
                    <p className={styles.bigNumber}>{indicators.patientsInCycle}</p>
                    <p className={styles.explain}>
                        de {countOf(indicators.activePatients, "paciente ativo", "pacientes ativos")} hoje,
                        com acompanhamento automático valendo hoje.
                    </p>
                    <p>
                        {countOf(indicators.patientsWithForms, "paciente", "pacientes")} com formulário com prazo
                        no período.
                    </p>
                    <p>
                        Convites do período: {countOf(indicators.invitesSent, "enviado", "enviados")}
                        {" · "}{countOf(indicators.invitesRegistered, "cadastrado", "cadastrados")}
                        {" · "}{countOf(indicators.invitesInCycle, "entrou", "entraram")} em um acompanhamento
                        {" · "}{indicators.invitesStillOpen} com o link ainda válido
                    </p>
                    <p className={styles.explain}>Meta: todo paciente convidado cadastrado e no ciclo.</p>
                    {indicators.invitesSent > 0 && (
                        <p className={invitesGoalMet ? styles.goalMet : styles.goalMissed}>
                            {invitesGoalMet ? "Meta atingida" : "Meta ainda não atingida"}
                        </p>
                    )}
                </Card>

                <Card title="Formulários respondidos no prazo">
                    <p className={styles.bigNumber}>{percentText(percent)}</p>
                    <p className={styles.explain}>
                        {indicators.formsOnTime} de {countOf(indicators.formsDue, "formulário", "formulários")} com
                        prazo no período foram respondidos até o último dia.
                    </p>
                    {hasPercent && (
                        <>
                            <p className={percent >= goal ? styles.goalMet : styles.goalMissed}>
                                {percent >= goal ? "Meta de " + goal + "% atingida" : "Abaixo da meta de " + goal + "%"}
                            </p>
                            {/* o numero e a etiqueta ja dizem tudo, a barra eh so o desenho */}
                            <div className={styles.bar} aria-hidden="true">
                                <span className={styles.barFill} style={{width: Math.min(percent, 100) + "%"}}/>
                                <span className={styles.goalMark} style={{left: goal + "%"}}/>
                            </div>
                            <p className={styles.barCaption} aria-hidden="true">O traço marca a meta de {goal}%.</p>
                        </>
                    )}
                    {indicators.diaryDaysExpected > 0 && (
                        <p>
                            Diários: {indicators.diaryDaysFilled} de {indicators.diaryDaysExpected} dias
                            preenchidos ({percentText(indicators.diaryDaysPercent)})
                        </p>
                    )}
                </Card>
            </div>

            <Card title="Por escala">
                {indicators.scales.length === 0 ? (
                    <p className={styles.empty}>Nenhum formulário com prazo no período.</p>
                ) : (
                    <ItemList>
                        {indicators.scales.map((scale) => (
                            <ListItem
                                key={scale.scaleType}
                                title={scale.scaleName}
                                details={scale.formsOnTime + " de " + scale.formsDue + " no prazo · "
                                    + percentText(scale.onTimePercent)
                                    + (scale.daily ? " · " + scale.daysFilled + " de " + scale.daysExpected + " dias" : "")}
                            />
                        ))}
                    </ItemList>
                )}
            </Card>
        </>
    );
}
