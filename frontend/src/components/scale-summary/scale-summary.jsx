import {formatDate} from "../../utils/date-format.js";
import styles from "./scale-summary.module.css";

// resumo das escalas de um paciente com as pendentes e as ja respondidas (RF12 e RF13)
// quem usa escolhe o titulo das pendentes e pode colocar um botao embaixo delas
export default function ScaleSummary({scalesPage, pendingTitle, pendingAction}) {
    const pendingScales = scalesPage.pendingScales;
    // a respondida mais recente fica em cima
    const completedScales = [...scalesPage.completedScales].sort((firstScale, secondScale) =>
        String(secondScale.completionDate).localeCompare(String(firstScale.completionDate)));

    return (
        <div className={styles.boxes}>
            <div className={styles.box}>
                <h3>{pendingTitle}</h3>
                {pendingScales.length === 0 ? (
                    <p className={styles.empty}>Nenhuma escala pendente.</p>
                ) : (
                    <ul className={styles.scaleList}>
                        {pendingScales.map((scale) => (
                            <li key={scale.id}>{scale.scaleName}</li>
                        ))}
                    </ul>
                )}
                {pendingScales.length > 0 && pendingAction}
            </div>

            <div className={styles.box}>
                <h3>Respondidas</h3>
                {completedScales.length === 0 ? (
                    <p className={styles.empty}>Nenhuma escala respondida ainda.</p>
                ) : (
                    <ul className={styles.scaleList}>
                        {completedScales.map((scale) => (
                            <li key={scale.id}>
                                <span>{scale.scaleName}</span>
                                <span className={styles.date}>{formatDate(scale.completionDate)}</span>
                            </li>
                        ))}
                    </ul>
                )}
            </div>
        </div>
    );
}
