import {useState} from "react";
import {useNavigate} from "react-router";
import {apiService, ApiError} from "../../services/api.js";
import "./sign-up.css";

export default function SignUp() {
    const navigate = useNavigate();
    const [cpf, setCpf] = useState("");
    // estados para controlar o carregamento e os erros (resp: maiqueli)
    const [isLoading, setIsLoading] = useState(false);
    const [formErrors, setFormErrors] = useState({});

    // função para formatar o cpf
    const handleCpf = (e) => {
        let value = e.target.value.replace(/\D/g, "");
        value = value.replace(/(\d{3})(\d)/, "$1.$2");
        value = value.replace(/(\d{3})(\d)/, "$1.$2");
        value = value.replace(/(\d{3})(\d{1,2})$/, "$1-$2");
        setCpf(value);
    };


    // modifiquei função handleSubmit para integrar (resp: maiqueli)
    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsLoading(true);
        setFormErrors({}); // limpa os erros antigos antes de cada nova tentativa

        const form = e.target;
        const password = form.senha.value;
        const confirmPassword = form.confirmarSenha.value;

        // validação simples pra ver se as senhas batem
        if (password !== confirmPassword) {
            setFormErrors({confirmarSenha: "As senhas não conferem!"});
            setIsLoading(false);
            return;
        }

        // ajustei para a gente pegar os valores dos novos campos separados, igual deixei no banco e no back (maiqueli)
        const addressObject = {
            street: form.street.value,
            number: form.number.value,
            city: form.city.value,
            state: form.state.value,
            country: form.country.value
        };

        // so paciente se cadastra sozinho. conta de prescritor eh
        // criada pela clinica, n por autocadastro
        const dados = {
            name: form.nomeCompleto.value,
            email: form.email.value,
            senha: password,
            cpf: cpf.replace(/\D/g, ""),
            birthDate: form.dataNascimento.value,
            phone: form.telefone.value.replace(/\D/g, ""),
            address: addressObject,
            professionalCode: form.codigoProfissional.value
        };

        try {
            await apiService.post("/auth/register", dados);
            // o login mostra o aviso de cadastro feito, em vez de alert
            navigate("/login", {state: {cadastrado: true}});
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
            // o finally garante que o loading sempre será desativado
            setIsLoading(false);
        }
    };

    return (
        <div className="sign-up">
            <section className="form__content">
                <div className="form__content-wrapper">
                    <img
                        alt="Logotipo 2AG"
                        className="form__content__logo"
                        src="/images/logotipo-horizontal.svg"
                    />
                    <h2 className="form__content__title">Cadastro de usuário</h2>
                    <form className="form__content__form" onSubmit={handleSubmit}>
                        <p className="sign-up__note">
                            Este cadastro é para pacientes. Se você é prescritor, fale com a clínica para
                            que sua conta seja criada.
                        </p>

                        <>
                                <div className="form__content__form__input-group">
                                    <label htmlFor="nomeCompleto">Nome Completo *</label>
                                    <input id="nomeCompleto" name="nomeCompleto" type="text" required={true}
                                           placeholder="Digite seu nome completo"/>
                                    {formErrors.name && <span className="input-error-message">{formErrors.name}</span>}
                                </div>
                                <div className="form__content__form__input-group">
                                    <label htmlFor="cpf">CPF *</label>
                                    <input type="text" id="cpf" name="cpf" value={cpf} onChange={handleCpf}
                                           placeholder="000.000.000-00" maxLength="14"/>
                                    {formErrors.cpf && <span className="input-error-message">{formErrors.cpf}</span>}
                                </div>
                                <div className="form__content__form__input-group">
                                    <label htmlFor="email">E-mail *</label>
                                    <input id="email" name="email" type="email" required={true}
                                           placeholder="seu@email.com"/>
                                    {formErrors.email &&
                                        <span className="input-error-message">{formErrors.email}</span>}
                                </div>
                                <div className="form__content__form__input-group">
                                    <label htmlFor="dataNascimento">Data de Nascimento *</label>
                                    <input id="dataNascimento" name="dataNascimento" type="date" required={true}/>
                                    {formErrors.birthDate &&
                                        <span className="input-error-message">{formErrors.birthDate}</span>}
                                </div>
                                <div className="form__content__form__input-group">
                                    <label htmlFor="telefone">Telefone *</label>
                                    <input id="telefone" name="telefone" type="tel" required={true}
                                           placeholder="(00) 00000-0000"/>
                                    {formErrors.phone &&
                                        <span className="input-error-message">{formErrors.phone}</span>}
                                </div>
                                <fieldset className="form__fieldset">
                                    <legend>Endereço:</legend>
                                    <div className="form__content__form__input-group">
                                        <label htmlFor="street">Logradouro *</label>
                                        <input id="street" name="street" type="text" required={true}
                                               placeholder="Rua, Avenida, etc."/>
                                        {formErrors['address.street'] &&
                                            <span className="input-error-message">{formErrors['address.street']}</span>}
                                    </div>
                                    <div className="form__content__form__input-group">
                                        <label htmlFor="number">Número *</label>
                                        <input id="number" name="number" type="text" required={true}
                                               placeholder="Ex: 123"/>
                                        {formErrors['address.number'] &&
                                            <span className="input-error-message">{formErrors['address.number']}</span>}
                                    </div>
                                    <div className="form__content__form__input-group">
                                        <label htmlFor="city">Cidade *</label>
                                        <input id="city" name="city" type="text" required={true}
                                               placeholder="Ex: Chapecó"/>
                                        {formErrors['address.city'] &&
                                            <span className="input-error-message">{formErrors['address.city']}</span>}
                                    </div>
                                    <div className="form__content__form__input-group">
                                        <label htmlFor="state">Estado (UF) *</label>
                                        <input id="state" name="state" type="text" required={true} maxLength="2"
                                               placeholder="Ex: SC"/>
                                        {formErrors['address.state'] &&
                                            <span className="input-error-message">{formErrors['address.state']}</span>}
                                    </div>
                                    <div className="form__content__form__input-group">
                                        <label htmlFor="country">País *</label>
                                        <input id="country" name="country" type="text" required={true}
                                               placeholder="Ex: Brasil"/>
                                        {formErrors['address.country'] &&
                                            <span
                                                className="input-error-message">{formErrors['address.country']}</span>}
                                    </div>
                                </fieldset>
                                <div className="form__content__form__input-group">
                                    <label htmlFor="senha">Senha *</label>
                                    <input id="senha" name="senha" type="password" required={true}
                                           placeholder="Digite sua senha"/>
                                    {formErrors.senha &&
                                        <span className="input-error-message">{formErrors.senha}</span>}
                                </div>
                                <div className="form__content__form__input-group">
                                    <label htmlFor="confirmarSenha">Confirmar Senha *</label>
                                    <input id="confirmarSenha" name="confirmarSenha" type="password" required={true}
                                           placeholder="Confirme sua senha"/>
                                </div>

                                <div className="form__content__form__input-group">
                                        <label htmlFor="codigoProfissional">Código do Prescritor *</label>
                                        <p className="text-sm text-muted-foreground">
                                            Informe o código fornecido pelo seu prescritor:
                                        </p>
                                        <input id="codigoProfissional" name="codigoProfissional" type="text"
                                               required={true}
                                               placeholder="Ex: ABC01"/>
                                        {formErrors.professionalCode &&
                                            <span className="input-error-message">{formErrors.professionalCode}</span>}
                                </div>
                                {/* exibe a mensagem de erro, se houver (maiqueli) */}
                                {formErrors.general && <p className="sign-up__error-message">{formErrors.general}</p>}
                                <div className="form__content__form__actions">
                                    {/* desabilita o botão enquanto carrega (maiqueli) */}
                                    <button type="submit" disabled={isLoading}>
                                        {isLoading ? "Cadastrando..." : "Cadastrar"}
                                    </button>
                                </div>
                        </>
                    </form>
                </div>
            </section>
            <section className="sign__art">
                <img
                    alt="Logotipo 2AG"
                    className="sign__art__top-left"
                    src="/images/logotipo-icon-claro.svg"
                />
                <img
                    alt="Logotipo 2AG"
                    className="sign__art__top-right"
                    src="/images/logotipo-icon-claro.svg"
                />
                <img
                    alt="Logotipo 2AG"
                    className="sign__art__center"
                    src="/images/logotipo-vertical-claro.svg"
                />
                <img
                    alt="Logotipo 2AG"
                    className="sign__art__bottom-left"
                    src="/images/logotipo-icon-claro.svg"
                />
                <img
                    alt="Logotipo 2AG"
                    className="sign__art__bottom-right"
                    src="/images/logotipo-icon-claro.svg"
                />
            </section>
        </div>
    );
}