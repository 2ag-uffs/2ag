import {useState} from "react";
import TextAreaField from "../form/text-area-field.jsx";
import Modal from "../modal/modal.jsx";
import {apiService, ApiError} from "../../services/api.js";
import styles from "./annulment-modal.module.css";

// pede o motivo e anula um registro clinico feito por engano
// o registro n some e continua no historico com o motivo e quem anulou
// quem usa so monta o modal quando ele precisa aparecer entao o motivo sempre comeca vazio
export default function AnnulmentModal({title, recordDescription, endpoint, onClose, onAnnulled}) {
    const [reason, setReason] = useState("");
    const [reasonError, setReasonError] = useState(null);
    const [formError, setFormError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);

    const handleSubmit = async (event) => {
        event.preventDefault();
        setReasonError(null);
        setFormError(null);

        if (reason.trim() === "") {
            setReasonError("Conte o motivo da anulação.");
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
                } else {
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
            <form className={styles.form} onSubmit={handleSubmit} noValidate>
                <p className={styles.recordDescription}>{recordDescription}</p>
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
                    <button type="submit" className="button" disabled={isSaving}>
                        {isSaving ? "Anulando..." : "Anular registro"}
                    </button>
                </div>
            </form>
        </Modal>
    );
}
