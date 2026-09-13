import {useState} from "react";
import {useNavigate} from "react-router";
import Header from "../../components/header/header.jsx";
import {apiService, ApiError} from "../../services/api.js";
import "../../styles/colors.css";
import "../../styles/fonts.css";
import "../../styles/button.css";
import "../../styles/input.css";
import "./escala-pittsburgh.css";

// as frequencias do psqi, na ordem em que valem 0, 1, 2 e 3
const FREQUENCIAS = [
    "Nenhuma no último mês",
    "Menos de uma vez por semana",
    "Uma ou duas vezes por semana",
    "Três ou mais vezes na semana",
];

// os itens 5B a 5J: com que frequencia voce teve dificuldade pra dormir
// porque... o 5A fica fora daqui pq entra no componente de latencia
const MOTIVOS = [
    {campo: "freqWakesUpMiddleNight", texto: "Acordou no meio da noite ou de manhã cedo"},
    {campo: "freqWakeUpForBathroom", texto: "Precisou levantar para ir ao banheiro"},
    {campo: "freqCannotBreathe", texto: "Não conseguiu respirar confortavelmente"},
    {campo: "freqCoughOrSnore", texto: "Tossiu ou roncou forte"},
    {campo: "freqFeelCold", texto: "Sentiu muito frio"},
    {campo: "freqFeelHot", texto: "Sentiu muito calor"},
    {campo: "freqHaveBadDreams", texto: "Teve sonhos ruins"},
    {campo: "freqHavePain", texto: "Teve dor"},
];

const QUALIDADE = ["Muito boa", "Boa", "Ruim", "Muito ruim"];
const PROBLEMA = ["Nenhuma dificuldade", "Um problema leve", "Um problema razoável", "Um grande problema"];
const PARCEIRO = [
    "Não",
    "Parceiro ou colega, mas em outro quarto",
    "Parceiro no mesmo quarto, mas em outra cama",
    "Parceiro na mesma cama",
];

// bloco de botoes que vale 0, 1, 2 ou 3
function Opcoes({opcoes, valor, aoEscolher}) {
    return (
        <div className="psqi__opcoes">
            {opcoes.map((texto, indice) => (
                <button
                    type="button"
                    key={indice}
                    className={valor === indice ? "selecionada" : ""}
                    onClick={() => aoEscolher(indice)}
                >
                    {texto}
                </button>
            ))}
        </div>
    );
}

