import React, {useState} from 'react';
import {useNavigate} from 'react-router';
import ScaleSelector from "../../components/scale-selector/scale-selector.jsx";
import './escala-hamilton.css';
import ModalConfirmacao from "../../components/modal/modal-confirmacao.jsx";
import {apiService, ApiError} from "../../services/api.js";

const hamAItems = [
    {
        id: 1,
        title: "Humor ansioso",
        description: "Preocupações, previsão do pior, antecipação temerosa, irritabilidade.",
        leftLabel: "Ausente",
        rightLabel: "Muito grave"
    },
    {
        id: 2,
        title: "Tensão",
        description: "Sensação de tensão, fadiga, resposta ao susto, comove-se facilmente, tremor, sentimentos de inquietação, incapacidade para relaxar.",
        leftLabel: "Ausente",
        rightLabel: "Muito grave"
    },
    {
        id: 3,
        title: "Medos",
        description: "De escuro, de estranhos, de ficar sozinho, de animais, de tráfego, de multidões.",
        leftLabel: "Ausente",
        rightLabel: "Muito grave"
    },
    {
        id: 4,
        title: "Insônia",
        description: "Dificuldade em adormecer, sono interrompido, sono insatisfatório e fadiga ao acordar, sonhos, pesadelos, terrores noturnos.",
        leftLabel: "Ausente",
        rightLabel: "Muito grave"
    },
    {
        id: 5,
        title: "Comprometimento intelectual (cognitivo)",
        description: "Dificuldade de concentração, memória fraca.",
        leftLabel: "Ausente",
        rightLabel: "Muito grave"
    },
    {
        id: 6,
        title: "Humor deprimido",
        description: "Perda de interesse, falta de prazer nos passatempos, depressão, despertar precoce, oscilação diurna.",
        leftLabel: "Ausente",
        rightLabel: "Muito grave"
    },
    {
        id: 7,
        title: "Somatizações motoras",
        description: "Dores musculares, torções, espasmos, rigidez, espasmos mioclônicos, ranger de dentes, voz insegura, tônus muscular aumentado.",
        leftLabel: "Ausente",
        rightLabel: "Muito grave"
    },
    {
        id: 8,
        title: "Somatizações sensoriais",
        description: "Zumbido, visão turva, afrontamentos, sensações de fraqueza, sensação de irritação, formigamento, câimbras, dormências.",
        leftLabel: "Ausente",
        rightLabel: "Muito grave"
    },
    {
        id: 9,
        title: "Sintomas cardiovasculares",
        description: "Taquicardia, palpitações, dores torácicas, pulsação dos vasos sanguíneos, sensação de desmaio, batimentos irregulares.",
        leftLabel: "Ausente",
        rightLabel: "Muito grave"
    },
    {
        id: 10,
        title: "Sintomas respiratórios",
        description: "Pressão ou constrição no tórax, sensação de sufocamento ou asfixia, suspiros, dispneia.",
        leftLabel: "Ausente",
        rightLabel: "Muito grave"
    },
    {
        id: 11,
        title: "Sintomas gastrointestinais",
        description: "Dificuldades para engolir, gases, sensação de queimação ou azia, plenitude abdominal, náuseas, vômitos, relaxamento intestinal, perda de peso, prisão de ventre.",
        leftLabel: "Ausente",
        rightLabel: "Muito grave"
    },
    {
        id: 12,
        title: "Sintomas geniturinários",
        description: "Frequência da micção, urgência da micção, menorragia, desenvolvimento de frigidez, ejaculação precoce, perda da libido, impotência.",
        leftLabel: "Ausente",
        rightLabel: "Muito grave"
    },
    {
        id: 13,
        title: "Sintomas autonômicos",
        description: "Boca seca, rubor, palidez, tendência a sudorese, mãos molhadas, inquietação, tensão, dor de cabeça, tontura, pelos eriçados.",
        leftLabel: "Ausente",
        rightLabel: "Muito grave"
    },
    {
        // o 14o item faltava, entao o escore ia ate 52 em vez de 56 e n
        // dava pra comparar com as faixas do formulario da clinica
        id: 14,
        title: "Comportamento durante a entrevista",
        description: "Inquietação, impaciência ou intranquilidade, tremor das mãos, testa franzida, fácies tensa, suspiros ou respiração rápida, palidez facial, engolir em seco, eructação, tiques.",
        leftLabel: "Ausente",
        rightLabel: "Muito grave"
    }
];

