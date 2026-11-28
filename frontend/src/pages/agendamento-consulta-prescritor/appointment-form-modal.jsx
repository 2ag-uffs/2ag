import {useState} from "react";
import BusyButton from "../../components/form/busy-button.jsx";
import {useFocusFirstError} from "../../components/form/focus-first-error.js";
import SelectField from "../../components/form/select-field.jsx";
import TextField from "../../components/form/text-field.jsx";
import Modal from "../../components/modal/modal.jsx";
import {readRequestError} from "../../services/api.js";
import {APPOINTMENT_TYPE_OPTIONS, DURATION_OPTIONS, MODALITY_OPTIONS} from "../../utils/appointment-labels.js";
import styles from "./agendamento-consulta-prescritor.module.css";

// formulario de marcar ou remarcar consulta dentro de um modal
// quem usa diz o q fazer com os dados e o erro da api aparece aqui dentro
// a lista de pacientes so vem quando eh consulta nova
// tipo e duracao vazios vao como nulo e quem decide eh a api: pelo historico na consulta nova
// e mantendo o q era na remarcacao
export default function AppointmentFormModal({
    title,
    submitLabel,
    patients,
    initialValues,
    durationsByType,
    typePlaceholder,
    durationPlaceholder,
    onSubmit,
    onClose,
}) {
    const [values, setValues] = useState(initialValues);
    const [fieldErrors, setFieldErrors] = useState({});
    const [formError, setFormError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const [formRef, focusFirstError] = useFocusFirstError();

    const updateValue = (fieldName, value) => {
        setValues((currentValues) => ({...currentValues, [fieldName]: value}));
    };

    // escolher o tipo ja traz a duracao dele dos horarios de atendimento
    const updateType = (appointmentType) => {
        const typeDuration = appointmentType === "" ? "" : String(durationsByType[appointmentType]);
        setValues((currentValues) => ({...currentValues, appointmentType, durationMinutes: typeDuration}));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setFieldErrors({});
        setFormError(null);

        // o q falta fica marcado no proprio campo e o foco vai pro primeiro deles (CT-39)
        const missingFields = {};
        if (patients && values.patientId === "") {
            missingFields.patientId = "Escolha o paciente";
        }
        if (values.date === "") {
            missingFields.date = "Escolha a data";
        }
        if (values.time === "") {
            missingFields.time = "Escolha o horário";
        }
        // a modalidade vem vazia na consulta nova e o prescritor escolhe (decisao de 14/11/2026, revisao das regras R24)
        if (values.modality === "") {
            missingFields.modality = "Escolha se a consulta é presencial ou remota";
        }
        if (Object.keys(missingFields).length > 0) {
            setFieldErrors(missingFields);
            focusFirstError();
            return;
        }

        setIsSaving(true);
        try {
            await onSubmit(values);
        } catch (requestError) {
            const errorInfo = readRequestError(requestError);
            setFieldErrors(errorInfo.fieldErrors);
            if (errorInfo.message) {
                setFormError(errorInfo.message);
            }
            setIsSaving(false);
            focusFirstError();
        }
    };

    // com o envio no ar o X e o Esc n fecham, como o Voltar: a recusa da api ia pra um modal q ja tinha sumido
    const closeModal = () => {
        if (!isSaving) {
            onClose();
        }
    };

    return (
        <Modal show={true} title={title} onClickClose={closeModal}>
            <form className={styles.modalForm} onSubmit={handleSubmit} noValidate ref={formRef}>
                {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}
                {patients && (
                    <SelectField
                        label="Paciente"
                        name="patientId"
                        options={patients.map((patient) => ({value: String(patient.id), label: patient.name}))}
                        placeholder="Escolha o paciente"
                        value={values.patientId}
                        onChange={(event) => updateValue("patientId", event.target.value)}
                        error={fieldErrors.patientId}
                        disabled={isSaving}
                    />
                )}
                <div className={styles.formRow}>
                    <TextField
                        label="Data"
                        name="appointmentDate"
                        type="date"
                        value={values.date}
                        onChange={(event) => updateValue("date", event.target.value)}
                        error={fieldErrors.date || fieldErrors.dateTime}
                        disabled={isSaving}
                    />
                    <TextField
                        label="Horário"
                        name="appointmentTime"
                        type="time"
                        step="300"
                        value={values.time}
                        onChange={(event) => updateValue("time", event.target.value)}
                        error={fieldErrors.time}
                        disabled={isSaving}
                    />
                </div>
                <div className={styles.formRow}>
                    <SelectField
                        label="Tipo"
                        name="appointmentType"
                        options={APPOINTMENT_TYPE_OPTIONS}
                        placeholder={typePlaceholder}
                        value={values.appointmentType}
                        onChange={(event) => updateType(event.target.value)}
                        error={fieldErrors.appointmentType}
                        disabled={isSaving}
                    />
                    <SelectField
                        label="Duração"
                        name="durationMinutes"
                        options={DURATION_OPTIONS}
                        placeholder={durationPlaceholder}
                        value={values.durationMinutes}
                        onChange={(event) => updateValue("durationMinutes", event.target.value)}
                        error={fieldErrors.durationMinutes}
                        disabled={isSaving}
                    />
                </div>
                <SelectField
                    label="Modalidade"
                    name="modality"
                    options={MODALITY_OPTIONS}
                    placeholder="Escolha a modalidade"
                    value={values.modality}
                    onChange={(event) => updateValue("modality", event.target.value)}
                    error={fieldErrors.modality}
                    disabled={isSaving}
                />
                <div className={styles.modalActions}>
                    <button type="button" className="button-secondary" onClick={onClose} disabled={isSaving}>
                        Voltar
                    </button>
                    <BusyButton type="submit" className="button" isBusy={isSaving} busyText="Salvando...">
                        {submitLabel}
                    </BusyButton>
                </div>
            </form>
        </Modal>
    );
}
