import styles from "./shown-of-total.module.css";

// o cartao do painel traz so os primeiros cinco, entao avisa qnd a lista tem mais
export default function ShownOfTotal({shown, total}) {
    if (total <= shown) {
        return null;
    }
    return <p className={styles.shown}>Mostrando {shown} de {total}.</p>;
}
