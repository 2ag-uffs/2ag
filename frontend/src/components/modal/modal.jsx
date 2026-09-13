import {useEffect} from "react";
import "./modal.css";

export default function Modal({ show, title, children, onClickClose }) {
    // o confirm() do navegador fechava com esc, entao o modal tbm fecha.
    // sem isso quem usa teclado ficava preso dentro da caixa
    useEffect(() => {
        if (!show) {
            return;
        }
        const aoTeclar = (evento) => {
            if (evento.key === "Escape") {
                onClickClose();
            }
        };
        window.addEventListener("keydown", aoTeclar);
        return () => window.removeEventListener("keydown", aoTeclar);
    }, [show, onClickClose]);

    if (!show) {
        return null;
    }

    return (
        <div className="modal">
            <div className="modal__content" role="dialog" aria-modal="true" aria-label={title}>
                <div className="modal__header">
                    <h2>{title}</h2>
                    <button onClick={onClickClose} aria-label="Fechar">X</button>
                </div>
                {children}
            </div>
        </div>
    );
}
