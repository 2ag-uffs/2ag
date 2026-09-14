import {useEffect, useState} from 'react';
import {useNavigate, useParams} from 'react-router';
import './selecao-escalas.css';
import {apiService, ApiError} from "../../services/api.js";

export default function SelecaoEscalas() {
    const navigate = useNavigate();
    const {pacienteId} = useParams();

    const [nomePaciente, setNomePaciente] = useState('');
    // as escalas q da pra enviar vem do servidor (RN09), entao o mini-exame
    // nem aparece aqui: quem aplica eh o prescritor durante a consulta
    const [escalasDisponiveis, setEscalasDisponiveis] = useState([]);
    // tipos de escala q o paciente ja recebeu e ainda n respondeu
    const [escalasPendentes, setEscalasPendentes] = useState([]);
    const [escalasSelecionadas, setEscalasSelecionadas] = useState({});
    const [termoBusca, setTermoBusca] = useState('');
    const [loading, setLoading] = useState(true);
    const [loadingEnvio, setLoadingEnvio] = useState(false);
    const [error, setError] = useState(null);
    // o error acima derruba a tela toda entao o recado do envio fica aqui
    const [avisoEnvio, setAvisoEnvio] = useState(null);

    useEffect(() => {
        Promise.all([
            apiService.get(`/paciente/${pacienteId}`),
            apiService.get(`/pacientes/${pacienteId}/escalas`),
            apiService.get('/escalas/designaveis')
        ])
            .then(([dadosPaciente, escalasEnviadas, designaveis]) => {
                setNomePaciente(dadosPaciente.name || 'Nome não informado');
                setEscalasDisponiveis(designaveis.map((escala) => ({
                    id: escala.type,
                    nome: escala.name,
                    descricao: escala.description,
                    backendType: escala.type
                })));
                setEscalasPendentes(escalasEnviadas
                    .filter(escala => escala.status === 'PENDENTE')
                    .map(escala => escala.scaleType));
            })
            .catch((erro) => {
                setError(erro instanceof ApiError
                    ? erro.message
                    : 'Erro ao carregar dados. Verifique sua conexão e tente novamente.');
            })
            .finally(() => setLoading(false));
    }, [pacienteId]);

    const handleSelecaoChange = (escalaId) => {
        setEscalasSelecionadas(prev => ({
            ...prev,
            [escalaId]: !prev[escalaId]
        }));
    };

    // so vai o q foi marcado agora e ainda n esta esperando resposta do paciente
    const escalasParaEnviar = escalasDisponiveis.filter(escala =>
        escalasSelecionadas[escala.id] && !escalasPendentes.includes(escala.backendType)
    );

    const handleEnviar = async () => {
        if (escalasParaEnviar.length === 0) {
            setAvisoEnvio('Marque pelo menos uma escala para enviar.');
            return;
        }

        setAvisoEnvio(null);
        setLoadingEnvio(true);
        const enviadas = [];
        try {
            // uma de cada vez pra saber o q chegou ao paciente se alguma falhar
            for (const escala of escalasParaEnviar) {
                await apiService.post(`/pacientes/${pacienteId}/escalas`, {scaleType: escala.backendType});
                enviadas.push(escala.backendType);
            }
            navigate('/dashboard-prescritor', {
                state: {
                    aviso: `${enviadas.length} escala(s) enviada(s) para ${nomePaciente}. `
                        + 'O paciente recebe um aviso no sistema e a escala aparece no painel dele.'
                },
            });
        } catch (erro) {
            const detalhe = erro instanceof ApiError ? erro.message : 'Não foi possível falar com o servidor.';
            setEscalasPendentes(atuais => [...atuais, ...enviadas]);
            setAvisoEnvio(enviadas.length > 0
                ? `Só parte das escalas foi enviada. ${detalhe}`
                : `Nenhuma escala foi enviada. ${detalhe}`);
            setLoadingEnvio(false);
        }
    };

    const escalasFiltradas = escalasDisponiveis.filter(escala =>
        escala.nome.toLowerCase().includes(termoBusca.toLowerCase())
    );

    if (loading) {
        return (
            <div className="selecao-escalas">
                <main className="selecao-main">
                    <div className="selecao-title">
                        <h1>Carregando...</h1>
                        <p>Buscando dados do paciente e escalas enviadas.</p>
                    </div>
                </main>
            </div>
        );
    }

    if (error) {
        return (
            <div className="selecao-escalas">
                <main className="selecao-main">
                    <div className="selecao-title">
                        <h1>Erro</h1>
                        <p className="error-message">{error}</p>
                        <button onClick={() => window.location.reload()} className="retry-button">
                            Tentar Novamente
                        </button>
                    </div>
                </main>
            </div>
        );
    }

    return (
        <div className="selecao-escalas">

            <main className="selecao-main">
                <div className="selecao-title">
                    <h1>Enviar escalas para {nomePaciente}</h1>
                    <p>
                        Marque as escalas que o paciente vai preencher. Ele recebe um aviso no sistema e a
                        escala aparece no painel dele.
                    </p>
                </div>

                {avisoEnvio && <p className="aviso aviso--atencao">{avisoEnvio}</p>}

                <div className="selecao-container">
                    <div className="selecao-filtros">
                        <input
                            type="text"
                            placeholder="Buscar escala pelo nome..."
                            value={termoBusca}
                            onChange={(e) => setTermoBusca(e.target.value)}
                            className="busca-input"
                        />
                    </div>

                    <div className="lista-escalas-container">
                        {escalasFiltradas.map(escala => {
                            const jaEnviada = escalasPendentes.includes(escala.backendType);
                            return (
                                <div key={escala.id} className="escala-item">
                                    <div className="escala-info">
                                        <h3>{escala.nome}</h3>
                                        <p>{jaEnviada ? 'Já enviada. Aguardando o paciente responder.' : escala.descricao}</p>
                                    </div>
                                    <div className="escala-selecao">
                                        <input
                                            type="checkbox"
                                            id={`checkbox-${escala.id}`}
                                            className="custom-checkbox"
                                            checked={jaEnviada || !!escalasSelecionadas[escala.id]}
                                            disabled={jaEnviada}
                                            onChange={() => handleSelecaoChange(escala.id)}
                                            aria-label={jaEnviada ? escala.nome + ' já enviada' : 'Enviar ' + escala.nome}
                                        />
                                        <label htmlFor={`checkbox-${escala.id}`}></label>
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    <footer className="selecao-footer">
                        <p>{escalasParaEnviar.length} escala(s) para enviar</p>
                        <button
                            className="button-primary"
                            onClick={handleEnviar}
                            disabled={escalasParaEnviar.length === 0 || loadingEnvio}
                        >
                            {loadingEnvio ? 'Enviando...' : 'Enviar ao paciente'}
                        </button>
                    </footer>
                </div>
            </main>
        </div>
    );
}
