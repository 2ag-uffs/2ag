import { useNavigate } from "react-router-dom";
import Header from "../../components/header/header.jsx";
import "../../styles/button.css";
import "../../styles/colors.css";
import "../../styles/fonts.css";
import "./historico-clinico-paciente.css";

const dadosHistorico = {
    anamnese:
        "Paciente relata início de sintomas de ansiedade há cerca de 6 meses, associados a estresse no trabalho. Refere dificuldade para dormir, irritabilidade e falta de concentração. Nega histórico familiar de transtornos psiquiátricos. Tabagista (10 cigarros/dia), etilista social. Nega uso de outras substâncias.",
    diagnosticos: [
        {
            id: 1,
            data: "15/05/2025",
            diagnostico: "Transtorno de Ansiedade Generalizada (CID-10 F41.1)",
        },
        {
            id: 2,
            data: "22/03/2025",
            diagnostico: "Hipertensão Arterial Sistêmica (CID-10 I10)",
        },
    ],
    tratamentos: [
        {
            id: 1,
            data: "15/05/2025",
            tratamento:
                "Psicoterapia Cognitivo-Comportamental (TCC), sessões semanais.",
        },
        {
            id: 2,
            data: "15/05/2025",
            tratamento: "Atividade física regular (3x por semana).",
        },
    ],
    evolucoes: [
        {
            id: 1,
            data: "01/07/2025",
            evolucao:
                "Paciente relata melhora na qualidade do sono e diminuição da irritabilidade após início da TCC.",
        },
        {
            id: 2,
            data: "15/06/2025",
            evolucao:
                "Apresenta boa adesão às sessões de psicoterapia e às recomendações de atividade física.",
        },
    ],
    prescricoes: [
        {
            id: 1,
            data: "15/05/2025",
            medicamento: "Sertralina",
            dose: "50mg",
            posologia: "1 comprimido pela manhã",
        },
        {
            id: 2,
            data: "22/03/2025",
            medicamento: "Losartana Potássica",
            dose: "50mg",
            posologia: "1 comprimido pela manhã",
        },
    ],
};

export default function HistoricoClinico() {
    const navigate = useNavigate();

    const handleBack = () => {
        navigate(-1);
    };

    const handleExport = () => {
        // Em uma implementação real, essa função geraria um PDF ou CSV com os dados.
        alert(
            "Funcionalidade de exportação em desenvolvimento. Os dados seriam exportados aqui.",
        );
        console.log("Exportando dados:", dadosHistorico);
    };

    return (
        <div className="historico-paciente__page">
            <Header
                showBackButton={true}
                backButtonText={"Voltar"}
                onBackClick={handleBack}
            />
            <main className="historico-paciente__main">
                <div className="historico-paciente__header">
                    <h1>Meu Histórico Clínico</h1>
                    <h2>
                        Aqui você encontra um resumo completo de suas
                        informações de saúde registradas na plataforma.
                    </h2>
                    <button className="button-primary" onClick={handleExport}>
                        Exportar Dados
                    </button>
                </div>

                <div className="historico-paciente__content">
                    <section className="historico-paciente__section">
                        <h3>Anamnese</h3>
                        <p>{dadosHistorico.anamnese}</p>
                    </section>

                    <section className="historico-paciente__section">
                        <h3>Diagnósticos Realizados</h3>
                        <ul>
                            {dadosHistorico.diagnosticos.map((item) => (
                                <li key={item.id}>
                                    <strong>{item.data}: </strong>
                                    {item.diagnostico}
                                </li>
                            ))}
                        </ul>
                    </section>

                    <section className="historico-paciente__section">
                        <h3>Tratamentos Prescritos</h3>
                        <ul>
                            {dadosHistorico.tratamentos.map((item) => (
                                <li key={item.id}>
                                    <strong>{item.data}: </strong>
                                    {item.tratamento}
                                </li>
                            ))}
                        </ul>
                    </section>

                    <section className="historico-paciente__section">
                        <h3>Evoluções no Quadro Clínico</h3>
                        <ul>
                            {dadosHistorico.evolucoes.map((item) => (
                                <li key={item.id}>
                                    <strong>{item.data}: </strong>
                                    {item.evolucao}
                                </li>
                            ))}
                        </ul>
                    </section>

                    <section className="historico-paciente__section">
                        <h3>Prescrições Anteriores</h3>
                        <div className="historico-paciente__prescriptions">
                            {dadosHistorico.prescricoes.map((item) => (
                                <div
                                    key={item.id}
                                    className="historico-paciente__prescription-item"
                                >
                                    <div className="historico-paciente__prescription-item__header">
                                        <p>{item.medicamento}</p>
                                        <span>{item.data}</span>
                                    </div>
                                    <p>
                                        <strong>Dose: </strong>
                                        {item.dose}
                                    </p>
                                    <p>
                                        <strong>Posologia: </strong>
                                        {item.posologia}
                                    </p>
                                </div>
                            ))}
                        </div>
                    </section>
                </div>
            </main>
        </div>
    );
}
