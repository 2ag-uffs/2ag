import styles from "./page-loader.module.css";

// aparece enquanto a tela ainda esta sendo baixada
export default function PageLoader() {
    return (
        <div className={styles.loader} role="status" aria-label="Carregando">
            <span className={styles.spinner}/>
        </div>
    );
}
