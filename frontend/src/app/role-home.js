// tela inicial de cada perfil
const HOME_BY_ROLE = {
    PATIENT: "/painel-paciente",
    PRESCRIBER: "/painel-prescritor",
    ADMIN: "/administracao",
};

const LOGIN_PATH = "/entrar";

// sem ninguem logado a tela inicial eh o login
export function homePathFor(user) {
    if (!user) {
        return LOGIN_PATH;
    }
    return HOME_BY_ROLE[user.role] || LOGIN_PATH;
}

// papel q a api criou e o front ainda n conhece n tem tela inicial
export function hasKnownRole(user) {
    return Boolean(user && HOME_BY_ROLE[user.role]);
}
