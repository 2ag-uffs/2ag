import styles from "./page-header.module.css";

// titulo da tela com a frase de apoio e as acoes principais do lado
// no celular as acoes descem pra baixo do titulo
export default function PageHeader({title, subtitle, actions}) {
    return (
        <header className={styles.header}>
            <div className={styles.text}>
                <h1 className={styles.title}>{title}</h1>
                {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
            </div>
            {actions && <div className={styles.actions}>{actions}</div>}
        </header>
    );
}
