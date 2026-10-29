// tema claro ou escuro
//
// sem escolha nenhuma o sistema segue o aparelho. qnd a pessoa usa o botao a
// escolha fica gravada no navegador e passa a valer sobre a do aparelho
//
// o public/theme-boot.js aplica a escolha antes do react carregar, entao aqui
// a gente so le o q ja esta valendo e troca

const STORAGE_KEY = "2ag-theme";

// navegador sem matchMedia fica no claro
function prefersDark() {
    if (typeof window.matchMedia !== "function") {
        return false;
    }
    return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

// o tema q esta na tela agora, escolhido ou herdado do aparelho
export function currentTheme() {
    const chosen = document.documentElement.getAttribute("data-theme");
    if (chosen === "dark" || chosen === "light") {
        return chosen;
    }
    return prefersDark() ? "dark" : "light";
}

export function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    try {
        localStorage.setItem(STORAGE_KEY, theme);
    } catch {
        // navegador com armazenamento bloqueado so n lembra da escolha
    }
}
