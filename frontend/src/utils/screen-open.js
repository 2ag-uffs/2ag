import {useEffect, useRef} from "react";

// diz se a tela ainda esta aberta qnd a resposta de um envio chega
// a funcao q esperava a api continua viva dps de a pessoa trocar de tela, e o navigate do fim dela vale pro app
// inteiro: a resposta atrasada tirava a pessoa da tela pra onde ela tinha ido. o envio em si n eh desfeito,
// so a troca de tela do fim dele deixa de acontecer
export function useScreenOpen() {
    const isOpenRef = useRef(true);

    useEffect(() => {
        isOpenRef.current = true;
        return () => {
            isOpenRef.current = false;
        };
    }, []);

    return isOpenRef;
}
