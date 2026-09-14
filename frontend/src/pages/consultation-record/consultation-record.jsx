import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
import SelectField from "../../components/form/select-field.jsx";
import TextAreaField from "../../components/form/text-area-field.jsx";
import TextField from "../../components/form/text-field.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {ageFrom} from "../../utils/date-format.js";
import styles from "./consultation-record.module.css";

const MODALITY_OPTIONS = [
    {value: "PRESENCIAL", label: "Presencial"},
    {value: "REMOTA", label: "Remota"},
];

const EMPTY_RECORD = {
    dateTime: "",
    modality: "PRESENCIAL",
    clinicalObservation: "",
    physicalExam: "",
    bloodPressure: "",
    weight: "",
    height: "",
    evolution: "",
    diagnosis: "",
    therapeuticPlan: "",
    complementaryExams: "",
};

// data e hora no formato do input datetime-local sem passar por utc
// o toISOString adiantaria tres horas no horario de brasilia
function toInputDateTime(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    const hours = String(date.getHours()).padStart(2, "0");
    const minutes = String(date.getMinutes()).padStart(2, "0");
    return year + "-" + month + "-" + day + "T" + hours + ":" + minutes;
}

// o formulario guarda texto vazio e a api espera null
function textOrNull(text) {
    return text.trim() === "" ? null : text;
}

function numberOrNull(text) {
    return text === "" ? null : Number(text);
}

function textOf(value) {
    return value === null || value === undefined ? "" : String(value);
}

// a consulta q veio da api vira o estado do formulario
function recordFromAppointment(appointment) {
    return {
        dateTime: appointment.dateTime ? appointment.dateTime.slice(0, 16) : "",
        modality: appointment.modality || "PRESENCIAL",
        clinicalObservation: textOf(appointment.clinicalObservation),
        physicalExam: textOf(appointment.physicalExam),
        bloodPressure: textOf(appointment.bloodPressure),
        weight: textOf(appointment.weight),
        height: textOf(appointment.height),
        evolution: textOf(appointment.evolution),
        diagnosis: textOf(appointment.diagnosis),
        therapeuticPlan: textOf(appointment.therapeuticPlan),
        complementaryExams: textOf(appointment.complementaryExams),
    };
}

