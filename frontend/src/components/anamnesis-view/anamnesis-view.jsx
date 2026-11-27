import {useId} from "react";
import AnnulmentNotice from "../annulment-notice/annulment-notice.jsx";
import {formatDate, formatDateTime} from "../../utils/date-format.js";
import styles from "./anamnesis-view.module.css";

// perguntas agrupadas do mesmo jeito da ficha q o paciente preenche
const ANAMNESIS_SECTIONS = [
    {
        title: "Motivo da consulta",
        fields: [
            {name: "reasonForVisit", label: "Motivo principal"},
            {name: "profession", label: "Ocupação"},
        ],
    },
    {
        title: "Histórico de saúde",
        fields: [
            {name: "previousDiagnosis", label: "Diagnósticos anteriores"},
            {name: "previousTreatment", label: "Tratamentos anteriores"},
            {name: "currentMedication", label: "Medicações em uso"},
            {name: "familyHistory", label: "Doenças importantes na família"},
            {name: "adverseReaction", label: "Reações ruins a medicamentos"},
            {name: "geneticCondition", label: "Condições genéticas conhecidas"},
        ],
    },
    {
        title: "Hábitos",
        fields: [
            {name: "diet", label: "Alimentação"},
            {name: "smokingHabits", label: "Fuma"},
            {name: "alcoholConsumption", label: "Bebe álcool"},
            {name: "weight", label: "Peso (kg)", isDecimal: true},
            {name: "height", label: "Altura (cm)", isDecimal: true},
            {name: "substanceUse", label: "Uso de outras substâncias"},
            {name: "physicalActivity", label: "Exercícios físicos"},
        ],
    },
    {
        title: "Sintomas",
        fields: [
            {name: "sleepHabits", label: "Sono"},
            {name: "anxiety", label: "Ansiedade"},
            {name: "pain", label: "Dor"},
        ],
    },
    {
        title: "Sobre o tratamento",
        fields: [
            {name: "expectations", label: "Expectativas"},
            {name: "treatmentAwareness", label: "Sabe que precisa de acompanhamento regular"},
            {name: "observation", label: "Observações"},
        ],
    },
];

const ANAMNESIS_FIELDS = ANAMNESIS_SECTIONS.flatMap((section) => section.fields);

// o peso e a altura chegam como o navegador manda, tipo 68.5, e aparecem com a virgula como o peso da consulta
// so o numero com ponto muda, a resposta escrita fica como veio
function decimalAnswer(answer) {
    const text = String(answer).trim();
    return /^\d+\.\d+$/.test(text) ? text.replace(".", ",") : answer;
}

function answerText(field, answer) {
    if (!answer) {
        return "em branco";
    }
    return field.isDecimal ? decimalAnswer(answer) : answer;
}

// as versoes q as correcoes do paciente substituiram, da mais nova pra mais velha, cada uma so com o q
// mudou dela pra seguinte. a api so manda pra prescritora (decisao de 14/11/2026, revisao das regras R4)
function previousVersionsOf(anamnesis) {
    const corrections = anamnesis.corrections || [];
    const versions = corrections.map((correction, index) => {
        const next = index + 1 < corrections.length ? corrections[index + 1] : null;
        const nextAnswers = next ? next.answers : anamnesis;
        return {
            correction,
            dateChanged: correction.assessmentDate !== (next ? next.assessmentDate : anamnesis.assessmentDate),
            changedFields: ANAMNESIS_FIELDS.filter((field) => (correction.answers[field.name] || "")
                !== (nextAnswers[field.name] || "")),
        };
    });
    return versions.reverse();
}

function PreviousVersion({version}) {
    const titleId = useId();
    const correction = version.correction;

    return (
        <section className={styles.version} aria-labelledby={titleId}>
            <h4 id={titleId} className={styles.sectionTitle}>
                Antes da correção de {formatDateTime(correction.correctedAt)}
            </h4>
            <dl className={styles.details}>
                {version.dateChanged && (
                    <div>
                        <dt>Data de preenchimento</dt>
                        <dd>{formatDate(correction.assessmentDate)}</dd>
                    </div>
                )}
                {version.changedFields.map((field) => (
                    <div key={field.name}>
                        <dt>{field.label}</dt>
                        <dd>{answerText(field, correction.answers[field.name])}</dd>
                    </div>
                ))}
            </dl>
        </section>
    );
}

// ficha de anamnese so pra leitura com as respostas q foram dadas (RF12 e RF13)
// o actions deixa quem usa colocar botoes como o de anular
export default function AnamnesisView({anamnesis, actions}) {
    const previousVersions = previousVersionsOf(anamnesis);

    return (
        <article className={styles.card}>
            <header className={styles.header}>
                <h3>Preenchida em {formatDate(anamnesis.assessmentDate)}</h3>
                <span className={styles.badges}>
                    {previousVersions.length > 0 && (
                        <span className="etiqueta etiqueta--atencao">
                            Corrigida em {formatDate(previousVersions[0].correction.correctedAt)}
                        </span>
                    )}
                    {anamnesis.annulled && <span className="etiqueta etiqueta--anulada">Anulada</span>}
                </span>
            </header>

            {ANAMNESIS_SECTIONS.map((section) => {
                const answeredFields = section.fields.filter((field) => anamnesis[field.name]);
                // grupo sem nenhuma resposta nem aparece
                if (answeredFields.length === 0) {
                    return null;
                }
                return (
                    <div key={section.title}>
                        <h4 className={styles.sectionTitle}>{section.title}</h4>
                        <dl className={styles.details}>
                            {answeredFields.map((field) => (
                                <div key={field.name}>
                                    <dt>{field.label}</dt>
                                    <dd>{field.isDecimal ? decimalAnswer(anamnesis[field.name]) : anamnesis[field.name]}</dd>
                                </div>
                            ))}
                        </dl>
                    </div>
                );
            })}

            {previousVersions.map((version) => (
                <PreviousVersion key={version.correction.correctedAt} version={version}/>
            ))}

            {anamnesis.annulled && <AnnulmentNotice record={anamnesis}/>}

            {actions && <div className={styles.actions}>{actions}</div>}
        </article>
    );
}
