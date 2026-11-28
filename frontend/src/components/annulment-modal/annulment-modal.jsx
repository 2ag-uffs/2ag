import {useState} from "react";
import {useFocusFirstError} from "../form/focus-first-error.js";
import BusyButton from "../form/busy-button.jsx";
import TextAreaField from "../form/text-area-field.jsx";
import Modal from "../modal/modal.jsx";
import {apiService, ApiError, CONNECTION_ERROR_MESSAGE} from "../../services/api.js";
import styles from "./annulment-modal.module.css";

// pede o motivo e anula um registro clinico feito por engano
// o registro n some e continua no historico com o motivo e quem anulou
// quem usa so monta o modal quando ele precisa aparecer entao o motivo sempre comeca vazio
// warning eh opcional, pra quando anular tem uma consequencia q a pessoa precisa saber antes
// patientNote diz o q o paciente ve do registro anulado, pq ele le o motivo (revisao das regras R7)
// no MEEM, q o paciente n ve, quem usa n manda
export default function AnnulmentModal({
    title, recordDescription, warning, patientNote, endpoint, onClose, onAnnulled,
}) {
    const [reason, setReason] = useState("");
    const [reasonError, setReasonError] = useState(null);
    const [formError, setFormError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const [formRef, focusFirstError] = useFocusFirstError();

    const handleSubmit = async (event) => {
        event.preventDefault();
        setReasonError(null);
        setFormError(null);

        if (reason.trim() === "") {
            setReasonError("Conte o motivo da anulação.");
            focusFirstError();
            return;
        }

        setIsSaving(true);
        try {
            const annulledRecord = await apiService.put(endpoint, {reason: reason.trim()});
            onAnnulled(annulledRecord);
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                const errorsByField = requestError.fieldErrors();
                if (errorsByField.reason) {
                    setReasonError(errorsByField.reason);
                    focusFirstError();
                } else {
                    setFormError(requestError.message);
                }
            } else {
                setFormError(CONNECTION_ERROR_MESSAGE);
            }
            setIsSaving(false);
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
            <form className={styles.form} onSubmit={handleSubmit} noValidate ref={formRef}>
                <p className={styles.recordDescription}>{recordDescription}</p>
                {warning && <p className="aviso aviso--atencao">{warning}</p>}
                {patientNote && <p className="aviso">{patientNote}</p>}
                <p className={styles.note}>
                    O registro não é apagado. Ele continua no histórico marcado como anulado, com o motivo e o nome de
                    quem anulou.
                </p>
                {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}
                <TextAreaField
                    label="Motivo da anulação"
                    name="annulmentReason"
                    rows={3}
                    maxLength={2000}
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    error={reasonError}
                    disabled={isSaving}
                    required={true}
                />
                <div className={styles.actions}>
                    <button type="button" className="button-secondary" onClick={onClose} disabled={isSaving}>
                        Voltar
                    </button>
                    <BusyButton type="submit" className="button-danger-filled" isBusy={isSaving} busyText="Anulando...">
                        Anular registro
                    </BusyButton>
                </div>
            </form>
        </Modal>
    );
}
