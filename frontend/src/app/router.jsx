import {createBrowserRouter, RouterProvider} from "react-router";
import AppLayout from "../components/app-layout/app-layout.jsx";
import PageLoader from "../components/page-loader/page-loader.jsx";
import StatusPage from "../components/status-page/status-page.jsx";
import {redirectHome, redirectLoggedUserHome, requireRole} from "./route-guards.js";

// cada tela vira um arquivo separado q so eh baixado quando a rota abre
// enquanto baixa a tela anterior continua na frente e a troca fica suave
function lazyPage(importPage) {
    return async () => {
        const pageModule = await importPage();
        return {Component: pageModule.default};
    };
}

const routes = [
    {
        hydrateFallbackElement: <PageLoader/>,
        errorElement: (
            <StatusPage
                title="Algo deu errado"
                message="Não foi possível abrir esta tela. Tente de novo em instantes."
                showReloadButton={true}
            />
        ),
        children: [
            {path: "/", loader: redirectHome},
            {
                path: "/login",
                loader: redirectLoggedUserHome,
                lazy: lazyPage(() => import("../pages/login/login.jsx")),
            },
            {
                path: "/cadastro",
                loader: redirectLoggedUserHome,
                lazy: lazyPage(() => import("../pages/sign-up/sign-up.jsx")),
            },
            {
                // daqui pra baixo so entra quem esta logado e todas as telas usam o mesmo layout
                loader: requireRole(),
                element: <AppLayout/>,
                children: [
                    {
                        loader: requireRole("PATIENT"),
                        children: [
                            {
                                path: "/dashboard-paciente",
                                lazy: lazyPage(() => import("../pages/dashboard-paciente/dashboard-paciente.jsx")),
                            },
                            {
                                path: "/acompanhamento-paciente",
                                lazy: lazyPage(() => import("../pages/acompanhamento-semanal-paciente/acompanhamento-semanal-paciente.jsx")),
                            },
                            {
                                path: "/agendamento-consulta",
                                lazy: lazyPage(() => import("../pages/agendamento-consulta-paciente/agendamento-consulta-paciente.jsx")),
                            },
                            {
                                path: "/pacientes/:patientId/escalas",
                                lazy: lazyPage(() => import("../pages/escala-clinica-paciente/escala-clinica-paciente.jsx")),
                            },
                            {
                                path: "/historico-paciente",
                                lazy: lazyPage(() => import("../pages/historico-clinico-paciente/historico-clinico-paciente.jsx")),
                            },
                            {
                                path: "/anamnese",
                                lazy: lazyPage(() => import("../pages/anamnese/anamnese.jsx")),
                            },
                            {
                                path: "/escala-hamilton",
                                lazy: lazyPage(() => import("../pages/escala-hamilton/escala-hamilton.jsx")),
                            },
                            {
                                path: "/escala-pittsburgh",
                                lazy: lazyPage(() => import("../pages/escala-pittsburgh/escala-pittsburgh.jsx")),
                            },
                            {
                                path: "/diario-sono",
                                lazy: lazyPage(() => import("../pages/diario-sono/diario-sono.jsx")),
                            },
                            {
                                path: "/diario-dor",
                                lazy: lazyPage(() => import("../pages/diario-dor/diario-dor.jsx")),
                            },
                            {
                                path: "/diario-tea",
                                lazy: lazyPage(() => import("../pages/diario-tea/diario-tea.jsx")),
                            },
                            {
                                path: "/progresso",
                                lazy: lazyPage(() => import("../pages/progresso/progresso.jsx")),
                            },
                            {
                                path: "/notificacoes-paciente",
                                lazy: lazyPage(() => import("../pages/notificacoes-paciente/notificacoes-paciente.jsx")),
                            },
                        ],
                    },
                    {
                        loader: requireRole("PRESCRIBER"),
                        children: [
                            {
                                path: "/dashboard-prescritor",
                                lazy: lazyPage(() => import("../pages/dashboard-prescritor/dashboard-prescritor.jsx")),
                            },
                            {
                                path: "/lista-paciente",
                                lazy: lazyPage(() => import("../pages/lista-paciente/lista-paciente.jsx")),
                            },
                            {
                                path: "/consulta",
                                lazy: lazyPage(() => import("../pages/consulta-clinica/consulta-clinica.jsx")),
                            },
                            {
                                path: "/consulta/:appointmentId/prescricao",
                                lazy: lazyPage(() => import("../pages/prescricao/prescricao.jsx")),
                            },
                            {
                                path: "/consulta/:appointmentId/mini-exame",
                                lazy: lazyPage(() => import("../pages/mini-exame/mini-exame-estado-mental.jsx")),
                            },
                            {
                                path: "/agendamento-prescritor",
                                lazy: lazyPage(() => import("../pages/agendamento-consulta-prescritor/agendamento-consulta-prescritor.jsx")),
                            },
                            {
                                path: "/paciente/:pacienteId/selecao-escalas",
                                lazy: lazyPage(() => import("../pages/selecao-escalas/selecao-escalas.jsx")),
                            },
                            {
                                path: "/paciente/:pacienteId/historico",
                                lazy: lazyPage(() => import("../pages/historico-clinico-prescritor/historico-clinico-prescritor.jsx")),
                            },
                            {
                                path: "/paciente/:patientId/progresso",
                                lazy: lazyPage(() => import("../pages/progresso/progresso.jsx")),
                            },
                            {
                                path: "/paciente/:patientId/acompanhamento",
                                lazy: lazyPage(() => import("../pages/acompanhamento-protocolo/acompanhamento-protocolo.jsx")),
                            },
                            {
                                path: "/notificacoes-prescritor",
                                lazy: lazyPage(() => import("../pages/notificacoes-prescritor/notificacoes-prescritor.jsx")),
                            },
                        ],
                    },
                    {
                        loader: requireRole("PATIENT", "PRESCRIBER"),
                        children: [
                            {
                                path: "/perfil",
                                lazy: lazyPage(() => import("../pages/perfil/perfil.jsx")),
                            },
                        ],
                    },
                    {
                        loader: requireRole("ADMIN"),
                        children: [
                            {
                                path: "/admin",
                                lazy: lazyPage(() => import("../pages/admin/admin-prescribers.jsx")),
                            },
                        ],
                    },
                ],
            },
            {
                path: "*",
                element: (
                    <StatusPage
                        title="Página não encontrada"
                        message="O endereço pode ter mudado ou não existe mais."
                    />
                ),
            },
        ],
    },
];

let router = null;

// o router so eh criado depois q a sessao carregou
// senao as regras de acesso rodariam achando q ninguem esta logado
export default function AppRouter() {
    if (router === null) {
        router = createBrowserRouter(routes);
    }
    return <RouterProvider router={router}/>;
}
