import {useEffect} from "react";
import {Outlet, useMatches} from "react-router";

const APP_NAME = "2AG";
const ERROR_PREFIX = "Erro: ";

// o titulo da aba eh sempre um nome fixo da tela, escrito na rota do router.jsx
// ele fica nas abas e no historico de um computador q pode ser de mais gente, entao nunca eh montado
// com dado: nada de nome de paciente, nome de escala, diagnostico ou data
let routeName = "";
// a tela de erro entra no lugar da rota, e o nome dela vale enquanto ela estiver na tela
let statusName = "";
let hasError = false;

function writeTitle() {
    const name = statusName || routeName;
    const title = name ? name + " · " + APP_NAME : APP_NAME;
    // a tela de erro ja diz o problema no proprio nome, entao ela n ganha o "Erro: "
    document.title = hasError && !statusName ? ERROR_PREFIX + title : title;
}

// campo q o envio recusado marcou, ou aviso geral de erro sem campo (falha de conexao, recusa da api)
// todo aviso de erro da tela leva role="alert". a nota neutra e o aviso de sucesso n levam, e n contam
// o da tela escondida pelo termo ou pelo login por cima tbm n conta
function findError() {
    const marked = Array.from(document.querySelectorAll("[aria-invalid='true'], [role='alert']"));
    return marked.some((element) => !element.closest("[hidden]"));
}

// raiz de todas as rotas, pra o titulo valer tbm nas telas de fora do layout (entrar, cadastro, senha)
// o "Erro: " na frente acompanha o campo marcado com erro e o aviso geral de erro q estao na tela: ele sai
// sozinho qnd o envio seguinte passa, qnd a pessoa troca de tela e qnd o modal do formulario fecha, pq o campo
// marcado e o aviso somem junto
export default function PageTitle() {
    const matches = useMatches();
    const lastMatch = matches[matches.length - 1];
    const name = lastMatch && lastMatch.handle && lastMatch.handle.title ? lastMatch.handle.title : "";

    useEffect(() => {
        routeName = name;
        writeTitle();
    }, [name]);

    useEffect(() => {
        const checkErrors = () => {
            hasError = findError();
            writeTitle();
        };
        const observer = new MutationObserver(checkErrors);
        observer.observe(document.body, {
            subtree: true,
            childList: true,
            attributes: true,
            attributeFilter: ["aria-invalid", "role", "hidden"],
        });
        checkErrors();
        return () => {
            observer.disconnect();
            hasError = false;
            writeTitle();
        };
    }, []);

    return <Outlet/>;
}

// titulo das telas de erro, de pagina q n existe e de sem conexao
// sair da tela devolve o nome da rota
export function StatusTitle({name}) {
    useEffect(() => {
        statusName = name;
        writeTitle();
        return () => {
            statusName = "";
            writeTitle();
        };
    }, [name]);

    return null;
}
