import styles from "./empty-state.module.css";

// aviso de lista vazia com um icone e, quando faz sentido, a acao q resolve
// o compacto fica numa linha so e serve pra secao dentro de uma tela
export default function EmptyState({icon, message, action, isCompact = false}) {
    const Icon = icon;
    return (
        <div className={isCompact ? styles.empty + " " + styles.compact : styles.empty}>
            {Icon && <Icon className={styles.icon} aria-hidden="true"/>}
            <p>{message}</p>
            {action}
        </div>
    );
}
