import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
import SelectField from "../../components/form/select-field.jsx";
import TextAreaField from "../../components/form/text-area-field.jsx";
import TextField from "../../components/form/text-field.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {
    ADMINISTRATION_ROUTE_OPTIONS,
    CANNABINOID_OPTIONS,
    compositionSummary,
    SPECTRUM_OPTIONS,
    UNIT_OPTIONS,
} from "../../utils/prescription-labels.js";
import styles from "./prescription-form.module.css";

const EMPTY_FORM = {
    productDescription: "",
    brand: "",
    batch: "",
    spectrum: "",
    volume: "",
    posology: "",
    administrationRoute: "",
    instructions: "",
    precautions: "",
    expectedEffects: "",
    treatmentDurationDays: "",
    nextConsultationDate: "",
};

function newComponent() {
    return {cannabinoid: "CBD", concentration: "", unit: "PERCENTUAL"};
}

function newEscalationStep() {
    return {dosage: "", note: ""};
}

// o formulario guarda texto vazio e a api espera null
function textOrNull(text) {
    return text.trim() === "" ? null : text;
}

function numberOrNull(text) {
    return text === "" ? null : Number(text);
}

function formatDate(isoDateTime) {
    return new Date(isoDateTime).toLocaleDateString("pt-BR");
}

// o erro de um canabinoide chega com o nome tipo components[0].concentration
function compositionErrorOf(fieldErrors) {
    const errorField = Object.keys(fieldErrors).find((field) => field.startsWith("components"));
    return errorField ? fieldErrors[errorField] : null;
}

