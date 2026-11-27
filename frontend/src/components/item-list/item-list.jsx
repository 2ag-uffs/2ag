import styles from "./item-list.module.css";

// lista simples de linhas separadas por um fio, usada dentro dos cartoes
export default function ItemList({children}) {
    return <ul className={styles.list}>{children}</ul>;
}

// uma linha da lista: o texto principal, o detalhe embaixo e a acao do lado
// no celular a acao desce pra baixo do texto
// isLate: a linha com o prazo vencido ganha o traco pessego na esquerda, como o cartao das avaliacoes
export function ListItem({title, details, aside, isLate}) {
    return (
        <li className={isLate ? styles.item + " " + styles.itemLate : styles.item}>
            <div className={styles.text}>
                <strong className={styles.title}>{title}</strong>
                {details && <span className={styles.details}>{details}</span>}
            </div>
            {aside && <div className={styles.aside}>{aside}</div>}
        </li>
    );
}
