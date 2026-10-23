import {useEffect, useRef} from "react";
import styles from "./modal.module.css";

const FOCUSABLE_SELECTOR = "a[href], button, input:not([type='hidden']), select, textarea, "
    + "[tabindex]:not([tabindex='-1'])";

// botoes, links e campos da caixa q o tab alcanca
function focusableInside(dialog) {
    return Array.from(dialog.querySelectorAll(FOCUSABLE_SELECTOR))
        .filter((element) => !element.matches(":disabled"));
}

// o tab anda so dentro da caixa: do ultimo volta pro primeiro e o shift+tab do primeiro vai pro ultimo
function keepFocusInside(event, dialog) {
    const controls = focusableInside(dialog);
    if (controls.length === 0) {
        event.preventDefault();
        return;
    }
    const first = controls[0];
    const last = controls[controls.length - 1];
    if (!dialog.contains(document.activeElement)) {
        event.preventDefault();
        first.focus();
    } else if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
    }
}

export default function Modal({show, title, children, onClickClose}) {
    const dialogRef = useRef(null);

    // o confirm do navegador fechava com esc entao o modal tbm fecha
    // sem isso quem usa teclado ficava preso dentro da caixa
    useEffect(() => {
        if (!show) {
            return;
        }
        const handleKeyDown = (event) => {
            if (event.key === "Escape") {
                onClickClose();
            }
            if (event.key === "Tab" && dialogRef.current) {
                keepFocusInside(event, dialogRef.current);
            }
        };
        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [show, onClickClose]);

    // ao abrir o foco entra na caixa, senao o teclado e o leitor de tela ficam na tela de tras
    // ao fechar volta pro botao q abriu
    useEffect(() => {
        if (!show) {
            return;
        }
        const openedFrom = document.activeElement;
        const firstControl = focusableInside(dialogRef.current)[0];
        if (firstControl) {
            firstControl.focus();
        }
        return () => {
            if (openedFrom && document.body.contains(openedFrom)) {
                openedFrom.focus();
            }
        };
    }, [show]);

    if (!show) {
        return null;
    }

    return (
        <div className={styles.backdrop}>
            <div className={styles.content} role="dialog" aria-modal="true" aria-label={title} ref={dialogRef}>
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
