import {useState} from "react";
import {useNavigate, useParams} from "react-router";
import Header from "../../components/header/header.jsx";
import {apiService, ApiError} from "../../services/api.js";
import "../../styles/colors.css";
import "../../styles/fonts.css";
import "../../styles/button.css";
import "../../styles/input.css";
import "./mini-exame-estado-mental.css";

// as 11 secoes do instrumento, na ordem do formulario da clinica.
// cada item vale 1 ponto, entao a nota da secao eh quantos itens a
// pessoa acertou. o total fecha 30.
//
// antes esta tela tinha so 6 secoes, somando 17 pontos, e mesmo assim
// interpretava o resultado contra as faixas de 30. ou seja ninguem
// conseguia ser classificado como normal: 17 de 17 ja caia em
// "comprometimento leve"
const SECOES = [
    {
        campo: "temporalOrientation",
        titulo: "Orientação temporal",
        pontos: 5,
        itens: [
            "Qual é a hora aproximada?",
            "Em que dia da semana estamos?",
            "Que dia do mês é hoje?",
            "Em que mês estamos?",
            "Em que ano estamos?",
        ],
    },
    {
        campo: "spatialOrientation",
        titulo: "Orientação espacial",
        pontos: 5,
        itens: [
            "Em que local estamos?",
            "Que local é este aqui?",
            "Em que bairro nós estamos ou qual é o endereço daqui?",
            "Em que cidade nós estamos?",
            "Em que estado nós estamos?",
        ],
    },
    {
        campo: "registration",
        titulo: "Registro",
        pontos: 3,
        ajuda: "Peça para repetir: CARRO, VASO, TIJOLO",
        itens: ["Repetiu CARRO", "Repetiu VASO", "Repetiu TIJOLO"],
    },
    {
        campo: "attentionAndCalculation",
        titulo: "Atenção e cálculo",
        pontos: 5,
        ajuda: "Subtrair 7 sucessivamente a partir de 100: 93, 86, 79, 72, 65",
        itens: ["93", "86", "79", "72", "65"],
    },
    {
        campo: "recall",
        titulo: "Memória de evocação",
        pontos: 3,
        ajuda: "Quais os três objetos perguntados anteriormente?",
        itens: ["Lembrou CARRO", "Lembrou VASO", "Lembrou TIJOLO"],
    },
    {
        campo: "naming",
        titulo: "Nomeação",
        pontos: 2,
        itens: ["Nomeou o relógio", "Nomeou a caneta"],
    },
    {
        campo: "repetition",
        titulo: "Repetição",
        pontos: 1,
        itens: ['Repetiu "Nem aqui, nem ali, nem lá"'],
    },
    {
        campo: "command",
        titulo: "Comando de três estágios",
        pontos: 3,
        ajuda: "Apanhe esta folha de papel com a mão direita, dobre-a ao meio e coloque-a no chão",
        itens: ["Pegou o papel", "Dobrou ao meio", "Colocou no chão"],
    },
    {
        campo: "reading",
        titulo: "Leitura",
        pontos: 1,
        itens: ['Leu e executou "Feche os seus olhos"'],
    },
    {
        campo: "writing",
        titulo: "Escrita",
        pontos: 1,
        itens: ["Escreveu uma frase com sentido"],
    },
    {
        campo: "copying",
        titulo: "Cópia",
        pontos: 1,
        itens: ["Copiou os dois pentágonos com intersecção"],
    },
];

const TOTAL_POSSIVEL = SECOES.reduce((soma, secao) => soma + secao.pontos, 0);

// as faixas variam por escolaridade, entao o resultado sempre aparece
// junto com a escolaridade escolhida (RN14)
const CORTES_POR_ESCOLARIDADE = [
    {valor: "analfabeto", texto: "Analfabeto", corte: 20},
    {valor: "1a4", texto: "1 a 4 anos de estudo", corte: 25},
    {valor: "5a8", texto: "5 a 8 anos de estudo", corte: 26},
    {valor: "9a11", texto: "9 a 11 anos de estudo", corte: 28},
    {valor: "acima11", texto: "Mais de 11 anos de estudo", corte: 29},
];

