import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
import Header from "../../components/header/header.jsx";
import ModalConfirmacao from "../../components/modal/modal-confirmacao.jsx";
import {apiService, ApiError} from "../../services/api.js";
import "../../styles/colors.css";
import "../../styles/fonts.css";
import "../../styles/button.css";
import "../../styles/input.css";
import "./acompanhamento-protocolo.css";

// as escalas que o paciente responde sozinho. o mini-exame fica de fora
// porque quem aplica eh a prescritora, durante a consulta (RN09)
const ESCALAS = [
    {valor: "ACOMPANHAMENTO_SEMANAL", texto: "Acompanhamento semanal"},
    {valor: "ESCALA_HAMILTON", texto: "Escala de ansiedade de Hamilton"},
    {valor: "ESCALA_PITTSBURGH", texto: "Índice de qualidade do sono de Pittsburgh"},
    {valor: "REGISTRO_SONO", texto: "Diário de sono"},
    {valor: "REGISTRO_DOR", texto: "Registro de dor"},
    {valor: "REGISTRO_TEA", texto: "Registro de sintomas (TEA)"},
    {valor: "ANAMNESE", texto: "Anamnese"},
];

const PERIODICIDADES = [
    {valor: "SEMANAL", texto: "Toda semana"},
    {valor: "QUINZENAL", texto: "A cada 15 dias"},
    {valor: "MENSAL", texto: "Uma vez por mês"},
];

