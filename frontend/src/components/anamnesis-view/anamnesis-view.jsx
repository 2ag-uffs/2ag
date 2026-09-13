import AnnulmentNotice from "../annulment-notice/annulment-notice.jsx";
import {formatDate} from "../../utils/date-format.js";
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
            {name: "weight", label: "Peso (kg)"},
            {name: "height", label: "Altura (cm)"},
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

// ficha de anamnese so pra leitura com as respostas q foram dadas (RF12 e RF13)
// o actions deixa quem usa colocar botoes como o de anular
export default function AnamnesisView({anamnesis, actions}) {
    return (
        <article className={styles.card}>
            <header className={styles.header}>
                <h3>Preenchida em {formatDate(anamnesis.assessmentDate)}</h3>
                {anamnesis.annulled && <span className={styles.badgeAnnulled}>Anulada</span>}
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
                                    <dd>{anamnesis[field.name]}</dd>
                                </div>
                            ))}
                        </dl>
                    </div>
                );
            })}

            {anamnesis.annulled && <AnnulmentNotice record={anamnesis}/>}

            {actions && <div className={styles.actions}>{actions}</div>}
        </article>
    );
}
