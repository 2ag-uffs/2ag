import Modal from "../modal/modal.jsx";
import styles from "./confirm-modal.module.css";

// pergunta de sim ou nao antes de uma acao q perde dado
//
// antes cada tela chamava o confirm do navegador q trava a pagina
// inteira e n da pra estilizar
// danger eh pra qnd o sim faz perder algo (anular, desativar, cancelar, sair sem salvar):
// o botao sai na terracota cheia, e n no verde q tem cara de caminho seguro
export default function ConfirmModal({
                                         show,
                                         title = "Confirmar",
                                         message,
                                         confirmText = "Confirmar",
                                         cancelText = "Voltar",
                                         danger = false,
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
                <button type="button" className={danger ? "button-danger-filled" : "button"} onClick={onConfirm}>
                    {confirmText}
                </button>
            </div>
        </Modal>
    );
}
