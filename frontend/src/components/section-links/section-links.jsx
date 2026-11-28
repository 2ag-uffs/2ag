import styles from "./section-links.module.css";

// atalhos q levam direto pra uma parte de uma pagina longa sem mudar o endereco
export default function SectionLinks({label, sections}) {
    const scrollToSection = (sectionId) => {
        // quem pediu menos movimento no sistema pula direto sem animacao
        const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        const section = document.getElementById(sectionId);
        section.scrollIntoView({
            behavior: prefersReducedMotion ? "auto" : "smooth",
            block: "start",
        });
        // o foco vai junto pro titulo da parte, so dessa vez. so rolar deixava o foco no atalho: o tab
        // seguinte voltava pro alto da pagina e o leitor de tela n dizia nada
        // o data-own-scroll avisa a conferencia do foco atras das barras (utils/focus-above-bars) q a rolagem
        // ja eh daqui: sem ele ela subia a pagina so ate o titulo aparecer no pe e cortava a rolagem suave
        const title = section.querySelector("h2") || section;
        title.setAttribute("tabindex", "-1");
        title.setAttribute("data-own-scroll", "");
        title.focus({preventScroll: true});
        title.addEventListener("blur", () => {
            title.removeAttribute("tabindex");
            title.removeAttribute("data-own-scroll");
        }, {once: true});
    };

    return (
        <nav className={styles.links} aria-label={label}>
            {sections.map((section) => (
                <button
                    key={section.id}
                    type="button"
                    className={styles.link}
                    onClick={() => scrollToSection(section.id)}
                >
                    {section.label}
                </button>
            ))}
        </nav>
    );
}
