import React, { useState, useEffect } from "react";
import "../../styles/colors.css";
import "../../styles/fonts.css";
import "../../styles/button.css";
import "../../styles/input.css";
import "./agendamento-consulta-prescritor.css";
import { useNavigate } from "react-router-dom";
import Header from "../../components/header/header.jsx";

export default function AgendamentoPrescritor() {
    const navigate = useNavigate();

    const [currentWeek, setCurrentWeek] = useState(new Date());
    const [selectedDate, setSelectedDate] = useState(new Date());
    const [selectedTimeSlot, setSelectedTimeSlot] = useState(null);
    const [showNewAppointmentModal, setShowNewAppointmentModal] = useState(false);
    const [showEditModal, setShowEditModal] = useState(false);
    const [editingAppointment, setEditingAppointment] = useState(null);

    // Estados do formulário
    const [selectedPatient, setSelectedPatient] = useState(null);
    const [appointmentType, setAppointmentType] = useState("presencial");
    const [duration, setDuration] = useState(60);
    const [observations, setObservations] = useState("");
    const [patientSearch, setPatientSearch] = useState("");

    // Mock data para pacientes
    const patients = [
        {
            id: 1,
            nome: "João Silva",
            email: "joao@email.com",
            telefone: "(11) 99999-9999",
            ultimaConsulta: "2025-05-01"
        },
        {
            id: 2,
            nome: "Maria Santos",
            email: "maria@email.com",
            telefone: "(11) 88888-8888",
            ultimaConsulta: "2025-04-28"
        },
        {
            id: 3,
            nome: "Ana Costa",
            email: "ana@email.com",
            telefone: "(11) 77777-7777",
            ultimaConsulta: "2025-05-03"
        },
        {
            id: 4,
            nome: "Pedro Lima",
            email: "pedro@email.com",
            telefone: "(11) 66666-6666",
            ultimaConsulta: "2025-04-30"
        }
    ];

    // Mock data para agendamentos
    const [appointments, setAppointments] = useState([
        {
            id: 1,
            pacienteId: 1,
            pacienteNome: "João Silva",
            data: "2025-05-08",
            horarioInicio: "09:00",
            horarioFim: "10:00",
            tipo: "presencial",
            status: "confirmado",
            observacoes: "Consulta de acompanhamento"
        },
        {
            id: 2,
            pacienteId: 2,
            pacienteNome: "Maria Santos",
            data: "2025-05-08",
            horarioInicio: "10:30",
            horarioFim: "11:30",
            tipo: "telemedicina",
            status: "agendado",
            observacoes: "Primeira consulta"
        },
        {
            id: 3,
            pacienteId: 3,
            pacienteNome: "Ana Costa",
            data: "2025-05-09",
            horarioInicio: "14:00",
            horarioFim: "15:00",
            tipo: "presencial",
            status: "confirmado",
            observacoes: "Revisão de prescrição"
        }
    ]);

    // Horários de trabalho
    const workingHours = {
        start: 8,
        end: 18,
        interval: 30 // minutos
    };

    const handleBack = () => {
        navigate(-1);
    };

    const getWeekDays = (date) => {
        const week = [];
        const startOfWeek = new Date(date);
        const day = startOfWeek.getDay();
        const diff = startOfWeek.getDate() - day + (day === 0 ? -6 : 1); // Ajustar para segunda-feira
        startOfWeek.setDate(diff);

        for (let i = 0; i < 7; i++) {
            const day = new Date(startOfWeek);
            day.setDate(startOfWeek.getDate() + i);
            week.push(day);
        }
        return week;
    };

    const generateTimeSlots = () => {
        const slots = [];
        for (let hour = workingHours.start; hour < workingHours.end; hour++) {
            for (let minute = 0; minute < 60; minute += workingHours.interval) {
                const time = `${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}`;
                slots.push(time);
            }
        }
        return slots;
    };

    const isTimeSlotOccupied = (date, time) => {
        const dateString = date.toISOString().split('T')[0];
        return appointments.some(apt =>
            apt.data === dateString &&
            apt.horarioInicio <= time &&
            apt.horarioFim > time
        );
    };

    const getAppointmentAtTime = (date, time) => {
        const dateString = date.toISOString().split('T')[0];
        return appointments.find(apt =>
            apt.data === dateString &&
            apt.horarioInicio <= time &&
            apt.horarioFim > time
        );
    };

    const handleTimeSlotClick = (date, time) => {
        if (isTimeSlotOccupied(date, time)) {
            const appointment = getAppointmentAtTime(date, time);
            setEditingAppointment(appointment);
            setShowEditModal(true);
        } else {
            setSelectedDate(date);
            setSelectedTimeSlot(time);
            setShowNewAppointmentModal(true);
        }
    };

    const handleCreateAppointment = () => {
        if (!selectedPatient || !selectedTimeSlot) {
            alert("Por favor, selecione um paciente e horário.");
            return;
        }

        const endTime = new Date(`2000-01-01T${selectedTimeSlot}`);
        endTime.setMinutes(endTime.getMinutes() + duration);
        const endTimeString = endTime.toTimeString().slice(0, 5);

        const newAppointment = {
            id: appointments.length + 1,
            pacienteId: selectedPatient.id,
            pacienteNome: selectedPatient.nome,
            data: selectedDate.toISOString().split('T')[0],
            horarioInicio: selectedTimeSlot,
            horarioFim: endTimeString,
            tipo: appointmentType,
            status: "agendado",
            observacoes: observations
        };

        setAppointments([...appointments, newAppointment]);

        // Simular notificação
        alert(`Consulta agendada com sucesso!\n\nPaciente: ${selectedPatient.nome}\nData: ${selectedDate.toLocaleDateString("pt-BR")}\nHorário: ${selectedTimeSlot}\nTipo: ${appointmentType}\n\nNotificação enviada para o paciente.`);

        // Reset form
        setShowNewAppointmentModal(false);
        setSelectedPatient(null);
        setObservations("");
        setPatientSearch("");
    };

    const handleEditAppointment = () => {
        // Simular edição
        alert(`Agendamento editado com sucesso!\n\nNotificação de alteração enviada para ${editingAppointment.pacienteNome}.`);
        setShowEditModal(false);
        setEditingAppointment(null);
    };

    const handleCancelAppointment = () => {
        if (window.confirm(`Tem certeza que deseja cancelar a consulta de ${editingAppointment.pacienteNome}?`)) {
            setAppointments(appointments.filter(apt => apt.id !== editingAppointment.id));
            alert(`Consulta cancelada.\n\nNotificação de cancelamento enviada para ${editingAppointment.pacienteNome}.`);
            setShowEditModal(false);
            setEditingAppointment(null);
        }
    };

    const filteredPatients = patients.filter(patient =>
        patient.nome.toLowerCase().includes(patientSearch.toLowerCase())
    );

    const formatDate = (date) => {
        return date.toLocaleDateString("pt-BR", {
            weekday: "short",
            day: "2-digit",
            month: "2-digit"
        });
    };

    const getTodayAppointments = () => {
        const today = new Date().toISOString().split('T')[0];
        return appointments.filter(apt => apt.data === today);
    };

    const navigateWeek = (direction) => {
        const newWeek = new Date(currentWeek);
        newWeek.setDate(currentWeek.getDate() + (direction * 7));
        setCurrentWeek(newWeek);
    };

    // Estado para armazenar os dias da semana
    const [weekDays, setWeekDays] = useState([]);

    // UseEffect para inicializar e atualizar weekDays quando currentWeek muda
    useEffect(() => {
        setWeekDays(getWeekDays(currentWeek));
    }, [currentWeek]);

    return (
        <div className="agendamento-prescritor">
            <Header
                title="Dr. Maria Santos - CRM 12345"
                showBackButton={true}
                backButtonText="Voltar"
                onBackClick={handleBack}
            />

            <main className="dashboard-main">
                <div className="agendamento-header">
                    <div className="dashboard-welcome">
                        <h1>Gerenciar Agenda</h1>
                        <p>Agende consultas para seus pacientes e gerencie sua agenda de forma eficiente.</p>
                    </div>
                </div>

                <div className="agenda-container">
                    {/* Calendário Semanal */}
                    <section className="dashboard-card calendar-section">
                        <div className="card-header">
                            <h2>Agenda Semanal</h2>
                            <div className="week-navigation">
                                <button className="nav-button" onClick={() => navigateWeek(-1)}>
                                    Anterior
                                </button>
                                <span className="week-display">
                                    {weekDays.length > 0 ? `${weekDays[0].toLocaleDateString("pt-BR", { day: "2-digit", month: "short" })} - ${weekDays[6].toLocaleDateString("pt-BR", { day: "2-digit", month: "short" })}` : 'Carregando...'}
                                </span>
                                <button className="nav-button" onClick={() => navigateWeek(1)}>
                                    Próxima
                                </button>
                            </div>
                        </div>
                        <div className="calendar-grid">
                            <div className="time-column">
                                <div className="time-header"></div>
                                {generateTimeSlots().map(time => (
                                    <div key={time} className="time-slot-label">
                                        {time}
                                    </div>
                                ))}
                            </div>
                            {weekDays.map(day => (
                                <div key={day.toISOString()} className="day-column">
                                    <div className="day-header">
                                        <span className="day-name">{formatDate(day)}</span>
                                    </div>
                                    {generateTimeSlots().map(time => {
                                        const isOccupied = isTimeSlotOccupied(day, time);
                                        const appointment = getAppointmentAtTime(day, time);

                                        return (
                                            <div
                                                key={`${day.toISOString()}-${time}`}
                                                className={`time-slot ${isOccupied ? 'occupied' : 'free'}`}
                                                onClick={() => handleTimeSlotClick(day, time)}
                                            >
                                                {isOccupied && appointment && (
                                                    <div className="appointment-info">
                                                        <span className="patient-name">{appointment.pacienteNome}</span>
                                                        <span className="appointment-type">{appointment.tipo}</span>
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            ))}
                        </div>
                    </section>

                    {/* Agendamentos de Hoje */}
                    <section className="dashboard-card today-appointments">
                        <div className="card-header">
                            <h2>Agendamentos de Hoje</h2>
                            <span className="card-badge">{getTodayAppointments().length} consultas</span>
                        </div>
                        <div className="card-content">
                            {getTodayAppointments().length === 0 ? (
                                <div className="empty-state">
                                    <p>Nenhum agendamento para hoje</p>
                                </div>
                            ) : (
                                getTodayAppointments().map(appointment => (
                                    <div key={appointment.id} className="appointment-item">
                                        <div className="appointment-time">{appointment.horarioInicio}</div>
                                        <div className="appointment-details">
                                            <h3>{appointment.pacienteNome}</h3>
                                            <p>{appointment.observacoes}</p>
                                            <span className={`appointment-type-badge ${appointment.tipo}`}>
                                                {appointment.tipo}
                                            </span>
                                        </div>
                                        <div className="appointment-actions">
                                            <button
                                                className="button-secondary"
                                                onClick={() => {
                                                    setEditingAppointment(appointment);
                                                    setShowEditModal(true);
                                                }}
                                            >
                                                Editar
                                            </button>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </section>
                </div>
            </main>

            {/* Modal de Novo Agendamento */}
            {showNewAppointmentModal && (
                <div className="modal-overlay" onClick={() => setShowNewAppointmentModal(false)}>
                    <div className="modal-content" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>Novo Agendamento</h2>
                            <button
                                className="close-button"
                                onClick={() => setShowNewAppointmentModal(false)}
                            >
                                ×
                            </button>
                        </div>
                        <div className="modal-body">
                            <div className="form-group">
                                <label>Data e Horário</label>
                                <div className="datetime-display">
                                    {selectedDate.toLocaleDateString("pt-BR")} às {selectedTimeSlot}
                                </div>
                            </div>

                            <div className="form-group">
                                <label>Buscar Paciente</label>
                                <input
                                    type="text"
                                    value={patientSearch}
                                    onChange={(e) => setPatientSearch(e.target.value)}
                                    placeholder="Digite o nome do paciente..."
                                    className="patient-search"
                                />
                                {patientSearch && (
                                    <div className="patient-list">
                                        {filteredPatients.map(patient => (
                                            <div
                                                key={patient.id}
                                                className={`patient-item ${selectedPatient?.id === patient.id ? 'selected' : ''}`}
                                                onClick={() => {
                                                    setSelectedPatient(patient);
                                                    setPatientSearch(patient.nome);
                                                }}
                                            >
                                                <div className="patient-info">
                                                    <h4>{patient.nome}</h4>
                                                    <p>Última consulta: {new Date(patient.ultimaConsulta).toLocaleDateString("pt-BR")}</p>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>

                            <div className="form-group">
                                <label>Tipo de Consulta</label>
                                <div className="radio-group">
                                    <label className="radio-option">
                                        <input
                                            type="radio"
                                            value="presencial"
                                            checked={appointmentType === "presencial"}
                                            onChange={(e) => setAppointmentType(e.target.value)}
                                        />
                                        <span>Presencial</span>
                                    </label>
                                    <label className="radio-option">
                                        <input
                                            type="radio"
                                            value="telemedicina"
                                            checked={appointmentType === "telemedicina"}
                                            onChange={(e) => setAppointmentType(e.target.value)}
                                        />
                                        <span>Telemedicina</span>
                                    </label>
                                </div>
                            </div>

                            <div className="form-group">
                                <label>Duração (minutos)</label>
                                <select
                                    value={duration}
                                    onChange={(e) => setDuration(parseInt(e.target.value))}
                                >
                                    <option value={30}>30 minutos</option>
                                    <option value={60}>60 minutos</option>
                                    <option value={90}>90 minutos</option>
                                </select>
                            </div>

                            <div className="form-group">
                                <label>Observações</label>
                                <textarea
                                    value={observations}
                                    onChange={(e) => setObservations(e.target.value)}
                                    placeholder="Observações sobre a consulta..."
                                    rows="3"
                                />
                            </div>
                        </div>
                        <div className="modal-footer">
                            <button
                                className="button-secondary"
                                onClick={() => setShowNewAppointmentModal(false)}
                            >
                                Cancelar
                            </button>
                            <button
                                className="button"
                                onClick={handleCreateAppointment}
                            >
                                Agendar Consulta
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Modal de Edição */}
            {showEditModal && editingAppointment && (
                <div className="modal-overlay" onClick={() => setShowEditModal(false)}>
                    <div className="modal-content" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>Editar Agendamento</h2>
                            <button
                                className="close-button"
                                onClick={() => setShowEditModal(false)}
                            >
                                ×
                            </button>
                        </div>
                        <div className="modal-body">
                            <div className="appointment-summary">
                                <h3>{editingAppointment.pacienteNome}</h3>
                                <p>Data: {new Date(editingAppointment.data).toLocaleDateString("pt-BR")}</p>
                                <p>Horário: {editingAppointment.horarioInicio} - {editingAppointment.horarioFim}</p>
                                <p>Tipo: {editingAppointment.tipo}</p>
                                <p>Status: {editingAppointment.status}</p>
                            </div>

                            <div className="form-group">
                                <label>Observações</label>
                                <textarea
                                    defaultValue={editingAppointment.observacoes}
                                    placeholder="Observações sobre a consulta..."
                                    rows="3"
                                />
                            </div>
                        </div>
                        <div className="modal-footer">
                            <button
                                className="button-danger"
                                onClick={handleCancelAppointment}
                            >
                                Cancelar Consulta
                            </button>
                            <button
                                className="button-secondary"
                                onClick={() => setShowEditModal(false)}
                            >
                                Fechar
                            </button>
                            <button
                                className="button"
                                onClick={handleEditAppointment}
                            >
                                Salvar Alterações
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}


