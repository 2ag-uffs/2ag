import {useEffect, useState} from "react";
import {useNavigate} from "react-router";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import "./perfil.css";

// serve pro paciente e pro prescritor. a tela chamava um profileService
// que n existia no projeto, entao quebrava na hora de montar, e os
// campos dela (cep, especialidade, contato de emergencia) n existem no
// modelo. agora ela bate nos endpoints que ja existem, um por papel
const ENDERECO_VAZIO = {street: "", number: "", city: "", state: "", country: ""};

export default function Perfil() {
    const navigate = useNavigate();
    const usuarioLogado = getLoggedUser();
    const ehPaciente = usuarioLogado?.role === "PATIENT";
    const recurso = ehPaciente ? "paciente" : "prescritor";

    const [perfil, setPerfil] = useState(null);
    const [carregando, setCarregando] = useState(true);
    const [erro, setErro] = useState(null);
    const [editando, setEditando] = useState(false);
    const [salvando, setSalvando] = useState(false);
    const [errosDeCampo, setErrosDeCampo] = useState({});
    const [aviso, setAviso] = useState(null);

    const [modalSenha, setModalSenha] = useState(false);
    const [senhas, setSenhas] = useState({atual: "", nova: "", confirmacao: ""});
    const [erroSenha, setErroSenha] = useState("");
    const [trocandoSenha, setTrocandoSenha] = useState(false);

    useEffect(() => {
        if (!usuarioLogado) {
            navigate("/login");
            return;
        }

        apiService
            .get(`/${recurso}/${usuarioLogado.id}`)
            .then((dados) => setPerfil({...dados, address: dados.address || ENDERECO_VAZIO}))
            .catch((err) => {
                setErro(err instanceof ApiError ? err.message : "Não foi possível carregar o perfil.");
            })
            .finally(() => setCarregando(false));
        // o id e o papel saem do token, que n muda enquanto a tela esta aberta
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const mudarCampo = (campo, valor) => {
        setPerfil((atual) => ({...atual, [campo]: valor}));
    };

    const mudarEndereco = (campo, valor) => {
        setPerfil((atual) => ({...atual, address: {...atual.address, [campo]: valor}}));
    };

    const salvar = async () => {
        setSalvando(true);
        setErro(null);
        setErrosDeCampo({});
        setAviso(null);

        // o endpoint de update n aceita id nem prescritor, entao vai so
        // o que o dto pede
        const dados = {
            name: perfil.name,
            email: perfil.email,
            cpf: perfil.cpf,
            birthDate: perfil.birthDate,
            phone: perfil.phone,
            address: perfil.address,
        };

        if (!ehPaciente) {
            dados.profession = perfil.profession;
            dados.registryType = perfil.registryType;
            dados.registryNumber = perfil.registryNumber;
        }

        try {
            const atualizado = await apiService.put(`/${recurso}/${usuarioLogado.id}`, dados);
            setPerfil({...atualizado, address: atualizado.address || ENDERECO_VAZIO});
            setEditando(false);
            setAviso("Perfil atualizado.");
        } catch (err) {
            if (err instanceof ApiError) {
                const porCampo = err.fieldErrors();
                if (Object.keys(porCampo).length > 0) {
                    setErrosDeCampo(porCampo);
                } else {
                    setErro(err.message);
                }
            } else {
                setErro("Não foi possível falar com o servidor.");
            }
        } finally {
            setSalvando(false);
        }
    };

    const fecharModalSenha = () => {
        setModalSenha(false);
        setErroSenha("");
        setSenhas({atual: "", nova: "", confirmacao: ""});
    };

    const trocarSenha = async () => {
        setErroSenha("");

        if (senhas.nova !== senhas.confirmacao) {
            setErroSenha("As senhas não coincidem.");
            return;
        }
        // o backend pede 8, entao a tela avisa antes de gastar uma ida
        if (senhas.nova.length < 8) {
            setErroSenha("A nova senha precisa ter pelo menos 8 caracteres.");
            return;
        }

        setTrocandoSenha(true);
        try {
            await apiService.put("/auth/senha", {senhaAtual: senhas.atual, novaSenha: senhas.nova});
            fecharModalSenha();
            setAviso("Senha alterada.");
        } catch (err) {
            setErroSenha(err instanceof ApiError ? err.message : "Não foi possível alterar a senha.");
        } finally {
            setTrocandoSenha(false);
        }
    };

    const voltar = () => {
        navigate(ehPaciente ? "/dashboard-paciente" : "/dashboard-prescritor");
    };

    if (carregando) {
        return (
            <div className="perfil-loading">
                <h1>Carregando perfil...</h1>
            </div>
        );
    }

    if (!perfil) {
        return (
            <div className="perfil-error">
                <h1>Erro ao carregar perfil</h1>
                <p>{erro}</p>
                <button onClick={voltar}>Voltar</button>
            </div>
        );
    }

    // repete em todo campo, entao virou funcao em vez de copia
    const campo = (rotulo, nome, valor, aoMudar, tipo = "text", bloqueado = false) => (
        <div className="input-group">
            <label htmlFor={nome}>{rotulo}</label>
            <input
                id={nome}
                type={tipo}
                value={valor || ""}
                onChange={(e) => aoMudar(e.target.value)}
                disabled={bloqueado || !editando}
                className={bloqueado ? "input-disabled" : ""}
            />
            {errosDeCampo[nome] && <span className="error-message">{errosDeCampo[nome]}</span>}
        </div>
    );

    const somenteLeitura = () => {
    };

    return (
        <div className="perfil-container">

            <main className="perfil-main">
                <div className="perfil-header">
                    <h1>Meu perfil</h1>
                    <div className="perfil-actions">
                        {!editando ? (
                            <>
                                <button className="button-secondary" onClick={() => setModalSenha(true)}>
                                    Alterar senha
                                </button>
                                <button className="button" onClick={() => setEditando(true)}>
                                    Editar perfil
                                </button>
                            </>
                        ) : (
                            <>
                                <button
                                    className="button-secondary"
                                    onClick={() => setEditando(false)}
                                    disabled={salvando}
                                >
                                    Cancelar
                                </button>
                                <button className="button" onClick={salvar} disabled={salvando}>
                                    {salvando ? "Salvando..." : "Salvar"}
                                </button>
                            </>
                        )}
                    </div>
                </div>

                {aviso && <p className="aviso">{aviso}</p>}
                {erro && <p className="error-message">{erro}</p>}

                <div className="perfil-content">
                    <section className="perfil-section">
                        <h2>Informações pessoais</h2>
                        <div className="perfil-grid">
                            {campo("Nome completo", "name", perfil.name, (v) => mudarCampo("name", v))}
                            {campo("E-mail", "email", perfil.email, (v) => mudarCampo("email", v), "email")}
                            {campo("Telefone", "phone", perfil.phone, (v) => mudarCampo("phone", v), "tel")}
                            {campo("Data de nascimento", "birthDate", perfil.birthDate, (v) => mudarCampo("birthDate", v), "date")}
                            {campo("CPF", "cpf", perfil.cpf, somenteLeitura, "text", true)}
                        </div>
                    </section>

                    <section className="perfil-section">
                        <h2>Endereço</h2>
                        <div className="perfil-grid">
                            {campo("Logradouro", "address.street", perfil.address.street, (v) => mudarEndereco("street", v))}
                            {campo("Número", "address.number", perfil.address.number, (v) => mudarEndereco("number", v))}
                            {campo("Cidade", "address.city", perfil.address.city, (v) => mudarEndereco("city", v))}
                            {campo("Estado", "address.state", perfil.address.state, (v) => mudarEndereco("state", v))}
                            {campo("País", "address.country", perfil.address.country, (v) => mudarEndereco("country", v))}
                        </div>
                    </section>

                    {ehPaciente ? (
                        <section className="perfil-section">
                            <h2>Acompanhamento</h2>
                            <div className="perfil-grid">
                                {campo(
                                    "Prescritor",
                                    "prescriberName",
                                    perfil.prescriberName || "Sem prescritor vinculado",
                                    somenteLeitura,
                                    "text",
                                    true,
                                )}
                            </div>
                        </section>
                    ) : (
                        <section className="perfil-section">
                            <h2>Dados profissionais</h2>
                            <div className="perfil-grid">
                                {campo("Profissão", "profession", perfil.profession, (v) => mudarCampo("profession", v))}
                                {campo("Conselho", "registryType", perfil.registryType, (v) => mudarCampo("registryType", v))}
                                {campo("Número do registro", "registryNumber", perfil.registryNumber, (v) => mudarCampo("registryNumber", v))}
                            </div>
                        </section>
                    )}
                </div>
            </main>

            {modalSenha && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h2>Alterar senha</h2>
                        {erroSenha && <div className="error-message">{erroSenha}</div>}
                        <div className="modal-form">
                            <div className="input-group">
                                <label htmlFor="senhaAtual">Senha atual</label>
                                <input
                                    id="senhaAtual"
                                    type="password"
                                    value={senhas.atual}
                                    onChange={(e) => setSenhas((s) => ({...s, atual: e.target.value}))}
                                />
                            </div>
                            <div className="input-group">
                                <label htmlFor="novaSenha">Nova senha</label>
                                <input
                                    id="novaSenha"
                                    type="password"
                                    value={senhas.nova}
                                    onChange={(e) => setSenhas((s) => ({...s, nova: e.target.value}))}
                                />
                            </div>
                            <div className="input-group">
                                <label htmlFor="confirmacaoSenha">Confirmar nova senha</label>
                                <input
                                    id="confirmacaoSenha"
                                    type="password"
                                    value={senhas.confirmacao}
                                    onChange={(e) => setSenhas((s) => ({...s, confirmacao: e.target.value}))}
                                />
                            </div>
                        </div>
                        <div className="modal-actions">
                            <button className="button-secondary" onClick={fecharModalSenha} disabled={trocandoSenha}>
                                Cancelar
                            </button>
                            <button className="button" onClick={trocarSenha} disabled={trocandoSenha}>
                                {trocandoSenha ? "Alterando..." : "Alterar senha"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
