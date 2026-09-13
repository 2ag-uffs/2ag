import {useState} from "react";
import {useNavigate} from "react-router";
import Header from "../../components/header/header.jsx";
import {apiService, ApiError} from "../../services/api.js";
import "../../styles/colors.css";
import "../../styles/fonts.css";
import "../../styles/button.css";
import "../../styles/input.css";
import "./diario-tea.css";

// as mesmas quatro opcoes do formulario de dor, na ordem
const FREQUENCIAS = ["Nenhum dia", "Até 3 dias", "Entre 3 e 6 dias", "Todos os dias"];

const COMPORTAMENTOS = [
    {campo: "freqAggressiveness", texto: "Agressividade e impulsividade"},
    {campo: "freqAgitation", texto: "Agitação psicomotora e ansiedade"},
    {campo: "freqSleepIssues", texto: "Sono"},
    {campo: "freqSocialInteraction", texto: "Interação social"},
    {campo: "freqStereotypy", texto: "Estereotipia"},
    {campo: "freqAppetiteIssues", texto: "Apetite"},
];

export default function DiarioTea() {
    const navigate = useNavigate();

    const [assessmentDate, setAssessmentDate] = useState(new Date().toISOString().split("T")[0]);
    const [qualityOfLife, setQualityOfLife] = useState(null);
    const [comportamentos, setComportamentos] = useState({});
    const [observation, setObservation] = useState("");

    const [salvando, setSalvando] = useState(false);
    const [erro, setErro] = useState(null);

    const marcarComportamento = (campo, valor) => {
        setComportamentos((antes) => ({...antes, [campo]: valor}));
    };

    const salvar = async (e) => {
        e.preventDefault();
        setErro(null);

        if (qualityOfLife === null) {
            setErro("Escolha uma nota para a qualidade de vida.");
            return;
        }

        setSalvando(true);
        try {
            await apiService.post("/registro-tea", {
                assessmentDate,
                qualityOfLife,
                ...comportamentos,
                observation,
            });
            alert("Registro salvo!");
            navigate("/dashboard-paciente");
        } catch (err) {
            setErro(err instanceof ApiError ? err.message : "Não foi possível salvar o registro.");
        } finally {
            setSalvando(false);
        }
    };

    return (
        <div className="diario-tea">
            <Header/>
            <main className="diario-tea__conteudo">
                <h1>Acompanhamento semanal (TEA)</h1>

                <form onSubmit={salvar}>
                    <div className="diario-tea__campo">
                        <label htmlFor="data">Período avaliado</label>
                        <input
                            id="data"
                            type="date"
                            value={assessmentDate}
                            onChange={(e) => setAssessmentDate(e.target.value)}
                            required={true}
                        />
                    </div>

                    <section className="diario-tea__bloco">
                        <h2>
                            Em uma escala de 0 a 10, sendo 10 a melhor pontuação, como você
                            classifica sua qualidade de vida no último período?
                        </h2>
                        <div className="diario-tea__escala">
                            {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((valor) => (
                                <button
                                    type="button"
                                    key={valor}
                                    className={qualityOfLife === valor ? "selecionada" : ""}
                                    onClick={() => setQualityOfLife(valor)}
                                >
                                    {valor}
                                </button>
                            ))}
                        </div>
                    </section>

                    <section className="diario-tea__bloco">
                        <h2>Com que frequência esses comportamentos ocorreram na última semana:</h2>
                        {COMPORTAMENTOS.map((item) => (
                            <div className="diario-tea__linha" key={item.campo}>
                                <p className="diario-tea__pergunta">{item.texto}</p>
                                <div className="diario-tea__opcoes">
                                    {FREQUENCIAS.map((texto, valor) => (
                                        <button
                                            type="button"
                                            key={valor}
                                            className={comportamentos[item.campo] === valor ? "selecionada" : ""}
                                            onClick={() => marcarComportamento(item.campo, valor)}
                                        >
                                            {texto}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        ))}
                    </section>

                    <div className="diario-tea__campo">
                        <label htmlFor="observacao">Observações</label>
                        <p className="diario-tea__ajuda">
                            Escreva aqui qualquer ponto que julgar relevante, como o relato de
                            algum efeito colateral, a ausência de tomada de alguma medicação ou
                            algum momento significativo na rotina.
                        </p>
                        <textarea
                            id="observacao"
                            rows={6}
                            value={observation}
                            onChange={(e) => setObservation(e.target.value)}
                        />
                    </div>

                    {erro && <p className="diario-tea__erro">{erro}</p>}

                    <div className="diario-tea__acoes">
                        <button type="submit" disabled={salvando}>
                            {salvando ? "Salvando..." : "Salvar"}
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
