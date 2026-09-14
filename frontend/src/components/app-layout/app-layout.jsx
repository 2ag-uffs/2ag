import {Link, Outlet, ScrollRestoration, useLocation, useNavigate, useNavigation} from "react-router";
import {
    FiActivity,
    FiArrowLeft,
    FiBell,
    FiCalendar,
    FiClipboard,
    FiClock,
    FiFileText,
    FiHome,
    FiLogOut,
    FiShield,
    FiTrendingUp,
    FiUser,
    FiUsers,
} from "react-icons/fi";
import {homePathFor} from "../../app/role-home.js";
import {getLoggedUser, logout} from "../../services/api.js";
import styles from "./app-layout.module.css";

const ROLE_LABELS = {
    PATIENT: "Paciente",
    PRESCRIBER: "Prescritor",
    ADMIN: "Administração",
};

// itens do menu de cada perfil
// o menu lateral mostra todos e a barra de baixo do celular so os com mobile
// prefixes deixa o item marcado tbm nas telas q ficam dentro dele
// pattern marca o item numa tela q tbm cairia no prefixo de outro item
function menuFor(user) {
    if (!user) {
        return [];
    }
    if (user.role === "PATIENT") {
        return [
            {path: "/painel-paciente", label: "Início", icon: FiHome, mobile: true},
            {path: "/pacientes/" + user.id + "/escalas", label: "Escalas", icon: FiClipboard, prefixes: ["/escalas/"]},
            {path: "/progresso", label: "Progresso", icon: FiTrendingUp, mobile: true},
            {path: "/agendamento-consulta", label: "Consultas", icon: FiCalendar, mobile: true},
            {path: "/minhas-prescricoes", label: "Prescrições", icon: FiFileText},
            {path: "/historico-paciente", label: "Histórico", icon: FiActivity, prefixes: ["/anamnese", "/impressao"]},
            {path: "/notificacoes", label: "Avisos", icon: FiBell, mobile: true},
            {path: "/perfil", label: "Perfil", icon: FiUser, mobile: true},
        ];
    }
    if (user.role === "PRESCRIBER") {
        return [
            {path: "/painel-prescritor", label: "Início", icon: FiHome, mobile: true},
            {path: "/lista-paciente", label: "Pacientes", icon: FiUsers, mobile: true, prefixes: ["/paciente/", "/consulta/"]},
            {path: "/progresso-pacientes", label: "Progresso", icon: FiTrendingUp, pattern: /^\/paciente\/[^/]+\/progresso$/},
            {path: "/agendamento-prescritor", label: "Agenda", icon: FiCalendar, mobile: true},
            {path: "/agenda/disponibilidade", label: "Horários de atendimento", icon: FiClock},
            {path: "/notificacoes", label: "Avisos", icon: FiBell, mobile: true},
            {path: "/perfil", label: "Perfil", icon: FiUser, mobile: true},
        ];
    }
    return [
        {path: "/administracao", label: "Prescritores", icon: FiUsers, mobile: true},
        {path: "/administracao/auditoria", label: "Auditoria", icon: FiShield, mobile: true},
    ];
}

// so um item fica marcado por vez
// o pattern vem antes pq /paciente/5/progresso tbm comeca com o prefixo da lista de pacientes
function findActiveItem(items, pathname) {
    const patternItem = items.find((item) => item.pattern && item.pattern.test(pathname));
    if (patternItem) {
        return patternItem;
    }
    return items.find((item) => {
        const prefixes = item.prefixes || [];
        return pathname === item.path || prefixes.some((prefix) => pathname.startsWith(prefix));
    });
}

