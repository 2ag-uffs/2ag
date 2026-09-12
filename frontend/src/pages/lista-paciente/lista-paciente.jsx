import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './lista-paciente.css';
import '../../styles/colors.css';
import '../../styles/fonts.css';
import '../../styles/button.css';
import Header from "../../components/header/header.jsx";

// Função para decodificar o token e obter o ID do prescritor
function parseJwt(token) {
    try {
        return JSON.parse(atob(token.split('.')[1]));
    } catch (e) {
        return null;
    }
}

export default function ListaPacientes() {
    const navigate = useNavigate();
    const [pacientes, setPacientes] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);
    const [searchTerm, setSearchTerm] = useState("");

    useEffect(() => {
        const fetchPacientes = async () => {
            const token = localStorage.getItem("authToken");
            if (!token) {
                navigate("/login");
                return;
            }

            const decodedToken = parseJwt(token);
            const prescriberId = decodedToken?.id;

            if (!prescriberId) {
                setError("Não foi possível identificar o prescritor.");
                setIsLoading(false);
                return;
            }

            try {
                const response = await fetch(`http://localhost:8080/paciente/prescritor/${prescriberId}`, {
                    headers: { "Authorization": `Bearer ${token}` }
                });

                if (!response.ok) {
                    throw new Error(`Erro ao buscar pacientes: ${response.statusText}`);
                }

                const data = await response.json();
                setPacientes(data);
            } catch (err) {
                setError(err.message);
            } finally {
                setIsLoading(false);
            }
        };

        fetchPacientes();
    }, [navigate]);

    const handleSelectScales = (pacienteId) => {
        navigate(`/paciente/${pacienteId}/selecao-escalas`);
    };

    const handleViewHistory = (pacienteId) => {
        navigate(`/paciente/${pacienteId}/historico`);
    };

    const handleNewConsult = (patientId) => {
        navigate(`/consulta?patientId=${patientId}`);
    };

    const handleBack = () => {
        navigate(-1);
    };

    const filteredPacientes = pacientes.filter(paciente =>
        paciente.name.toLowerCase().includes(searchTerm.toLowerCase())
    );

    const calculateAge = (birthDate) => {
        if (!birthDate) return 'N/A';
        const today = new Date();
        const birth = new Date(birthDate);
        let age = today.getFullYear() - birth.getFullYear();
        const m = today.getMonth() - birth.getMonth();
        if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) {
            age--;
        }
        return age;
    };


    if (isLoading) {
        return <div className="lista-pacientes-page"><h1>Carregando pacientes...</h1></div>;
    }

    if (error) {
        return <div className="lista-pacientes-page"><h1>Erro: {error}</h1></div>;
    }

    return (
        <div className="lista-pacientes-page">
            <Header
                showBackButton={true}
                backButtonText="Voltar"
                onBackClick={handleBack}
            />

            <main className="lp-main">
                <div className="lp-pacientes-header">
                    <h2>Meus Pacientes Ativos</h2>
                    <input
                        type="text"
                        placeholder="Buscar paciente..."
                        className="lp-busca-paciente"
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                    />
                </div>

                <div className="lp-pacientes-lista">
                    {filteredPacientes.length > 0 ? (
                        filteredPacientes.map(paciente => (
                            <div key={paciente.id} className="lp-paciente-card">
                                <div className="lp-paciente-info-wrapper">
                                    <div className="lp-paciente-avatar">{paciente.name.charAt(0)}</div>
                                    <div className="lp-paciente-info">
                                        <h3>{paciente.name}</h3>
                                        <p>{calculateAge(paciente.birthDate)} anos</p>
                                    </div>
                                    <div className="lp-paciente-status">
                                        <span className="lp-status-dot"></span>
                                        Ativo
                                    </div>
                                </div>
                                <div className="lp-paciente-actions">
                                    <button className="button-tertiary" onClick={() => handleNewConsult(paciente.id)}>
                                        Iniciar Consulta
                                    </button>
                                    <button className="button-tertiary" onClick={() => handleSelectScales(paciente.id)}>
                                        Gerenciar Escalas
                                    </button>
                                    <button className="button-tertiary" onClick={() => handleViewHistory(paciente.id)}>
                                        Ver Histórico
                                    </button>
                                </div>
                            </div>
                        ))
                    ) : (
                        <p>Nenhum paciente encontrado.</p>
                    )}
                </div>
            </main>
        </div>
    );
}