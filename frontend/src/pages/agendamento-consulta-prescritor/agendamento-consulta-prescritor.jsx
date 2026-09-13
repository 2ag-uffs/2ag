import { useCallback, useEffect, useState } from 'react';
import "../../styles/colors.css";
import "../../styles/fonts.css";
import "../../styles/button.css";
import "../../styles/input.css";
import "./agendamento-consulta-prescritor.css";
import { useNavigate } from "react-router";
import Header from "../../components/header/header.jsx";
import ModalConfirmacao from "../../components/modal/modal-confirmacao.jsx";
import {apiService, ApiError} from "../../services/api.js";

// o toISOString devolve a data em utc, entao aqui no brasil ele troca o
// dia depois das 21h. a agenda compara data com data, entao precisa da
// data local
const dataLocal = (date) => {
    const ano = date.getFullYear();
    const mes = String(date.getMonth() + 1).padStart(2, "0");
    const dia = String(date.getDate()).padStart(2, "0");
    return `${ano}-${mes}-${dia}`;
};

// consulta cancelada continua na lista, pra ficar registrado que
// existiu, mas o horario dela volta a ficar livre
const cancelada = (consulta) => consulta.status === "CANCELADA";

const STATUS_LEGIVEL = {
    AGENDADA: "Agendada",
    EM_ANDAMENTO: "Em andamento",
    CONCLUIDA: "Concluída",
    CANCELADA: "Cancelada",
};

// a api guarda um instante so (dateTime) mais a duracao. a agenda
// trabalha com data, hora de inicio e hora de fim, entao a conversao
// fica aqui na borda, em vez de espalhada pela tela
const daApi = (consulta) => {
    const duracao = consulta.durationMinutes || 60;
    const inicio = new Date(consulta.dateTime);
    const fim = new Date(inicio.getTime() + duracao * 60000);
    const hhmm = (d) => String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0");
    return {
        id: consulta.id,
        pacienteId: consulta.patientId,
        pacienteNome: consulta.patientName,
        data: dataLocal(inicio),
        horarioInicio: hhmm(inicio),
        horarioFim: hhmm(fim),
        tipo: consulta.modality === "REMOTA" ? "remota" : "presencial",
        status: consulta.status,
        observacoes: consulta.clinicalObservation || "",
        duracao,
    };
};