// emissao de prescricao dentro de uma consulta (RF05)
// a prescricao nova substitui a vigente do paciente e a anterior fica no historico
export default function PrescriptionForm() {
    const navigate = useNavigate();
    const {appointmentId} = useParams();

    const [appointment, setAppointment] = useState(null);
    const [currentPrescription, setCurrentPrescription] = useState(null);
    const [form, setForm] = useState(EMPTY_FORM);
    const [components, setComponents] = useState(() => [newComponent()]);
    const [escalationSteps, setEscalationSteps] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [wasIssued, setWasIssued] = useState(false);
    const [loadError, setLoadError] = useState(null);
    const [formError, setFormError] = useState(null);
    const [fieldErrors, setFieldErrors] = useState({});

    useEffect(() => {
        let isCurrentRequest = true;

        const loadPage = async () => {
            const foundAppointment = await apiService.get("/appointments/" + appointmentId);
            // a vigente aparece pra prescritora saber o q vai ser substituido
            const prescriptions = await apiService.get("/patients/" + foundAppointment.patientId + "/prescriptions");
            if (isCurrentRequest) {
                setAppointment(foundAppointment);
                setCurrentPrescription(prescriptions.find((prescription) => prescription.current) || null);
            }
        };

        loadPage()
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível carregar a consulta. Confira sua internet e tente de novo.");
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
    }, [appointmentId]);

    const updateField = (fieldName, value) => {
        setForm((currentForm) => ({...currentForm, [fieldName]: value}));
    };

    const updateComponent = (index, fieldName, value) => {
        setComponents((currentComponents) => currentComponents.map((component, componentIndex) => (
            componentIndex === index ? {...component, [fieldName]: value} : component
        )));
    };

    const removeComponent = (index) => {
        setComponents((currentComponents) => currentComponents.filter(
            (component, componentIndex) => componentIndex !== index));
    };

    const updateEscalationStep = (index, fieldName, value) => {
        setEscalationSteps((currentSteps) => currentSteps.map((step, stepIndex) => (
            stepIndex === index ? {...step, [fieldName]: value} : step
        )));
    };

    const removeEscalationStep = (index) => {
        setEscalationSteps((currentSteps) => currentSteps.filter((step, stepIndex) => stepIndex !== index));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setIsSaving(true);
        setFormError(null);
        setFieldErrors({});

        const requestBody = {
            productDescription: form.productDescription,
            brand: textOrNull(form.brand),
            batch: textOrNull(form.batch),
            spectrum: form.spectrum === "" ? null : form.spectrum,
            components: components.map((component) => ({
                cannabinoid: component.cannabinoid,
                concentration: numberOrNull(component.concentration),
                unit: component.unit,
            })),
            volume: textOrNull(form.volume),
            posology: form.posology,
            administrationRoute: form.administrationRoute === "" ? null : form.administrationRoute,
            // semana sem dose fica de fora e a numeracao segue a ordem da lista
            escalationSteps: escalationSteps
                .filter((step) => step.dosage.trim() !== "")
                .map((step, index) => ({week: index + 1, dosage: step.dosage, note: textOrNull(step.note)})),
            instructions: textOrNull(form.instructions),
            precautions: textOrNull(form.precautions),
            expectedEffects: textOrNull(form.expectedEffects),
            treatmentDurationDays: numberOrNull(form.treatmentDurationDays),
            nextConsultationDate: form.nextConsultationDate === "" ? null : form.nextConsultationDate,
        };

        try {
            await apiService.post("/appointments/" + appointmentId + "/prescriptions", requestBody);
            setWasIssued(true);
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                const errorsByField = requestError.fieldErrors();
                setFieldErrors(errorsByField);
                if (Object.keys(errorsByField).length === 0) {
                    setFormError(requestError.message);
                } else {
                    setFormError("Confira os campos destacados.");
                }
            } else {
                setFormError("Não foi possível falar com o servidor. Confira sua internet e tente de novo.");
            }
        } finally {
            setIsSaving(false);
        }
    };

    if (isLoading) {
        return <p className={styles.status}>Carregando...</p>;
    }

    if (loadError) {
        return <p className="aviso aviso--atencao">{loadError}</p>;
    }

    if (wasIssued) {
        return (
            <section className={styles.page}>
                <div className={styles.header}>
                    <h1>Prescrição emitida</h1>
                    <p>{appointment.patientName}</p>
                </div>
                <p className="aviso">
                    A prescrição agora é a vigente do paciente.
                    {currentPrescription ? " A anterior passou para o histórico." : ""}
                </p>
                <div className={styles.actions}>
                    <button
                        type="button"
                        className={styles.primaryButton}
                        onClick={() => navigate("/paciente/" + appointment.patientId + "/historico")}
                    >
                        Ver histórico do paciente
                    </button>
                    <button type="button" className={styles.secondaryButton} onClick={() => navigate("/painel-prescritor")}>
                        Voltar ao painel
                    </button>
                </div>
            </section>
        );
    }

    let blockedMessage = null;
    if (appointment.annulled) {
        blockedMessage = "Esta consulta foi anulada e não gera prescrição.";
    } else if (appointment.status === "CANCELADA") {
        blockedMessage = "Esta consulta foi cancelada e não gera prescrição.";
    } else if (appointment.status === "SOLICITADA" || appointment.status === "RECUSADA") {
        blockedMessage = "Só consulta confirmada na agenda gera prescrição.";
    }
    const compositionError = compositionErrorOf(fieldErrors);

    return (
        <section className={styles.page}>
            <div className={styles.header}>
                <h1>Nova prescrição</h1>
                <p>{appointment.patientName} · consulta de {formatDate(appointment.dateTime)}</p>
            </div>

            {blockedMessage && <p className="aviso aviso--atencao">{blockedMessage}</p>}
            {!blockedMessage && currentPrescription && (
                <p className="aviso">
                    A prescrição vigente é {currentPrescription.productDescription} (
                    {compositionSummary(currentPrescription.components)}). A nova vai substituir essa, que continua
                    no histórico.
                </p>
            )}
            {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}

            {!blockedMessage && (
                <form className={styles.form} onSubmit={handleSubmit}>
                    <fieldset className={styles.section} disabled={isSaving}>
                        <legend>Produto</legend>
                        <TextField
                            label="Produto"
                            name="productDescription"
                            placeholder="Ex: Óleo full spectrum"
                            value={form.productDescription}
                            onChange={(event) => updateField("productDescription", event.target.value)}
                            error={fieldErrors.productDescription}
                            required={true}
                        />
                        <div className={styles.row}>
                            <TextField
                                label="Marca (opcional)"
                                name="brand"
                                value={form.brand}
                                onChange={(event) => updateField("brand", event.target.value)}
                            />
                            <TextField
                                label="Lote (opcional)"
                                name="batch"
                                value={form.batch}
                                onChange={(event) => updateField("batch", event.target.value)}
                            />
                        </div>
                        <div className={styles.row}>
                            <SelectField
                                label="Espectro"
                                name="spectrum"
                                options={SPECTRUM_OPTIONS}
                                placeholder="Escolha o espectro"
                                value={form.spectrum}
                                onChange={(event) => updateField("spectrum", event.target.value)}
                                error={fieldErrors.spectrum}
                                required={true}
                            />
                            <TextField
                                label="Volume do frasco em mL (opcional)"
                                name="volume"
                                type="number"
                                inputMode="numeric"
                                min="1"
                                value={form.volume}
                                onChange={(event) => updateField("volume", event.target.value)}
                            />
                        </div>
                    </fieldset>

                    <fieldset className={styles.section} disabled={isSaving}>
                        <legend>Composição do óleo</legend>
                        <p className={styles.hint}>Um canabinoide por linha, cada um com a própria concentração.</p>
                        {components.map((component, index) => (
                            <div key={index} className={styles.componentRow}>
                                <SelectField
                                    label="Canabinoide"
                                    name={"component-" + index + "-cannabinoid"}
                                    options={CANNABINOID_OPTIONS}
                                    value={component.cannabinoid}
                                    onChange={(event) => updateComponent(index, "cannabinoid", event.target.value)}
                                />
                                <TextField
                                    label="Concentração"
                                    name={"component-" + index + "-concentration"}
                                    type="number"
                                    inputMode="decimal"
                                    step="0.001"
                                    min="0"
                                    value={component.concentration}
                                    onChange={(event) => updateComponent(index, "concentration", event.target.value)}
                                    required={true}
                                />
                                <SelectField
                                    label="Unidade"
                                    name={"component-" + index + "-unit"}
                                    options={UNIT_OPTIONS}
                                    value={component.unit}
                                    onChange={(event) => updateComponent(index, "unit", event.target.value)}
                                />
                                {components.length > 1 && (
                                    <button
                                        type="button"
                                        className={styles.removeButton}
                                        onClick={() => removeComponent(index)}
                                        aria-label={"Tirar o canabinoide da linha " + (index + 1)}
                                    >
                                        Tirar
                                    </button>
                                )}
                            </div>
                        ))}
                        {compositionError && <p className={styles.fieldError}>{compositionError}</p>}
                        <button
                            type="button"
                            className={styles.secondaryButton}
                            onClick={() => setComponents((currentComponents) => [...currentComponents, newComponent()])}
                        >
                            Adicionar canabinoide
                        </button>
                    </fieldset>

                    <fieldset className={styles.section} disabled={isSaving}>
                        <legend>Posologia</legend>
                        <TextAreaField
                            label="Posologia"
                            name="posology"
                            rows={3}
                            placeholder="Ex: 2 gotas sublinguais à noite"
                            value={form.posology}
                            onChange={(event) => updateField("posology", event.target.value)}
                            error={fieldErrors.posology}
                            required={true}
                        />
                        <SelectField
                            label="Via de administração (opcional)"
                            name="administrationRoute"
                            options={ADMINISTRATION_ROUTE_OPTIONS}
                            placeholder="Escolha a via"
                            value={form.administrationRoute}
                            onChange={(event) => updateField("administrationRoute", event.target.value)}
                        />

                        <h3 className={styles.subTitle}>Escalonamento de dose</h3>
                        {escalationSteps.length === 0 && (
                            <p className={styles.hint}>Se a dose for subir aos poucos, adicione uma linha por semana.</p>
                        )}
                        {escalationSteps.map((step, index) => (
                            <div key={index} className={styles.stepRow}>
                                <TextField
                                    label={"Semana " + (index + 1)}
                                    name={"step-" + index + "-dosage"}
                                    placeholder="Ex: 3 gotas à noite"
                                    value={step.dosage}
                                    onChange={(event) => updateEscalationStep(index, "dosage", event.target.value)}
                                />
                                <TextField
                                    label="Observação (opcional)"
                                    name={"step-" + index + "-note"}
                                    value={step.note}
                                    onChange={(event) => updateEscalationStep(index, "note", event.target.value)}
                                />
                                <button
                                    type="button"
                                    className={styles.removeButton}
                                    onClick={() => removeEscalationStep(index)}
                                    aria-label={"Tirar a semana " + (index + 1)}
                                >
                                    Tirar
                                </button>
                            </div>
                        ))}
                        <button
                            type="button"
                            className={styles.secondaryButton}
                            onClick={() => setEscalationSteps((currentSteps) => [...currentSteps, newEscalationStep()])}
                        >
                            Adicionar semana
                        </button>
                    </fieldset>

                    <fieldset className={styles.section} disabled={isSaving}>
                        <legend>Orientações ao paciente</legend>
                        <TextAreaField
                            label="Instruções de uso"
                            name="instructions"
                            value={form.instructions}
                            onChange={(event) => updateField("instructions", event.target.value)}
                        />
                        <TextAreaField
                            label="Precauções e contraindicações"
                            name="precautions"
                            rows={3}
                            value={form.precautions}
                            onChange={(event) => updateField("precautions", event.target.value)}
                        />
                        <TextAreaField
                            label="Efeitos esperados"
                            name="expectedEffects"
                            rows={3}
                            value={form.expectedEffects}
                            onChange={(event) => updateField("expectedEffects", event.target.value)}
                        />
                    </fieldset>

                    <fieldset className={styles.section} disabled={isSaving}>
                        <legend>Acompanhamento</legend>
                        <div className={styles.row}>
                            <TextField
                                label="Duração do tratamento em dias"
                                name="treatmentDurationDays"
                                type="number"
                                inputMode="numeric"
                                min="1"
                                value={form.treatmentDurationDays}
                                onChange={(event) => updateField("treatmentDurationDays", event.target.value)}
                                error={fieldErrors.treatmentDurationDays}
                            />
                            <TextField
                                label="Próxima consulta"
                                name="nextConsultationDate"
                                type="date"
                                value={form.nextConsultationDate}
                                onChange={(event) => updateField("nextConsultationDate", event.target.value)}
                            />
                        </div>
                    </fieldset>

                    <div className={styles.actions}>
                        <button type="button" className={styles.secondaryButton} onClick={() => navigate(-1)}>
                            Voltar
                        </button>
                        <button type="submit" className={styles.primaryButton} disabled={isSaving}>
                            {isSaving ? "Emitindo..." : "Emitir prescrição"}
                        </button>
                    </div>
                </form>
            )}
        </section>
    );
}
