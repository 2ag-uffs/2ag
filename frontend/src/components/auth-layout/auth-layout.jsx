import styles from "./auth-layout.module.css";

// moldura das telas de quem ainda n entrou no sistema
// no computador a arte da marca fica do lado e no celular aparece so o formulario
export default function AuthLayout({title, children}) {
    return (
        <div className={styles.layout}>
            {/* lazy: no celular a arte fica escondida e o navegador n baixa as imagens dela */}
            <section className={styles.art} aria-hidden="true">
                <img src="/images/logotipo-icon-claro.svg" alt="" className={styles.cornerTopLeft} loading="lazy"/>
                <img src="/images/logotipo-icon-claro.svg" alt="" className={styles.cornerTopRight} loading="lazy"/>
                <img src="/images/logotipo-vertical-claro.svg" alt="" className={styles.artLogo} loading="lazy"/>
                <img src="/images/logotipo-icon-claro.svg" alt="" className={styles.cornerBottomLeft} loading="lazy"/>
                <img src="/images/logotipo-icon-claro.svg" alt="" className={styles.cornerBottomRight} loading="lazy"/>
            </section>

            <main className={styles.content}>
                <div className={styles.card}>
                    {/* so uma das duas aparece: a verde no tema claro e a de palha no escuro (styles/colors.css)
                        a largura e a altura do desenho guardam o lugar dela na internet lenta e a escondida n eh baixada */}
                    <img src="/images/logotipo-horizontal.svg" alt="2AG Cannabis Medicinal" width="611" height="149"
                         loading="lazy" className={"logo-on-light " + styles.logo}/>
                    <img src="/images/logotipo-horizontal-claro.svg" alt="2AG Cannabis Medicinal" width="611" height="149"
                         loading="lazy" className={"logo-on-dark " + styles.logo}/>
                    <h1 className={styles.title}>{title}</h1>
                    {children}
                </div>
            </main>
        </div>
    );
}
