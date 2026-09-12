import React, {useEffect, useMemo, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import './notificacoes-paciente.css';
import Header from "../../components/header/header.jsx";

// funcao para pegar dados do usuario do token
function getUserDataFromToken() {
    const token = localStorage.getItem("authToken");
    if (!token) return null;
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        return {id: payload.id, name: payload.name, role: payload.role};
    } catch (e) {
        console.error("Erro ao decodificar token:", e);
        return null;
    }
}

// helper para traduzir os tipos de notificacao
const getTypeLabel = (type) => {
    switch (type) {
        case 'APPOINTMENT':
            return 'Compromissos';
        case 'FORM':
            return 'Formulários';
        case 'ALERT':
            return 'Alertas';
        default:
            return 'Geral';
    }
};

const ICONS = {
    APPOINTMENT: '📅',
    FORM: '📋',
    ALERT: '⚠️',
    DEFAULT: '🔔'
};


export default function NotificacoesPaciente() {
    const navigate = useNavigate();
    const [notifications, setNotifications] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);
    const [userData, setUserData] = useState(null);
    const [activeFilter, setActiveFilter] = useState('ALL');

    // filtros da topbar
    const [stagedFilters, setStagedFilters] = useState({
        period: 'ALL',
        sort: 'DESC'
    });
    const [appliedFilters, setAppliedFilters] = useState({
        period: 'ALL',
        sort: 'DESC'
    });
    const [selectedNotifications, setSelectedNotifications] = useState(new Set());

    useEffect(() => {
        const user = getUserDataFromToken();
        if (!user) {
            navigate("/login");
            return;
        }
        // protecao de rota basica: so pacientes podem ver esta tela
        if (user.role !== 'PATIENT') {
            navigate("/"); // redireciona para a home ou outra pagina padrao
            return;
        }
        setUserData(user);
    }, [navigate]);

    // fetch das notificacoes
    useEffect(() => {
        if (!userData) return;

        const fetchNotifications = async () => {
            setIsLoading(true);
            setError(null);
            try {
                const token = localStorage.getItem("authToken");
                const response = await fetch("http://localhost:8080/notifications", {
                    headers: {"Authorization": `Bearer ${token}`}
                });
                if (!response.ok) {
                    throw new Error("Falha ao buscar notificações.");
                }
                const data = await response.json();
                setNotifications(data.map(n => ({...n, read: n.isRead})));
            } catch (err) {
                setError(err.message);
            } finally {
                setIsLoading(false);
            }
        };

        fetchNotifications();
    }, [userData]);

    // logica de filtragem
    const filteredNotifications = useMemo(() => {
        let processed = [...notifications];
        const {period, sort} = appliedFilters;

        // filtro de periodo
        const now = new Date();
        if (period !== 'ALL') {
            processed = processed.filter(n => {
                const notificationDate = new Date(n.createdAt);
                switch (period) {
                    case '7_DAYS':
                        return (now - notificationDate) / (1000 * 60 * 60 * 24) <= 7;
                    case '30_DAYS':
                        return (now - notificationDate) / (1000 * 60 * 60 * 24) <= 30;
                    case 'THIS_MONTH':
                        return notificationDate.getMonth() === now.getMonth() && notificationDate.getFullYear() === now.getFullYear();
                    default:
                        return true;
                }
            });
        }

        // filtro de categoria da sidebar
        if (activeFilter !== 'ALL') {
            if (activeFilter === 'UNREAD') {
                processed = processed.filter(n => !n.read);
            } else {
                processed = processed.filter(n => n.type === activeFilter);
            }
        }

        // ordenacao
        processed.sort((a, b) => {
            const dateA = new Date(a.createdAt);
            const dateB = new Date(b.createdAt);
            return sort === 'DESC' ? dateB - dateA : dateA - dateB;
        });

        return processed;
    }, [notifications, activeFilter, appliedFilters]);

    // logica para agrupar as notificacoes filtradas por tipo
    const groupedNotifications = useMemo(() => {
        return filteredNotifications.reduce((acc, notification) => {
            const type = notification.type || 'DEFAULT';
            if (!acc[type]) {
                acc[type] = [];
            }
            acc[type].push(notification);
            return acc;
        }, {});
    }, [filteredNotifications]);

    // funcao para o botao filtrar
    const handleFilterClick = () => {
        setAppliedFilters(stagedFilters);
        setSelectedNotifications(new Set()); // limpa a selecao ao filtrar
    };

    const handleClearFilters = () => {
        setStagedFilters({period: 'ALL', sort: 'DESC'});
        setAppliedFilters({period: 'ALL', sort: 'DESC'});
        setActiveFilter('ALL');
        setSelectedNotifications(new Set());
    };

    // controla a mudanca de um unico checkbox
    const handleCheckboxChange = (id) => {
        const newSelection = new Set(selectedNotifications);
        if (newSelection.has(id)) newSelection.delete(id);
        else newSelection.add(id);
        setSelectedNotifications(newSelection);
    };

    // controla o checkbox selecionar todas
    const handleSelectAll = (e) => {
        if (e.target.checked) {
            setSelectedNotifications(new Set(filteredNotifications.map(n => n.id)));
        } else {
            setSelectedNotifications(new Set());
        }
    };

    // funcao helper para chamadas de API autenticadas
    const makeAuthenticatedApiCall = async (url, options) => {
        const token = localStorage.getItem("authToken");
        const headers = {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
            ...options.headers,
        };
        try {
            const response = await fetch(url, {...options, headers});
            if (!response.ok) {
                // lanca um erro para ser pego pelo Promise.all ou pelo try/catch
                throw new Error(`API call failed: ${response.status}`);
            }
            return true;
        } catch (error) {
            console.error("API call error:", error);
            setError("Ocorreu um erro ao processar sua solicitação.");
            return false;
        }
    };

    const handleMarkOneAsRead = async (id) => {
        const success = await makeAuthenticatedApiCall(`http://localhost:8080/notifications/${id}/read`, {method: 'POST'});
        if (success) {
            setNotifications(prev =>
                prev.map(n => n.id === id ? {...n, read: true} : n)
            );
        }
    };

    const handleDeleteOne = async (id) => {
        const success = await makeAuthenticatedApiCall(`http://localhost:8080/notifications/${id}`, {method: 'DELETE'});
        if (success) {
            setNotifications(prev => prev.filter(n => n.id !== id));
        }
    };

    const handleMarkSelectedAsRead = async () => {
        const idsToMark = Array.from(selectedNotifications);

        const promises = idsToMark.map(id =>
            makeAuthenticatedApiCall(`http://localhost:8080/notifications/${id}/read`, {method: 'POST'})
        );

        const results = await Promise.all(promises);

        if (results.every(res => res === true)) {
            setNotifications(prev =>
                prev.map(n => idsToMark.includes(n.id) ? {...n, read: true} : n)
            );
            setSelectedNotifications(new Set());
        }
    };

    const handleDeleteSelected = async () => {
        const idsToDelete = Array.from(selectedNotifications);

        const promises = idsToDelete.map(id =>
            makeAuthenticatedApiCall(`http://localhost:8080/notifications/${id}`, {method: 'DELETE'})
        );

        const results = await Promise.all(promises);

        if (results.every(res => res === true)) {
            setNotifications(prev => prev.filter(n => !idsToDelete.includes(n.id)));
            setSelectedNotifications(new Set());
        }
    };

    const handleNotificationClick = (link, id) => {
        if (link) {
            // marcar como lida ao clicar no card (fora do checkbox)
            const notification = notifications.find(n => n.id === id);
            if (notification && !notification.read) {
                handleMarkOneAsRead(id).then(() => {
                    navigate(link);
                });
            } else {
                navigate(link);
            }
        }
    };

    const formatTimestamp = (timestamp) => {
        const now = new Date();
        const notificationDate = new Date(timestamp);
        const diff = now - notificationDate;
        const minutes = Math.floor(diff / (1000 * 60));
        if (minutes < 1) return "agora";
        if (minutes < 60) return `${minutes}m atrás`;
        const hours = Math.floor(diff / (1000 * 60 * 60));
        if (hours < 24) return `${hours}h atrás`;
        const days = Math.floor(diff / (1000 * 60 * 60 * 24));
        return `${days}d atrás`;
    };
    return (
        <div className="notifications-page">
            <Header
                title="Notificações"
                showBackButton={true}
                backButtonText="Voltar"
                onBackClick={() => navigate(-1)}
            />

            <main className="notifications-main-content">
                <div className="notifications-filter-bar">
                    <div className="filter-group">
                        <label>Período</label>
                        <select value={stagedFilters.period}
                                onChange={e => setStagedFilters({...stagedFilters, period: e.target.value})}
                                className="filter-select">
                            <option value="ALL">Desde o início</option>
                            <option value="7_DAYS">Últimos 7 dias</option>
                            <option value="30_DAYS">Últimos 30 dias</option>
                            <option value="THIS_MONTH">Este mês</option>
                        </select>
                    </div>
                    <div className="filter-group">
                        <label>Ordenar por</label>
                        <select value={stagedFilters.sort}
                                onChange={e => setStagedFilters({...stagedFilters, sort: e.target.value})}
                                className="filter-select">
                            <option value="DESC">Mais recentes</option>
                            <option value="ASC">Mais antigas</option>
                        </select>
                    </div>
                    <div className="filter-actions">
                        {selectedNotifications.size > 0 ? (
                            <>
                                <span className="selection-count">{selectedNotifications.size} selecionada(s)</span>
                                <button className="action-btn" onClick={handleMarkSelectedAsRead}>Marcar como lidas
                                </button>
                                <button className="action-btn delete-btn" onClick={handleDeleteSelected}>Excluir
                                </button>
                            </>
                        ) : (
                            <>
                                <button className="clear-filters-btn" onClick={handleClearFilters}>Limpar filtros
                                </button>
                                <button className="filter-btn" onClick={handleFilterClick}>Filtrar</button>
                            </>
                        )}
                    </div>
                </div>

                <div className="notifications-container">
                    <aside className="notifications-sidebar">
                        <h3>Categorias</h3>
                        <ul>
                            <li className={activeFilter === 'ALL' ? 'active' : ''}
                                onClick={() => setActiveFilter('ALL')}>Todas
                            </li>
                            <li className={activeFilter === 'UNREAD' ? 'active' : ''}
                                onClick={() => setActiveFilter('UNREAD')}>Não Lidas
                            </li>
                            <li className={activeFilter === 'APPOINTMENT' ? 'active' : ''}
                                onClick={() => setActiveFilter('APPOINTMENT')}>Consultas
                            </li>
                            <li className={activeFilter === 'FORM' ? 'active' : ''}
                                onClick={() => setActiveFilter('FORM')}>Formulários
                            </li>
                            <li className={activeFilter === 'ALERT' ? 'active' : ''}
                                onClick={() => setActiveFilter('ALERT')}>Alertas
                            </li>
                        </ul>
                    </aside>
                    <div className="notifications-list-main">
                        {isLoading && <p>Carregando...</p>}
                        {error && <p className="error-message">{error}</p>}

                        {!isLoading && !error && filteredNotifications.length > 0 && (
                            <div className="notification-list-header">
                                <div className="select-all-wrapper">
                                    <input type="checkbox" id="select-all" onChange={handleSelectAll}
                                           checked={filteredNotifications.length > 0 && selectedNotifications.size === filteredNotifications.length}/>
                                    <label htmlFor="select-all">Selecionar todas</label>
                                </div>
                            </div>
                        )}

                        {!isLoading && !error && filteredNotifications.length === 0 && (
                            <div className="no-notifications">
                                <h3>Nenhuma notificação encontrada</h3>
                                <p>Não há notificações que correspondam aos filtros selecionados</p>
                            </div>
                        )}

                        {Object.entries(groupedNotifications).map(([type, notificationsOfType]) => (
                            <div key={type} className="notification-group">
                                <div className="notifications-stack">
                                    {notificationsOfType.map(notification => (
                                        <div key={notification.id}
                                             className={`notification-card ${!notification.read ? 'unread' : ''} ${selectedNotifications.has(notification.id) ? 'selected' : ''}`}>
                                            <div className="notification-card-header">
                                                <div className="notification-checkbox-wrapper">
                                                    <input type="checkbox"
                                                           checked={selectedNotifications.has(notification.id)}
                                                           onChange={(e) => {
                                                               e.stopPropagation();
                                                               handleCheckboxChange(notification.id);
                                                           }} onClick={(e) => e.stopPropagation()}/>
                                                </div>
                                                <div
                                                    className="notification-icon">{ICONS[notification.type] || ICONS.DEFAULT}</div>
                                                <div className="notification-meta">
                                                    <span
                                                        className="notification-type">{getTypeLabel(notification.type)}</span>
                                                    <span
                                                        className="notification-timestamp">{formatTimestamp(notification.createdAt)}</span>
                                                </div>
                                                <div className="notification-actions">
                                                    {!notification.read && (
                                                        <button className="mark-read-btn" onClick={(e) => {
                                                            e.stopPropagation();
                                                            handleMarkOneAsRead(notification.id);
                                                        }} title="Marcar como lida">✓</button>
                                                    )}
                                                    <button className="delete-btn" onClick={(e) => {
                                                        e.stopPropagation();
                                                        handleDeleteOne(notification.id);
                                                    }} title="Excluir">🗑️
                                                    </button>
                                                </div>
                                            </div>
                                            <div className="notification-card-content"
                                                 onClick={() => handleNotificationClick(notification.link, notification.id)}>
                                                <h3 className="notification-title">
                                                    {notification.title}
                                                    {!notification.read && <span className="unread-indicator"></span>}
                                                </h3>
                                                <p className="notification-message">{notification.message}</p>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            </main>
        </div>
    );
}