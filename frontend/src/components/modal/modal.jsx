import "./modal.css";

export default function Modal({ show, title, children, onClickClose }) {
    if (!show) {
        return null;
    }

    return (
        <div className="modal">
            <div className="modal__content">
                <div className="modal__header">
                    <h2>{title}</h2>
                    <button onClick={onClickClose}>X</button>
                </div>
                {children}
            </div>
        </div>
    );
}
