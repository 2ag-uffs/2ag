import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router';
import './selecao-escalas.css';
import '../../styles/colors.css';
import '../../styles/fonts.css';
import '../../styles/button.css';
import Header from "../../components/header/header.jsx";

const API_BASE_URL = 'http://localhost:8080';

const ESCALAS_MAPEAMENTO = {
    'ESCALA_HAMILTON': {
        id: 'escala_hamilton',
        nome: 'Escala de Hamilton para Ansiedade (HAM-A)',
        descricao: 'Avalia a intensidade dos sintomas de ansiedade.'
    },
    'ESCALA_PITTSBURGH': {
        id: 'escala_pittsburgh',
        nome: 'Índice de Qualidade do Sono de Pittsburgh',
        descricao: 'Avalia a qualidade do sono e distúrbios do sono.'
    },
    'MINI_EXAME_ESTADO_MENTAL': {
        id: 'mini_exame_estado_mental',
        nome: 'Mini-Exame do Estado Mental (MEEM)',
        descricao: 'Avalia a função cognitiva geral do paciente.'
    },
    'REGISTRO_DOR': {
        id: 'registro_dor',
        nome: 'Registro de Dor',
        descricao: 'Registra e monitora a intensidade da dor do paciente.'
    },
    'REGISTRO_SONO': {
        id: 'registro_sono',
        nome: 'Registro de Sono',
        descricao: 'Registra padrões e qualidade do sono do paciente.'
    },
    'REGISTRO_TEA': {
        id: 'registro_tea',
        nome: 'Registro TEA',
        descricao: 'Registro específico para Transtorno do Espectro Autista.'
    },
    'ACOMPANHAMENTO_SEMANAL': {
        id: 'acompanhamento_semanal',
        nome: 'Acompanhamento Semanal',
        descricao: 'Acompanhamento semanal do progresso do paciente.'
    }
};

