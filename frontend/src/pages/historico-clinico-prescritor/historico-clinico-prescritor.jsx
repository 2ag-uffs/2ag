import { useNavigate, useParams } from 'react-router';
import { useState, useEffect } from 'react';
import './historico-clinico-prescritor.css';
import '../../styles/colors.css';
import '../../styles/fonts.css';
import '../../styles/button.css';
import Header from "../../components/header/header.jsx";
import React from "react";

const API_BASE_URL = 'http://localhost:8080';

export default function HistoricoClinicoPrescritor() {
    const navigate = useNavigate();
    const { pacienteId } = useParams();

    const [dadosPaciente, setDadosPaciente] = useState({
        nome: '',
        anamnese: '',
        diagnosticos: [],
        tratamentos: [],
        prescricoes: []
    });

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const getAuthToken = () => {
        return localStorage.getItem('authToken');
    };

    const fetchWithAuth = async (url) => {
        const token = getAuthToken();
        if (!token) {
            throw new Error('Token de autenticação não encontrado');
        }

        const response = await fetch(url, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });

        if (!response.ok) {
            throw new Error(`Erro na requisição: ${response.status} ${response.statusText}`);
        }

        return response.json();
    };

    const buscarDadosPaciente = async () => {
        try {
            const dadosBasicos = await fetchWithAuth(`${API_BASE_URL}/paciente/${pacienteId}`);
            return dadosBasicos;
        } catch (error) {
            console.error('Erro ao buscar dados básicos do paciente:', error);
            throw error;
        }
    };

    const buscarAnamnese = async () => {
        try {
            const anamneses = await fetchWithAuth(`${API_BASE_URL}/anamnese`);
            const anamnesePaciente = anamneses.find(anamnese => anamnese.patient?.id === parseInt(pacienteId));
            return anamnesePaciente?.description || 'Nenhuma informação de anamnese registrada.';
        } catch (error) {
            console.error('Erro ao buscar anamnese:', error);
            return 'Erro ao carregar anamnese.';
        }
    };

    const buscarConsultas = async () => {
        try {
            const consultas = await fetchWithAuth(`${API_BASE_URL}/consulta`);
            const consultasPaciente = consultas.filter(consulta => consulta.patient?.id === parseInt(pacienteId));

            const diagnosticos = consultasPaciente.map(consulta => ({
                id: consulta.id,
                data: new Date(consulta.consultationDate).toLocaleDateString('pt-BR'),
                diagnostico: consulta.diagnosis || 'Diagnóstico não informado'
            }));

            const tratamentos = consultasPaciente.map(consulta => ({
                id: consulta.id,
                data: new Date(consulta.consultationDate).toLocaleDateString('pt-BR'),
                tratamento: consulta.treatment || 'Tratamento não informado'
            }));

            return { diagnosticos, tratamentos };
        } catch (error) {
            console.error('Erro ao buscar consultas:', error);
            return { diagnosticos: [], tratamentos: [] };
        }
    };

    const buscarPrescricoes = async () => {
        try {
            const prescricoes = await fetchWithAuth(`${API_BASE_URL}/prescricao`);
            const prescricoesPaciente = prescricoes.filter(prescricao => prescricao.patient?.id === parseInt(pacienteId));

            return prescricoesPaciente.map(prescricao => ({
                id: prescricao.id,
                data: new Date(prescricao.createdAt || Date.now()).toLocaleDateString('pt-BR'),
                medicamento: prescricao.productDescription || 'Medicamento não informado',
                dose: prescricao.concentration || 'Dose não informada',
                posologia: prescricao.posology || 'Posologia não informada'
            }));
        } catch (error) {
            console.error('Erro ao buscar prescrições:', error);
            return [];
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

                const [dadosBasicos, anamnese, consultas, prescricoes] = await Promise.all([
                    buscarDadosPaciente(),
                    buscarAnamnese(),
                    buscarConsultas(),
                    buscarPrescricoes()
                ]);

                setDadosPaciente({
                    nome: dadosBasicos.name || 'Nome não informado',
                    anamnese: anamnese,
                    diagnosticos: consultas.diagnosticos,
                    tratamentos: consultas.tratamentos,
                    prescricoes: prescricoes
                });

            } catch (error) {
                console.error('Erro ao carregar dados do paciente:', error);
                setError('Erro ao carregar dados do paciente. Verifique sua conexão e tente novamente.');
            } finally {
                setLoading(false);
            }
        };

        carregarDados();
    }, [pacienteId]);

    const handleBack = () => {
        navigate(-1);
    };

    if (loading) {
        return (
            <div className="historico-prescritor-page">
                <Header
                    title="Dr. Maria Santos - CRM 12345"
                    showBackButton={true}
                    backButtonText="Voltar"
                    onBackClick={handleBack}
                />
                <main className="historico-main">
                    <div className="historico-title">
                        <h1>Histórico Clínico do Paciente</h1>
                    </div>
                    <p className="historico-subtitle">Carregando dados do paciente...</p>
                </main>
            </div>
        );
    }

    if (error) {
        return (
            <div className="historico-prescritor-page">
                <Header
                    title="Dr. Maria Santos - CRM 12345"
                    showBackButton={true}
                    backButtonText="Voltar"
                    onBackClick={handleBack}
                />
                <main className="historico-main">
                    <div className="historico-title">
                        <h1>Histórico Clínico do Paciente</h1>
                    </div>
                    <p className="historico-subtitle error-message">{error}</p>
                    <button onClick={() => window.location.reload()} className="retry-button">
                        Tentar Novamente
                    </button>
                </main>
            </div>
        );
    }

    return (
        <div className="historico-prescritor-page">
            <Header
                title="Dr. Maria Santos - CRM 12345"
                showBackButton={true}
                backButtonText="Voltar"
                onBackClick={handleBack}
            />

            <main className="historico-main">
                <div className="historico-title">
                    <h1>Histórico Clínico do Paciente</h1>
                </div>
                <p className="historico-subtitle">
                    Visualizando o histórico completo de <strong>{dadosPaciente.nome}</strong>.
                </p>

                <div className="historico-content">
                    {/* Seção de Anamnese */}
                    <section className="historico-section">
                        <h2>Anamnese</h2>
                        <p>{dadosPaciente.anamnese}</p>
                    </section>

                    {/* Seção de Diagnósticos */}
                    <section className="historico-section">
                        <h2>Diagnósticos</h2>
                        <ul className="historico-lista">
                            {dadosPaciente.diagnosticos?.length > 0 ? dadosPaciente.diagnosticos.map(item => (
                                <li key={item.id}><strong>{item.data}:</strong> {item.diagnostico}</li>
                            )) : <li>Nenhum diagnóstico registrado.</li>}
                        </ul>
                    </section>

                    {/* Seção de Tratamentos */}
                    <section className="historico-section">
                        <h2>Tratamentos</h2>
                        <ul className="historico-lista">
                            {dadosPaciente.tratamentos?.length > 0 ? dadosPaciente.tratamentos.map(item => (
                                <li key={item.id}><strong>{item.data}:</strong> {item.tratamento}</li>
                            )) : <li>Nenhum tratamento registrado.</li>}
                        </ul>
                    </section>

                    {/* Seção de Prescrições Anteriores */}
                    <section className="historico-section">
                        <h2>Prescrições Anteriores</h2>
                        <div className="prescricoes-container">
                            {dadosPaciente.prescricoes?.length > 0 ? dadosPaciente.prescricoes.map(item => (
                                <div key={item.id} className="prescricao-item">
                                    <h4>{item.medicamento}</h4>
                                    <span>{item.data}</span>
                                    <p><strong>Dose:</strong> {item.dose}</p>
                                    <p><strong>Posologia:</strong> {item.posologia}</p>
                                </div>
                            )) : <p>Nenhuma prescrição anterior registrada.</p>}
                        </div>
                    </section>
                </div>
            </main>
        </div>
    );
}