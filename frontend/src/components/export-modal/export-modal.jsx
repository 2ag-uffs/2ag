import {useState} from "react";
import {useNavigate} from "react-router";
import Modal from "../modal/modal.jsx";
import {ApiError, download} from "../../services/api.js";
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
// o modo sem identificacao eh o de pesquisa e so sai de quem aceitou participar (RF37)
export default function ExportModal({patientId, printPath, canDeidentify, hasResearchConsent, onClose}) {
    const navigate = useNavigate();
    const [isDeidentified, setIsDeidentified] = useState(false);
    const [downloadError, setDownloadError] = useState(null);
    const [downloadingFile, setDownloadingFile] = useState(null);

    // pela api, senao sessao vencida salvava um csv com o json do erro dentro
    const downloadFile = async (file) => {
        setDownloadError(null);
        setDownloadingFile(file);
        try {
            const deidentifiedParam = isDeidentified && hasResearchConsent ? "?deidentified=true" : "";
            await download("/patients/" + patientId + "/export/" + file + deidentifiedParam, file);
        } catch (requestError) {
            setDownloadError(requestError instanceof ApiError
                ? requestError.message
                : "Não foi possível baixar o arquivo. Confira sua internet e tente de novo.");
        } finally {
            setDownloadingFile(null);
        }
    };

    return (
        <Modal show={true} title="Exportar dados" onClickClose={onClose}>
            <div className={styles.box}>
                <p className={styles.help}>
                    Cada arquivo abre direto numa planilha. Para levar em papel ou salvar em PDF, use a versão
                    para impressão.
                </p>

                {canDeidentify && (
                    <label className={styles.deidentified}>
                        <input
                            type="checkbox"
                            checked={isDeidentified && hasResearchConsent}
                            onChange={(event) => setIsDeidentified(event.target.checked)}
                            disabled={!hasResearchConsent}
                        />
                        <span>
                            Modo sem identificação, para pesquisa
                            <span className={styles.help}>
                                {hasResearchConsent
                                    ? "Nome, CPF, e-mail, telefone e endereço ficam de fora, e o paciente e o "
                                        + "prescritor aparecem só por um número. Não é anônimo: a clínica sabe de quem "
                                        + "é cada número, as datas saem como estão e o texto livre, como queixa e "
                                        + "observação, sai como foi escrito e pode citar nomes. Pela LGPD o arquivo "
                                        + "continua sendo dado de saúde: guarde e envie com o mesmo cuidado do prontuário."
                                    : "O paciente não aceitou participar da pesquisa, então o arquivo sem "
                                        + "identificação não sai. Ele pode aceitar no perfil dele."}
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
                            <button
                                type="button"
                                className="button-secondary"
                                onClick={() => downloadFile(item.file)}
                                disabled={downloadingFile !== null}
                            >
                                {downloadingFile === item.file ? "Baixando..." : "Baixar CSV"}
                            </button>
                        </li>
                    ))}
                </ul>

                {downloadError && (
                    <p className="aviso aviso--atencao" role="alert">{downloadError}</p>
                )}

                <p className={styles.help}>
                    A série de evolução sai na tela de progresso, junto com a escala e o período escolhidos.
                    {canDeidentify ? " O modo sem identificação dela é marcado lá." : ""}
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
