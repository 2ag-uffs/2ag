import styles from "./section-links.module.css";

// atalhos q levam direto pra uma parte de uma pagina longa sem mudar o endereco
export default function SectionLinks({label, sections}) {
    const scrollToSection = (sectionId) => {
        // quem pediu menos movimento no sistema pula direto sem animacao
        const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        document.getElementById(sectionId).scrollIntoView({
            behavior: prefersReducedMotion ? "auto" : "smooth",
            block: "start",
        });
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