// registro clinico da consulta (RF04)
// abre vazio pra uma consulta nova ou com os dados de uma consulta q ja existe
export default function ConsultationRecord() {
    const navigate = useNavigate();
    const {patientId, appointmentId} = useParams();
    const isNewConsultation = appointmentId === undefined;

    const [patient, setPatient] = useState(null);
    const [record, setRecord] = useState(() => ({...EMPTY_RECORD, dateTime: toInputDateTime(new Date())}));
    // consulta anulada ou cancelada abre so pra leitura
    const [blockedMessage, setBlockedMessage] = useState(null);
    const [savedAppointmentId, setSavedAppointmentId] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [loadError, setLoadError] = useState(null);
    const [formError, setFormError] = useState(null);
    const [fieldErrors, setFieldErrors] = useState({});

    useEffect(() => {
        let isCurrentRequest = true;

        const loadPage = isNewConsultation
            ? apiService.get("/paciente/" + patientId).then((foundPatient) => {
                if (isCurrentRequest) {
                    setPatient({id: foundPatient.id, name: foundPatient.name, birthDate: foundPatient.birthDate});
                }
            })
            : apiService.get("/consulta/" + appointmentId).then((appointment) => {
                if (!isCurrentRequest) {
                    return;
                }
                setPatient({id: appointment.patientId, name: appointment.patientName, birthDate: null});
                setRecord(recordFromAppointment(appointment));
                if (appointment.annulled) {
                    setBlockedMessage("Esta consulta foi anulada e não pode ser alterada. Motivo: "
                        + appointment.annulmentReason);
                } else if (appointment.status === "CANCELADA") {
                    setBlockedMessage("Esta consulta foi cancelada e não recebe registro clínico.");
                } else if (appointment.status === "SOLICITADA") {
                    setBlockedMessage("Este pedido de consulta ainda não foi confirmado na agenda. Confirme antes de registrar o atendimento.");
                } else if (appointment.status === "RECUSADA") {
                    setBlockedMessage("Este pedido de consulta foi recusado e não recebe registro clínico.");
                }
            });

        loadPage
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível carregar os dados. Confira sua internet e tente de novo.");
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
    }, [isNewConsultation, patientId, appointmentId]);

    const updateField = (fieldName, value) => {
        setRecord((currentRecord) => ({...currentRecord, [fieldName]: value}));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setIsSaving(true);
        setFormError(null);
        setFieldErrors({});

        const requestBody = {
            dateTime: record.dateTime === "" ? null : record.dateTime + ":00",
            modality: record.modality,
            clinicalObservation: textOrNull(record.clinicalObservation),
            physicalExam: textOrNull(record.physicalExam),
            evolution: textOrNull(record.evolution),
            diagnosis: textOrNull(record.diagnosis),
            therapeuticPlan: textOrNull(record.therapeuticPlan),
            complementaryExams: textOrNull(record.complementaryExams),
            bloodPressure: textOrNull(record.bloodPressure),
            weight: numberOrNull(record.weight),
            height: numberOrNull(record.height),
        };

        try {
            const savedAppointment = isNewConsultation
                ? await apiService.post("/pacientes/" + patientId + "/consultas", requestBody)
                : await apiService.put("/consulta/" + appointmentId + "/registro-clinico", requestBody);
            setSavedAppointmentId(savedAppointment.id);
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                const errorsByField = requestError.fieldErrors();
                setFieldErrors(errorsByField);
                if (Object.keys(errorsByField).length === 0) {
                    setFormError(requestError.message);
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

    if (savedAppointmentId !== null) {
        return (
            <section className={styles.page}>
                <div className={styles.header}>
                    <h1>{isNewConsultation ? "Consulta registrada" : "Registro atualizado"}</h1>
                    <p>{patient.name}</p>
                </div>
                <p className="aviso">O registro clínico foi salvo. O que você quer fazer agora?</p>
                <div className={styles.nextSteps}>
                    <button
                        type="button"
                        className={styles.primaryButton}
                        onClick={() => navigate("/consulta/" + savedAppointmentId + "/prescricao")}
                    >
                        Emitir prescrição
                    </button>
                    <button
                        type="button"
                        className={styles.secondaryButton}
                        onClick={() => navigate("/consulta/" + savedAppointmentId + "/mini-exame")}
                    >
                        Aplicar mini-exame
                    </button>
                    <button
                        type="button"
                        className={styles.secondaryButton}
                        onClick={() => navigate("/paciente/" + patient.id + "/historico")}
                    >
                        Ver histórico do paciente
                    </button>
                </div>
            </section>
        );
    }

    const age = ageFrom(patient.birthDate);
    const isReadOnly = blockedMessage !== null;

    return (
        <section className={styles.page}>
            <div className={styles.header}>
                <h1>{isNewConsultation ? "Nova consulta" : "Registro da consulta"}</h1>
                <p>{patient.name}{age !== null ? " · " + age + " anos" : ""}</p>
            </div>

            {blockedMessage && <p className="aviso aviso--atencao">{blockedMessage}</p>}
            {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}

            <form className={styles.form} onSubmit={handleSubmit}>
                <fieldset className={styles.section} disabled={isReadOnly || isSaving}>
                    <legend>Quando e como</legend>
                    <div className={styles.row}>
                        <TextField
                            label="Data e hora"
                            name="dateTime"
                            type="datetime-local"
                            value={record.dateTime}
                            max={toInputDateTime(new Date())}
                            onChange={(event) => updateField("dateTime", event.target.value)}
                            error={fieldErrors.dateTime}
                        />
                        <SelectField
                            label="Modalidade"
                            name="modality"
                            options={MODALITY_OPTIONS}
                            value={record.modality}
                            onChange={(event) => updateField("modality", event.target.value)}
                            error={fieldErrors.modality}
                        />
                    </div>
                </fieldset>

                <fieldset className={styles.section} disabled={isReadOnly || isSaving}>
                    <legend>Queixa e exame</legend>
                    <TextAreaField
                        label="Queixa principal e observações"
                        name="clinicalObservation"
                        value={record.clinicalObservation}
                        onChange={(event) => updateField("clinicalObservation", event.target.value)}
                        error={fieldErrors.clinicalObservation}
                    />
                    <TextAreaField
                        label="Exame físico"
                        name="physicalExam"
                        value={record.physicalExam}
                        onChange={(event) => updateField("physicalExam", event.target.value)}
                        error={fieldErrors.physicalExam}
                    />
                    <div className={styles.row}>
                        <TextField
                            label="Pressão arterial"
                            name="bloodPressure"
                            placeholder="120/80"
                            value={record.bloodPressure}
                            onChange={(event) => updateField("bloodPressure", event.target.value)}
                            error={fieldErrors.bloodPressure}
                        />
                        <TextField
                            label="Peso (kg)"
                            name="weight"
                            type="number"
                            inputMode="decimal"
                            step="0.1"
                            value={record.weight}
                            onChange={(event) => updateField("weight", event.target.value)}
                            error={fieldErrors.weight}
                        />
                        <TextField
                            label="Altura (cm)"
                            name="height"
                            type="number"
                            inputMode="numeric"
                            value={record.height}
                            onChange={(event) => updateField("height", event.target.value)}
                            error={fieldErrors.height}
                        />
                    </div>
                </fieldset>

                <fieldset className={styles.section} disabled={isReadOnly || isSaving}>
                    <legend>Avaliação</legend>
                    <TextAreaField
                        label="Evolução do quadro"
                        name="evolution"
                        value={record.evolution}
                        onChange={(event) => updateField("evolution", event.target.value)}
                        error={fieldErrors.evolution}
                    />
                    <TextAreaField
                        label="Hipótese diagnóstica"
                        name="diagnosis"
                        rows={3}
                        value={record.diagnosis}
                        onChange={(event) => updateField("diagnosis", event.target.value)}
                        error={fieldErrors.diagnosis}
                    />
                </fieldset>

                <fieldset className={styles.section} disabled={isReadOnly || isSaving}>
                    <legend>Conduta</legend>
                    <TextAreaField
                        label="Conduta e plano terapêutico"
                        name="therapeuticPlan"
                        value={record.therapeuticPlan}
                        onChange={(event) => updateField("therapeuticPlan", event.target.value)}
                        error={fieldErrors.therapeuticPlan}
                    />
                    <TextAreaField
                        label="Exames complementares"
                        name="complementaryExams"
                        rows={3}
                        value={record.complementaryExams}
                        onChange={(event) => updateField("complementaryExams", event.target.value)}
                        error={fieldErrors.complementaryExams}
                    />
                </fieldset>

                <div className={styles.actions}>
                    <button type="button" className={styles.secondaryButton} onClick={() => navigate(-1)}>
                        Voltar
                    </button>
                    {!isReadOnly && (
                        <button type="submit" className={styles.primaryButton} disabled={isSaving}>
                            {isSaving ? "Salvando..." : "Salvar consulta"}
                        </button>
                    )}
                </div>
            </form>
        </section>
    );
}
