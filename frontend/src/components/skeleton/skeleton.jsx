import styles from "./skeleton.module.css";

// bloco cinza q pulsa no lugar de um dado q ainda esta chegando
// width e height aceitam qualquer medida do css
export function SkeletonBlock({width = "100%", height = "1rem", isRound = false}) {
    return (
        <span
            className={isRound ? styles.block + " " + styles.round : styles.block}
            style={{width: width, height: height}}
        />
    );
}

// esqueleto de uma tela inteira: o titulo, os numeros do topo se tiver e os cartoes
// assim a tela n pula de lugar quando os dados chegam
export default function SkeletonPage({cards = 2, stats = 0}) {
    const statNumbers = Array.from({length: stats}, (unused, index) => index);
    const cardNumbers = Array.from({length: cards}, (unused, index) => index);

    return (
        <div className={styles.page} role="status" aria-label="Carregando">
            <div className={styles.header}>
                <SkeletonBlock width="14rem" height="2rem"/>
                <SkeletonBlock width="22rem" height="1rem"/>
            </div>

            {stats > 0 && (
                <div className={styles.stats}>
                    {statNumbers.map((index) => (
                        <div key={index} className={styles.box}>
                            <SkeletonBlock width="2.25rem" height="2.25rem" isRound={true}/>
                            <SkeletonBlock width="3rem" height="2rem"/>
                            <SkeletonBlock width="60%" height="0.875rem"/>
                        </div>
                    ))}
                </div>
            )}

            <div className={styles.cards}>
                {cardNumbers.map((index) => (
                    <div key={index} className={styles.box}>
                        <SkeletonBlock width="45%" height="1.25rem"/>
                        <SkeletonBlock height="0.875rem"/>
                        <SkeletonBlock width="80%" height="0.875rem"/>
                        <SkeletonBlock width="65%" height="0.875rem"/>
                    </div>
                ))}
            </div>
        </div>
    );
}
