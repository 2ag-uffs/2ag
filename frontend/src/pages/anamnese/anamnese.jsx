import {useState} from "react";
import {useNavigate} from "react-router";
import SelectField from "../../components/form/select-field.jsx";
import TextAreaField from "../../components/form/text-area-field.jsx";
import TextField from "../../components/form/text-field.jsx";
import ModalConfirmacao from "../../components/modal/modal-confirmacao.jsx";
import {apiService, ApiError} from "../../services/api.js";
import styles from "./anamnese.module.css";

const SMOKING_OPTIONS = [
    {value: "Não", label: "Não"},
    {value: "Sim, diariamente", label: "Sim, diariamente"},
    {value: "Sim, ocasionalmente", label: "Sim, ocasionalmente"},
];

const ALCOHOL_OPTIONS = [
    {value: "Não", label: "Não"},
    {value: "Sim, socialmente", label: "Sim, socialmente"},
    {value: "Sim, frequentemente", label: "Sim, frequentemente"},
];

const AWARENESS_OPTIONS = [
    {value: "Sim", label: "Sim"},
    {value: "Não", label: "Não"},
];

// data de hoje no formato do input date sem passar por utc
function todayForInput() {
    const today = new Date();
    const month = String(today.getMonth() + 1).padStart(2, "0");
    const day = String(today.getDate()).padStart(2, "0");
    return today.getFullYear() + "-" + month + "-" + day;
}

function emptyAnswers() {
    return {
        assessmentDate: todayForInput(),
        profession: "",
        reasonForVisit: "",
        previousDiagnosis: "",
        previousTreatment: "",
        currentMedication: "",
        familyHistory: "",
        adverseReaction: "",
        geneticCondition: "",
        diet: "",
        smokingHabits: "Não",
        alcoholConsumption: "Não",
        weight: "",
        height: "",
        substanceUse: "",
        physicalActivity: "",
        sleepHabits: "",
        anxiety: "",
        pain: "",
        expectations: "",
        treatmentAwareness: "",
        observation: "",
    };
}