export default function HamAScale() {
    const navigate = useNavigate();
    // o id de cada item da tela mapeado pro campo da entidade
    const CAMPOS_POR_ITEM = {
        1: "anxiousMood",
        2: "tension",
        3: "fears",
        4: "insomnia",
        5: "cognition",
        6: "depressedMood",
        7: "somaticMotor",
        8: "somaticSensory",
        9: "cardiovascularSymptoms",
        10: "respiratorySymptoms",
        11: "gastrointestinalSymptoms",
        12: "genitourinarySymptoms",
        13: "autonomicSymptoms",
        14: "interviewBehavior",
    };

    const [salvando, setSalvando] = useState(false);
    const [erro, setErro] = useState(null);
    const [confirmandoSaida, setConfirmandoSaida] = useState(false);

    const [data, setData] = useState({
        evaluationDate: '',
        scores: {},
        observations: ''
    });

    const handleScoreChange = (itemId, value) => {
        setData(prev => ({
            ...prev,
            scores: {
                ...prev.scores,
                [itemId]: value
            }
        }));
    };

    const calculateTotal = () => {
        return Object.values(data.scores).reduce((sum, score) => sum + (score || 0), 0);
    };

    const getAnxietyLevel = (total) => {
        if (total < 9) return "Sem ansiedade";
        if (total <= 15) return "Ansiedade temporária";
        if (total <= 25) return "Ansiedade moderada";
        return "Ansiedade grave";
    };

    const handleSave = async () => {
        setErro(null);

        if (!data.evaluationDate) {
            setErro('Por favor, preencha a data da avaliação.');
            return;
        }

        // monta o corpo com os nomes de campo da entidade.
        // antes esta tela so mostrava um alert e n mandava nada pra api,
        // entao o paciente preenchia e o dado simplesmente n existia
        const escala = {assessmentDate: data.evaluationDate};
        Object.entries(CAMPOS_POR_ITEM).forEach(([itemId, campo]) => {
            escala[campo] = data.scores[itemId] ?? null;
        });

        setSalvando(true);
        try {
            await apiService.post('/escala-hamilton', escala);
            const total = calculateTotal();
            navigate('/dashboard-paciente', {
                state: {aviso: 'Avaliação salva. Total: ' + total + '/56, ' + getAnxietyLevel(total) + '.'},
            });
        } catch (err) {
            setErro(err instanceof ApiError ? err.message : 'Não foi possível salvar a avaliação.');
        } finally {
            setSalvando(false);
        }
    };

    // o confirm do navegador virou modal da propria tela
    const handleCancel = () => {
        setConfirmandoSaida(true);
    };

    const total = calculateTotal();
    const anxietyLevel = getAnxietyLevel(total);

    return (
        <div className="escala-hamilton-algo">

            <div className="escala-hamilton ">
                <div className="escala-hamilton__content">
                    <div className="escala-hamilton__header">
                        <h1>Escala de Avaliação de Ansiedade de Hamilton</h1>
                        <h2>HAM-A - Instrumento para avaliação da intensidade de sintomas de ansiedade</h2>
                    </div>

                    <div className="escala-hamilton__form">
                        {/* Data da Avaliação */}
                        <div className="escala-hamilton__form__row">
                            <div>
                                <label htmlFor="evaluationDate">Data da Avaliação</label>
                                <input
                                    id="evaluationDate"
                                    type="date"
                                    value={data.evaluationDate}
                                    onChange={(e) =>
                                        setData(prev => ({
                                            ...prev,
                                            evaluationDate: e.target.value
                                        }))
                                    }
                                />
                            </div>
                            <div>
                                <label>Total da Pontuação</label>
                                <div className="ham-a-total">
                                    <span className="ham-a-total__score">{total}/56</span>
                                    <span className="ham-a-total__level">{anxietyLevel}</span>
                                </div>
                            </div>
                        </div>

                        {/* Itens da Escala HAM-A */}
                        {hamAItems.map((item) => (
                            <div key={item.id} className="escala-hamilton__form__group">
                                <h3>{item.id}. {item.title}</h3>
                                <p className="ham-a-description">{item.description}</p>
                                <ScaleSelector
                                    minValue={0}
                                    maxValue={4}
                                    leftLabel={item.leftLabel}
                                    rightLabel={item.rightLabel}
                                    value={data.scores[item.id] || 0}
                                    onChangeValue={(value) => handleScoreChange(item.id, value)}
                                />
                            </div>
                        ))}

                        {/* Observações */}
                        <div className="escala-hamilton__form__group">
                            <h3>Observações</h3>
                            <textarea
                                id="observations"
                                placeholder="Escreva aqui qualquer observação relevante sobre a avaliação"
                                rows="4"
                                value={data.observations}
                                onChange={(e) =>
                                    setData(prev => ({
                                        ...prev,
                                        observations: e.target.value
                                    }))
                                }
                            />
                        </div>

                        {/* Interpretação */}
                        <div className="escala-hamilton__form__group">
                            <h3>Interpretação dos Resultados</h3>
                            <div className="ham-a-interpretation">
                                <ul>
                                    <li><strong>Abaixo de 9:</strong> sem ansiedade</li>
                                    <li><strong>9 a 15:</strong> ansiedade temporária</li>
                                    <li><strong>16 a 25:</strong> ansiedade moderada</li>
                                    <li><strong>Acima de 26:</strong> ansiedade grave</li>
                                </ul>
                                <div className="ham-a-current-result">
                                    <strong>Resultado atual: {total} pontos - {anxietyLevel}</strong>
                                </div>
                            </div>
                        </div>

                        {erro && <p className="escala-hamilton__erro">{erro}</p>}

                        {/* Botões de Ação */}
                        <div className="escala-hamilton_end">
                            <button className="button" onClick={handleSave} disabled={salvando}>
                                Salvar Avaliação
                            </button>
                            <button className="button-secondary" onClick={handleCancel}>
                                Cancelar
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            <ModalConfirmacao
                show={confirmandoSaida}
                titulo="Cancelar preenchimento"
                mensagem="Tudo o que você preencheu será perdido. Quer mesmo sair?"
                textoConfirmar="Sim, cancelar"
                textoCancelar="Continuar preenchendo"
                onConfirmar={() => navigate('/dashboard-paciente')}
                onCancelar={() => setConfirmandoSaida(false)}
            />
        </div>
    );
}

