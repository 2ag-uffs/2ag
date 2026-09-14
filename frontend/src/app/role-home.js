// tela inicial de cada perfil
const HOME_BY_ROLE = {
    PATIENT: "/painel-paciente",
    PRESCRIBER: "/painel-prescritor",
    ADMIN: "/administracao",
};

// sem ninguem logado a tela inicial eh o login
export function homePathFor(user) {
    if (!user) {
        return "/entrar";
    }
    return HOME_BY_ROLE[user.role] || "/entrar";
}