// ficha de anamnese preenchida pelo proprio paciente (RF19)
export default function Anamnese() {
    const navigate = useNavigate();
    const [answers, setAnswers] = useState(emptyAnswers);
    const [isSaving, setIsSaving] = useState(false);
    const [formError, setFormError] = useState(null);
    const [fieldErrors, setFieldErrors] = useState({});
    const [isConfirmingExit, setIsConfirmingExit] = useState(false);

    const updateAnswer = (fieldName, value) => {
        setAnswers((currentAnswers) => ({...currentAnswers, [fieldName]: value}));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setIsSaving(true);
        setFormError(null);
        setFieldErrors({});

        // resposta em branco vai como null pra api
        const requestBody = {};
        Object.keys(answers).forEach((fieldName) => {
            const answer = answers[fieldName].trim();
            requestBody[fieldName] = answer === "" ? null : answer;
        });

        try {
            await apiService.post("/anamnese", requestBody);
            navigate("/dashboard-paciente", {state: {aviso: "Ficha de anamnese enviada. Obrigado por responder."}});
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                const errorsByField = requestError.fieldErrors();
                setFieldErrors(errorsByField);
                setFormError(Object.keys(errorsByField).length === 0
                    ? requestError.message
                    : "Confira as respostas destacadas.");
            } else {
                setFormError("Não foi possível falar com o servidor. Confira sua internet e tente de novo.");
            }
            setIsSaving(false);
        }
    };

    return (
        <section className={styles.page}>
            <div className={styles.header}>
                <h1>Anamnese</h1>
                <p>
                    Estas perguntas ajudam o seu prescritor a conhecer sua saúde antes de começar o tratamento. Só o
                    motivo da consulta e a pergunta sobre o acompanhamento são obrigatórios.
                </p>
            </div>

            {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}

            <form className={styles.form} onSubmit={handleSubmit}>
                <fieldset className={styles.section} disabled={isSaving}>
                    <legend>Sobre você</legend>
                    <div className={styles.row}>
                        <TextField
                            label="Data de preenchimento"
                            name="assessmentDate"
                            type="date"
                            max={todayForInput()}
                            value={answers.assessmentDate}
                            onChange={(event) => updateAnswer("assessmentDate", event.target.value)}
                            error={fieldErrors.assessmentDate}
                        />
                        <TextField
                            label="Ocupação"
                            name="profession"
                            value={answers.profession}
                            onChange={(event) => updateAnswer("profession", event.target.value)}
                            error={fieldErrors.profession}
                        />
                    </div>
                    <TextAreaField
                        label="Motivo principal da consulta"
                        name="reasonForVisit"
                        rows={3}
                        value={answers.reasonForVisit}
                        onChange={(event) => updateAnswer("reasonForVisit", event.target.value)}
                        error={fieldErrors.reasonForVisit}
                        required={true}
                    />
                </fieldset>

                <fieldset className={styles.section} disabled={isSaving}>
                    <legend>Histórico de saúde</legend>
                    <TextAreaField
                        label="Diagnósticos anteriores"
                        name="previousDiagnosis"
                        rows={2}
                        value={answers.previousDiagnosis}
                        onChange={(event) => updateAnswer("previousDiagnosis", event.target.value)}
                    />
                    <TextAreaField
                        label="Tratamentos anteriores"
                        name="previousTreatment"
                        rows={3}
                        value={answers.previousTreatment}
                        onChange={(event) => updateAnswer("previousTreatment", event.target.value)}
                    />
                    <TextAreaField
                        label="Medicações em uso"
                        name="currentMedication"
                        rows={3}
                        value={answers.currentMedication}
                        onChange={(event) => updateAnswer("currentMedication", event.target.value)}
                    />
                    <TextAreaField
                        label="Doenças importantes na família"
                        name="familyHistory"
                        rows={2}
                        value={answers.familyHistory}
                        onChange={(event) => updateAnswer("familyHistory", event.target.value)}
                    />
                    <TextAreaField
                        label="Reações ruins a medicamentos"
                        name="adverseReaction"
                        rows={2}
                        value={answers.adverseReaction}
                        onChange={(event) => updateAnswer("adverseReaction", event.target.value)}
                    />
                    <TextAreaField
                        label="Condições genéticas conhecidas"
                        name="geneticCondition"
                        rows={2}
                        value={answers.geneticCondition}
                        onChange={(event) => updateAnswer("geneticCondition", event.target.value)}
                    />
                </fieldset>

                <fieldset className={styles.section} disabled={isSaving}>
                    <legend>Hábitos</legend>
                    <TextField
                        label="Tipo de alimentação"
                        name="diet"
                        placeholder="Ex: onívora, vegetariana ou vegana"
                        value={answers.diet}
                        onChange={(event) => updateAnswer("diet", event.target.value)}
                    />
                    <div className={styles.row}>
                        <SelectField
                            label="Fuma?"
                            name="smokingHabits"
                            options={SMOKING_OPTIONS}
                            value={answers.smokingHabits}
                            onChange={(event) => updateAnswer("smokingHabits", event.target.value)}
                        />
                        <SelectField
                            label="Bebe álcool?"
                            name="alcoholConsumption"
                            options={ALCOHOL_OPTIONS}
                            value={answers.alcoholConsumption}
                            onChange={(event) => updateAnswer("alcoholConsumption", event.target.value)}
                        />
                    </div>
                    <div className={styles.row}>
                        <TextField
                            label="Peso (kg)"
                            name="weight"
                            type="number"
                            inputMode="decimal"
                            step="0.1"
                            value={answers.weight}
                            onChange={(event) => updateAnswer("weight", event.target.value)}
                            error={fieldErrors.weight}
                        />
                        <TextField
                            label="Altura (cm)"
                            name="height"
                            type="number"
                            inputMode="numeric"
                            value={answers.height}
                            onChange={(event) => updateAnswer("height", event.target.value)}
                            error={fieldErrors.height}
                        />
                    </div>
                    <TextAreaField
                        label="Uso de outras substâncias"
                        name="substanceUse"
                        rows={2}
                        value={answers.substanceUse}
                        onChange={(event) => updateAnswer("substanceUse", event.target.value)}
                    />
                    <TextAreaField
                        label="Exercícios físicos"
                        name="physicalActivity"
                        hint="Conte qual exercício e quantas vezes por semana."
                        rows={2}
                        value={answers.physicalActivity}
                        onChange={(event) => updateAnswer("physicalActivity", event.target.value)}
                    />
                </fieldset>

                <fieldset className={styles.section} disabled={isSaving}>
                    <legend>Como você está hoje</legend>
                    <TextAreaField
                        label="Sono"
                        name="sleepHabits"
                        placeholder="Ex: durmo bem, tenho insônia ou acordo várias vezes"
                        rows={2}
                        value={answers.sleepHabits}
                        onChange={(event) => updateAnswer("sleepHabits", event.target.value)}
                    />
                    <TextAreaField
                        label="Ansiedade"
                        name="anxiety"
                        hint="Com que frequência aparece e com que intensidade."
                        rows={2}
                        value={answers.anxiety}
                        onChange={(event) => updateAnswer("anxiety", event.target.value)}
                    />
                    <TextAreaField
                        label="Dor"
                        name="pain"
                        hint="Onde dói, com que frequência e com que intensidade."
                        rows={2}
                        value={answers.pain}
                        onChange={(event) => updateAnswer("pain", event.target.value)}
                    />
                </fieldset>

                <fieldset className={styles.section} disabled={isSaving}>
                    <legend>Sobre o tratamento</legend>
                    <TextAreaField
                        label="O que você espera do tratamento"
                        name="expectations"
                        rows={3}
                        value={answers.expectations}
                        onChange={(event) => updateAnswer("expectations", event.target.value)}
                    />
                    <SelectField
                        label="Você sabe que o tratamento precisa de acompanhamento regular?"
                        name="treatmentAwareness"
                        options={AWARENESS_OPTIONS}
                        placeholder="Escolha uma resposta"
                        value={answers.treatmentAwareness}
                        onChange={(event) => updateAnswer("treatmentAwareness", event.target.value)}
                        error={fieldErrors.treatmentAwareness}
                        required={true}
                    />
                    <TextAreaField
                        label="Algo mais que queira contar"
                        name="observation"
                        rows={3}
                        value={answers.observation}
                        onChange={(event) => updateAnswer("observation", event.target.value)}
                    />
                </fieldset>

                <div className={styles.actions}>
                    <button
                        type="button"
                        className={styles.secondaryButton}
                        onClick={() => setIsConfirmingExit(true)}
                        disabled={isSaving}
                    >
                        Cancelar
                    </button>
                    <button type="submit" className={styles.primaryButton} disabled={isSaving}>
                        {isSaving ? "Enviando..." : "Enviar anamnese"}
                    </button>
                </div>
            </form>

            <ModalConfirmacao
                show={isConfirmingExit}
                titulo="Sair sem enviar"
                mensagem="O que você preencheu ainda não foi enviado e vai se perder. Quer mesmo sair?"
                textoConfirmar="Sim, sair"
                textoCancelar="Continuar preenchendo"
                onConfirmar={() => navigate("/dashboard-paciente")}
                onCancelar={() => setIsConfirmingExit(false)}
            />
        </section>
    );
}
