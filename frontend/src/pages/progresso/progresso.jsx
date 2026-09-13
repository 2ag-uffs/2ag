import {useEffect, useMemo, useState} from "react";
import {useNavigate, useParams} from "react-router";
import {
    CartesianGrid,
    Legend,
    Line,
    LineChart,
    ReferenceLine,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts";
import Header from "../../components/header/header.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import "../../styles/colors.css";
import "../../styles/fonts.css";
import "../../styles/button.css";
import "./progresso.css";

// o recharts precisa das cores como valor, n como var() do css.
// entao a gente le o token no momento de montar a tela
const lerToken = (nome, padrao) =>
    getComputedStyle(document.documentElement).getPropertyValue(nome).trim() || padrao;

const CORES = {
    linha: lerToken("--color-chart-line", "#006633"),
    grade: lerToken("--color-chart-grid", "#e3dcd2"),
    consulta: lerToken("--color-terracotta-dark", "#a44819"),
};

// aaaa-mm-dd vira dd/mm, que eh como o eixo do grafico mostra
const diaEMes = (data) => data.slice(8, 10) + "/" + data.slice(5, 7);

const PERIODOS = [
    {valor: "DIAS_15", texto: "Últimos 15 dias"},
    {valor: "DIAS_30", texto: "Últimos 30 dias"},
    {valor: "DIAS_60", texto: "Últimos 60 dias"},
    {valor: "DIAS_90", texto: "Últimos 90 dias"},
];

// serve pras duas telas de progresso, a do paciente e a do prescritor
// (RF27 e RF28). a diferenca eh so de onde vem o id do paciente: da url
// quando o prescritor abre, ou do token quando o proprio paciente entra
export default function Progresso() {
    const navigate = useNavigate();
    const {patientId: patientIdDaUrl} = useParams();
    const usuarioLogado = getLoggedUser();
    const patientId = patientIdDaUrl || usuarioLogado?.id;

    const [atributos, setAtributos] = useState([]);
    const [escalaEscolhida, setEscalaEscolhida] = useState("");
    const [atributoEscolhido, setAtributoEscolhido] = useState("");
    const [periodo, setPeriodo] = useState("DIAS_30");

    const [pontos, setPontos] = useState([]);
    // as consultas do mesmo periodo, marcadas no grafico
    const [consultas, setConsultas] = useState([]);
    const [mostrarConsultas, setMostrarConsultas] = useState(true);
    const [carregando, setCarregando] = useState(false);
    const [erro, setErro] = useState(null);

    // o catalogo vem do backend, entao a tela n tem uma lista de escalas
    // e atributos repetida aqui dentro pra ficar desatualizada
    useEffect(() => {
        apiService
            .get("/progresso/atributos")
            .then((lista) => {
                setAtributos(lista);
                if (lista.length > 0) {
                    setEscalaEscolhida(lista[0].scaleType);
                    setAtributoEscolhido(lista[0].name);
                }
            })
            .catch((err) => {
                setErro(err instanceof ApiError ? err.message : "Não foi possível carregar as escalas.");
            });
    }, []);

    // as escalas que aparecem no seletor, sem repetir
    const escalas = useMemo(() => {
        const vistas = new Map();
        atributos.forEach((a) => vistas.set(a.scaleType, a.scaleName));
        return Array.from(vistas, ([valor, texto]) => ({valor, texto}));
    }, [atributos]);

    const atributosDaEscala = useMemo(
        () => atributos.filter((a) => a.scaleType === escalaEscolhida),
        [atributos, escalaEscolhida],
    );

    const atributoAtual = atributos.find((a) => a.name === atributoEscolhido);

    // o eixo x eh categorico: so da pra marcar consulta em dia que tem
    // ponto no grafico, senao o recharts n sabe onde por a linha
    const marcasDeConsulta = useMemo(() => {
        if (!mostrarConsultas) {
            return [];
        }
        const diasComPonto = new Set(pontos.map((ponto) => ponto.data));
        const vistos = new Set();
        return consultas
            .map((consulta) => ({...consulta, rotulo: diaEMes(consulta.data)}))
            .filter((consulta) => {
                if (!diasComPonto.has(consulta.rotulo) || vistos.has(consulta.rotulo)) {
                    return false;
                }
                vistos.add(consulta.rotulo);
                return true;
            });
    }, [consultas, pontos, mostrarConsultas]);

    // as que ficaram de fora do eixo continuam valendo como informacao,
    // entao aparecem na lista embaixo do grafico
    const consultasNoPeriodo = consultas.length;

    // ao trocar de escala, o atributo escolhido pode n ser mais dela
    const trocarEscala = (novaEscala) => {
        setEscalaEscolhida(novaEscala);
        const primeiro = atributos.find((a) => a.scaleType === novaEscala);
        setAtributoEscolhido(primeiro ? primeiro.name : "");
    };

    useEffect(() => {
        if (!patientId || !atributoEscolhido) {
            return;
        }

        setCarregando(true);
        setErro(null);

        apiService
            .get(`/pacientes/${patientId}/progresso?atributo=${atributoEscolhido}&periodo=${periodo}`)
            .then((dados) => {
                setPontos(dados.map((ponto) => ({data: diaEMes(ponto.date), valor: ponto.value})));
            })
            .catch((err) => {
                setErro(err instanceof ApiError ? err.message : "Não foi possível carregar o progresso.");
                setPontos([]);
            })
            .finally(() => setCarregando(false));
    }, [patientId, atributoEscolhido, periodo]);

    // as consultas n dependem da escala escolhida, so do periodo, entao
    // elas ficam num efeito separado pra n buscar de novo a cada troca
    // de atributo
    useEffect(() => {
        if (!patientId) {
            return;
        }

        apiService
            .get(`/pacientes/${patientId}/progresso/consultas?periodo=${periodo}`)
            .then(setConsultas)
            // se as consultas falharem, o grafico ainda serve: elas sao
            // contexto, n o dado principal
            .catch(() => setConsultas([]));
    }, [patientId, periodo]);

    if (!patientId) {
        return (
            <div className="progresso">
                <Header/>
                <main className="progresso__conteudo">
                    <p className="progresso__erro">Não foi possível identificar o paciente.</p>
                </main>
            </div>
        );
    }

    return (
        <div className="progresso">
            <Header/>
            <main className="progresso__conteudo">
                <h1 className="progresso__titulo">Progresso do tratamento</h1>

                <section className="progresso__filtros">
                    <div className="progresso__filtro">
                        <label htmlFor="periodo">Período</label>
                        <select
                            id="periodo"
                            value={periodo}
                            onChange={(e) => setPeriodo(e.target.value)}
                        >
                            {PERIODOS.map((p) => (
                                <option key={p.valor} value={p.valor}>{p.texto}</option>
                            ))}
                        </select>
                    </div>

                    <div className="progresso__filtro">
                        <label htmlFor="escala">Escala</label>
                        <select
                            id="escala"
                            value={escalaEscolhida}
                            onChange={(e) => trocarEscala(e.target.value)}
                        >
                            {escalas.map((e) => (
                                <option key={e.valor} value={e.valor}>{e.texto}</option>
                            ))}
                        </select>
                    </div>

                    <div className="progresso__filtro">
                        <label htmlFor="atributo">O que acompanhar</label>
                        <select
                            id="atributo"
                            value={atributoEscolhido}
                            onChange={(e) => setAtributoEscolhido(e.target.value)}
                        >
                            {atributosDaEscala.map((a) => (
                                <option key={a.name} value={a.name}>{a.displayName}</option>
                            ))}
                        </select>
                    </div>

                    <div className="progresso__filtro progresso__filtro--caixa">
                        <label htmlFor="consultas">
                            <input
                                id="consultas"
                                type="checkbox"
                                checked={mostrarConsultas}
                                onChange={(e) => setMostrarConsultas(e.target.checked)}
                            />
                            Marcar as consultas
                        </label>
                    </div>
                </section>

                {erro && <p className="progresso__erro">{erro}</p>}

                <section className="progresso__grafico">
                    {carregando && <p>Carregando...</p>}

                    {!carregando && pontos.length === 0 && !erro && (
                        <p className="progresso__vazio">
                            Nenhum registro preenchido neste período.
                        </p>
                    )}

                    {!carregando && pontos.length > 0 && (
                        <>
                            <h2 className="progresso__grafico-titulo">
                                {atributoAtual ? atributoAtual.displayName : ""}
                            </h2>
                            <ResponsiveContainer width="100%" height={320}>
                                <LineChart data={pontos} margin={{top: 16, right: 24, bottom: 8, left: 0}}>
                                    {/* as cores saem da paleta da marca, n do
                                        padrao do recharts */}
                                    <CartesianGrid strokeDasharray="3 3" stroke={CORES.grade}/>
                                    <XAxis dataKey="data"/>
                                    {/* a faixa vem do backend: 0 a 10 e 0 a 56 n
                                        podem dividir o mesmo eixo */}
                                    <YAxis
                                        domain={[
                                            atributoAtual?.minValue ?? 0,
                                            atributoAtual?.maxValue ?? "auto",
                                        ]}
                                        allowDecimals={false}
                                    />
                                    <Tooltip/>
                                    <Legend/>
                                    {/* a consulta vira uma linha vertical no dia em
                                        que aconteceu, pra dar pra ler a curva junto
                                        com o que foi feito no atendimento */}
                                    {marcasDeConsulta.map((consulta) => (
                                        <ReferenceLine
                                            key={consulta.id}
                                            x={consulta.rotulo}
                                            stroke={CORES.consulta}
                                            strokeDasharray="4 4"
                                            label={{
                                                value: consulta.geraPrescricao ? "consulta + receita" : "consulta",
                                                position: "top",
                                                fontSize: 11,
                                                fill: CORES.consulta,
                                            }}
                                        />
                                    ))}
                                    <Line
                                        type="monotone"
                                        dataKey="valor"
                                        name={atributoAtual ? atributoAtual.displayName : "valor"}
                                        stroke={CORES.linha}
                                        strokeWidth={2}
                                        dot={{r: 4, fill: CORES.linha}}
                                        activeDot={{r: 6}}
                                    />
                                </LineChart>
                            </ResponsiveContainer>
                            <p className="progresso__nota">
                                Cada ponto é um preenchimento. Dia sem resposta não aparece no
                                gráfico, em vez de aparecer como zero.
                            </p>

                            {mostrarConsultas && consultasNoPeriodo > 0 && (
                                <section className="progresso__consultas">
                                    <h3>Consultas no período</h3>
                                    <ul>
                                        {consultas.map((consulta) => (
                                            <li key={consulta.id}>
                                                <strong>{diaEMes(consulta.data)}</strong>
                                                {consulta.diagnosis ? " — " + consulta.diagnosis : ""}
                                                {consulta.geraPrescricao && (
                                                    <span className="progresso__marca">receita emitida</span>
                                                )}
                                            </li>
                                        ))}
                                    </ul>
                                    {/* so vira linha no grafico a consulta que caiu num
                                        dia com preenchimento, entao a lista avisa */}
                                    {marcasDeConsulta.length < consultasNoPeriodo && (
                                        <p className="progresso__nota">
                                            Consulta em dia sem preenchimento aparece aqui, mas não no
                                            gráfico: não há ponto onde marcar.
                                        </p>
                                    )}
                                </section>
                            )}
                        </>
                    )}
                </section>

                <button className="button-secondary" onClick={() => navigate(-1)}>
                    Voltar
                </button>
            </main>
        </div>
    );
}
