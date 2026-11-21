// aplica o tema escolhido antes do react carregar, senao a tela pisca clara
// fica em arquivo proprio pq a politica de seguranca do nginx bloqueia script inline
(function () {
    // cor do cabecalho de cada tema, a msm do utils/theme.js
    var barColors = {light: "#006633", dark: "#16281d"};
    try {
        var chosen = localStorage.getItem("2ag-theme");
        if (chosen === "dark" || chosen === "light") {
            document.documentElement.setAttribute("data-theme", chosen);
            // a barra do navegador fica da cor do cabecalho escolhido. sem escolha as metas seguem o aparelho
            var metas = document.querySelectorAll("meta[name=theme-color]");
            for (var i = 0; i < metas.length; i++) {
                metas[i].setAttribute("content", barColors[chosen]);
            }
        }
    } catch {
        // navegador com armazenamento bloqueado segue a preferencia do aparelho
    }
})();
