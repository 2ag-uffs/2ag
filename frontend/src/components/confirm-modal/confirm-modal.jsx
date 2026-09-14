import Modal from "../modal/modal.jsx";
import styles from "./confirm-modal.module.css";

// pergunta de sim ou nao antes de uma acao q perde dado
//
// antes cada tela chamava o confirm do navegador q trava a pagina
// inteira e n da pra estilizar
export default function ConfirmModal({
                                         show,
                                         title = "Confirmar",
                                         message,
                                         confirmText = "Confirmar",
                                         cancelText = "Voltar",
                                         onConfirm,
                                         onCancel,
                                     }) {
    return (
        <Modal show={show} title={title} onClickClose={onCancel}>
            <p className={styles.message}>{message}</p>
            <div className={styles.actions}>
                {/* o de voltar vem primeiro pra n ser o alvo mais facil */}
                <button type="button" className="button-secondary" onClick={onCancel}>
                    {cancelText}
                </button>
                <button type="button" className="button" onClick={onConfirm}>
                    {confirmText}
                </button>
            </div>
        </Modal>
    );
}
