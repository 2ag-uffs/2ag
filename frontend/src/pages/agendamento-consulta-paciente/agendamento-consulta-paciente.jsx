import {useCallback, useEffect, useState} from "react";
import "../../styles/colors.css";
import "../../styles/fonts.css";
import "../../styles/button.css";
import "../../styles/input.css";
import "./agendamento-consulta-paciente.css";
import {useNavigate} from "react-router";
import Header from "../../components/header/header.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";

// a agenda funciona em blocos de meia em meia hora, das 8h as 18h
const HORA_INICIAL = 8;
const HORA_FINAL = 18;
const DURACAO = 60;

// o toISOString devolve utc e troca o dia depois das 21h aqui
const dataLocal = (date) => {
    const ano = date.getFullYear();
    const mes = String(date.getMonth() + 1).padStart(2, "0");
    const dia = String(date.getDate()).padStart(2, "0");
    return `${ano}-${mes}-${dia}`;
};

const paraMinutos = (hhmm) => {
    const [hora, minuto] = hhmm.split(":").map(Number);
    return hora * 60 + minuto;
};

export default function AgendamentoConsultaPaciente() {
    const navigate = useNavigate();

    // o paciente tem um prescritor so, o dele. antes a tela oferecia tres
    // medicos inventados, com nota de avaliacao e tudo (RF10)
    const [prescritor, setPrescritor] = useState(null);
    const [selectedDate, setSelectedDate] = useState(null);
    const [selectedTime, setSelectedTime] = useState(null);
    const [consultaType, setConsultaType] = useState("presencial");
    const [observacoes, setObservacoes] = useState("");
    const [erro, setErro] = useState(null);
    const [enviando, setEnviando] = useState(false);
    const [carregando, setCarregando] = useState(true);
    // os horarios que o prescritor ja tem ocupados no dia escolhido
    const [ocupados, setOcupados] = useState([]);
    const [buscandoHorarios, setBuscandoHorarios] = useState(false);

    // o prescritor vem do vinculo do paciente, n de uma lista
    useEffect(() => {
        const usuarioLogado = getLoggedUser();
        if (!usuarioLogado) {
            navigate("/login");
            return;
        }

        apiService
            .get(`/paciente/${usuarioLogado.id}`)
            .then((paciente) => {
                if (paciente.prescriberId) {
                    setPrescritor({id: paciente.prescriberId, nome: paciente.prescriberName});
                }
            })
            .catch((err) => {
                setErro(err instanceof ApiError ? err.message : "Não foi possível carregar seus dados.");
            })
            .finally(() => setCarregando(false));
    }, [navigate]);

    // os horarios livres do dia saem da agenda do prescritor. a api
    // devolve so os intervalos ocupados, sem dizer de quem sao
    const buscarOcupados = useCallback(async (data) => {
        setBuscandoHorarios(true);
        try {
            setOcupados(await apiService.get(`/consulta/disponibilidade?data=${data}`));
        } catch {
            setOcupados([]);
            setErro("Não foi possível ver os horários do seu prescritor.");
        } finally {
            setBuscandoHorarios(false);
        }
    }, []);

    // todos os blocos do dia, marcando quais ja estao tomados
    const horariosDoDia = () => {
        const blocos = [];
        for (let hora = HORA_INICIAL; hora < HORA_FINAL; hora++) {
            for (let minuto = 0; minuto < 60; minuto += 30) {
                blocos.push(String(hora).padStart(2, "0") + ":" + String(minuto).padStart(2, "0"));
            }
        }

        const agora = new Date();
        const hoje = dataLocal(agora);
        const minutosAgora = agora.getHours() * 60 + agora.getMinutes();

        return blocos.map((horario) => {
            const inicio = paraMinutos(horario);
            const fim = inicio + DURACAO;
            const tomado = ocupados.some((faixa) => {
                const inicioOutro = paraMinutos(faixa.inicio);
                const fimOutro = paraMinutos(faixa.fim);
                return inicio < fimOutro && inicioOutro < fim;
            });
            // horario que ja passou hoje tbm n serve
            const passou = selectedDate === hoje && inicio <= minutosAgora;
            return {horario, livre: !tomado && !passou};
        });
    };

    const handleBack = () => {
        navigate(-1);
    };

    const handleDateSelect = (date) => {
        setSelectedDate(date);
        setSelectedTime(null);
        setErro(null);
        buscarOcupados(date);
    };

    const handleTimeSelect = (time) => {
        setSelectedTime(time);
    };

    const handleConfirmarAgendamento = async () => {
        if (!selectedDate || !selectedTime) {
            setErro("Escolha a data e o horário.");
            return;
        }

        const usuarioLogado = getLoggedUser();
        if (!usuarioLogado) {
            navigate("/login");
            return;
        }

        setErro(null);
        setEnviando(true);

        try {
            // o prescritor n vai no corpo: o backend pega o do vinculo
            await apiService.post("/consulta", {
                patientId: usuarioLogado.id,
                dateTime: `${selectedDate}T${selectedTime}:00`,
                modality: consultaType === "presencial" ? "PRESENCIAL" : "REMOTA",
                status: "AGENDADA",
                clinicalObservation: observacoes,
                durationMinutes: DURACAO,
            });
            navigate("/dashboard-paciente", {state: {aviso: "Consulta agendada."}});
        } catch (err) {
            setErro(err instanceof ApiError ? err.message : "Não foi possível agendar a consulta.");
            // o horario pode ter sido tomado enquanto a tela estava aberta
            buscarOcupados(selectedDate);
        } finally {
            setEnviando(false);
        }
    };


    const formatDate = (dateString) => {
        const date = new Date(dateString);
        return date.toLocaleDateString("pt-BR");
    };

    // os proximos 14 dias. domingo fica de fora, o resto abre e quem
    // diz o que sobrou de horario eh a agenda do prescritor
    const generateCalendarDays = () => {
        const today = new Date();
        const days = [];

        for (let i = 0; i < 14; i++) {
            const date = new Date(today);
            date.setDate(today.getDate() + i);

            days.push({
                date: dataLocal(date),
                day: date.getDate(),
                dayName: date.toLocaleDateString("pt-BR", {weekday: "short"}),
                available: date.getDay() !== 0
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
                        <p>Escolha a data e o horário que melhor se adequam à sua agenda.</p>
                    </div>
                </div>

                <div className="agendamento-grid">
    {/* o paciente tem um prescritor so, o dele. n ha o que escolher */}
                    <section className="dashboard-card">
                        <div className="card-header">
                            <h2>Seu Prescritor</h2>
                        </div>
                        <div className="card-content">
                            {carregando && <p>Carregando...</p>}
                            {!carregando && !prescritor && (
                                <div className="empty-state">
                                    <p>Você ainda não tem um prescritor vinculado. Fale com a clínica.</p>
                                </div>
                            )}
                            {prescritor && (
                                <div className="prescritor-item selected">
                                    <div className="prescritor-avatar">
                                        <div className="avatar-placeholder">
                                            {prescritor.nome.split(' ').map(parte => parte[0]).join('')}
                                        </div>
                                    </div>
                                    <div className="prescritor-info">
                                        <h3>{prescritor.nome}</h3>
                                    </div>
                                </div>
                            )}
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
                            {!prescritor ? (
                                <div className="empty-state">
                                    <p>Sem prescritor vinculado, não há agenda para mostrar</p>
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
                            {!selectedDate && (
                                <div className="empty-state">
                                    <p>Selecione uma data para ver os horários disponíveis</p>
                                </div>
                            )}
                            {selectedDate && buscandoHorarios && <p>Vendo a agenda...</p>}
                            {selectedDate && !buscandoHorarios && (
                                <div className="horarios-grid">
                                    {/* horario ocupado aparece, mas desabilitado: some
                                        um botao do nada confunde mais do que ajuda */}
                                    {horariosDoDia().map(({horario, livre}) => (
                                        <button
                                            type="button"
                                            key={horario}
                                            className={`horario-button ${selectedTime === horario ? 'selected' : ''}`}
                                            onClick={() => handleTimeSelect(horario)}
                                            disabled={!livre}
                                            title={livre ? "" : "Horário já ocupado"}
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
                                            value="remota"
                                            checked={consultaType === "remota"}
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
                    {(prescritor && selectedDate && selectedTime) && (
                        <section className="dashboard-card resumo-card">
                            <div className="card-header">
                                <h2>Resumo do Agendamento</h2>
                            </div>
                            <div className="card-content">
                                <div className="resumo-info">
                                    <div className="resumo-item">
                                        <strong>Prescritor:</strong> {prescritor.nome}
                                    </div>
                                    <div className="resumo-item">
                                        <strong>Data:</strong> {formatDate(selectedDate)}
                                    </div>
                                    <div className="resumo-item">
                                        <strong>Horário:</strong> {selectedTime}
                                    </div>
                                    <div className="resumo-item">
                                        <strong>Tipo:</strong> {consultaType === "presencial" ? "Presencial" : "Remota"}
                                    </div>
                                </div>

                                {erro && <p className="agendamento-erro">{erro}</p>}

                                <div className="resumo-actions">
                                    <button className="button-secondary" onClick={handleBack} disabled={enviando}>
                                        Cancelar
                                    </button>
                                    <button
                                        type="button"
                                        className="button"
                                        onClick={handleConfirmarAgendamento}
                                        disabled={enviando || !prescritor}
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



