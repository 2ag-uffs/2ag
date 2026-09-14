import {useState} from "react";
import TextAreaField from "../../components/form/text-area-field.jsx";
import Modal from "../../components/modal/modal.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {formatDateTime} from "../../utils/date-format.js";
import styles from "./agendamento-consulta-prescritor.module.css";

// recusa de um pedido de consulta com o motivo opcional q vai no aviso pro paciente
export default function DeclineRequestModal({request, onClose, onDeclined}) {
    const [reason, setReason] = useState("");
    const [formError, setFormError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);

    const handleSubmit = async (event) => {
        event.preventDefault();
        setFormError(null);
        setIsSaving(true);
        try {
            await apiService.put("/consulta/" + request.id + "/recusa", {
                reason: reason.trim() === "" ? null : reason.trim(),
            });
            onDeclined();
        } catch (requestError) {
            setFormError(requestError instanceof ApiError
                ? requestError.message
                : "Não foi possível falar com o servidor. Confira sua internet e tente de novo.");
            setIsSaving(false);
        }
    };

    return (
        <Modal show={true} title="Recusar pedido de consulta" onClickClose={onClose}>
            <form className={styles.modalForm} onSubmit={handleSubmit}>
                <p className={styles.modalDescription}>
                    {request.patientName} pediu consulta para {formatDateTime(request.dateTime)}.
                </p>
                {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}
                <TextAreaField
                    label="Motivo (opcional)"
                    name="declineReason"
                    hint="O paciente recebe o aviso com esse motivo e pode escolher outro horário."
                    rows={3}
                    maxLength={500}
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    disabled={isSaving}
                />
                <div className={styles.modalActions}>
                    <button type="button" className="button-secondary" onClick={onClose} disabled={isSaving}>
                        Voltar
                    </button>
                    <button type="submit" className="button" disabled={isSaving}>
                        {isSaving ? "Recusando..." : "Recusar pedido"}
                    </button>
                </div>
            </form>
        </Modal>
    );
}
