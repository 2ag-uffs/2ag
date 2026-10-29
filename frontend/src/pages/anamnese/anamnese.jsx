import {useEffect, useState} from "react";
import {useNavigate, useSearchParams} from "react-router";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import SelectField from "../../components/form/select-field.jsx";
import TextAreaField from "../../components/form/text-area-field.jsx";
import TextField from "../../components/form/text-field.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, errorMessageOf, readRequestError} from "../../services/api.js";
import {toIsoDate} from "../../utils/date-format.js";
import {emptyToNull} from "../../utils/form-values.js";
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

// data de hoje em brasilia no formato do input date, q eh o hoje da api
function todayForInput() {
    return toIsoDate(new Date());
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

// resposta q ficou em branco volta vazia e as de escolha voltam no padrao
function answersFrom(savedAnamnesis) {
    const defaultAnswers = emptyAnswers();
    const savedAnswers = {};
    Object.keys(defaultAnswers).forEach((fieldName) => {
        const savedValue = savedAnamnesis[fieldName];
        savedAnswers[fieldName] = savedValue === null || savedValue === undefined
            ? defaultAnswers[fieldName]
            : String(savedValue);
    });
    return savedAnswers;
}

// resposta em branco vai como null pra api
function requestBodyOf(answers) {
    const requestBody = {};
    Object.keys(answers).forEach((fieldName) => {
        requestBody[fieldName] = emptyToNull(answers[fieldName].trim());
    });
    return requestBody;
}

// ficha de anamnese preenchida pelo proprio paciente (RF19)
// com id na url a mesma tela corrige uma ficha q ja foi enviada
export default function Anamnese() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const anamnesisId = searchParams.get("id");
    const isCorrection = anamnesisId !== null;

    const [answers, setAnswers] = useState(emptyAnswers);
    const [isLoading, setIsLoading] = useState(isCorrection);
    const [loadError, setLoadError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const [formError, setFormError] = useState(null);
    const [fieldErrors, setFieldErrors] = useState({});
    const [isConfirmingExit, setIsConfirmingExit] = useState(false);

    useEffect(() => {
        if (!isCorrection) {
            return;
        }
        let isCurrentRequest = true;

        apiService.get("/anamneses/" + anamnesisId)
            .then((savedAnamnesis) => {
                if (!isCurrentRequest) {
                    return;
                }
                if (savedAnamnesis.annulled) {
                    setLoadError("Esta ficha foi anulada pelo prescritor e fica no histórico como está.");
                } else {
                    setAnswers(answersFrom(savedAnamnesis));
                }
                setIsLoading(false);
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(errorMessageOf(requestError,
                        "Não foi possível abrir a ficha. Confira sua internet e tente de novo."));
                    setIsLoading(false);
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [isCorrection, anamnesisId]);

    // quem desiste volta pra tela de onde veio a ficha
    const exitPath = isCorrection ? "/historico-paciente" : "/painel-paciente";

    // o name de cada campo eh a chave da resposta
    const updateAnswer = (event) => {
        const fieldName = event.target.name;
        const value = event.target.value;
        setAnswers((currentAnswers) => ({...currentAnswers, [fieldName]: value}));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setIsSaving(true);
        setFormError(null);
        setFieldErrors({});

        const requestBody = requestBodyOf(answers);

        // a ficha enviada sai do historico do navegador
        // senao o voltar do celular reabre a ficha em branco e enviar de novo cria outra anamnese
        try {
            if (isCorrection) {
                await apiService.put("/anamneses/" + anamnesisId, requestBody);
            } else {
                await apiService.post("/anamneses", requestBody);
            }
            const notice = isCorrection
                ? "Correção da anamnese salva. O seu prescritor já vê a ficha nova."
                : "Ficha de anamnese enviada. Obrigado por responder.";
            navigate(exitPath, {replace: true, state: {notice}});
        } catch (requestError) {
            const errorInfo = readRequestError(requestError);
            setFieldErrors(errorInfo.fieldErrors);
            setFormError(errorInfo.message || "Confira as respostas destacadas.");
            setIsSaving(false);
        }
    };

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (isLoading) {
        return <SkeletonPage cards={2}/>;
    }

    let submitText = isSaving ? "Enviando..." : "Enviar anamnese";
    if (isCorrection) {
        submitText = isSaving ? "Salvando..." : "Salvar correção";
    }

    return (
        <section className={styles.page}>
            <div className={styles.header}>
                <h1>{isCorrection ? "Corrigir anamnese" : "Anamnese"}</h1>
                {isCorrection ? (
                    <p>
                        Mude o que precisar e salve. A ficha corrigida substitui a anterior e a mudança fica
                        registrada no seu prontuário.
                    </p>
                ) : (
                    <p>
                        Estas perguntas ajudam o seu prescritor a conhecer sua saúde antes de começar o tratamento. Só
                        o motivo da consulta e a pergunta sobre o acompanhamento são obrigatórios.
                    </p>
                )}
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
                            onChange={updateAnswer}
                            error={fieldErrors.assessmentDate}
                        />
                        <TextField
                            label="Ocupação"
                            name="profession"
                            value={answers.profession}
                            onChange={updateAnswer}
                            error={fieldErrors.profession}
                        />
                    </div>
                    <TextAreaField
                        label="Motivo principal da consulta"
                        name="reasonForVisit"
                        rows={3}
                        value={answers.reasonForVisit}
                        onChange={updateAnswer}
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
                        onChange={updateAnswer}
                    />
                    <TextAreaField
                        label="Tratamentos anteriores"
                        name="previousTreatment"
                        rows={3}
                        value={answers.previousTreatment}
                        onChange={updateAnswer}
                    />
                    <TextAreaField
                        label="Medicações em uso"
                        name="currentMedication"
                        rows={3}
                        value={answers.currentMedication}
                        onChange={updateAnswer}
                    />
                    <TextAreaField
                        label="Doenças importantes na família"
                        name="familyHistory"
                        rows={2}
                        value={answers.familyHistory}
                        onChange={updateAnswer}
                    />
                    <TextAreaField
                        label="Reações ruins a medicamentos"
                        name="adverseReaction"
                        rows={2}
                        value={answers.adverseReaction}
                        onChange={updateAnswer}
                    />
                    <TextAreaField
                        label="Condições genéticas conhecidas"
                        name="geneticCondition"
                        rows={2}
                        value={answers.geneticCondition}
                        onChange={updateAnswer}
                    />
                </fieldset>

                <fieldset className={styles.section} disabled={isSaving}>
                    <legend>Hábitos</legend>
                    <TextField
                        label="Tipo de alimentação"
                        name="diet"
                        placeholder="Ex: onívora, vegetariana ou vegana"
                        value={answers.diet}
                        onChange={updateAnswer}
                    />
                    <div className={styles.row}>
                        <SelectField
                            label="Fuma?"
                            name="smokingHabits"
                            options={SMOKING_OPTIONS}
                            value={answers.smokingHabits}
                            onChange={updateAnswer}
                        />
                        <SelectField
                            label="Bebe álcool?"
                            name="alcoholConsumption"
                            options={ALCOHOL_OPTIONS}
                            value={answers.alcoholConsumption}
                            onChange={updateAnswer}
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
                            onChange={updateAnswer}
                            error={fieldErrors.weight}
                        />
                        <TextField
                            label="Altura (cm)"
                            name="height"
                            type="number"
                            inputMode="numeric"
                            value={answers.height}
                            onChange={updateAnswer}
                            error={fieldErrors.height}
                        />
                    </div>
                    <TextAreaField
                        label="Uso de outras substâncias"
                        name="substanceUse"
                        rows={2}
                        value={answers.substanceUse}
                        onChange={updateAnswer}
                    />
                    <TextAreaField
                        label="Exercícios físicos"
                        name="physicalActivity"
                        hint="Conte qual exercício e quantas vezes por semana."
                        rows={2}
                        value={answers.physicalActivity}
                        onChange={updateAnswer}
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
                        onChange={updateAnswer}
                    />
                    <TextAreaField
                        label="Ansiedade"
                        name="anxiety"
                        hint="Com que frequência aparece e com que intensidade."
                        rows={2}
                        value={answers.anxiety}
                        onChange={updateAnswer}
                    />
                    <TextAreaField
                        label="Dor"
                        name="pain"
                        hint="Onde dói, com que frequência e com que intensidade."
                        rows={2}
                        value={answers.pain}
                        onChange={updateAnswer}
                    />
                </fieldset>

                <fieldset className={styles.section} disabled={isSaving}>
                    <legend>Sobre o tratamento</legend>
                    <TextAreaField
                        label="O que você espera do tratamento"
                        name="expectations"
                        rows={3}
                        value={answers.expectations}
                        onChange={updateAnswer}
                    />
                    <SelectField
                        label="Você sabe que o tratamento precisa de acompanhamento regular?"
                        name="treatmentAwareness"
                        options={AWARENESS_OPTIONS}
                        placeholder="Escolha uma resposta"
                        value={answers.treatmentAwareness}
                        onChange={updateAnswer}
                        error={fieldErrors.treatmentAwareness}
                        required={true}
                    />
                    <TextAreaField
                        label="Algo mais que queira contar"
                        name="observation"
                        rows={3}
                        value={answers.observation}
                        onChange={updateAnswer}
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
                        {submitText}
                    </button>
                </div>
            </form>

            <ConfirmModal
                show={isConfirmingExit}
                title={isCorrection ? "Sair sem salvar" : "Sair sem enviar"}
                message={isCorrection
                    ? "O que você mudou ainda não foi salvo e vai se perder. Quer mesmo sair?"
                    : "O que você preencheu ainda não foi enviado e vai se perder. Quer mesmo sair?"}
                confirmText="Sim, sair"
                cancelText="Continuar preenchendo"
                onConfirm={() => navigate(exitPath)}
                onCancel={() => setIsConfirmingExit(false)}
            />
        </section>
    );
}