// atencao: esta tela ainda n fala com a api. as consultas ficam so no
// estado do componente e somem quando a pagina recarrega. por isso os
// avisos daqui n dizem que o paciente foi notificado, porque n foi
export default function AgendamentoPrescritor() {
    const navigate = useNavigate();

    const [aviso, setAviso] = useState(null);
    const [erro, setErro] = useState(null);
    const [carregando, setCarregando] = useState(true);
    const [salvando, setSalvando] = useState(false);
    const [confirmandoCancelamento, setConfirmandoCancelamento] = useState(false);
    const [observacoesEdicao, setObservacoesEdicao] = useState("");
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

    const [patients, setPatients] = useState([]);
    const [appointments, setAppointments] = useState([]);

    // carrega os pacientes do prescritor e as consultas dele.
    // antes as duas listas eram fixas no codigo e sumiam ao recarregar
    const carregar = useCallback(async () => {
        try {
            const [consultas, pacientes] = await Promise.all([
                apiService.get("/consulta"),
                apiService.get("/paciente"),
            ]);
            setAppointments(consultas.map(daApi));
            setPatients(pacientes);
        } catch (err) {
            setErro(err instanceof ApiError ? err.message : "Não foi possível carregar a agenda.");
        } finally {
            setCarregando(false);
        }
    }, []);

    useEffect(() => {
        carregar();
    }, [carregar]);

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
        const dateString = dataLocal(date);
        return appointments.some(apt =>
            !cancelada(apt) &&
            apt.data === dateString &&
            apt.horarioInicio <= time &&
            apt.horarioFim > time
        );
    };

    const getAppointmentAtTime = (date, time) => {
        const dateString = dataLocal(date);
        return appointments.find(apt =>
            !cancelada(apt) &&
            apt.data === dateString &&
            apt.horarioInicio <= time &&
            apt.horarioFim > time
        );
    };

    const handleTimeSlotClick = (date, time) => {
        if (isTimeSlotOccupied(date, time)) {
            const appointment = getAppointmentAtTime(date, time);
            setEditingAppointment(appointment);
            setObservacoesEdicao(appointment.observacoes);
            setShowEditModal(true);
        } else {
            setSelectedDate(date);
            setSelectedTimeSlot(time);
            setShowNewAppointmentModal(true);
        }
    };

    const handleCreateAppointment = async () => {
        if (!selectedPatient || !selectedTimeSlot) {
            setAviso(null);
            setErro("Selecione um paciente e um horário.");
            return;
        }

        setErro(null);
        setSalvando(true);
        try {
            await apiService.post("/consulta", {
                patientId: selectedPatient.id,
                dateTime: `${dataLocal(selectedDate)}T${selectedTimeSlot}:00`,
                modality: appointmentType === "remota" ? "REMOTA" : "PRESENCIAL",
                status: "AGENDADA",
                clinicalObservation: observations,
                durationMinutes: duration,
            });
            await carregar();
            setAviso(
                `Consulta de ${selectedPatient.name} marcada para ` +
                `${selectedDate.toLocaleDateString("pt-BR")} às ${selectedTimeSlot}.`,
            );
            setShowNewAppointmentModal(false);
            setSelectedPatient(null);
            setObservations("");
            setPatientSearch("");
        } catch (err) {
            setErro(err instanceof ApiError ? err.message : "Não foi possível agendar a consulta.");
        } finally {
            setSalvando(false);
        }
    };

    // o put pede a consulta inteira, n so o campo mexido, entao o resto
    // vai de volta do jeito que veio
    const handleEditAppointment = async () => {
        setErro(null);
        setSalvando(true);
        try {
            await apiService.put(`/consulta/${editingAppointment.id}`, {
                patientId: editingAppointment.pacienteId,
                dateTime: `${editingAppointment.data}T${editingAppointment.horarioInicio}:00`,
                modality: editingAppointment.tipo === "remota" ? "REMOTA" : "PRESENCIAL",
                status: editingAppointment.status,
                clinicalObservation: observacoesEdicao,
                durationMinutes: editingAppointment.duracao,
            });
            await carregar();
            setAviso(`Agendamento de ${editingAppointment.pacienteNome} alterado.`);
            setShowEditModal(false);
            setEditingAppointment(null);
        } catch (err) {
            setErro(err instanceof ApiError ? err.message : "Não foi possível alterar a consulta.");
        } finally {
            setSalvando(false);
        }
    };

    // cancelar muda o status, n apaga: o registro fica no historico e o
    // horario volta a ficar livre na grade
    const handleCancelAppointment = async () => {
        setConfirmandoCancelamento(false);
        const nome = editingAppointment.pacienteNome;
        setErro(null);
        setSalvando(true);
        try {
            await apiService.put(`/consulta/${editingAppointment.id}/cancelar`);
            await carregar();
            setAviso(`Consulta de ${nome} cancelada.`);
            setShowEditModal(false);
            setEditingAppointment(null);
        } catch (err) {
            setErro(err instanceof ApiError ? err.message : "Não foi possível cancelar a consulta.");
        } finally {
            setSalvando(false);
        }
    };

    const filteredPatients = patients.filter(patient =>
        patient.name.toLowerCase().includes(patientSearch.toLowerCase())
    );

    const formatDate = (date) => {
        return date.toLocaleDateString("pt-BR", {
            weekday: "short",
            day: "2-digit",
            month: "2-digit"
        });
    };

    const getTodayAppointments = () => {
        const today = dataLocal(new Date());
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
                {carregando && <p>Carregando agenda...</p>}
                {erro && <p className="aviso aviso--atencao">{erro}</p>}
                {aviso && <p className="aviso">{aviso}</p>}
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
                                    <div
                                        key={appointment.id}
                                        className={`appointment-item ${cancelada(appointment) ? "cancelada" : ""}`}
                                    >
                                        <div className="appointment-time">{appointment.horarioInicio}</div>
                                        <div className="appointment-details">
                                            <h3>{appointment.pacienteNome}</h3>
                                            <p>{appointment.observacoes}</p>
                                            <span className={`appointment-type-badge ${appointment.tipo}`}>
                                                {appointment.tipo}
                                            </span>
                                            {cancelada(appointment) && (
                                                <span className="appointment-status-badge">Cancelada</span>
                                            )}
                                        </div>
                                        <div className="appointment-actions">
                                            {/* consulta cancelada n abre pra editar */}
                                            {!cancelada(appointment) && (
                                                <button
                                                    type="button"
                                                    className="button-secondary"
                                                    onClick={() => {
                                                        setEditingAppointment(appointment);
                                                        setObservacoesEdicao(appointment.observacoes);
                                                        setShowEditModal(true);
                                                    }}
                                                >
                                                    Editar
                                                </button>
                                            )}
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
                                                    setPatientSearch(patient.name);
                                                }}
                                            >
                                                <div className="patient-info">
                                                    <h4>{patient.name}</h4>
                                                    <p>{patient.email}</p>
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
                                            value="remota"
                                            checked={appointmentType === "remota"}
                                            onChange={(e) => setAppointmentType(e.target.value)}
                                        />
                                        <span>Remota</span>
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
                                type="button"
                                className="button"
                                onClick={handleCreateAppointment}
                                disabled={salvando}
                            >
                                {salvando ? "Agendando..." : "Agendar Consulta"}
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
                                <p>Status: {STATUS_LEGIVEL[editingAppointment.status] || editingAppointment.status}</p>
                            </div>

                            <div className="form-group">
                                <label>Observações</label>
                                <textarea
                                    value={observacoesEdicao}
                                    onChange={(e) => setObservacoesEdicao(e.target.value)}
                                    placeholder="Observações sobre a consulta..."
                                    rows="3"
                                />
                            </div>
                        </div>
                        <div className="modal-footer">
                            <button
                                type="button"
                                className="button-danger"
                                onClick={() => setConfirmandoCancelamento(true)}
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
                                type="button"
                                className="button"
                                onClick={handleEditAppointment}
                                disabled={salvando}
                            >
                                {salvando ? "Salvando..." : "Salvar Alterações"}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            <ModalConfirmacao
                show={confirmandoCancelamento}
                titulo="Cancelar consulta"
                mensagem={
                    editingAppointment
                        ? `A consulta de ${editingAppointment.pacienteNome} sai da agenda. Quer cancelar?`
                        : ""
                }
                textoConfirmar="Sim, cancelar"
                textoCancelar="Manter consulta"
                onConfirmar={handleCancelAppointment}
                onCancelar={() => setConfirmandoCancelamento(false)}
            />
        </div>
    );
}


