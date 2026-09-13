import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import './lista-paciente.css';
import InvitePatientModal from "../../components/invite-patient-modal/invite-patient-modal.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";

export default function ListaPacientes() {
    const navigate = useNavigate();
    const [pacientes, setPacientes] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);
    const [searchTerm, setSearchTerm] = useState("");
    const [isInviteOpen, setIsInviteOpen] = useState(false);

    useEffect(() => {
        const prescritor = getLoggedUser();
        if (!prescritor) {
            navigate("/login");
            return;
        }

        apiService
            .get("/paciente")
            .then(setPacientes)
            .catch((err) => {
                setError(err instanceof ApiError ? err.message : "Erro ao buscar pacientes.");
            })
            .finally(() => setIsLoading(false));
    }, [navigate]);

    const handleSelectScales = (pacienteId) => {
        navigate(`/paciente/${pacienteId}/selecao-escalas`);
    };

    const handleViewHistory = (pacienteId) => {
        navigate(`/paciente/${pacienteId}/historico`);
    };

    // RF28: o prescritor vendo a evolucao de um paciente dele
    const handleViewProgress = (pacienteId) => {
        navigate(`/paciente/${pacienteId}/progresso`);
    };

    // RF32: monta o acompanhamento automatico de 90 dias
    const handleAcompanhamento = (pacienteId) => {
        navigate(`/paciente/${pacienteId}/acompanhamento`);
    };

    // RF31: quem criou alterou ou abriu o prontuario desse paciente
    const handleViewAudit = (paciente) => {
        navigate(`/paciente/${paciente.id}/auditoria`, {state: {patientName: paciente.name}});
    };

    const handleNewConsult = (patientId) => {
        navigate(`/consulta?patientId=${patientId}`);
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
                    <button type="button" className="lp-invite-button" onClick={() => setIsInviteOpen(true)}>
                        Convidar paciente
                    </button>
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
                                    <button className="button-tertiary" onClick={() => handleViewProgress(paciente.id)}>
                                        Ver Progresso
                                    </button>
                                    <button className="button-tertiary" onClick={() => handleAcompanhamento(paciente.id)}>
                                        Acompanhamento
                                    </button>
                                    <button className="button-tertiary" onClick={() => handleViewAudit(paciente)}>
                                        Histórico de Acesso
                                    </button>
                                </div>
                            </div>
                        ))
                    ) : (
                        <p className="lp-empty-state">
                            {pacientes.length === 0
                                ? "Você ainda não tem pacientes. Use o botão Convidar paciente para enviar o link de cadastro."
                                : "Nenhum paciente encontrado."}
                        </p>
                    )}
                </div>
            </main>
            <InvitePatientModal show={isInviteOpen} onClose={() => setIsInviteOpen(false)}/>
        </div>
    );
}