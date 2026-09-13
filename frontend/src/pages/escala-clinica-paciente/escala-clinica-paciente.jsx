import {useNavigate, useParams} from 'react-router';
import {useEffect, useState} from 'react';
import './escala-clinica-paciente.css';
import '../../styles/colors.css';
import '../../styles/fonts.css';
import '../../styles/button.css';
import Header from "../../components/header/header.jsx";
import {apiService, ApiError} from "../../services/api.js";

export default function CentralEscalas() {
    const navigate = useNavigate();
    const {patientId} = useParams();

    const [pageData, setPageData] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!patientId) {
            return;
        }

        apiService
            .get(`/pacientes/${patientId}/escalas/central`)
            .then(setPageData)
            .catch((err) => {
                if (err instanceof ApiError && err.status === 403) {
                    setError('Você não tem permissão para ver estes dados.');
                } else {
                    setError('Falha ao buscar os dados das avaliações.');
                }
            })
            .finally(() => setIsLoading(false));
    }, [patientId]);

    const handleNavigate = (path) => {
        navigate(path);
    };

    const handleBack = () => {
        navigate(-1);
    };

    if (isLoading) {
        return <div>Carregando avaliações...</div>;
    }

    if (error) {
        return <div>Erro: {error}</div>;
    }

    // Garante que o componente não quebre se pageData ainda for nulo
    if (!pageData) {
        return <div>Não foi possível carregar os dados.</div>;
    }

    return (
        <div className="central-escalas-page">
            <Header
                title={pageData?.patientName || "Paciente"}
                showBackButton={true}
                backButtonText="Voltar"
                onBackClick={handleBack}
            />

            <main className="escalas-main">
                <div className="escalas-title">
                    <h1>Minhas Avaliações Clínicas</h1>
                    <p>Acompanhe suas avaliações pendentes e seu histórico de respostas.</p>
                </div>
                <section className="secao-escalas">
                    <h2>Avaliações Pendentes</h2>
                    <div className="escalas-pendentes-container">
                        {pageData.pendingScales.length > 0 ? (
                            pageData.pendingScales.map((escala) => (
                                <div key={escala.id} className="escala-card">
                                    <h3>{escala.scaleName}</h3>
                                    <p>{escala.description}</p>
                                    <button className="button-primary" onClick={() => handleNavigate(escala.path)}>
                                        Preencher
                                    </button>
                                </div>
                            ))
                        ) : (
                            <p>Nenhuma avaliação pendente no momento.</p>
                        )}
                    </div>
                </section>
                <section className="secao-escalas">
                    <h2>Histórico de Avaliações</h2>
                    <div className="historico-tabela-container">
                        <table className="historico-tabela">
                            <thead>
                            <tr>
                                <th>Nome da Escala</th>
                                <th>Data de Conclusão</th>
                                <th>Resultado</th>
                                <th>Ações</th>
                            </tr>
                            </thead>
                            <tbody>
                            {pageData.completedScales.length > 0 ? (
                                pageData.completedScales.map((escala) => (
                                    <tr key={escala.id}>
                                        <td>{escala.scaleName}</td>
                                        {/* Formata a data para o padrão brasileiro */}
                                        <td>{new Date(escala.completionDate + 'T00:00:00').toLocaleDateString('pt-BR')}</td>
                                        <td>{escala.result}</td>
                                        <td className="tabela-acoes">
                                            <button className="button-tertiary"
                                                    onClick={() => handleNavigate(escala.viewPath)}>Ver Respostas
                                            </button>
                                            <button className="button-tertiary"
                                                    onClick={() => handleNavigate('/progresso')}>Ver Progresso
                                            </button>
                                        </td>
                                    </tr>
                                ))
                            ) : (
                                <tr>
                                    <td colSpan="4" style={{textAlign: 'center'}}>Nenhuma avaliação foi concluída
                                        ainda.
                                    </td>
                                </tr>
                            )}
                            </tbody>
                        </table>
                    </div>
                </section>
            </main>
        </div>
    );
}