import {useCallback, useEffect, useState} from "react";
import {useNavigate} from "react-router";
import {FiAlertCircle, FiBell, FiCalendar, FiClipboard} from "react-icons/fi";
import EmptyState from "../../components/empty-state/empty-state.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {formatDateTime} from "../../utils/date-format.js";
import styles from "./notificacoes.module.css";

const CONNECTION_ERROR_MESSAGE = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

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

    const loadPage = useCallback(() => {
        apiService.get("/notifications?page=" + pageNumber)
            .then((loadedPage) => {
                setPage(loadedPage);
                setLoadError(null);
            })
            .catch((requestError) => {
                setLoadError(requestError instanceof ApiError
                    ? requestError.message
                    : "Não foi possível carregar os avisos. Confira sua internet e tente de novo.");
            });
    }, [pageNumber]);

    useEffect(loadPage, [loadPage]);

    const showActionError = (requestError) => {
        setActionError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
    };

    // abrir o aviso marca como lido e leva pra tela de onde ele veio
    const openNotification = async (notification) => {
        setActionError(null);
        try {
            if (!notification.isRead) {
                await apiService.post("/notifications/" + notification.id + "/read");
            }
            if (notification.link) {
                navigate(notification.link);
                return;
            }
            loadPage();
        } catch (requestError) {
            showActionError(requestError);
        }
    };

    const markAllAsRead = async () => {
        setActionError(null);
        try {
            await apiService.post("/notifications/read-all");
            loadPage();
        } catch (requestError) {
            showActionError(requestError);
        }
    };

    const removeNotification = async (notificationId) => {
        setActionError(null);
        try {
            await apiService.delete("/notifications/" + notificationId);
            loadPage();
        } catch (requestError) {
            showActionError(requestError);
        }
    };

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!page) {
        return <SkeletonPage cards={1}/>;
    }

    let unreadText = page.unread + " avisos não lidos.";
    if (page.unread === 0) {
        unreadText = "Você está em dia, nenhum aviso novo.";
    } else if (page.unread === 1) {
        unreadText = "1 aviso não lido.";
    }

    return (
        <section className={styles.page}>
            <PageHeader
                title="Avisos"
                subtitle={unreadText}
                actions={page.unread > 0 ? (
                    <button type="button" className="button-secondary" onClick={markAllAsRead}>
                        Marcar todos como lidos
                    </button>
                ) : null}
            />

            {actionError && <p className="aviso aviso--atencao" role="alert">{actionError}</p>}

            {page.notifications.length === 0 ? (
                <EmptyState icon={FiBell} message="Nenhum aviso por aqui."/>
            ) : (
                <ul className={styles.list}>
                    {page.notifications.map((notification) => {
                        const Icon = TYPE_ICONS[notification.type] || FiBell;
                        return (
                            <li
                                key={notification.id}
                                className={notification.isRead ? styles.item : styles.item + " " + styles.itemUnread}
                            >
                                <span className={styles.icon} aria-hidden="true"><Icon/></span>
                                <div className={styles.content}>
                                    <p className={styles.meta}>
                                        <span className={styles.tag}>
                                            {TYPE_LABELS[notification.type] || "Aviso"}
                                        </span>
                                        {formatDateTime(notification.createdAt)}
                                        {!notification.isRead && <span className={styles.unreadMark}>não lido</span>}
                                    </p>
                                    <h2 className={styles.title}>{notification.title}</h2>
                                    <p className={styles.message}>{notification.message}</p>
                                </div>
                                <div className={styles.actions}>
                                    <button
                                        type="button"
                                        className="button-secondary button-small"
                                        onClick={() => openNotification(notification)}
                                    >
                                        {notification.link ? "Abrir" : "Marcar como lido"}
                                    </button>
                                    <button
                                        type="button"
                                        className="button-tertiary button-small"
                                        onClick={() => removeNotification(notification.id)}
                                    >
                                        Apagar
                                    </button>
                                </div>
                            </li>
                        );
                    })}
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
