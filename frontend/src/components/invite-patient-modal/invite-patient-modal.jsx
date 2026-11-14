import {useEffect, useState} from "react";
import {FiCopy, FiMessageCircle, FiRefreshCw} from "react-icons/fi";
import BusyButton from "../form/busy-button.jsx";
import Modal from "../modal/modal.jsx";
import {apiService, errorMessageOf} from "../../services/api.js";
import {formatDateTime, formatDateTimeWithZone} from "../../utils/date-format.js";
import styles from "./invite-patient-modal.module.css";

// link q o paciente abre pra criar a conta
function buildInviteLink(token) {
    return window.location.origin + "/cadastro?convite=" + encodeURIComponent(token);
}

// dois convites do mesmo minuto ficavam iguais na lista, entao esses mostram os segundos tbm
// a api manda a hora de brasilia como 2026-11-13T12:36:28.121631 e os segundos sao o 17 e o 18
function createdTextOf(openInvite, openInvites) {
    const minuteText = formatDateTime(openInvite.createdAt);
    const sameMinute = openInvites.filter((other) => formatDateTime(other.createdAt) === minuteText);
    if (sameMinute.length > 1) {
        return minuteText + ":" + openInvite.createdAt.slice(17, 19);
    }
    return minuteText;
}

// os convites q ainda estao na rua, pra cancelar o q foi pro numero errado
// o link q esta na tela vem marcado, pro prescritor saber qual linha eh qual
function OpenInviteList({openInvites, shownInviteId, onCancel}) {
    return (
        <div className={styles.openInvites}>
            <h3 className={styles.openTitle}>Convites em aberto</h3>
            <p className={styles.hint}>
                Link que foi para a pessoa errada? Cancele antes que alguém use. O link que já virou conta
                não aparece aqui.
            </p>
            <ul className={styles.openList}>
                {openInvites.map((openInvite) => {
                    const createdText = createdTextOf(openInvite, openInvites);
                    const isShown = openInvite.id === shownInviteId;
                    return (
                        <li key={openInvite.id} className={styles.openRow}>
                            <span>
                                Gerado em {createdText}, vale até{" "}
                                {formatDateTimeWithZone(openInvite.expiresAt)}
                                {isShown && <strong className={styles.shownMark}>É o link acima</strong>}
                            </span>
                            <button
                                type="button"
                                className="button-danger button-small"
                                aria-label={"Cancelar convite gerado em " + createdText + (isShown ? ", o link acima" : "")}
                                onClick={() => onCancel(openInvite.id)}
                            >
                                Cancelar
                            </button>
                        </li>
                    );
                })}
            </ul>
        </div>
    );
}

// janela do prescritor pra gerar o link de convite de um paciente novo (RN06)
export default function InvitePatientModal({show, onClose}) {
    const [invite, setInvite] = useState(null);
    const [isCreating, setIsCreating] = useState(false);
    const [errorMessage, setErrorMessage] = useState(null);
    const [isCopied, setIsCopied] = useState(false);
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
            setCancelError(errorMessageOf(requestError,
                "Não foi possível cancelar o convite. Confira sua internet e tente de novo."));
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
            setErrorMessage(errorMessageOf(requestError,
                "Não foi possível gerar o convite. Confira sua internet e tente de novo."));
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
                    <BusyButton type="button" className={styles.generateButton} onClick={createInvite}
                                isBusy={isCreating} busyText="Gerando link...">
                        Gerar link de convite
                    </BusyButton>
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
                        {/* o link vence na hora em q foi gerado, 7 dias dps, e n no fim do dia */}
                        <p className={styles.hint}>
                            Vale até {formatDateTimeWithZone(invite.expiresAt)}.
                        </p>

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
                    <OpenInviteList
                        openInvites={openInvites}
                        shownInviteId={invite ? invite.id : null}
                        onCancel={cancelInvite}
                    />
                )}
            </div>
        </Modal>
    );
}
