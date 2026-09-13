import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router";
import Modal from "../../components/modal/modal";
import "../../styles/button.css";
import "../../styles/colors.css";
import "../../styles/fonts.css";
import "../../styles/input.css";
import "./dashboard-prescritor.css";
import {apiService, ApiError, getLoggedUser, clearToken} from "../../services/api.js";

export default function DashboardPrescritor() {
    const navigate = useNavigate();

    const [showModal, setShowModal] = useState(false);
    const [prescritorInfo, setPrescritorInfo] = useState(null);
    const [dashboardData, setDashboardData] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);
    const [isCreatingPatient, setIsCreatingPatient] = useState(false);
    const [formErrors, setFormErrors] = useState({});
    const [cpf, setCpf] = useState("");

    const fetchData = useCallback(async () => {
        const usuario = getLoggedUser();
        if (!usuario) {
            navigate("/login");
            return;
        }

        try {
            const [prescritor, dashboard] = await Promise.all([
                apiService.get(`/prescritor/${usuario.id}`),
                apiService.get(`/dashboard/prescritor/${usuario.id}`),
            ]);
            setPrescritorInfo(prescritor);
            setDashboardData(dashboard);
        } catch (err) {
            setError(err instanceof ApiError ? err.message : "Erro ao carregar o painel.");
        } finally {
            setIsLoading(false);
        }
    }, [navigate]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const handleLogout = (e) => {
        e.preventDefault();
        clearToken();
        navigate("/login");
    };

    const handleNewConsult = (e) => {
        e.preventDefault();
        navigate("/consulta");
    };

    // prescricao n existe solta, ela sai de dentro de uma consulta.
    // entao o atalho leva pra lista de pacientes, que eh por onde comeca
    const handleNewPrescription = (e) => {
        e.preventDefault();
        navigate("/lista-paciente");
    };

    const handleAgenda = (e) => {
        e.preventDefault();
        navigate("/agendamento-prescritor");
    };

    const handleNotificacoes = (e) => {
        e.preventDefault();
        navigate("/notificacoes-prescritor");
    };

    const handlePaciente = (e) => {
        e.preventDefault();
        navigate("/lista-paciente");
    };

    const handleCloseModal = (e) => {
        e.preventDefault();
        if (!isCreatingPatient) {
            setShowModal(false);
            setCpf("");
        }
    };

    const handleCpf = (e) => {
        let value = e.target.value.replace(/\D/g, "");
        value = value.replace(/(\d{3})(\d)/, "$1.$2");
        value = value.replace(/(\d{3})(\d)/, "$1.$2");
        value = value.replace(/(\d{3})(\d{1,2})$/, "$1-$2");
        setCpf(value);
    };

    const handleSubmitNewPatient = async (e) => {
        e.preventDefault();
        setIsCreatingPatient(true);
        setFormErrors({});
        const form = e.target;
        const password = form.senha.value;
        const confirmPassword = form.confirmarSenha.value;
        if (password !== confirmPassword) {
            setFormErrors({ confirmarSenha: "As senhas não conferem!" });
            setIsCreatingPatient(false);
            return;
        }
        const addressObject = {
            street: form.street.value,
            number: form.number.value,
            city: form.city.value,
            state: form.state.value,
            country: form.country.value,
        };
        let data = {
            name: form.nomeCompleto.value,
            email: form.email.value,
            senha: password,
            cpf: cpf.replace(/\D/g, ""),
            birthDate: form.dataNascimento.value,
            phone: form.telefone.value.replace(/\D/g, ""),
            address: addressObject,
        };

        // o vinculo com o prescritor sai do token no backend, entao o
        // professionalCode n precisa ir no corpo. ia um "abc12" fixo
        try {
            await apiService.post("/paciente/cadastrar-para-prescritor", data);
            setShowModal(false);
            setCpf("");
            fetchData();
        } catch (err) {
            if (err instanceof ApiError) {
                const porCampo = err.fieldErrors();
                setFormErrors(
                    Object.keys(porCampo).length > 0 ? porCampo : {general: err.message},
                );
            } else {
                setFormErrors({general: "Não foi possível falar com o servidor."});
            }
        } finally {
            setIsCreatingPatient(false);
        }
    };

    if (isLoading) {
        return (
            <div className="dashboard-loading">
                <h1>Carregando dados do painel...</h1>
            </div>
        );
    }

    if (error) {
        return (
            <div className="dashboard-error">
                <h1>Erro ao carregar o painel</h1>
                <p>{error}</p>
                <button onClick={() => navigate("/login")}>
                    Voltar ao Login
                </button>
            </div>
        );
    }

    return (
        <div className="dashboard-prescritor">
            <header className="dashboard-header">
                <img
                    src="/images/logotipo-icon-claro.svg"
                    alt="Logo"
                    className="logo"
                />
                <div className="dashboard-header__user">
                    <span>
                        {prescritorInfo?.name || "Nome do Doutor"} -{" "}
                        {prescritorInfo?.registryType || "CRM"}{" "}
                        {prescritorInfo?.registryNumber || "00000"}
                    </span>
                    <button
                        className="button-secondary"
                        onClick={handleNotificacoes}
                    >
                        Notificações
                    </button>
                    <button className="button-secondary" onClick={handleLogout}>
                        Sair
                    </button>
                </div>
            </header>

            <main className="dashboard-main">
                <div className="dashboard-welcome">
                    <h1>Painel do Prescritor!</h1>
                    <p>
                        Gerencie seus pacientes, consultas e acompanhe a
                        evolução dos tratamentos.
                    </p>
                </div>

                {/* Estatísticas Rápidas */}
                <div className="stats-grid">
                    <div className="stat-card" onClick={handlePaciente}>
                        <div className="stat-number">
                            {dashboardData?.activePatientsCount ?? 0}
                        </div>
                        <div className="stat-label">Pacientes Ativos</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-number">
                            {dashboardData?.appointmentsTodayCount ?? 0}
                        </div>
                        <div className="stat-label">Consultas Hoje</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-number">
                            {dashboardData?.pendingFormsCount ?? 0}
                        </div>
                        <div className="stat-label">Fichas Pendentes</div>
                    </div>
                    <div className="stat-card warning">
                        <div className="stat-number">
                            {dashboardData?.stats?.alertasClinicos ?? 0}
                        </div>
                        <div className="stat-label">Alertas Clínicos</div>
                    </div>
                </div>

                {/* Ações Rápidas */}
                <section className="dashboard-actions">
                    <h2>Ações rápidas</h2>
                    <div className="actions-grid">
                        <button
                            className="action-button"
                            onClick={() => setShowModal(true)}
                        >
                            <span className="action-icon">👥</span>
                            <span>Novo paciente</span>
                        </button>
                        <button
                            className="action-button"
                            onClick={handleNewConsult}
                        >
                            <span className="action-icon">📋</span>
                            <span>Nova consulta</span>
                        </button>
                        <button
                            className="action-button"
                            onClick={handleNewPrescription}
                        >
                            <span className="action-icon">💊</span>
                            <span>Prescrever</span>
                        </button>
                        <button
                            className="action-button"
                            onClick={handleAgenda}
                        >
                            <span className="action-icon">📅</span>
                            <span>Agenda</span>
                        </button>
                        {/* <button className="action-button">
                            <span className="action-icon">⚙️</span>
                            <span>Configurações</span>
                        </button> */}
                    </div>
                </section>

                <div className="dashboard-grid">
                    {/* Agendamentos do Dia */}
                    <section className="dashboard-card">
                        <div className="card-header">
                            <h2>Agendamentos de hoje</h2>
                            <span className="card-badge">
                                {dashboardData?.agendamentos?.length ?? 0}{" "}
                                consultas
                            </span>
                        </div>
                        <div className="card-content">
                            {dashboardData?.todaysAppointments?.length > 0 ? (
                                dashboardData.todaysAppointments.map(
                                    (agendamento) => (
                                        <div
                                            key={agendamento.id}
                                            className="agendamento-item-wrapper"
                                        >
                                            <div className="agendamento-item">
                                                <div className="agendamento-time">
                                                    {agendamento.horario}
                                                </div>
                                                <div className="agendamento-info">
                                                    <h3>
                                                        {
                                                            agendamento.nomePaciente
                                                        }
                                                    </h3>
                                                    <p>
                                                        {
                                                            agendamento.tipoConsulta
                                                        }
                                                    </p>
                                                    <span
                                                        className={`agendamento-tipo ${agendamento.modalidade?.toLowerCase()}`}
                                                    >
                                                        {agendamento.modalidade}
                                                    </span>
                                                </div>
                                            </div>
                                            <button className="button-secondary">
                                                Iniciar
                                            </button>
                                        </div>
                                    ),
                                )
                            ) : (
                                <p>Nenhum agendamento para hoje.</p>
                            )}
                        </div>
                        <div className="card-footer">
                            <button className="button" onClick={handleAgenda}>
                                Ver todos os agendamentos
                            </button>
                        </div>
                    </section>

                    {/* Fichas Pendentes */}
                    <section className="dashboard-card">
                        <div className="card-header">
                            <h2>Fichas pendentes de análise</h2>
                            <span className="card-badge warning">
                                {dashboardData?.fichasPendentes?.length ?? 0}{" "}
                                pendentes
                            </span>
                        </div>
                        <div className="card-content">
                            {dashboardData?.fichasPendentes?.length > 0 ? (
                                dashboardData.fichasPendentes.map((ficha) => (
                                    <div
                                        key={ficha.id}
                                        className="ficha-item-wrapper"
                                    >
                                        <div className="ficha-item">
                                            <div className="ficha-info">
                                                <h3>{ficha.nomePaciente}</h3>
                                                <p>{ficha.tipo}</p>
                                                <span className="ficha-data">
                                                    {ficha.dataEnvio}
                                                </span>
                                            </div>
                                            <div
                                                className={`ficha-priority ${ficha.prioridade?.toLowerCase()}`}
                                            >
                                                {ficha.prioridade}
                                            </div>
                                        </div>
                                        <button className="button-secondary">
                                            Analisar
                                        </button>
                                    </div>
                                ))
                            ) : (
                                <p>Nenhuma ficha pendente.</p>
                            )}
                        </div>
                        <div className="card-footer">
                            <button className="button">
                                Ver todas as fichas
                            </button>
                        </div>
                    </section>

                    {/* Alertas Clínicos */}
                    <section className="dashboard-card alert-card">
                        <div className="card-header">
                            <h2>Alertas clínicos</h2>
                            <span className="card-badge danger">
                                {dashboardData?.alertas?.length ?? 0} alertas
                            </span>
                        </div>
                        <div className="card-content">
                            {dashboardData?.alertas?.length > 0 ? (
                                dashboardData.alertas.map((alerta) => (
                                    <div
                                        key={alerta.id}
                                        className="alert-item-wrapper"
                                    >
                                        <div
                                            className={`alert-item ${alerta.nivel?.toLowerCase()}`}
                                        >
                                            <div className="alert-icon">
                                                {alerta.nivel === "CRITICAL"
                                                    ? "⚠️"
                                                    : "⚡"}
                                            </div>
                                            <div className="alert-info">
                                                <h3>{alerta.nomePaciente}</h3>
                                                <p>{alerta.descricao}</p>
                                                <span className="alert-time">
                                                    {alerta.data}
                                                </span>
                                            </div>
                                        </div>
                                        <button
                                            className={
                                                alerta.nivel === "CRITICAL"
                                                    ? "button"
                                                    : "button-secondary"
                                            }
                                        >
                                            {alerta.nivel === "CRITICAL"
                                                ? "Verificar"
                                                : "Contatar"}
                                        </button>
                                    </div>
                                ))
                            ) : (
                                <p>Nenhum alerta clínico no momento.</p>
                            )}
                        </div>
                        <div className="card-footer">
                            <button className="button">
                                Ver todos os alertas
                            </button>
                        </div>
                    </section>

                    {/* Pacientes Recentes */}
                    <section className="dashboard-card">
                        <div className="card-header">
                            <h2>Pacientes recentes</h2>
                            <span className="card-badge">
                                Últimas atividades
                            </span>
                        </div>
                        <div className="card-content">
                            {dashboardData?.pacientesRecentes?.length > 0 ? (
                                dashboardData.pacientesRecentes.map(
                                    (paciente) => (
                                        <div
                                            key={paciente.id}
                                            className="paciente-item-wrapper"
                                        >
                                            <div className="paciente-item">
                                                <div className="paciente-avatar">
                                                    {paciente.iniciais}
                                                </div>
                                                <div className="paciente-info">
                                                    <h3>{paciente.nome}</h3>
                                                    <p>
                                                        Última consulta:{" "}
                                                        {
                                                            paciente.ultimaConsulta
                                                        }
                                                    </p>
                                                    <span
                                                        className={`paciente-status ${paciente.status?.toLowerCase().replace(" ", "-")}`}
                                                    >
                                                        {paciente.status}
                                                    </span>
                                                </div>
                                            </div>
                                            <button className="button-secondary">
                                                Ver prontuário
                                            </button>
                                        </div>
                                    ),
                                )
                            ) : (
                                <p>Nenhuma atividade recente de pacientes.</p>
                            )}
                        </div>
                        <div className="card-footer">
                            <button className="button" onClick={handlePaciente}>
                                Ver todos os pacientes
                            </button>
                        </div>
                    </section>
                </div>
            </main>
            <Modal
                show={showModal}
                title={"Novo paciente"}
                onClickClose={handleCloseModal}
            >
                <form className="new-patient" onSubmit={handleSubmitNewPatient}>
                    <div className="new-patient__field">
                        <label htmlFor="nomeCompleto">Nome completo*</label>
                        <input
                            id="nomeCompleto"
                            name="nomeCompleto"
                            placeholder="Digite o nome completo"
                            required
                        />
                        {formErrors.name && (
                            <span className="input-error-message">
                                {formErrors.name}
                            </span>
                        )}
                    </div>
                    <div className="new-patient__row">
                        <div className="new-patient__field">
                            <label htmlFor="cpf">CPF*</label>
                            <input
                                id="cpf"
                                name="cpf"
                                maxLength={14}
                                value={cpf}
                                onChange={handleCpf}
                                placeholder="000.000.000-00"
                            />
                            {formErrors.cpf && (
                                <span className="input-error-message">
                                    {formErrors.cpf}
                                </span>
                            )}
                        </div>
                        <div className="new-patient__field">
                            <label htmlFor="email">Email*</label>
                            <input
                                id="email"
                                name="email"
                                placeholder="paciente@email.com"
                                type="email"
                                required
                            />
                            {formErrors.email && (
                                <span className="input-error-message">
                                    {formErrors.email}
                                </span>
                            )}
                        </div>
                    </div>
                    <div className="new-patient__row">
                        <div className="new-patient__field">
                            <label htmlFor="dataNascimento">
                                Data de nascimento*
                            </label>
                            <input
                                id="dataNascimento"
                                name="dataNascimento"
                                type="date"
                                required
                            />
                            {formErrors.birthDate && (
                                <span className="input-error-message">
                                    {formErrors.birthDate}
                                </span>
                            )}
                        </div>
                        <div className="new-patient__field">
                            <label htmlFor="telefone">Telefone*</label>
                            <input
                                id="telefone"
                                name="telefone"
                                type="tel"
                                required
                                placeholder="(00) 00000-0000"
                            />
                            {formErrors.phone && (
                                <span className="input-error-message">
                                    {formErrors.phone}
                                </span>
                            )}
                        </div>
                    </div>
                    <div className="new-patient__field">
                        <label htmlFor="street">Logradouro*</label>
                        <input
                            id="street"
                            name="street"
                            required
                            placeholder="Rua, Avenida, etc."
                        />
                        {formErrors["address.street"] && (
                            <span className="input-error-message">
                                {formErrors["address.street"]}
                            </span>
                        )}
                    </div>
                    <div className="new-patient__row">
                        <div className="new-patient__field small">
                            <label htmlFor="number">Número*</label>
                            <input
                                id="number"
                                name="number"
                                required
                                placeholder="123"
                            />
                            {formErrors["address.number"] && (
                                <span className="input-error-message">
                                    {formErrors["address.number"]}
                                </span>
                            )}
                        </div>
                        <div className="new-patient__field">
                            <label htmlFor="city">Cidade*</label>
                            <input
                                id="city"
                                name="city"
                                required
                                placeholder="Cidade"
                            />
                            {formErrors["address.city"] && (
                                <span className="input-error-message">
                                    {formErrors["address.city"]}
                                </span>
                            )}
                        </div>
                    </div>
                    <div className="new-patient__row">
                        <div className="new-patient__field small">
                            <label htmlFor="state">Estado (UF)*</label>
                            <input
                                id="state"
                                name="state"
                                required
                                maxLength="2"
                                placeholder="UF"
                            />
                            {formErrors["address.state"] && (
                                <span className="input-error-message">
                                    {formErrors["address.state"]}
                                </span>
                            )}
                        </div>
                        <div className="new-patient__field">
                            <label htmlFor="country">País*</label>
                            <input
                                id="country"
                                name="country"
                                required
                                placeholder="País"
                            />
                            {formErrors["address.country"] && (
                                <span className="input-error-message">
                                    {formErrors["address.country"]}
                                </span>
                            )}
                        </div>
                    </div>
                    <div className="new-patient__field">
                        <label htmlFor="senha">Senha*</label>
                        <input
                            id="senha"
                            name="senha"
                            type="password"
                            required
                            placeholder="Digite uma senha"
                        />
                        {formErrors.senha && (
                            <span className="input-error-message">
                                {formErrors.senha}
                            </span>
                        )}
                    </div>
                    <div className="new-patient__field">
                        <label htmlFor="confirmarSenha">Confirmar senha*</label>
                        <input
                            id="confirmarSenha"
                            name="confirmarSenha"
                            type="password"
                            required
                            placeholder="Confirme a senha"
                        />
                    </div>
                    <div className="new-patient__actions">
                        <button type="button" onClick={handleCloseModal}>
                            Cancelar
                        </button>
                        <button disabled={isCreatingPatient}>
                            {isCreatingPatient ? "Salvando..." : "Salvar"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
