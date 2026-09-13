import {useEffect, useMemo, useState} from "react";
import {useNavigate, useParams} from "react-router";
import {
    CartesianGrid,
    Line,
    LineChart,
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
                // a data vem como aaaa-mm-dd, o grafico mostra dd/mm
                setPontos(
                    dados.map((ponto) => ({
                        data: ponto.date.slice(8, 10) + "/" + ponto.date.slice(5, 7),
                        valor: ponto.value,
                    })),
                );
            })
            .catch((err) => {
                setErro(err instanceof ApiError ? err.message : "Não foi possível carregar o progresso.");
                setPontos([]);
            })
            .finally(() => setCarregando(false));
    }, [patientId, atributoEscolhido, periodo]);

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
                                    <CartesianGrid strokeDasharray="3 3"/>
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
                                    <Line
                                        type="monotone"
                                        dataKey="valor"
                                        name={atributoAtual ? atributoAtual.displayName : "valor"}
                                        strokeWidth={2}
                                        dot={{r: 4}}
                                    />
                                </LineChart>
                            </ResponsiveContainer>
                            <p className="progresso__nota">
                                Cada ponto é um preenchimento. Dia sem resposta não aparece no
                                gráfico, em vez de aparecer como zero.
                            </p>
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