// moldura de todas as telas de quem esta logado
// no computador o menu fica na lateral, no tablet a lateral mostra so os
// icones e no celular o menu vai pra barra de baixo
export default function AppLayout() {
    const navigate = useNavigate();
    const location = useLocation();
    const navigation = useNavigation();
    const loggedUser = getLoggedUser();
    const menuItems = menuFor(loggedUser);
    const mobileItems = menuItems.filter((item) => item.mobile);
    const homePath = homePathFor(loggedUser);
    const isHomePage = location.pathname === homePath;
    const isChangingPage = navigation.state === "loading";

    // a barra do celular tem menos itens entao ela acha o item marcado dela
    // assim no progresso de um paciente o celular marca a lista de pacientes
    const activeSideItem = findActiveItem(menuItems, location.pathname);
    const activeBottomItem = findActiveItem(mobileItems, location.pathname);

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
        navigate("/entrar", {replace: true});
    };

    return (
        <div className={styles.layout}>
            {/* barra fina no topo enquanto a proxima tela carrega */}
            {isChangingPage && <div className={styles.loadingBar} role="progressbar" aria-label="Carregando"/>}

            <aside className={styles.sidebar}>
                <Link to={homePath} className={styles.brand} aria-label="Ir para o início">
                    <img src="/images/logotipo-vertical-claro.svg" alt="2AG" className={styles.brandFull}/>
                    <img src="/images/logotipo-icon-claro.svg" alt="" className={styles.brandIcon}/>
                </Link>

                <nav className={styles.sideMenu} aria-label="Menu principal">
                    {menuItems.map((item) => {
                        const Icon = item.icon;
                        const active = item === activeSideItem;
                        return (
                            <Link
                                key={item.path}
                                to={item.path}
                                title={item.label}
                                className={active ? styles.sideLink + " " + styles.sideLinkActive : styles.sideLink}
                                aria-current={active ? "page" : undefined}
                            >
                                <Icon className={styles.sideIcon}/>
                                <span className={styles.sideLabel}>{item.label}</span>
                            </Link>
                        );
                    })}
                </nav>

                <div className={styles.account}>
                    <div className={styles.accountText}>
                        <span className={styles.accountName}>{loggedUser ? loggedUser.name : ""}</span>
                        <span className={styles.accountRole}>{loggedUser ? ROLE_LABELS[loggedUser.role] : ""}</span>
                    </div>
                    <button type="button" className={styles.iconButton} onClick={handleLogout}
                            aria-label="Sair" title="Sair">
                        <FiLogOut/>
                    </button>
                </div>
            </aside>

            <header className={styles.mobileHeader}>
                {isHomePage ? (
                    <span className={styles.headerSpacer}/>
                ) : (
                    <button type="button" className={styles.iconButton} onClick={handleBack}
                            aria-label="Voltar" title="Voltar">
                        <FiArrowLeft/>
                    </button>
                )}
                <img src="/images/logotipo-icon-claro.svg" alt="2AG" className={styles.mobileLogo}/>
                <button type="button" className={styles.iconButton} onClick={handleLogout}
                        aria-label="Sair" title="Sair">
                    <FiLogOut/>
                </button>
            </header>

            <main className={styles.content}>
                {!isHomePage && (
                    <button type="button" className={styles.backLink} onClick={handleBack}>
                        <FiArrowLeft/>
                        Voltar
                    </button>
                )}
                {/* a chave muda a cada rota entao cada tela nova entra com o fade */}
                <div key={location.pathname} className={styles.page}>
                    <Outlet/>
                </div>
            </main>

            <nav className={styles.bottomMenu} aria-label="Menu">
                {mobileItems.map((item) => {
                    const Icon = item.icon;
                    const active = item === activeBottomItem;
                    return (
                        <Link
                            key={item.path}
                            to={item.path}
                            className={active ? styles.bottomLink + " " + styles.bottomLinkActive : styles.bottomLink}
                            aria-current={active ? "page" : undefined}
                        >
                            <span className={styles.bottomIcon}><Icon/></span>
                            <span>{item.label}</span>
                        </Link>
                    );
                })}
            </nav>

            <ScrollRestoration/>
        </div>
    );
}
