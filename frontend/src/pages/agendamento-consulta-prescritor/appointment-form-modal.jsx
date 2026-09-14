import {useState} from "react";
import SelectField from "../../components/form/select-field.jsx";
import TextField from "../../components/form/text-field.jsx";
import Modal from "../../components/modal/modal.jsx";
import {ApiError} from "../../services/api.js";
import {DURATION_OPTIONS, MODALITY_OPTIONS} from "../../utils/appointment-labels.js";
import styles from "./agendamento-consulta-prescritor.module.css";

// formulario de marcar ou remarcar consulta dentro de um modal
// quem usa diz o q fazer com os dados e o erro da api aparece aqui dentro
// a lista de pacientes so vem quando eh consulta nova
export default function AppointmentFormModal({title, submitLabel, patients, initialValues, onSubmit, onClose}) {
    const [values, setValues] = useState(initialValues);
    const [fieldErrors, setFieldErrors] = useState({});
    const [formError, setFormError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);

    const updateValue = (fieldName, value) => {
        setValues((currentValues) => ({...currentValues, [fieldName]: value}));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setFieldErrors({});
        setFormError(null);

        if (patients && values.patientId === "") {
            setFieldErrors({patientId: "Escolha o paciente"});
            return;
        }
        if (values.date === "" || values.time === "") {
            setFormError("Escolha a data e o horário.");
            return;
        }

        setIsSaving(true);
        try {
            await onSubmit(values);
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
            setIsSaving(false);
        }
    };

    return (
        <Modal show={true} title={title} onClickClose={onClose}>
            <form className={styles.modalForm} onSubmit={handleSubmit} noValidate>
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
                        error={fieldErrors.dateTime}
                        disabled={isSaving}
                    />
                    <TextField
                        label="Horário"
                        name="appointmentTime"
                        type="time"
                        step="300"
                        value={values.time}
                        onChange={(event) => updateValue("time", event.target.value)}
                        disabled={isSaving}
                    />
                </div>
                <div className={styles.formRow}>
                    <SelectField
                        label="Modalidade"
                        name="modality"
                        options={MODALITY_OPTIONS}
                        value={values.modality}
                        onChange={(event) => updateValue("modality", event.target.value)}
                        error={fieldErrors.modality}
                        disabled={isSaving}
                    />
                    <SelectField
                        label="Duração"
                        name="durationMinutes"
                        options={DURATION_OPTIONS}
                        value={values.durationMinutes}
                        onChange={(event) => updateValue("durationMinutes", event.target.value)}
                        error={fieldErrors.durationMinutes}
                        disabled={isSaving}
                    />
                </div>
                <div className={styles.modalActions}>
                    <button type="button" className="button-secondary" onClick={onClose} disabled={isSaving}>
                        Voltar
                    </button>
                    <button type="submit" className="button" disabled={isSaving}>
                        {isSaving ? "Salvando..." : submitLabel}
                    </button>
                </div>
            </form>
        </Modal>
    );
}
