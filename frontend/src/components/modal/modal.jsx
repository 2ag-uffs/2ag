import {useEffect} from "react";
import styles from "./modal.module.css";

export default function Modal({show, title, children, onClickClose}) {
    // o confirm do navegador fechava com esc entao o modal tbm fecha
    // sem isso quem usa teclado ficava preso dentro da caixa
    useEffect(() => {
        if (!show) {
            return;
        }
        const closeOnEscape = (event) => {
            if (event.key === "Escape") {
                onClickClose();
            }
        };
        window.addEventListener("keydown", closeOnEscape);
        return () => window.removeEventListener("keydown", closeOnEscape);
    }, [show, onClickClose]);

    if (!show) {
        return null;
    }

    return (
        <div className={styles.backdrop}>
            <div className={styles.content} role="dialog" aria-modal="true" aria-label={title}>
                <div className={styles.header}>
                    <h2>{title}</h2>
                    <button type="button" className={styles.closeButton} onClick={onClickClose} aria-label="Fechar">
                        X
                    </button>
                </div>
                {children}
            </div>
        </div>
    );
}
