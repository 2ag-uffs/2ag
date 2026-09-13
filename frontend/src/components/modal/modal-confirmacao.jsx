import Modal from "./modal.jsx";
import "./modal-confirmacao.css";

// pergunta de sim ou nao antes de uma acao que perde dado.
//
// antes cada tela chamava o confirm() do navegador, que trava a pagina
// inteira e n da pra estilizar. sao seis telas com a mesma pergunta,
// entao virou componente em vez de seis copias dos mesmos dois botoes
export default function ModalConfirmacao({
                                             show,
                                             titulo = "Confirmar",
                                             mensagem,
                                             textoConfirmar = "Confirmar",
                                             textoCancelar = "Voltar",
                                             onConfirmar,
                                             onCancelar,
                                         }) {
    return (
        <Modal show={show} title={titulo} onClickClose={onCancelar}>
            <p className="modal-confirmacao__texto">{mensagem}</p>
            <div className="modal-confirmacao__acoes">
                {/* o de voltar vem primeiro pra n ser o alvo mais facil */}
                <button type="button" className="button-secondary" onClick={onCancelar}>
                    {textoCancelar}
                </button>
                <button type="button" className="button" onClick={onConfirmar}>
                    {textoConfirmar}
                </button>
            </div>
        </Modal>
    );
}