export default function SelecaoEscalas() {
    const navigate = useNavigate();
    const { pacienteId } = useParams();

    const [paciente, setPaciente] = useState({ nome: '' });
    const [escalasDisponiveis, setEscalasDisponiveis] = useState([]);
    const [escalasSelecionadas, setEscalasSelecionadas] = useState({});

    const [termoBusca, setTermoBusca] = useState('');
    const [loading, setLoading] = useState(true);
    const [loadingSalvar, setLoadingSalvar] = useState(false);
    const [error, setError] = useState(null);

    const getAuthToken = () => {
        return localStorage.getItem('authToken');
    };

    const fetchWithAuth = async (url, options = {}) => {
        const token = getAuthToken();
        if (!token) {
            throw new Error('Token de autenticação não encontrado');
        }

        const response = await fetch(url, {
            ...options,
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json',
                ...options.headers
            }
        });

        if (!response.ok) {
            throw new Error(`Erro na requisição: ${response.status} ${response.statusText}`);
        }

        return response.json();
    };

    const buscarDadosPaciente = async () => {
        try {
            const dadosPaciente = await fetchWithAuth(`${API_BASE_URL}/paciente/${pacienteId}`);
            return dadosPaciente;
        } catch (error) {
            console.error('Erro ao buscar dados do paciente:', error);
            throw error;
        }
    };

    const buscarEscalasDisponiveis = () => {
        return Object.entries(ESCALAS_MAPEAMENTO).map(([backendType, frontendData]) => ({
            ...frontendData,
            backendType
        }));
    };

    const buscarEscalasAtribuidas = async () => {
        try {
            const response = await fetchWithAuth(`${API_BASE_URL}/dashboard/paciente/${pacienteId}`);
            return response.escalasAtribuidas || [];
        } catch (error) {
            console.error('Erro ao buscar escalas atribuídas:', error);
            return [];
        }
    };

    const salvarEscalasSelecionadas = async (escalasParaSalvar) => {
        try {
            const promises = escalasParaSalvar.map(async (escalaBackendType) => {
                const payload = {
                    scaleType: escalaBackendType
                };

                return await fetchWithAuth(`${API_BASE_URL}/pacientes/${pacienteId}/escalas`, {
                    method: 'POST',
                    body: JSON.stringify(payload)
                });
            });

            await Promise.all(promises);
            return true;
        } catch (error) {
            console.error('Erro ao salvar escalas:', error);
            throw error;
        }
    };

    useEffect(() => {
        const carregarDados = async () => {
            if (!pacienteId) {
                setError('ID do paciente não fornecido');
                setLoading(false);
                return;
            }

            try {
                setLoading(true);
                setError(null);

                const [dadosPaciente, escalasAtribuidas] = await Promise.all([
                    buscarDadosPaciente(),
                    buscarEscalasAtribuidas()
                ]);

                setPaciente({
                    nome: dadosPaciente.name || 'Nome não informado'
                });

                const escalas = buscarEscalasDisponiveis();
                setEscalasDisponiveis(escalas);

                const selecaoInicial = {};
                escalasAtribuidas.forEach(escalaAtribuida => {
                    const escalaEncontrada = escalas.find(e => e.backendType === escalaAtribuida.scaleType);
                    if (escalaEncontrada) {
                        selecaoInicial[escalaEncontrada.id] = true;
                    }
                });
                setEscalasSelecionadas(selecaoInicial);

            } catch (error) {
                console.error('Erro ao carregar dados:', error);
                setError('Erro ao carregar dados. Verifique sua conexão e tente novamente.');
            } finally {
                setLoading(false);
            }
        };

        carregarDados();
    }, [pacienteId]);

    const handleBack = () => {
        navigate(-1);
    };

    const handleSelecaoChange = (escalaId) => {
        setEscalasSelecionadas(prev => ({
            ...prev,
            [escalaId]: !prev[escalaId]
        }));
    };

    const handleSalvarSelecao = async () => {
        try {
            setLoadingSalvar(true);

            const selecionadas = Object.keys(escalasSelecionadas).filter(id => escalasSelecionadas[id]);

            const escalasParaSalvar = selecionadas.map(escalaId => {
                const escala = escalasDisponiveis.find(e => e.id === escalaId);
                return escala?.backendType;
            }).filter(Boolean);

            if (escalasParaSalvar.length === 0) {
                alert('Selecione pelo menos uma escala para salvar.');
                return;
            }

            await salvarEscalasSelecionadas(escalasParaSalvar);

            alert(`Escalas salvas com sucesso para ${paciente.nome}!`);
            navigate('/dashboard-prescritor');

        } catch (error) {
            console.error('Erro ao salvar escalas:', error);
            alert('Erro ao salvar escalas. Tente novamente.');
        } finally {
            setLoadingSalvar(false);
        }
    };

    const escalasFiltradas = escalasDisponiveis.filter(escala =>
        escala.nome.toLowerCase().includes(termoBusca.toLowerCase())
    );

    const totalSelecionadas = Object.values(escalasSelecionadas).filter(Boolean).length;

    if (loading) {
        return (
            <div className="selecao-escalas">
                <Header
                    showBackButton={true}
                    backButtonText="Voltar"
                    onBackClick={handleBack}
                />
                <main className="selecao-main">
                    <div className="selecao-title">
                        <h1>Carregando...</h1>
                        <p>Buscando dados do paciente e escalas disponíveis.</p>
                    </div>
                </main>
            </div>
        );
    }

    if (error) {
        return (
            <div className="selecao-escalas">
                <Header
                    showBackButton={true}
                    backButtonText="Voltar"
                    onBackClick={handleBack}
                />
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
            <Header
                showBackButton={true}
                backButtonText="Voltar"
                onBackClick={handleBack}
            />

            <main className="selecao-main">
                <div className="selecao-title">
                    <h1>Selecionar Escalas para {paciente.nome}</h1>
                    <p>Escolha as escalas de avaliação que ficarão disponíveis para o paciente preencher.</p>
                </div>

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
                        {escalasFiltradas.map(escala => (
                            <div key={escala.id} className="escala-item">
                                <div className="escala-info">
                                    <h3>{escala.nome}</h3>
                                    <p>{escala.descricao}</p>
                                </div>
                                <div className="escala-selecao">
                                    <input
                                        type="checkbox"
                                        id={`checkbox-${escala.id}`}
                                        className="custom-checkbox"
                                        checked={!!escalasSelecionadas[escala.id]}
                                        onChange={() => handleSelecaoChange(escala.id)}
                                    />
                                    <label htmlFor={`checkbox-${escala.id}`}></label>
                                </div>
                            </div>
                        ))}
                    </div>

                    <footer className="selecao-footer">
                        <p>{totalSelecionadas} escala(s) selecionada(s)</p>
                        <button
                            className="button-primary"
                            onClick={handleSalvarSelecao}
                            disabled={totalSelecionadas === 0 || loadingSalvar}
                        >
                            {loadingSalvar ? 'Salvando...' : 'Salvar Seleção'}
                        </button>
                    </footer>
                </div>
            </main>
        </div>
    );
}