export default function EscalaPittsburgh() {
    const navigate = useNavigate();

    const [resposta, setResposta] = useState({
        assessmentDate: new Date().toISOString().split("T")[0],
        usualBedTime: "",
        minutesToFallAsleep: "",
        usualWakeUpTime: "",
        actualSleepHours: "",
        otherReasonToTroubleSleep: "",
    });
    const [notas, setNotas] = useState({});

    const [salvando, setSalvando] = useState(false);
    const [erro, setErro] = useState(null);

    const mudarCampo = (campo, valor) => {
        setResposta((antes) => ({...antes, [campo]: valor}));
    };

    const marcarNota = (campo, valor) => {
        setNotas((antes) => ({...antes, [campo]: valor}));
    };

    const salvar = async (e) => {
        e.preventDefault();
        setErro(null);
        setSalvando(true);

        try {
            await apiService.post("/escala-pittsburgh", {
                assessmentDate: resposta.assessmentDate,
                // horario vazio vira null em vez de string vazia
                usualBedTime: resposta.usualBedTime || null,
                usualWakeUpTime: resposta.usualWakeUpTime || null,
                minutesToFallAsleep: resposta.minutesToFallAsleep
                    ? parseInt(resposta.minutesToFallAsleep)
                    : null,
                actualSleepHours: resposta.actualSleepHours
                    ? parseFloat(resposta.actualSleepHours)
                    : null,
                otherReasonToTroubleSleep: resposta.otherReasonToTroubleSleep,
                ...notas,
            });
            alert("Avaliação do sono salva!");
            navigate("/dashboard-paciente");
        } catch (err) {
            setErro(err instanceof ApiError ? err.message : "Não foi possível salvar a avaliação.");
        } finally {
            setSalvando(false);
        }
    };

    return (
        <div className="psqi">
            <Header/>
            <main className="psqi__conteudo">
                <h1>Índice de qualidade do sono de Pittsburgh</h1>
                <p className="psqi__ajuda">
                    As perguntas são sobre seus hábitos de sono durante o último mês. Responda
                    pensando na maioria dos dias e noites.
                </p>

                <form onSubmit={salvar}>
                    <div className="psqi__campo">
                        <label htmlFor="data">Data da avaliação</label>
                        <input
                            id="data"
                            type="date"
                            value={resposta.assessmentDate}
                            onChange={(e) => mudarCampo("assessmentDate", e.target.value)}
                            required={true}
                        />
                    </div>

                    <section className="psqi__bloco">
                        <h2>Seus horários no último mês</h2>

                        <div className="psqi__campo">
                            <label htmlFor="deitar">1. A que horas você geralmente foi para a cama?</label>
                            <input
                                id="deitar"
                                type="time"
                                value={resposta.usualBedTime}
                                onChange={(e) => mudarCampo("usualBedTime", e.target.value)}
                            />
                        </div>

                        <div className="psqi__campo">
                            <label htmlFor="demora">2. Quanto tempo (em minutos) você geralmente levou para dormir?</label>
                            <input
                                id="demora"
                                type="number"
                                min="0"
                                value={resposta.minutesToFallAsleep}
                                onChange={(e) => mudarCampo("minutesToFallAsleep", e.target.value)}
                            />
                        </div>

                        <div className="psqi__campo">
                            <label htmlFor="levantar">3. A que horas você geralmente levantou de manhã?</label>
                            <input
                                id="levantar"
                                type="time"
                                value={resposta.usualWakeUpTime}
                                onChange={(e) => mudarCampo("usualWakeUpTime", e.target.value)}
                            />
                        </div>

                        <div className="psqi__campo">
                            <label htmlFor="horas">
                                4. Quantas horas de sono você teve por noite?
                            </label>
                            <p className="psqi__ajuda">
                                Pode ser diferente do número de horas que você ficou na cama.
                            </p>
                            <input
                                id="horas"
                                type="number"
                                min="0"
                                step="0.5"
                                value={resposta.actualSleepHours}
                                onChange={(e) => mudarCampo("actualSleepHours", e.target.value)}
                            />
                        </div>
                    </section>

                    <section className="psqi__bloco">
                        <h2>5. Com que frequência você teve dificuldade para dormir porque:</h2>

                        <div className="psqi__linha">
                            <p className="psqi__pergunta">Não conseguiu adormecer em até 30 minutos</p>
                            <Opcoes
                                opcoes={FREQUENCIAS}
                                valor={notas.freqCannotFallAsleep}
                                aoEscolher={(v) => marcarNota("freqCannotFallAsleep", v)}
                            />
                        </div>

                        {MOTIVOS.map((item) => (
                            <div className="psqi__linha" key={item.campo}>
                                <p className="psqi__pergunta">{item.texto}</p>
                                <Opcoes
                                    opcoes={FREQUENCIAS}
                                    valor={notas[item.campo]}
                                    aoEscolher={(v) => marcarNota(item.campo, v)}
                                />
                            </div>
                        ))}

                        <div className="psqi__linha">
                            <label className="psqi__pergunta" htmlFor="outro">
                                Outras razões. Se houver, descreva:
                            </label>
                            <input
                                id="outro"
                                type="text"
                                value={resposta.otherReasonToTroubleSleep}
                                onChange={(e) => mudarCampo("otherReasonToTroubleSleep", e.target.value)}
                            />
                            <p className="psqi__pergunta">Com que frequência isso atrapalhou seu sono?</p>
                            <Opcoes
                                opcoes={FREQUENCIAS}
                                valor={notas.freqOtherReason}
                                aoEscolher={(v) => marcarNota("freqOtherReason", v)}
                            />
                        </div>
                    </section>

                    <section className="psqi__bloco">
                        <h2>Sobre o seu sono de maneira geral</h2>

                        <div className="psqi__linha">
                            <p className="psqi__pergunta">
                                6. Como você classificaria a qualidade do seu sono?
                            </p>
                            <Opcoes
                                opcoes={QUALIDADE}
                                valor={notas.sleepQualityRating}
                                aoEscolher={(v) => marcarNota("sleepQualityRating", v)}
                            />
                        </div>

                        <div className="psqi__linha">
                            <p className="psqi__pergunta">
                                7. Com que frequência você tomou medicamento para ajudar a dormir?
                            </p>
                            <Opcoes
                                opcoes={FREQUENCIAS}
                                valor={notas.freqUseSleepMedication}
                                aoEscolher={(v) => marcarNota("freqUseSleepMedication", v)}
                            />
                        </div>

                        <div className="psqi__linha">
                            <p className="psqi__pergunta">
                                8. Com que frequência teve dificuldade para ficar acordado enquanto
                                dirigia, comia ou participava de uma atividade social?
                            </p>
                            <Opcoes
                                opcoes={FREQUENCIAS}
                                valor={notas.freqTroubleStayingAwake}
                                aoEscolher={(v) => marcarNota("freqTroubleStayingAwake", v)}
                            />
                        </div>

                        <div className="psqi__linha">
                            <p className="psqi__pergunta">
                                9. Quão problemático foi manter o entusiasmo para fazer suas
                                atividades habituais?
                            </p>
                            <Opcoes
                                opcoes={PROBLEMA}
                                valor={notas.troubleWithEnthusiasm}
                                aoEscolher={(v) => marcarNota("troubleWithEnthusiasm", v)}
                            />
                        </div>

                        <div className="psqi__linha">
                            <p className="psqi__pergunta">
                                10. Você tem parceiro(a), esposo(a) ou colega de quarto?
                            </p>
                            <Opcoes
                                opcoes={PARCEIRO}
                                valor={notas.roomPartner}
                                aoEscolher={(v) => marcarNota("roomPartner", v)}
                            />
                        </div>
                    </section>

                    {erro && <p className="psqi__erro">{erro}</p>}

                    <div className="psqi__acoes">
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
