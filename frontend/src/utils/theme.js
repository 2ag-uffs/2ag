// tema claro ou escuro
//
// sem escolha nenhuma o sistema segue o aparelho. qnd a pessoa usa o botao a
// escolha fica gravada no navegador e passa a valer sobre a do aparelho
//
// o public/theme-boot.js aplica a escolha antes do react carregar, entao aqui
// a gente so le o q ja esta valendo e troca

const STORAGE_KEY = "2ag-theme";

// cor do cabecalho de cada tema (--color-brand-surface do colors.css), a msm do public/theme-boot.js
const BAR_COLORS = {light: "#006633", dark: "#16281d"};

// a barra do navegador fica da cor do cabecalho do tema escolhido, e n do tema do aparelho
// as duas metas recebem a msm cor pq o media delas olha so o aparelho
function paintBrowserBar(theme) {
    document.querySelectorAll("meta[name=theme-color]").forEach((meta) => {
        meta.setAttribute("content", BAR_COLORS[theme]);
    });
}

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
    paintBrowserBar(theme);
    try {
        localStorage.setItem(STORAGE_KEY, theme);
    } catch {
        // navegador com armazenamento bloqueado so n lembra da escolha
    }
}
