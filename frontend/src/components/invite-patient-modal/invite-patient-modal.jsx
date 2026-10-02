import {useEffect, useState} from "react";
import {FiCopy, FiMessageCircle, FiRefreshCw} from "react-icons/fi";
import Modal from "../modal/modal.jsx";
import {apiService, ApiError} from "../../services/api.js";
import styles from "./invite-patient-modal.module.css";

// link q o paciente abre pra criar a conta
function buildInviteLink(token) {
    return window.location.origin + "/cadastro?convite=" + encodeURIComponent(token);
}

function formatDate(dateTimeText) {
    return new Date(dateTimeText).toLocaleDateString("pt-BR");
}

function formatDateTime(dateTimeText) {
    return new Date(dateTimeText).toLocaleString("pt-BR", {dateStyle: "short", timeStyle: "short"});
}

// janela do prescritor pra gerar o link de convite de um paciente novo (RN06)
export default function InvitePatientModal({show, onClose}) {
    const [invite, setInvite] = useState(null);
    const [isCreating, setIsCreating] = useState(false);
    const [errorMessage, setErrorMessage] = useState(null);
    const [isCopied, setIsCopied] = useState(false);
    // os convites q ainda estao na rua, pra cancelar o q foi pro numero errado
    const [openInvites, setOpenInvites] = useState([]);
    const [cancelError, setCancelError] = useState(null);

    const loadOpenInvites = () => {
        apiService.get("/invites")
            .then(setOpenInvites)
            .catch(() => {
                // sem a lista nova a tela fica com a q tinha
            });
    };

    useEffect(() => {
        if (show) {
            loadOpenInvites();
        }
    }, [show]);

    const cancelInvite = async (inviteId) => {
        setCancelError(null);
        try {
            await apiService.put("/invites/" + inviteId + "/cancel");
            setOpenInvites((current) => current.filter((openInvite) => openInvite.id !== inviteId));
            // o link q acabou de ser gerado n pode continuar na tela depois de cancelado
            if (invite && invite.id === inviteId) {
                setInvite(null);
            }
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                setCancelError(requestError.message);
            } else {
                setCancelError("Não foi possível cancelar o convite. Confira sua internet e tente de novo.");
            }
            loadOpenInvites();
        }
    };

    const createInvite = async () => {
        setIsCreating(true);
        setErrorMessage(null);
        setIsCopied(false);
        try {
            const createdInvite = await apiService.post("/invites");
            setInvite(createdInvite);
            loadOpenInvites();
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                setErrorMessage(requestError.message);
            } else {
                setErrorMessage("Não foi possível gerar o convite. Confira sua internet e tente de novo.");
            }
        } finally {
            setIsCreating(false);
        }
    };

    // ao fechar a janela o proximo convite comeca do zero
    const handleClose = () => {
        setInvite(null);
        setErrorMessage(null);
        setCancelError(null);
        setIsCopied(false);
        onClose();
    };

    const inviteLink = invite ? buildInviteLink(invite.token) : "";
    const whatsAppMessage = "Olá! Este é o link para você criar sua conta no 2AG e começar o acompanhamento: "
        + inviteLink;
    const whatsAppLink = "https://wa.me/?text=" + encodeURIComponent(whatsAppMessage);

    const handleCopy = async () => {
        try {
            await navigator.clipboard.writeText(inviteLink);
            setIsCopied(true);
        } catch {
            // sem acesso a area de transferencia a pessoa copia na mao
            setErrorMessage("Não deu para copiar sozinho. Toque no link acima e copie.");
        }
    };

    return (
        <Modal show={show} title="Convidar paciente" onClickClose={handleClose}>
            <div className={styles.content}>
                <p className={styles.text}>
                    O paciente cria a própria conta pelo link, já vinculada a você. Cada link serve para um único
                    cadastro e vale por 7 dias.
                </p>

                {errorMessage && (
                    <p className={"aviso aviso--atencao " + styles.message} role="alert">{errorMessage}</p>
                )}
                {cancelError && (
                    <p className={"aviso aviso--atencao " + styles.message} role="alert">{cancelError}</p>
                )}

                {!invite && (
                    <button type="button" className={styles.generateButton} onClick={createInvite} disabled={isCreating}>
                        {isCreating ? "Gerando link..." : "Gerar link de convite"}
                    </button>
                )}

                {invite && (
                    <>
                        <label htmlFor="inviteLink" className={styles.label}>Link de convite</label>
                        <input
                            id="inviteLink"
                            className={styles.linkInput}
                            value={inviteLink}
                            readOnly={true}
                            onFocus={(event) => event.target.select()}
                        />
                        <p className={styles.hint}>Vale até {formatDate(invite.expiresAt)}.</p>

                        <div className={styles.actions}>
                            <button type="button" className={styles.actionButton} onClick={handleCopy}>
                                <FiCopy/> {isCopied ? "Link copiado" : "Copiar link"}
                            </button>
                            <a
                                className={"button button-secondary " + styles.actionButton}
                                href={whatsAppLink}
                                target="_blank"
                                rel="noopener noreferrer"
                            >
                                <FiMessageCircle/> Enviar pelo WhatsApp
                            </a>
                        </div>

                        <button type="button" className={styles.linkButton} onClick={createInvite} disabled={isCreating}>
                            <FiRefreshCw/> Gerar outro link para outro paciente
                        </button>
                    </>
                )}

                {openInvites.length > 0 && (
                    <div className={styles.openInvites}>
                        <h3 className={styles.openTitle}>Convites em aberto</h3>
                        <p className={styles.hint}>
                            Link que foi para a pessoa errada? Cancele antes que alguém use. O link que já virou conta
                            não aparece aqui.
                        </p>
                        <ul className={styles.openList}>
                            {openInvites.map((openInvite) => (
                                <li key={openInvite.id} className={styles.openRow}>
                                    <span>
                                        Gerado em {formatDateTime(openInvite.createdAt)}, vale até{" "}
                                        {formatDate(openInvite.expiresAt)}
                                    </span>
                                    <button
                                        type="button"
                                        className="button-danger button-small"
                                        aria-label={"Cancelar convite gerado em " + formatDateTime(openInvite.createdAt)}
                                        onClick={() => cancelInvite(openInvite.id)}
                                    >
                                        Cancelar
                                    </button>
                                </li>
                            ))}
                        </ul>
                    </div>
                )}
            </div>
        </Modal>
    );
}
