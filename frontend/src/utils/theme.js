// tema claro ou escuro
//
// sem escolha nenhuma o sistema segue o aparelho. qnd a pessoa usa o botao a
// escolha fica gravada no navegador e passa a valer sobre a do aparelho
//
// o public/theme-boot.js aplica a escolha antes do react carregar, entao aqui
// a gente so le o q ja esta valendo e troca

const STORAGE_KEY = "2ag-theme";
const THEME_EVENT = "2ag:theme";

function prefersDark() {
    return typeof window.matchMedia === "function"
        && window.matchMedia("(prefers-color-scheme: dark)").matches;
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
    window.dispatchEvent(new CustomEvent(THEME_EVENT, {detail: theme}));
}

// pra quem desenha com a cor em javascript e n em css, como o grafico
// devolve a funcao de parar de ouvir, q o efeito do react usa na limpeza
export function onThemeChange(listener) {
    const media = typeof window.matchMedia === "function"
        ? window.matchMedia("(prefers-color-scheme: dark)")
        : null;

    if (media) {
        media.addEventListener("change", listener);
    }
    window.addEventListener(THEME_EVENT, listener);

    return () => {
        if (media) {
            media.removeEventListener("change", listener);
        }
        window.removeEventListener(THEME_EVENT, listener);
    };
}
