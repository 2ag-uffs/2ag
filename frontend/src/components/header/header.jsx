import {useNavigate} from "react-router";
import {getLoggedUser} from "../../services/api.js";
import "./header.css";

export default function Header({showBackButton, backButtonText, onBackClick}) {
    const navigate = useNavigate();
    const loggedUser = getLoggedUser();
    const userName = loggedUser ? loggedUser.name : "Usuário";

    const handleBack = () => {
        if (onBackClick) {
            onBackClick();
        } else {
            navigate(-1);
        }
    };

    return (
        <header className="dashboard-header">
            <img src="/images/logotipo-icon-claro.svg" alt="Logo" className="logo"/>
            <div className="dashboard-header__user">
                <span>{userName}</span>
                {showBackButton && (
                    <button className="button-secondary" onClick={handleBack}>
                        {backButtonText}
                    </button>
                )}
            </div>
        </header>
    );
}