// monta o acompanhamento automatico de 90 dias (RF32).
//
// a prescritora escolhe uma vez quais escalas e de quanto em quanto
// tempo, e o sistema manda sozinho dali em diante. antes ela tinha que
// designar formulario por formulario, paciente por paciente
export default function AcompanhamentoProtocolo() {
    const navigate = useNavigate();
    const {patientId} = useParams();

    const [protocoloAtivo, setProtocoloAtivo] = useState(null);
    const [escolhidas, setEscolhidas] = useState({});
    const [inicio, setInicio] = useState(new Date().toISOString().split("T")[0]);
    const [duracao, setDuracao] = useState(90);

    const [carregando, setCarregando] = useState(true);
    const [salvando, setSalvando] = useState(false);
    const [erro, setErro] = useState(null);
    const [aviso, setAviso] = useState(null);
    const [confirmandoEncerrar, setConfirmandoEncerrar] = useState(false);

    const carregar = () => {
        setCarregando(true);
        apiService
            .get(`/pacientes/${patientId}/acompanhamento`)
            .then((protocolo) => setProtocoloAtivo(protocolo))
            .catch((err) => {
                // 404 aqui so quer dizer que ainda n existe acompanhamento
                if (err instanceof ApiError && err.status === 404) {
                    setProtocoloAtivo(null);
                } else {
                    setErro(err instanceof ApiError ? err.message : "Não foi possível carregar.");
                }
            })
            .finally(() => setCarregando(false));
    };

    useEffect(carregar, [patientId]);

    const alternarEscala = (valor) => {
        setEscolhidas((antes) => {
            const copia = {...antes};
            if (copia[valor]) {
                delete copia[valor];
            } else {
                copia[valor] = "SEMANAL";
            }
            return copia;
        });
    };

    const mudarPeriodicidade = (valor, periodicidade) => {
        setEscolhidas((antes) => ({...antes, [valor]: periodicidade}));
    };

    const criar = async (e) => {
        e.preventDefault();
        setErro(null);

        const itens = Object.entries(escolhidas).map(([scaleType, periodicity]) => ({
            scaleType,
            periodicity,
        }));

        if (itens.length === 0) {
            setErro("Escolha ao menos uma escala.");
            return;
        }

        setSalvando(true);
        try {
            await apiService.post(`/pacientes/${patientId}/acompanhamento`, {
                startDate: inicio,
                durationDays: Number(duracao),
                items: itens,
            });
            setAviso("Acompanhamento iniciado. O sistema vai enviar as escalas sozinho.");
            carregar();
        } catch (err) {
            setErro(err instanceof ApiError ? err.message : "Não foi possível iniciar o acompanhamento.");
        } finally {
            setSalvando(false);
        }
    };

    // o confirm do navegador virou modal, quem pergunta eh o botao
    const encerrar = async () => {
        setConfirmandoEncerrar(false);
        try {
            await apiService.delete(`/pacientes/${patientId}/acompanhamento`);
            carregar();
        } catch (err) {
            setErro(err instanceof ApiError ? err.message : "Não foi possível encerrar.");
        }
    };

    if (carregando) {
        return (
            <div className="protocolo">
                <Header/>
                <main className="protocolo__conteudo"><p>Carregando...</p></main>
            </div>
        );
    }

    return (
        <div className="protocolo">
            <Header/>
            <main className="protocolo__conteudo">
                <h1>Acompanhamento automático</h1>

                {erro && <p className="protocolo__erro">{erro}</p>}
                {aviso && <p className="aviso">{aviso}</p>}

                {protocoloAtivo ? (
                    <section className="protocolo__ativo">
                        <h2>Em andamento</h2>
                        <p>
                            Paciente: <strong>{protocoloAtivo.patientName}</strong>
                        </p>
                        <p>
                            De {protocoloAtivo.startDate} até {protocoloAtivo.endDate}
                        </p>

                        <table className="protocolo__tabela">
                            <thead>
                            <tr>
                                <th>Escala</th>
                                <th>Frequência</th>
                            </tr>
                            </thead>
                            <tbody>
                            {protocoloAtivo.items.map((item) => (
                                <tr key={item.scaleType}>
                                    <td>{item.scaleName}</td>
                                    <td>
                                        {PERIODICIDADES.find((p) => p.valor === item.periodicity)?.texto}
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>

                        <p className="protocolo__nota">
                            O sistema envia essas escalas sozinho, na frequência escolhida, até a
                            data final. Não é preciso mandar uma por uma.
                        </p>

                        <div className="protocolo__acoes">
                            <button
                                type="button"
                                className="button-secondary"
                                onClick={() => setConfirmandoEncerrar(true)}
                            >
                                Encerrar acompanhamento
                            </button>
                            <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                                Voltar
                            </button>
                        </div>
                    </section>
                ) : (
                    <form onSubmit={criar}>
                        <p className="protocolo__ajuda">
                            Escolha as escalas e de quanto em quanto tempo o paciente deve
                            respondê-las. O sistema envia sozinho durante todo o período.
                        </p>

                        <div className="protocolo__campos">
                            <div className="protocolo__campo">
                                <label htmlFor="inicio">Começa em</label>
                                <input
                                    id="inicio"
                                    type="date"
                                    value={inicio}
                                    onChange={(e) => setInicio(e.target.value)}
                                    required={true}
                                />
                            </div>
                            <div className="protocolo__campo">
                                <label htmlFor="duracao">Duração (dias)</label>
                                <input
                                    id="duracao"
                                    type="number"
                                    min="1"
                                    value={duracao}
                                    onChange={(e) => setDuracao(e.target.value)}
                                    required={true}
                                />
                            </div>
                        </div>

                        <section className="protocolo__lista">
                            {ESCALAS.map((escala) => (
                                <div className="protocolo__linha" key={escala.valor}>
                                    <label className="protocolo__escala">
                                        <input
                                            type="checkbox"
                                            checked={Boolean(escolhidas[escala.valor])}
                                            onChange={() => alternarEscala(escala.valor)}
                                        />
                                        <span>{escala.texto}</span>
                                    </label>

                                    {escolhidas[escala.valor] && (
                                        <select
                                            value={escolhidas[escala.valor]}
                                            onChange={(e) => mudarPeriodicidade(escala.valor, e.target.value)}
                                        >
                                            {PERIODICIDADES.map((p) => (
                                                <option key={p.valor} value={p.valor}>{p.texto}</option>
                                            ))}
                                        </select>
                                    )}
                                </div>
                            ))}
                        </section>

                        <div className="protocolo__acoes">
                            <button type="submit" disabled={salvando}>
                                {salvando ? "Iniciando..." : "Iniciar acompanhamento"}
                            </button>
                            <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                                Cancelar
                            </button>
                        </div>
                    </form>
                )}
            </main>

            <ModalConfirmacao
                show={confirmandoEncerrar}
                titulo="Encerrar acompanhamento"
                mensagem="O sistema para de enviar as escalas para este paciente. Quer encerrar?"
                textoConfirmar="Sim, encerrar"
                textoCancelar="Manter ativo"
                onConfirmar={encerrar}
                onCancelar={() => setConfirmandoEncerrar(false)}
            />
        </div>
    );
}
