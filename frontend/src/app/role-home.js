// tela inicial de cada perfil
const HOME_BY_ROLE = {
    PATIENT: "/dashboard-paciente",
    PRESCRIBER: "/dashboard-prescritor",
    ADMIN: "/admin",
};

// sem ninguem logado a tela inicial eh o login
export function homePathFor(user) {
    if (!user) {
        return "/login";
    }
    return HOME_BY_ROLE[user.role] || "/login";
}
