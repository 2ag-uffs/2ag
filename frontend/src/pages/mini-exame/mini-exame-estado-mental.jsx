import React, { useState } from "react";
import "./mini-exame-estado-mental.css";
import Header from "../../components/header/header.jsx";
import {useNavigate} from "react-router";

export default function MiniExameEstadoMental() {
    const navigate = useNavigate();

    const [data, setData] = useState({
        nomePaciente: "",
        dataAvaliacao: "",
        registro: {
            carro: false,
            vaso: false,
            tijolo: false,
            pontuacao: 0
        },
        atencaoCalculo: {
            resultado1: "", // 100-7 = 93
            resultado2: "", // 93-7 = 86
            resultado3: "", // 86-7 = 79
            resultado4: "", // 79-7 = 72
            resultado5: "", // 72-7 = 65
            pontuacao: 0
        },
        memoriaEvocacao: {
            carro: false,
            vaso: false,
            tijolo: false,
            pontuacao: 0
        },
        nomearObjetos: {
            relogio: false,
            caneta: false,
            pontuacao: 0
        },
        repetir: {
            frase: false, // "Nem aqui, nem ali, nem lá"
            pontuacao: 0
        },
        comandoEstagios: {
            pegarPapel: false,
            dobrarMeio: false,
            colocarChao: false,
            pontuacao: 0
        },
        pontuacaoTotal: 0
    });

    const updateRegistro = (item, checked) => {
        setData(prev => {
            const newRegistro = { ...prev.registro, [item]: checked };
            const pontuacao = Object.values(newRegistro).filter(val => val === true).length - 1; // -1 para não contar a pontuação
            return {
                ...prev,
                registro: { ...newRegistro, pontuacao }
            };
        });
    };

    const updateAtencaoCalculo = (campo, valor) => {
        setData(prev => {
            const newAtencao = { ...prev.atencaoCalculo, [campo]: valor };
            
            // Verificar respostas corretas
            const respostasCorretas = {
                resultado1: "93",
                resultado2: "86", 
                resultado3: "79",
                resultado4: "72",
                resultado5: "65"
            };
            
            let pontuacao = 0;
            Object.keys(respostasCorretas).forEach(key => {
                if (newAtencao[key] === respostasCorretas[key]) {
                    pontuacao++;
                }
            });
            
            return {
                ...prev,
                atencaoCalculo: { ...newAtencao, pontuacao }
            };
        });
    };

    const updateMemoriaEvocacao = (item, checked) => {
        setData(prev => {
            const newMemoria = { ...prev.memoriaEvocacao, [item]: checked };
            const pontuacao = Object.values(newMemoria).filter(val => val === true).length - 1; // -1 para não contar a pontuação
            return {
                ...prev,
                memoriaEvocacao: { ...newMemoria, pontuacao }
            };
        });
    };

    const updateNomearObjetos = (item, checked) => {
        setData(prev => {
            const newNomear = { ...prev.nomearObjetos, [item]: checked };
            const pontuacao = Object.values(newNomear).filter(val => val === true).length - 1; // -1 para não contar a pontuação
            return {
                ...prev,
                nomearObjetos: { ...newNomear, pontuacao }
            };
        });
    };

    const updateRepetir = (checked) => {
        setData(prev => ({
            ...prev,
            repetir: { frase: checked, pontuacao: checked ? 1 : 0 }
        }));
    };

    const updateComandoEstagios = (item, checked) => {
        setData(prev => {
            const newComando = { ...prev.comandoEstagios, [item]: checked };
            const pontuacao = Object.values(newComando).filter(val => val === true).length - 1; // -1 para não contar a pontuação
            return {
                ...prev,
                comandoEstagios: { ...newComando, pontuacao }
            };
        });
    };

    // Calcular pontuação total
    const calcularPontuacaoTotal = () => {
        return data.registro.pontuacao + 
               data.atencaoCalculo.pontuacao + 
               data.memoriaEvocacao.pontuacao + 
               data.nomearObjetos.pontuacao + 
               data.repetir.pontuacao + 
               data.comandoEstagios.pontuacao;
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        const pontuacaoTotal = calcularPontuacaoTotal();
        const dadosCompletos = { ...data, pontuacaoTotal };
        console.log('Dados do MEEM:', dadosCompletos);
        // Aqui você pode implementar a lógica para salvar os dados
    };

    const handleBack = () => {
        navigate(-1);
    };
    return (
        <div className="meem">
            <div className="meem__content">
                <Header
                    title="João Silva"
                    showBackButton={true}
                    backButtonText="Voltar"
                    onBackClick={handleBack}
                />

                <form onSubmit={handleSubmit} className="meem__form">
                    {/* Informações do Paciente */}
                    <div className="meem__section">
                        <h3>Informações do Paciente</h3>
                        <div className="meem__row">
                            <div className="meem__field">
                                <label htmlFor="nomePaciente">Nome do Paciente</label>
                                <input
                                    id="nomePaciente"
                                    type="text"
                                    value={data.nomePaciente}
                                    onChange={(e) => setData(prev => ({ ...prev, nomePaciente: e.target.value }))}
                                    placeholder="Digite o nome do paciente"
                                />
                            </div>
                            <div className="meem__field">
                                <label htmlFor="dataAvaliacao">Data da Avaliação</label>
                                <input
                                    id="dataAvaliacao"
                                    type="date"
                                    value={data.dataAvaliacao}
                                    onChange={(e) => setData(prev => ({ ...prev, dataAvaliacao: e.target.value }))}
                                />
                            </div>
                        </div>
                    </div>

                    {/* Registro (3 pontos) */}
                    <div className="meem__section">
                        <div className="meem__section-header">
                            <h3>Registro</h3>
                            <div className="meem__score">
                                <span>{data.registro.pontuacao}/3 pontos</span>
                            </div>
                        </div>
                        <p className="meem__instruction">Repetir: CARRO, VASO, TIJOLO</p>
                        <div className="meem__checkbox-group">
                            <label className="meem__checkbox">
                                <input
                                    type="checkbox"
                                    checked={data.registro.carro}
                                    onChange={(e) => updateRegistro('carro', e.target.checked)}
                                />
                                <span>CARRO</span>
                            </label>
                            <label className="meem__checkbox">
                                <input
                                    type="checkbox"
                                    checked={data.registro.vaso}
                                    onChange={(e) => updateRegistro('vaso', e.target.checked)}
                                />
                                <span>VASO</span>
                            </label>
                            <label className="meem__checkbox">
                                <input
                                    type="checkbox"
                                    checked={data.registro.tijolo}
                                    onChange={(e) => updateRegistro('tijolo', e.target.checked)}
                                />
                                <span>TIJOLO</span>
                            </label>
                        </div>
                    </div>

                    {/* Atenção e Cálculo (5 pontos) */}
                    <div className="meem__section">
                        <div className="meem__section-header">
                            <h3>Atenção e Cálculo</h3>
                            <div className="meem__score">
                                <span>{data.atencaoCalculo.pontuacao}/5 pontos</span>
                            </div>
                        </div>
                        <p className="meem__instruction">Subtrair de 7 em 7, começando de 100:</p>
                        <div className="meem__calculation-group">
                            <div className="meem__calculation">
                                <label>100 - 7 =</label>
                                <input
                                    type="number"
                                    value={data.atencaoCalculo.resultado1}
                                    onChange={(e) => updateAtencaoCalculo('resultado1', e.target.value)}
                                    placeholder="93"
                                />
                            </div>
                            <div className="meem__calculation">
                                <label>93 - 7 =</label>
                                <input
                                    type="number"
                                    value={data.atencaoCalculo.resultado2}
                                    onChange={(e) => updateAtencaoCalculo('resultado2', e.target.value)}
                                    placeholder="86"
                                />
                            </div>
                            <div className="meem__calculation">
                                <label>86 - 7 =</label>
                                <input
                                    type="number"
                                    value={data.atencaoCalculo.resultado3}
                                    onChange={(e) => updateAtencaoCalculo('resultado3', e.target.value)}
                                    placeholder="79"
                                />
                            </div>
                            <div className="meem__calculation">
                                <label>79 - 7 =</label>
                                <input
                                    type="number"
                                    value={data.atencaoCalculo.resultado4}
                                    onChange={(e) => updateAtencaoCalculo('resultado4', e.target.value)}
                                    placeholder="72"
                                />
                            </div>
                            <div className="meem__calculation">
                                <label>72 - 7 =</label>
                                <input
                                    type="number"
                                    value={data.atencaoCalculo.resultado5}
                                    onChange={(e) => updateAtencaoCalculo('resultado5', e.target.value)}
                                    placeholder="65"
                                />
                            </div>
                        </div>
                    </div>

                    {/* Memória de Evocação (3 pontos) */}
                    <div className="meem__section">
                        <div className="meem__section-header">
                            <h3>Memória de Evocação</h3>
                            <div className="meem__score">
                                <span>{data.memoriaEvocacao.pontuacao}/3 pontos</span>
                            </div>
                        </div>
                        <p className="meem__instruction">Quais os três objetos perguntados anteriormente?</p>
                        <div className="meem__checkbox-group">
                            <label className="meem__checkbox">
                                <input
                                    type="checkbox"
                                    checked={data.memoriaEvocacao.carro}
                                    onChange={(e) => updateMemoriaEvocacao('carro', e.target.checked)}
                                />
                                <span>CARRO</span>
                            </label>
                            <label className="meem__checkbox">
                                <input
                                    type="checkbox"
                                    checked={data.memoriaEvocacao.vaso}
                                    onChange={(e) => updateMemoriaEvocacao('vaso', e.target.checked)}
                                />
                                <span>VASO</span>
                            </label>
                            <label className="meem__checkbox">
                                <input
                                    type="checkbox"
                                    checked={data.memoriaEvocacao.tijolo}
                                    onChange={(e) => updateMemoriaEvocacao('tijolo', e.target.checked)}
                                />
                                <span>TIJOLO</span>
                            </label>
                        </div>
                    </div>

                    {/* Nomear 2 Objetos (2 pontos) */}
                    <div className="meem__section">
                        <div className="meem__section-header">
                            <h3>Nomear 2 Objetos</h3>
                            <div className="meem__score">
                                <span>{data.nomearObjetos.pontuacao}/2 pontos</span>
                            </div>
                        </div>
                        <p className="meem__instruction">Mostrar e pedir para nomear:</p>
                        <div className="meem__checkbox-group">
                            <label className="meem__checkbox">
                                <input
                                    type="checkbox"
                                    checked={data.nomearObjetos.relogio}
                                    onChange={(e) => updateNomearObjetos('relogio', e.target.checked)}
                                />
                                <span>RELÓGIO</span>
                            </label>
                            <label className="meem__checkbox">
                                <input
                                    type="checkbox"
                                    checked={data.nomearObjetos.caneta}
                                    onChange={(e) => updateNomearObjetos('caneta', e.target.checked)}
                                />
                                <span>CANETA</span>
                            </label>
                        </div>
                    </div>

                    {/* Repetir (1 ponto) */}
                    <div className="meem__section">
                        <div className="meem__section-header">
                            <h3>Repetir</h3>
                            <div className="meem__score">
                                <span>{data.repetir.pontuacao}/1 ponto</span>
                            </div>
                        </div>
                        <p className="meem__instruction">Repetir a frase:</p>
                        <div className="meem__phrase-box">
                            <p className="meem__phrase">"Nem aqui, nem ali, nem lá"</p>
                        </div>
                        <div className="meem__checkbox-group">
                            <label className="meem__checkbox">
                                <input
                                    type="checkbox"
                                    checked={data.repetir.frase}
                                    onChange={(e) => updateRepetir(e.target.checked)}
                                />
                                <span>Repetiu corretamente</span>
                            </label>
                        </div>
                    </div>

                    {/* Comando de Estágios (3 pontos) */}
                    <div className="meem__section">
                        <div className="meem__section-header">
                            <h3>Comando de Estágios</h3>
                            <div className="meem__score">
                                <span>{data.comandoEstagios.pontuacao}/3 pontos</span>
                            </div>
                        </div>
                        <p className="meem__instruction">
                            Dar o comando: "Apanhe esta folha de papel com a mão direita, dobre-a ao meio e coloque-a no chão."
                        </p>
                        <div className="meem__checkbox-group">
                            <label className="meem__checkbox">
                                <input
                                    type="checkbox"
                                    checked={data.comandoEstagios.pegarPapel}
                                    onChange={(e) => updateComandoEstagios('pegarPapel', e.target.checked)}
                                />
                                <span>Apanhou o papel com a mão direita</span>
                            </label>
                            <label className="meem__checkbox">
                                <input
                                    type="checkbox"
                                    checked={data.comandoEstagios.dobrarMeio}
                                    onChange={(e) => updateComandoEstagios('dobrarMeio', e.target.checked)}
                                />
                                <span>Dobrou ao meio</span>
                            </label>
                            <label className="meem__checkbox">
                                <input
                                    type="checkbox"
                                    checked={data.comandoEstagios.colocarChao}
                                    onChange={(e) => updateComandoEstagios('colocarChao', e.target.checked)}
                                />
                                <span>Colocou no chão</span>
                            </label>
                        </div>
                    </div>

                    {/* Pontuação Total */}
                    <div className="meem__total-score">
                        <h3>Pontuação Total: {calcularPontuacaoTotal()}/17 pontos</h3>
                        <div className="meem__score-interpretation">
                            {calcularPontuacaoTotal() >= 24 && <p className="score-normal">Normal (≥24 pontos)</p>}
                            {calcularPontuacaoTotal() >= 18 && calcularPontuacaoTotal() < 24 && <p className="score-mild">Comprometimento leve (18-23 pontos)</p>}
                            {calcularPontuacaoTotal() < 18 && <p className="score-severe">Comprometimento grave (&lt;18 pontos)</p>}
                        </div>
                    </div>

                    <div className="meem__actions">
                        <button type="submit" className="meem__submit">
                            Salvar Avaliação MEEM
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

