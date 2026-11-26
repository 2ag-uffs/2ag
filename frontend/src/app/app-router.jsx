import {useState} from "react";
import {createBrowserRouter} from "react-router";
// no navegador o RouterProvider vem do react-router/dom como pede a versao 8
import {RouterProvider} from "react-router/dom";
import {routes} from "./router.jsx";

// o router so eh criado depois q a sessao carregou
// senao as regras de acesso rodariam achando q ninguem esta logado
// o useState guarda o mesmo router enquanto o app fica aberto
// fica num arquivo proprio pq o router.jsx tbm exporta a tabela e o preloadRouteOf, q n sao telas
export default function AppRouter() {
    const [router] = useState(() => createBrowserRouter(routes));
    return <RouterProvider router={router}/>;
}
