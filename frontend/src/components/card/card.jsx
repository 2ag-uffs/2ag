import styles from "./card.module.css";

// cartao branco com titulo opcional e rodape de acoes
// o rodape fica sempre embaixo, entao cartoes lado a lado com alturas
// diferentes continuam com os botoes alinhados
export default function Card({title, count, headerAction, footer, children}) {
    return (
        <section className={styles.card}>
            {(title || headerAction) && (
                <div className={styles.cardHeader}>
                    {title && (
                        <h2 className={styles.title}>
                            {title}
                            {/* numero ao lado do titulo, tipo quantos pedidos esperam */}
                            {count !== undefined && <span className={styles.count}>{count}</span>}
                        </h2>
                    )}
                    {headerAction}
                </div>
            )}
            <div className={styles.body}>{children}</div>
            {footer && <div className={styles.footer}>{footer}</div>}
        </section>
    );
}
