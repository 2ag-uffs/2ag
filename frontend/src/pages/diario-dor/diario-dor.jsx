import {useState} from "react";
import {useNavigate} from "react-router";
import {apiService, ApiError} from "../../services/api.js";
import "./diario-dor.css";

// as quatro opcoes de frequencia do formulario em papel, na ordem.
// viram 0, 1, 2 e 3 na hora de salvar
const FREQUENCIAS = ["Nenhum dia", "Até 3 dias", "Entre 3 e 6 dias", "Todos os dias"];

// os 5 itens de interferencia, com o nome do campo que o backend espera
const INTERFERENCIAS = [
    {campo: "basicActivityInterference", texto: "Atividades básicas (comer, levantar, tomar banho)"},
    {campo: "socialActivityInterference", texto: "Atividades sociais (sair com amigos, passear, ficar com a família)"},
    {campo: "productivityInterference", texto: "Na produtividade do trabalho"},
    {campo: "sleepInterference", texto: "No seu sono"},
    {campo: "extraMedication", texto: "Necessitou de medicação extra para dor"},
];

// a escala visual do formulario: leve 0 a 2, moderada 3 a 7, intensa 8 a 10
function faixaDaDor(valor) {
    if (valor === null) return "";
    if (valor <= 2) return "leve";
    if (valor <= 7) return "moderada";
    return "intensa";
}

export default function DiarioDor() {
    const navigate = useNavigate();

    const [assessmentDate, setAssessmentDate] = useState(new Date().toISOString().split("T")[0]);
    const [painIntensity, setPainIntensity] = useState(null);
    const [interferencias, setInterferencias] = useState({});
    const [observation, setObservation] = useState("");

    const [salvando, setSalvando] = useState(false);
    const [erro, setErro] = useState(null);

    const marcarInterferencia = (campo, valor) => {
        setInterferencias((antes) => ({...antes, [campo]: valor}));
    };

    const salvar = async (e) => {
        e.preventDefault();
        setErro(null);

        if (painIntensity === null) {
            setErro("Escolha a intensidade da dor.");
            return;
        }

        setSalvando(true);
        try {
            // o paciente dono do registro vem do token, n daqui
            await apiService.post("/registro-dor", {
                assessmentDate,
                painIntensity,
                ...interferencias,
                observation,
            });
            navigate("/dashboard-paciente", {state: {aviso: "Registro de dor salvo."}});
        } catch (err) {
            setErro(err instanceof ApiError ? err.message : "Não foi possível salvar o registro.");
        } finally {
            setSalvando(false);
        }
    };

    return (
        <div className="diario-dor">
            <main className="diario-dor__conteudo">
                <h1>Acompanhamento semanal de dor</h1>

                <form onSubmit={salvar}>
                    <div className="diario-dor__campo">
                        <label htmlFor="data">Período avaliado</label>
                        <input
                            id="data"
                            type="date"
                            value={assessmentDate}
                            onChange={(e) => setAssessmentDate(e.target.value)}
                            required={true}
                        />
                    </div>

                    <section className="diario-dor__bloco">
                        <h2>Como você classifica sua dor no último período?</h2>
                        <div className="diario-dor__escala">
                            {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((valor) => (
                                <button
                                    type="button"
                                    key={valor}
                                    className={
                                        "diario-dor__nota diario-dor__nota--" + faixaDaDor(valor) +
                                        (painIntensity === valor ? " selecionada" : "")
                                    }
                                    onClick={() => setPainIntensity(valor)}
                                >
                                    {valor}
                                </button>
                            ))}
                        </div>
                        <div className="diario-dor__legenda">
                            <span>0 a 2: leve</span>
                            <span>3 a 7: moderada</span>
                            <span>8 a 10: intensa</span>
                        </div>
                    </section>

                    <section className="diario-dor__bloco">
                        <h2>Durante a última semana, com que frequência a dor te atrapalhou:</h2>
                        {INTERFERENCIAS.map((item) => (
                            <div className="diario-dor__linha" key={item.campo}>
                                <p className="diario-dor__pergunta">{item.texto}</p>
                                <div className="diario-dor__opcoes">
                                    {FREQUENCIAS.map((texto, valor) => (
                                        <button
                                            type="button"
                                            key={valor}
                                            className={interferencias[item.campo] === valor ? "selecionada" : ""}
                                            onClick={() => marcarInterferencia(item.campo, valor)}
                                        >
                                            {texto}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        ))}
                    </section>

                    <div className="diario-dor__campo">
                        <label htmlFor="observacao">Observações</label>
                        <p className="diario-dor__ajuda">
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

                    {erro && <p className="diario-dor__erro">{erro}</p>}

                    <div className="diario-dor__acoes">
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
