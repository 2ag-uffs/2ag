import styles from "./auth-layout.module.css";

// moldura das telas de quem ainda n entrou no sistema
// no computador a arte da marca fica do lado e no celular aparece so o formulario
export default function AuthLayout({title, children}) {
    return (
        <div className={styles.layout}>
            <section className={styles.art} aria-hidden="true">
                <img src="/images/logotipo-icon-claro.svg" alt="" className={styles.cornerTopLeft}/>
                <img src="/images/logotipo-icon-claro.svg" alt="" className={styles.cornerTopRight}/>
                <img src="/images/logotipo-vertical-claro.svg" alt="" className={styles.artLogo}/>
                <img src="/images/logotipo-icon-claro.svg" alt="" className={styles.cornerBottomLeft}/>
                <img src="/images/logotipo-icon-claro.svg" alt="" className={styles.cornerBottomRight}/>
            </section>

            <main className={styles.content}>
                <div className={styles.card}>
                    <img src="/images/logotipo-horizontal.svg" alt="2AG Cannabis Medicinal" className={styles.logo}/>
                    <h1 className={styles.title}>{title}</h1>
                    {children}
                </div>
            </main>
        </div>
    );
}
