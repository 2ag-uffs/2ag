import {useCallback, useEffect, useRef, useState} from "react";
import {useNavigate} from "react-router";
import {FiAlertCircle, FiBell, FiCalendar, FiClipboard} from "react-icons/fi";
import EmptyState from "../../components/empty-state/empty-state.jsx";
import {LoadError} from "../../components/error-card/error-card.jsx";
import BusyButton from "../../components/form/busy-button.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, errorMessageOf, loadErrorOf} from "../../services/api.js";
import {formatDateTime, withBrasiliaTimeNote} from "../../utils/date-format.js";
import styles from "./notificacoes.module.css";

// o tipo vem do backend e vira uma etiqueta curta e um icone na lista
const TYPE_LABELS = {
    APPOINTMENT: "Consulta",
    FORM: "Formulário",
    ALERT: "Aviso",
};

const TYPE_ICONS = {
    APPOINTMENT: FiCalendar,
    FORM: FiClipboard,
    ALERT: FiAlertCircle,
};

function unreadTextOf(unread) {
    if (unread === 0) {
        return "Você está em dia, nenhum aviso novo.";
    }
    if (unread === 1) {
        return "1 aviso não lido.";
    }
    return unread + " avisos não lidos.";
}

// o titulo e a contagem n sao de clicar, entao recebem o foco so dessa vez, como o main do layout
function focusText(element) {
    element.setAttribute("tabindex", "-1");
    element.focus();
    element.addEventListener("blur", () => element.removeAttribute("tabindex"), {once: true});
}

// enquanto a api responde o abrir ou o apagar, a linha fica ocupada e os dois botoes n aceitam outro toque
function NotificationItem({notification, isBusy, onOpen, onRemove}) {
    const Icon = TYPE_ICONS[notification.type] || FiBell;
    return (
        <li
            className={notification.isRead ? styles.item : styles.item + " " + styles.itemUnread}
            aria-busy={isBusy ? "true" : undefined}
        >
            <span className={styles.icon} aria-hidden="true"><Icon/></span>
            <div className={styles.content}>
                <p className={styles.meta}>
                    <span className="etiqueta etiqueta--neutra">
                        {TYPE_LABELS[notification.type] || "Aviso"}
                    </span>
                    {formatDateTime(notification.createdAt)}
                    {!notification.isRead && <span className={styles.unreadMark}>não lido</span>}
                </p>
                <h2 className={styles.title}>{notification.title}</h2>
                <p className={styles.message}>{withBrasiliaTimeNote(notification.message)}</p>
            </div>
            <div className={styles.actions}>
                <BusyButton type="button" className="button-secondary button-small" isBusy={isBusy} onClick={onOpen}>
                    {notification.link ? "Abrir" : "Marcar como lido"}
                </BusyButton>
                <BusyButton
                    type="button"
                    className={"button-tertiary button-small " + styles.remove}
                    isBusy={isBusy}
                    onClick={onRemove}
                >
                    Apagar
                </BusyButton>
            </div>
        </li>
    );
}