export default function MiniExameEstadoMental() {
    const navigate = useNavigate();
    const {appointmentId} = useParams();

    const [assessmentDate, setAssessmentDate] = useState(new Date().toISOString().split("T")[0]);
    const [escolaridade, setEscolaridade] = useState("5a8");
    // guarda quais itens de cada secao foram marcados
    const [marcados, setMarcados] = useState({});

    const [salvando, setSalvando] = useState(false);
    const [erro, setErro] = useState(null);

    const alternarItem = (campo, indice) => {
        const chave = campo + "-" + indice;
        setMarcados((antes) => ({...antes, [chave]: !antes[chave]}));
    };

    const pontosDaSecao = (secao) =>
        secao.itens.filter((_, indice) => marcados[secao.campo + "-" + indice]).length;

    const total = SECOES.reduce((soma, secao) => soma + pontosDaSecao(secao), 0);

    const corte = CORTES_POR_ESCOLARIDADE.find((c) => c.valor === escolaridade).corte;

    const salvar = async (e) => {
        e.preventDefault();
        setErro(null);

        if (!appointmentId) {
            setErro("Esta avaliação precisa ser aberta a partir de uma consulta.");
            return;
        }

        const avaliacao = {assessmentDate};
        SECOES.forEach((secao) => {
            avaliacao[secao.campo] = pontosDaSecao(secao);
        });

        setSalvando(true);
        try {
            // antes esta tela so dava console.log e nada era salvo
            await apiService.post(`/mini-exame/consulta/${appointmentId}`, avaliacao);
            // volta pra consulta, que ja mostra o exame salvo na ficha
            navigate(-1);
        } catch (err) {
            setErro(err instanceof ApiError ? err.message : "Não foi possível salvar a avaliação.");
        } finally {
            setSalvando(false);
        }
    };

    return (
        <div className="meem">
            <Header/>
            <main className="meem__conteudo">
                <h1>Mini-Exame do Estado Mental (MEEM)</h1>

                <form onSubmit={salvar}>
                    <div className="meem__campo">
                        <label htmlFor="data">Data da avaliação</label>
                        <input
                            id="data"
                            type="date"
                            value={assessmentDate}
                            onChange={(e) => setAssessmentDate(e.target.value)}
                            required={true}
                        />
                    </div>

                    <div className="meem__campo">
                        <label htmlFor="escolaridade">Escolaridade do paciente</label>
                        <p className="meem__ajuda">
                            A faixa de corte do instrumento muda conforme a escolaridade.
                        </p>
                        <select
                            id="escolaridade"
                            value={escolaridade}
                            onChange={(e) => setEscolaridade(e.target.value)}
                        >
                            {CORTES_POR_ESCOLARIDADE.map((c) => (
                                <option key={c.valor} value={c.valor}>{c.texto}</option>
                            ))}
                        </select>
                    </div>

                    {SECOES.map((secao) => (
                        <section className="meem__bloco" key={secao.campo}>
                            <h2>
                                {secao.titulo}
                                <span className="meem__pontos">
                                    {pontosDaSecao(secao)} de {secao.pontos}
                                </span>
                            </h2>
                            {secao.ajuda && <p className="meem__ajuda">{secao.ajuda}</p>}
                            <div className="meem__itens">
                                {secao.itens.map((texto, indice) => (
                                    <label className="meem__item" key={indice}>
                                        <input
                                            type="checkbox"
                                            checked={Boolean(marcados[secao.campo + "-" + indice])}
                                            onChange={() => alternarItem(secao.campo, indice)}
                                        />
                                        <span>{texto}</span>
                                    </label>
                                ))}
                            </div>
                        </section>
                    ))}

                    <section className="meem__resultado">
                        <h2>Total: {total} de {TOTAL_POSSIVEL} pontos</h2>
                        <p>
                            {total >= corte
                                ? `Dentro do esperado para esta escolaridade (corte ${corte}).`
                                : `Abaixo do corte para esta escolaridade (corte ${corte}).`}
                        </p>
                    </section>

                    {erro && <p className="meem__erro">{erro}</p>}

                    <div className="meem__acoes">
                        <button type="submit" disabled={salvando}>
                            {salvando ? "Salvando..." : "Salvar avaliação"}
                        </button>
                        <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                            Cancelar
                        </button>
                    </div>
                </form>
            </main>
        </div>
    );
}
