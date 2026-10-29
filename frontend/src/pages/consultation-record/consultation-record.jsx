import {useEffect, useState} from "react";
import {useLocation, useNavigate, useParams} from "react-router";
import {FiCheckCircle} from "react-icons/fi";
import Card from "../../components/card/card.jsx";
import FormSection, {FieldRow, FormActions} from "../../components/form-section/form-section.jsx";
import SelectField from "../../components/form/select-field.jsx";
import TextAreaField from "../../components/form/text-area-field.jsx";
import TextField from "../../components/form/text-field.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, errorMessageOf, readRequestError} from "../../services/api.js";
import {MODALITY_OPTIONS} from "../../utils/appointment-labels.js";
import {ageFrom, nowInBrasilia} from "../../utils/date-format.js";
import {numberOrNull, textOrNull} from "../../utils/form-values.js";
import styles from "./consultation-record.module.css";

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

// agora em brasilia no formato do input datetime-local, q eh o agora da api
function nowForInput() {
    return nowInBrasilia().slice(0, 16);
}

function textOf(value) {
    return value === null || value === undefined ? "" : String(value);
}

// a consulta q veio da api vira o estado do formulario
// a consulta de hoje pode estar marcada pra mais tarde, e o campo n aceita hora futura
// entao ela entra limitada ao momento atual, q eh o q a api tbm aceita
function recordFromAppointment(appointment) {
    const now = nowForInput();
    const scheduled = appointment.dateTime ? appointment.dateTime.slice(0, 16) : "";
    return {
        dateTime: scheduled > now ? now : scheduled,
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

function requestBodyOf(record) {
    return {
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
}

// o motivo da tela abrir so pra leitura ou null qnd a consulta recebe registro
function blockedMessageOf(appointment) {
    if (appointment.annulled) {
        return "Esta consulta foi anulada e não pode ser alterada. Motivo: " + appointment.annulmentReason;
    }
    if (appointment.status === "CANCELADA") {
        return "Esta consulta foi cancelada e não recebe registro clínico.";
    }
    if (appointment.status === "SOLICITADA") {
        return "Este pedido de consulta ainda não foi confirmado na agenda. Confirme antes de registrar o atendimento.";
    }
    if (appointment.status === "RECUSADA") {
        return "Este pedido de consulta foi recusado e não recebe registro clínico.";
    }
    return null;
}

// registro clinico da consulta (RF04)
// abre vazio pra uma consulta nova ou com os dados de uma consulta q ja existe
export default function ConsultationRecord() {
    const navigate = useNavigate();
    const location = useLocation();
    const {patientId, appointmentId} = useParams();
    const isNewConsultation = appointmentId === undefined;
    // consulta nova q acabou de ser salva, ja no endereco dela
    const wasJustRegistered = Boolean(location.state && location.state.registered);

    const [patient, setPatient] = useState(null);
    const [record, setRecord] = useState(() => ({...EMPTY_RECORD, dateTime: nowForInput()}));
    // consulta anulada ou cancelada abre so pra leitura
    const [blockedMessage, setBlockedMessage] = useState(null);
    const [isUpdated, setIsUpdated] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [loadError, setLoadError] = useState(null);
    const [formError, setFormError] = useState(null);
    const [fieldErrors, setFieldErrors] = useState({});

    useEffect(() => {
        let isCurrentRequest = true;

        const loadPage = isNewConsultation
            ? apiService.get("/patients/" + patientId).then((foundPatient) => {
                if (isCurrentRequest) {
                    setPatient({id: foundPatient.id, name: foundPatient.name, birthDate: foundPatient.birthDate});
                }
            })
            : apiService.get("/appointments/" + appointmentId).then((appointment) => {
                if (!isCurrentRequest) {
                    return;
                }
                setPatient({id: appointment.patientId, name: appointment.patientName, birthDate: null});
                setRecord(recordFromAppointment(appointment));
                const message = blockedMessageOf(appointment);
                if (message !== null) {
                    setBlockedMessage(message);
                }
            });

        loadPage
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(errorMessageOf(requestError,
                        "Não foi possível carregar os dados. Confira sua internet e tente de novo."));
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

        const requestBody = requestBodyOf(record);
        try {
            if (isNewConsultation) {
                const savedAppointment = await apiService.post("/patients/" + patientId + "/appointments", requestBody);
                // o endereco de consulta nova vira o da consulta salva
                // senao voltar ou recarregar abre o formulario vazio e salvar de novo cria outra consulta
                navigate("/consulta/" + savedAppointment.id + "/registro", {replace: true, state: {registered: true}});
            } else {
                await apiService.put("/appointments/" + appointmentId + "/clinical-record", requestBody);
                setIsUpdated(true);
            }
        } catch (requestError) {
            const errorInfo = readRequestError(requestError);
            setFieldErrors(errorInfo.fieldErrors);
            if (errorInfo.message) {
                setFormError(errorInfo.message);
            }
        } finally {
            setIsSaving(false);
        }
    };

    if (isLoading) {
        return <SkeletonPage cards={1}/>;
    }

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (isUpdated || wasJustRegistered) {
        return (
            <section className={styles.page}>
                <PageHeader
                    title={wasJustRegistered ? "Consulta registrada" : "Registro atualizado"}
                    subtitle={patient.name}
                />
                <Card>
                    <div className={styles.success}>
                        <FiCheckCircle className={styles.successIcon} aria-hidden="true"/>
                        <p>O registro clínico foi salvo. O que você quer fazer agora?</p>
                    </div>
                    <div className={styles.nextSteps}>
                        <button
                            type="button"
                            className="button"
                            onClick={() => navigate("/consulta/" + appointmentId + "/prescricao")}
                        >
                            Emitir prescrição
                        </button>
                        <button
                            type="button"
                            className="button-secondary"
                            onClick={() => navigate("/consulta/" + appointmentId + "/mini-exame")}
                        >
                            Aplicar mini-exame
                        </button>
                        <button
                            type="button"
                            className="button-tertiary"
                            onClick={() => navigate("/paciente/" + patient.id + "/historico")}
                        >
                            Ver histórico do paciente
                        </button>
                    </div>
                </Card>
            </section>
        );
    }

    const age = ageFrom(patient.birthDate);
    const isReadOnly = blockedMessage !== null;
    const areFieldsLocked = isReadOnly || isSaving;
    // a clinica n mede sinais vitais em consulta remota, mas registro q ja tem algum n pode sumir da tela
    const hasVitalSigns = record.bloodPressure !== "" || record.weight !== "" || record.height !== "";
    const showsVitalSigns = record.modality !== "REMOTA" || hasVitalSigns;

    return (
        <section className={styles.page}>
            <PageHeader
                title={isNewConsultation ? "Nova consulta" : "Registro da consulta"}
                subtitle={patient.name + (age !== null ? " · " + age + " anos" : "")}
            />

            {blockedMessage && <p className="aviso aviso--atencao">{blockedMessage}</p>}
            {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}

            <form className={styles.form} onSubmit={handleSubmit}>
                <FormSection title="Quando e como" disabled={areFieldsLocked}>
                    <FieldRow>
                        <TextField
                            label="Data e hora"
                            name="dateTime"
                            type="datetime-local"
                            value={record.dateTime}
                            max={nowForInput()}
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
                    </FieldRow>
                </FormSection>

                <FormSection title="Queixa e exame" disabled={areFieldsLocked}>
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
                    {showsVitalSigns && (
                        <FieldRow>
                            <TextField
                                label="Pressão arterial (opcional)"
                                name="bloodPressure"
                                placeholder="120/80"
                                value={record.bloodPressure}
                                onChange={(event) => updateField("bloodPressure", event.target.value)}
                                error={fieldErrors.bloodPressure}
                            />
                            <TextField
                                label="Peso em kg (opcional)"
                                name="weight"
                                type="number"
                                inputMode="decimal"
                                step="0.1"
                                value={record.weight}
                                onChange={(event) => updateField("weight", event.target.value)}
                                error={fieldErrors.weight}
                            />
                            <TextField
                                label="Altura em cm (opcional)"
                                name="height"
                                type="number"
                                inputMode="numeric"
                                value={record.height}
                                onChange={(event) => updateField("height", event.target.value)}
                                error={fieldErrors.height}
                            />
                        </FieldRow>
                    )}
                </FormSection>

                <FormSection title="Avaliação" disabled={areFieldsLocked}>
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
                </FormSection>

                <FormSection title="Conduta" disabled={areFieldsLocked}>
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
                </FormSection>

                <FormActions>
                    <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                        Voltar
                    </button>
                    {!isReadOnly && (
                        <button type="submit" className="button" disabled={isSaving}>
                            {isSaving ? "Salvando..." : "Salvar consulta"}
                        </button>
                    )}
                </FormActions>
            </form>
        </section>
    );
}
