// aplica o tema escolhido antes do react carregar, senao a tela pisca clara
// fica em arquivo proprio pq a politica de seguranca do nginx bloqueia script inline
(function () {
    try {
        var chosen = localStorage.getItem("2ag-theme");
        if (chosen === "dark" || chosen === "light") {
            document.documentElement.setAttribute("data-theme", chosen);
        }
    } catch {
        // navegador com armazenamento bloqueado segue a preferencia do aparelho
    }
})();
