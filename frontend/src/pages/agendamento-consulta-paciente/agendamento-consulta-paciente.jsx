import {useState} from "react";
import "../../styles/colors.css";
import "../../styles/fonts.css";
import "../../styles/button.css";
import "../../styles/input.css";
import "./agendamento-consulta-paciente.css";
import {useNavigate} from "react-router";
import Header from "../../components/header/header.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";

export default function AgendamentoConsultaPaciente() {
    const navigate = useNavigate();

    // Estados do formulário
    const [selectedPrescritor, setSelectedPrescritor] = useState(null);
    const [selectedDate, setSelectedDate] = useState(null);
    const [selectedTime, setSelectedTime] = useState(null);
    const [consultaType, setConsultaType] = useState("presencial");
    const [observacoes, setObservacoes] = useState("");
    const [erro, setErro] = useState(null);
    const [enviando, setEnviando] = useState(false);

    // Função auxiliar para obter datas futuras
    const getFutureDate = (days) => {
        const date = new Date();
        date.setDate(date.getDate() + days);
        return date.toISOString().split('T')[0];
    };

    // Mock data para prescritores (datas ajustadas dinamicamente)
    const prescritores = [
        {
            id: 1,
            nome: "Dr. Maria Santos",
            foto: "/images/dr-maria.jpg",
            avaliacao: 4.9,
            disponibilidade: [getFutureDate(1), getFutureDate(2), getFutureDate(3), getFutureDate(4)]
        },
        {
            id: 2,
            nome: "Dr. João Silva",
            foto: "/images/dr-joao.jpg",
            avaliacao: 4.8,
            disponibilidade: [getFutureDate(0), getFutureDate(2), getFutureDate(4), getFutureDate(5)]
        },
        {
            id: 3,
            nome: "Dra. Ana Costa",
            foto: "/images/dra-ana.jpg",
            avaliacao: 4.9,
            disponibilidade: [getFutureDate(1), getFutureDate(3), getFutureDate(4), getFutureDate(6)]
        }
    ];

    // Mock data para horários disponíveis (datas ajustadas dinamicamente)
    const horariosDisponiveis = {
        [getFutureDate(0)]: ["09:00", "10:30", "14:00", "15:30"],
        [getFutureDate(1)]: ["08:30", "10:00", "13:30", "16:00"],
        [getFutureDate(2)]: ["09:30", "11:00", "14:30", "16:30"],
        [getFutureDate(3)]: ["08:00", "09:30", "13:00", "15:00"],
        [getFutureDate(4)]: ["10:00", "11:30", "14:00", "17:00"],
        [getFutureDate(5)]: ["09:00", "10:30", "15:00", "16:30"],
        [getFutureDate(6)]: ["08:30", "10:00", "13:30", "15:30"]
    };

    const handleBack = () => {
        navigate(-1);
    };

    const handlePrescritorSelect = (prescritor) => {
        setSelectedPrescritor(prescritor);
        setSelectedDate(null);
        setSelectedTime(null);
    };

    const handleDateSelect = (date) => {
        setSelectedDate(date);
        setSelectedTime(null);
    };

    const handleTimeSelect = (time) => {
        setSelectedTime(time);
    };

    // atencao: hoje o POST /consulta exige hasRole('PRESCRIBER'), entao
    // esta tela recebe 403. paciente n agenda sozinho no backend atual,
    // quem marca eh o prescritor. deixei a chamada certa pra quando a
    // clinica decidir se paciente pode ou n marcar direto
    const handleConfirmarAgendamento = async () => {
        if (!selectedPrescritor || !selectedDate || !selectedTime) {
            setErro("Selecione prescritor, data e horário.");
            return;
        }

        const usuarioLogado = getLoggedUser();
        if (!usuarioLogado) {
            navigate("/login");
            return;
        }

        setErro(null);
        setEnviando(true);

        const consulta = {
            patientId: usuarioLogado.id,
            dateTime: `${selectedDate}T${selectedTime}`,
            modality: consultaType === "presencial" ? "PRESENCIAL" : "TELEMEDICINA",
            clinicalObservation: observacoes,
        };

        try {
            await apiService.post("/consulta", consulta);
            navigate("/dashboard-paciente");
        } catch (err) {
            if (err instanceof ApiError && err.status === 403) {
                setErro("O agendamento é feito pela clínica. Entre em contato com seu prescritor.");
            } else {
                setErro(err instanceof ApiError ? err.message : "Não foi possível agendar a consulta.");
            }
        } finally {
            setEnviando(false);
        }
    };


    const formatDate = (dateString) => {
        const date = new Date(dateString);
        return date.toLocaleDateString("pt-BR");
    };

    const isDateAvailable = (date) => {
        if (!selectedPrescritor) return false;
        return selectedPrescritor.disponibilidade.includes(date);
    };

    const generateCalendarDays = () => {
        const today = new Date();
        const days = [];

        for (let i = 0; i < 14; i++) {
            const date = new Date(today);
            date.setDate(today.getDate() + i);
            const dateString = date.toISOString().split('T')[0];

            days.push({
                date: dateString,
                day: date.getDate(),
                dayName: date.toLocaleDateString("pt-BR", {weekday: "short"}),
                available: isDateAvailable(dateString)
            });
        }

        return days;
    };

    return (
        <div className="agendamento-consulta">
            <Header
                title="João Silva"
                showBackButton={true}
                backButtonText="Voltar"
                onBackClick={handleBack}
            />

            <main className="dashboard-main">
                <div className="agendamento-header">
                    <div className="dashboard-welcome">
                        <h1>Agendar Nova Consulta</h1>
                        <p>Escolha o prescritor, data e horário que melhor se adequam à sua agenda.</p>
                    </div>
                </div>

                <div className="agendamento-grid">
                    {/* Seleção de Prescritor */}
                    <section className="dashboard-card">
                        <div className="card-header">
                            <h2>Escolha o Prescritor</h2>
                            {selectedPrescritor && (
                                <span className="card-badge">Selecionado</span>
                            )}
                        </div>
                        <div className="card-content">
                            <div className="prescritores-list">
                                {prescritores.map((prescritor) => (
                                    <div
                                        key={prescritor.id}
                                        className={`prescritor-item ${selectedPrescritor?.id === prescritor.id ? 'selected' : ''}`}
                                        onClick={() => handlePrescritorSelect(prescritor)}
                                    >
                                        <div className="prescritor-avatar">
                                            <div className="avatar-placeholder">
                                                {prescritor.nome.split(' ').map(n => n[0]).join('')}
                                            </div>
                                        </div>
                                        <div className="prescritor-info">
                                            <h3>{prescritor.nome}</h3>
                                            <div className="prescritor-rating">
                                                ⭐ {prescritor.avaliacao}
                                            </div>
                                        </div>
                                        <div className="prescritor-select">
                                            {selectedPrescritor?.id === prescritor.id ? '✓' : ''}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </section>

                    {/* Calendário */}
                    <section className="dashboard-card">
                        <div className="card-header">
                            <h2>Escolha a Data</h2>
                            {selectedDate && (
                                <span className="card-badge">{formatDate(selectedDate)}</span>
                            )}
                        </div>
                        <div className="card-content">
                            {!selectedPrescritor ? (
                                <div className="empty-state">
                                    <p>Selecione um prescritor para ver as datas disponíveis</p>
                                </div>
                            ) : (
                                <div className="calendar-grid">
                                    {generateCalendarDays().map((day) => (
                                        <button
                                            key={day.date}
                                            className={`calendar-day ${day.available ? 'available' : 'unavailable'} ${selectedDate === day.date ? 'selected' : ''}`}
                                            onClick={() => day.available && handleDateSelect(day.date)}
                                            disabled={!day.available}
                                        >
                                            <span className="day-name">{day.dayName}</span>
                                            <span className="day-number">{day.day}</span>
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                    </section>

                    {/* Seleção de Horário */}
                    <section className="dashboard-card">
                        <div className="card-header">
                            <h2>Escolha o Horário</h2>
                            {selectedTime && (
                                <span className="card-badge">{selectedTime}</span>
                            )}
                        </div>
                        <div className="card-content">
                            {!selectedDate ? (
                                <div className="empty-state">
                                    <p>Selecione uma data para ver os horários disponíveis</p>
                                </div>
                            ) : (
                                <div className="horarios-grid">
                                    {horariosDisponiveis[selectedDate]?.map((horario) => (
                                        <button
                                            key={horario}
                                            className={`horario-button ${selectedTime === horario ? 'selected' : ''}`}
                                            onClick={() => handleTimeSelect(horario)}
                                        >
                                            {horario}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                    </section>

                    {/* Detalhes da Consulta */}
                    <section className="dashboard-card">
                        <div className="card-header">
                            <h2>Detalhes da Consulta</h2>
                        </div>
                        <div className="card-content">
                            <div className="form-group">
                                <label>Tipo de Consulta</label>
                                <div className="radio-group">
                                    <label className="radio-option">
                                        <input
                                            type="radio"
                                            value="presencial"
                                            checked={consultaType === "presencial"}
                                            onChange={(e) => setConsultaType(e.target.value)}
                                        />
                                        <span>Presencial</span>
                                    </label>
                                    <label className="radio-option">
                                        <input
                                            type="radio"
                                            value="telemedicina"
                                            checked={consultaType === "telemedicina"}
                                            onChange={(e) => setConsultaType(e.target.value)}
                                        />
                                        <span>Telemedicina</span>
                                    </label>
                                </div>
                            </div>

                            <div className="form-group">
                                <label htmlFor="observacoes">Observações (opcional)</label>
                                <textarea
                                    id="observacoes"
                                    value={observacoes}
                                    onChange={(e) => setObservacoes(e.target.value)}
                                    placeholder="Descreva brevemente o motivo da consulta ou observações importantes..."
                                    rows="3"
                                />
                            </div>
                        </div>
                    </section>

                    {/* Resumo do Agendamento */}
                    {(selectedPrescritor && selectedDate && selectedTime) && (
                        <section className="dashboard-card resumo-card">
                            <div className="card-header">
                                <h2>Resumo do Agendamento</h2>
                            </div>
                            <div className="card-content">
                                <div className="resumo-info">
                                    <div className="resumo-item">
                                        <strong>Prescritor:</strong> {selectedPrescritor.nome}
                                    </div>
                                    <div className="resumo-item">
                                        <strong>Data:</strong> {formatDate(selectedDate)}
                                    </div>
                                    <div className="resumo-item">
                                        <strong>Horário:</strong> {selectedTime}
                                    </div>
                                    <div className="resumo-item">
                                        <strong>Tipo:</strong> {consultaType === "presencial" ? "Presencial" : "Telemedicina"}
                                    </div>
                                </div>

                                {erro && <p className="agendamento-erro">{erro}</p>}

                                <div className="resumo-actions">
                                    <button className="button-secondary" onClick={handleBack} disabled={enviando}>
                                        Cancelar
                                    </button>
                                    <button
                                        className="button"
                                        onClick={handleConfirmarAgendamento}
                                        disabled={enviando}
                                    >
                                        {enviando ? "Agendando..." : "Confirmar agendamento"}
                                    </button>
                                </div>
                            </div>
                        </section>
                    )}
                </div>
            </main>
        </div>
    );
}



