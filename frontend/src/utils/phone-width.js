import {useEffect, useEffectEvent, useState} from "react";

// a mesma quebra do celular do css (styles/tokens.css): ate 47.99rem, abaixo de 768 px
export const PHONE_QUERY = "(max-width: 47.99rem)";

// navegador sem matchMedia, como o jsdom dos testes, fica com a tela de computador
function phoneQuery() {
    return typeof window.matchMedia === "function" ? window.matchMedia(PHONE_QUERY) : null;
}

// a largura de agora, pra quem so precisa olhar uma vez, como a agenda q abre rolada ate hoje
export function isPhoneWidth() {
    const query = phoneQuery();
    return query ? query.matches : false;
}

// diz se a tela esta na largura do celular, e muda junto qnd a pessoa gira o aparelho ou estica a janela
// o onChange roda antes da tela se desenhar na largura nova, entao o q ele mudar ja vale nesse desenho
export function usePhoneWidth(onChange) {
    const [isPhone, setIsPhone] = useState(() => {
        const query = phoneQuery();
        return query ? query.matches : false;
    });
    const notifyChange = useEffectEvent((nowPhone) => {
        if (onChange) {
            onChange(nowPhone);
        }
    });

    useEffect(() => {
        const query = phoneQuery();
        if (!query) {
            return;
        }
        const update = () => {
            notifyChange(query.matches);
            setIsPhone(query.matches);
        };
        update();
        query.addEventListener("change", update);
        return () => query.removeEventListener("change", update);
    }, []);

    return isPhone;
}
