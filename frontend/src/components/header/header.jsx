import React, {useEffect, useState} from 'react'; // 1. importe o useState e o useEffect
import {useNavigate} from "react-router-dom";
import './header.css';

// função auxiliar pra decodificar o token
function parseJwt(token) {
    try {
        return JSON.parse(atob(token.split('.')[1]));
    } catch (e) {
        return null;
    }
}

export default function Header({showBackButton, backButtonText, onBackClick}) {
    const navigate = useNavigate();

    // estado para guardar o nome do usuário
    const [userName, setUserName] = useState("Usuário");

    // useEffect é usado para buscar o nome do usuário quando o componente é carregado
    useEffect(() => {
        // pega o token salvo no navegador
        const token = localStorage.getItem("authToken");
        if (token) {
            const decodedToken = parseJwt(token);
            // se o token tiver a claim name, a gente atualiza o estado
            if (decodedToken && decodedToken.name) {
                setUserName(decodedToken.name);
            }
        }
    }, []); // o array vazio garante que isso rode só uma vez

    const handleBack = () => {
        if (onBackClick) {
            onBackClick();
        } else {
            navigate(-1);
        }
    };

    return (
        <header className="dashboard-header">
            <img src="/images/logotipo-icon.svg" alt="Logo" className="logo"/>
            <div className="dashboard-header__user">
                {/* agora o nome exibido vem do nosso estado userName */}
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