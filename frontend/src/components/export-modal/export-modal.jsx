import {useState} from "react";
import {useNavigate} from "react-router";
import Modal from "../modal/modal.jsx";
import styles from "./export-modal.module.css";

// o q da pra levar, um arquivo por tipo
const FILES = [
    {file: "appointments.csv", label: "Consultas", help: "Data, conduta e o que foi registrado em cada atendimento"},
    {file: "prescriptions.csv", label: "Prescrições", help: "Óleo, composição, posologia e vigência de cada uma"},
    {file: "scales.csv", label: "Escalas respondidas", help: "Uma linha por item respondido, com o escore e a faixa"},
    {file: "anamneses.csv", label: "Anamnese", help: "A ficha de triagem, pergunta por pergunta"},
];

// exportacao dos dados do paciente (RF33)
//
// o csv abre direto na planilha e a versao para impressao serve pra
// salvar em pdf pelo proprio navegador
export default function ExportModal({patientId, printPath, canAnonymize, onClose}) {
    const navigate = useNavigate();
    const [isAnonymous, setIsAnonymous] = useState(false);

    const downloadUrl = (file) =>
        "/api/patients/" + patientId + "/export/" + file + (isAnonymous ? "?anonymous=true" : "");

    return (
        <Modal show={true} title="Exportar dados" onClickClose={onClose}>
            <div className={styles.box}>
                <p className={styles.help}>
                    Cada arquivo abre direto numa planilha. Para levar em papel ou salvar em PDF, use a versão
                    para impressão.
                </p>

                {canAnonymize && (
                    <label className={styles.anonymous}>
                        <input
                            type="checkbox"
                            checked={isAnonymous}
                            onChange={(event) => setIsAnonymous(event.target.checked)}
                        />
                        <span>
                            Modo anônimo, para pesquisa
                            <span className={styles.help}>
                                Sai sem nome, CPF, e-mail, telefone e endereço. O paciente aparece só por um número.
                            </span>
                        </span>
                    </label>
                )}

                <ul className={styles.files}>
                    {FILES.map((item) => (
                        <li key={item.file} className={styles.file}>
                            <div>
                                <strong>{item.label}</strong>
                                <p className={styles.help}>{item.help}</p>
                            </div>
                            <a className="button-secondary" href={downloadUrl(item.file)} download={true}>
                                Baixar CSV
                            </a>
                        </li>
                    ))}
                </ul>

                <p className={styles.help}>
                    A série de evolução sai na tela de progresso, junto com a escala e o período escolhidos.
                </p>

                <div className={styles.actions}>
                    <button type="button" className="button-secondary" onClick={onClose}>
                        Fechar
                    </button>
                    <button type="button" className="button" onClick={() => navigate(printPath)}>
                        Versão para impressão
                    </button>
                </div>
            </div>
        </Modal>
    );
}
