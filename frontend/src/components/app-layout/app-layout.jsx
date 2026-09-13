import {NavLink, Outlet, ScrollRestoration, useLocation, useNavigate, useNavigation} from "react-router";
import {FiArrowLeft, FiBell, FiCalendar, FiHome, FiLogOut, FiTrendingUp, FiUser, FiUsers} from "react-icons/fi";
import {homePathFor} from "../../app/role-home.js";
import {getLoggedUser, logout} from "../../services/api.js";
import styles from "./app-layout.module.css";

// itens do menu de cada perfil
// no celular os mesmos itens aparecem na barra de baixo
const MENU_BY_ROLE = {
    PATIENT: [
        {path: "/dashboard-paciente", label: "Início", icon: FiHome},
        {path: "/progresso", label: "Progresso", icon: FiTrendingUp},
        {path: "/agendamento-consulta", label: "Consultas", icon: FiCalendar},
        {path: "/notificacoes-paciente", label: "Avisos", icon: FiBell},
        {path: "/perfil", label: "Perfil", icon: FiUser},
    ],
    PRESCRIBER: [
        {path: "/dashboard-prescritor", label: "Início", icon: FiHome},
        {path: "/lista-paciente", label: "Pacientes", icon: FiUsers},
        {path: "/agendamento-prescritor", label: "Agenda", icon: FiCalendar},
        {path: "/notificacoes-prescritor", label: "Avisos", icon: FiBell},
        {path: "/perfil", label: "Perfil", icon: FiUser},
    ],
    ADMIN: [
        {path: "/admin", label: "Prescritores", icon: FiUsers},
    ],
};

function topLinkClass({isActive}) {
    return isActive ? styles.menuLink + " " + styles.menuLinkActive : styles.menuLink;
}

function bottomLinkClass({isActive}) {
    return isActive ? styles.bottomLink + " " + styles.bottomLinkActive : styles.bottomLink;
}

// moldura de todas as telas de quem esta logado
// cabecalho com menu e sair e a tela da rota aparece no meio
export default function AppLayout() {
    const navigate = useNavigate();
    const location = useLocation();
    const navigation = useNavigation();
    const loggedUser = getLoggedUser();
    const menuItems = loggedUser ? MENU_BY_ROLE[loggedUser.role] || [] : [];
    const homePath = homePathFor(loggedUser);
    const isHomePage = location.pathname === homePath;
    const isChangingPage = navigation.state === "loading";

    // quem abriu o link direto n tem pra onde voltar entao vai pro inicio
    const handleBack = () => {
        if (location.key === "default") {
            navigate(homePath);
        } else {
            navigate(-1);
        }
    };

    const handleLogout = async () => {
        await logout();
        navigate("/login", {replace: true});
    };

    return (
        <div className={styles.layout}>
            {/* barra fina no topo enquanto a proxima tela carrega */}
            {isChangingPage && <div className={styles.loadingBar} role="progressbar" aria-label="Carregando"/>}

            <header className={styles.header}>
                <div className={styles.headerStart}>
                    {!isHomePage && (
                        <button type="button" className={styles.iconButton} onClick={handleBack}
                                aria-label="Voltar" title="Voltar">
                            <FiArrowLeft/>
                        </button>
                    )}
                    <img src="/images/logotipo-icon-claro.svg" alt="2AG" className={styles.logo}/>
                </div>

                <nav className={styles.topMenu} aria-label="Menu principal">
                    {menuItems.map((item) => {
                        const Icon = item.icon;
                        return (
                            <NavLink key={item.path} to={item.path} className={topLinkClass}>
                                <Icon/>
                                <span className={styles.menuLabel}>{item.label}</span>
                            </NavLink>
                        );
                    })}
                </nav>

                <div className={styles.headerEnd}>
                    <span className={styles.userName}>{loggedUser ? loggedUser.name : ""}</span>
                    <button type="button" className={styles.iconButton} onClick={handleLogout}
                            aria-label="Sair" title="Sair">
                        <FiLogOut/>
                    </button>
                </div>
            </header>

            <main className={styles.content}>
                {/* a chave muda a cada rota entao cada tela nova entra com o fade */}
                <div key={location.pathname} className={styles.page}>
                    <Outlet/>
                </div>
            </main>

            <nav className={styles.bottomMenu} aria-label="Menu">
                {menuItems.map((item) => {
                    const Icon = item.icon;
                    return (
                        <NavLink key={item.path} to={item.path} className={bottomLinkClass}>
                            <Icon/>
                            <span>{item.label}</span>
                        </NavLink>
                    );
                })}
            </nav>

            <ScrollRestoration/>
        </div>
    );
}