// avisos da propria conta (RF14 e RF15)
//
// serve pros dois perfis: a lista eh sempre a da pessoa logada. vem
// paginada pq cresce sem limite (RNF06)
export default function Notificacoes() {
    const navigate = useNavigate();

    const [page, setPage] = useState(null);
    const [pageNumber, setPageNumber] = useState(0);
    const [loadError, setLoadError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const actionErrorRef = useRef(null);
    const pageRef = useRef(null);
    // a posicao do aviso apagado ou "count", pra saber onde por o foco qnd a lista chegar de novo
    const focusAfterLoadRef = useRef(null);

    // o aviso de erro fica no alto da tela e o botao q falhou pode estar bem embaixo, entao o foco vai pro aviso
    // e o navegador rola ate ele
    useEffect(() => {
        if (actionError && actionErrorRef.current) {
            actionErrorRef.current.focus();
        }
    }, [actionError]);

    // o botao tocado some dps de apagar ou de marcar todos e o foco caia no body: com o leitor de tela a pessoa
    // voltava pro comeco da pagina. entao ele vai pro aviso q ficou no lugar do apagado (ou o de cima, se era
    // o ultimo), pro titulo qnd a lista acaba ou pra contagem de nao lidos
    useEffect(() => {
        const target = focusAfterLoadRef.current;
        if (target === null || !page || !pageRef.current) {
            return;
        }
        focusAfterLoadRef.current = null;
        if (target === "count") {
            focusText(pageRef.current.querySelector("header p"));
            return;
        }
        const items = pageRef.current.querySelectorAll("ul > li");
        if (items.length === 0) {
            focusText(pageRef.current.querySelector("h1"));
            return;
        }
        items[Math.min(target, items.length - 1)].querySelector("button").focus();
    }, [page]);

    // os avisos com abrir ou apagar no ar. o aviso continua na lista ate a recarga chegar, e o segundo toque em
    // Apagar mandava outro pedido, q a api recusa pq o aviso ja foi apagado
    const [busyIds, setBusyIds] = useState([]);
    // os avisos q a api ainda n respondeu. a recarga pedida por um aviso so solta as linhas q ja tiveram resposta,
    // senao o outro aviso com o apagar no ar aceitava o segundo toque
    const waitingIdsRef = useRef([]);
    const stopWaiting = (notificationId) => {
        waitingIdsRef.current = waitingIdsRef.current.filter((waitingId) => waitingId !== notificationId);
    };
    const freeAnsweredRows = () => {
        setBusyIds((currentIds) => currentIds.filter((busyId) => waitingIdsRef.current.includes(busyId)));
    };

    // so a resposta do ultimo pedido da lista vale. com dois pedidos no ar (dois toques seguidos no Proxima)
    // o da pagina anterior podia chegar por ultimo e ficar na tela no lugar da pagina pedida
    const lastLoadRef = useRef(0);

    const loadPage = useCallback(() => {
        lastLoadRef.current += 1;
        const loadNumber = lastLoadRef.current;
        apiService.get("/notifications?page=" + pageNumber)
            .then((loadedPage) => {
                if (loadNumber !== lastLoadRef.current) {
                    return;
                }
                // apagar o ultimo aviso da ultima pagina deixa ela vazia, entao volta pra ultima q ainda tem aviso
                if (loadedPage.notifications.length === 0 && loadedPage.page > 0) {
                    setPageNumber(Math.max(loadedPage.totalPages - 1, 0));
                    return;
                }
                setPage(loadedPage);
                setLoadError(null);
                // a lista nova chegou, entao a linha q ja teve resposta da api n continua ocupada
                freeAnsweredRows();
                // o numero do menu acompanha o q foi lido ou apagado aqui
                window.dispatchEvent(new CustomEvent("notifications-changed", {detail: loadedPage.unread}));
            })
            .catch((requestError) => {
                if (loadNumber !== lastLoadRef.current) {
                    return;
                }
                freeAnsweredRows();
                setLoadError(loadErrorOf(requestError,
                    "Não foi possível carregar os avisos. Confira sua internet e tente de novo."));
            });
    }, [pageNumber]);

    useEffect(() => {
        loadPage();
        // a resposta q chega dps de a pessoa sair da tela tbm n vale
        return () => {
            lastLoadRef.current += 1;
        };
    }, [loadPage]);

    const showActionError = (requestError, notificationId) => {
        stopWaiting(notificationId);
        setBusyIds((currentIds) => currentIds.filter((busyId) => busyId !== notificationId));
        setActionError(errorMessageOf(requestError));
    };

    // abrir o aviso marca como lido e leva pra tela de onde ele veio
    const openNotification = async (notification) => {
        setActionError(null);
        setBusyIds((currentIds) => [...currentIds, notification.id]);
        waitingIdsRef.current = [...waitingIdsRef.current, notification.id];
        try {
            if (!notification.isRead) {
                await apiService.post("/notifications/" + notification.id + "/read");
            }
            stopWaiting(notification.id);
            if (notification.link) {
                navigate(notification.link);
                return;
            }
            loadPage();
        } catch (requestError) {
            showActionError(requestError, notification.id);
        }
    };

    const markAllAsRead = async () => {
        setActionError(null);
        try {
            await apiService.post("/notifications/read-all");
            focusAfterLoadRef.current = "count";
            loadPage();
        } catch (requestError) {
            showActionError(requestError);
        }
    };

    const removeNotification = async (notificationId, index) => {
        setActionError(null);
        setBusyIds((currentIds) => [...currentIds, notificationId]);
        waitingIdsRef.current = [...waitingIdsRef.current, notificationId];
        try {
            await apiService.delete("/notifications/" + notificationId);
            stopWaiting(notificationId);
            focusAfterLoadRef.current = index;
            loadPage();
        } catch (requestError) {
            showActionError(requestError, notificationId);
        }
    };

    if (loadError) {
        return <LoadError error={loadError}/>;
    }

    if (!page) {
        return <SkeletonPage cards={1}/>;
    }

    return (
        <section className={styles.page} ref={pageRef}>
            <PageHeader
                title="Avisos"
                subtitle={unreadTextOf(page.unread)}
                actions={page.unread > 0 ? (
                    <button type="button" className="button-secondary" onClick={markAllAsRead}>
                        Marcar todos como lidos
                    </button>
                ) : null}
            />

            {actionError && (
                <p className="aviso aviso--atencao" role="alert" tabIndex={-1} ref={actionErrorRef}>{actionError}</p>
            )}

            {page.notifications.length === 0 ? (
                <EmptyState icon={FiBell} message="Nenhum aviso por aqui."/>
            ) : (
                <ul className={styles.list}>
                    {page.notifications.map((notification, index) => (
                        <NotificationItem
                            key={notification.id}
                            notification={notification}
                            isBusy={busyIds.includes(notification.id)}
                            onOpen={() => openNotification(notification)}
                            onRemove={() => removeNotification(notification.id, index)}
                        />
                    ))}
                </ul>
            )}

            {page.totalPages > 1 && (
                <div className={styles.pagination}>
                    <button
                        type="button"
                        className="button-secondary button-small"
                        disabled={pageNumber === 0}
                        onClick={() => setPageNumber((current) => current - 1)}
                    >
                        Anterior
                    </button>
                    <span>Página {page.page + 1} de {page.totalPages}</span>
                    <button
                        type="button"
                        className="button-secondary button-small"
                        disabled={page.page + 1 >= page.totalPages}
                        onClick={() => setPageNumber((current) => current + 1)}
                    >
                        Próxima
                    </button>
                </div>
            )}
        </section>
    );
}
