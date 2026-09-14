import {useState} from "react";
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
                path: "/entrar",
                loader: redirectLoggedUserHome,
                lazy: lazyPage(() => import("../pages/login/login.jsx")),
            },
            {
                path: "/cadastro",
                loader: redirectLoggedUserHome,
                lazy: lazyPage(() => import("../pages/sign-up/sign-up.jsx")),
            },
            {
                path: "/esqueci-senha",
                loader: redirectLoggedUserHome,
                lazy: lazyPage(() => import("../pages/forgot-password/forgot-password.jsx")),
            },
            {
                path: "/redefinir-senha",
                loader: redirectLoggedUserHome,
                lazy: lazyPage(() => import("../pages/reset-password/reset-password.jsx")),
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
                                path: "/painel-paciente",
                                lazy: lazyPage(() => import("../pages/dashboard-paciente/dashboard-paciente.jsx")),
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
                                path: "/impressao",
                                lazy: lazyPage(() => import("../pages/impressao/impressao.jsx")),
                            },
                            {
                                path: "/historico-paciente",
                                lazy: lazyPage(() => import("../pages/historico-clinico-paciente/historico-clinico-paciente.jsx")),
                            },
                            {
                                path: "/minhas-prescricoes",
                                lazy: lazyPage(() => import("../pages/my-prescriptions/my-prescriptions.jsx")),
                            },
                            {
                                path: "/anamnese",
                                lazy: lazyPage(() => import("../pages/anamnese/anamnese.jsx")),
                            },
                            {
                                path: "/escalas/:slug",
                                lazy: lazyPage(() => import("../pages/escala/escala.jsx")),
                            },
                            {
                                path: "/progresso",
                                lazy: lazyPage(() => import("../pages/progresso/progresso.jsx")),
                            },
                        ],
                    },
                    {
                        loader: requireRole("PRESCRIBER"),
                        children: [
                            {
                                path: "/painel-prescritor",
                                lazy: lazyPage(() => import("../pages/dashboard-prescritor/dashboard-prescritor.jsx")),
                            },
                            {
                                path: "/lista-paciente",
                                lazy: lazyPage(() => import("../pages/lista-paciente/lista-paciente.jsx")),
                            },
                            {
                                path: "/progresso-pacientes",
                                lazy: lazyPage(() => import("../pages/progresso/progresso.jsx")),
                            },
                            {
                                path: "/paciente/:patientId/consulta/nova",
                                lazy: lazyPage(() => import("../pages/consultation-record/consultation-record.jsx")),
                            },
                            {
                                path: "/consulta/:appointmentId/registro",
                                lazy: lazyPage(() => import("../pages/consultation-record/consultation-record.jsx")),
                            },
                            {
                                path: "/consulta/:appointmentId/prescricao",
                                lazy: lazyPage(() => import("../pages/prescription-form/prescription-form.jsx")),
                            },
                            {
                                path: "/consulta/:appointmentId/mini-exame",
                                lazy: lazyPage(() => import("../pages/mini-exame/mini-exame.jsx")),
                            },
                            {
                                path: "/agendamento-prescritor",
                                lazy: lazyPage(() => import("../pages/agendamento-consulta-prescritor/agendamento-consulta-prescritor.jsx")),
                            },
                            {
                                path: "/agenda/disponibilidade",
                                lazy: lazyPage(() => import("../pages/agenda-disponibilidade/agenda-disponibilidade.jsx")),
                            },
                            {
                                path: "/paciente/:patientId/selecao-escalas",
                                lazy: lazyPage(() => import("../pages/selecao-escalas/selecao-escalas.jsx")),
                            },
                            {
                                path: "/paciente/:patientId/historico",
                                lazy: lazyPage(() => import("../pages/historico-clinico-prescritor/historico-clinico-prescritor.jsx")),
                            },
                            {
                                path: "/paciente/:patientId/impressao",
                                lazy: lazyPage(() => import("../pages/impressao/impressao.jsx")),
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
                                path: "/paciente/:patientId/auditoria",
                                lazy: lazyPage(() => import("../pages/patient-audit/patient-audit.jsx")),
                            },
                        ],
                    },
                    {
                        loader: requireRole("PATIENT", "PRESCRIBER"),
                        children: [
                            {
                                path: "/perfil",
                                lazy: lazyPage(() => import("../pages/profile/profile.jsx")),
                            },
                            {
                                path: "/notificacoes",
                                lazy: lazyPage(() => import("../pages/notificacoes/notificacoes.jsx")),
                            },
                            {
                                path: "/escalas/resposta/:responseId",
                                lazy: lazyPage(() => import("../pages/resposta-escala/resposta-escala.jsx")),
                            },
                        ],
                    },
                    {
                        loader: requireRole("ADMIN"),
                        children: [
                            {
                                path: "/administracao",
                                lazy: lazyPage(() => import("../pages/admin/admin-prescribers.jsx")),
                            },
                            {
                                path: "/administracao/auditoria",
                                lazy: lazyPage(() => import("../pages/admin/admin-audit.jsx")),
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

// o router so eh criado depois q a sessao carregou
// senao as regras de acesso rodariam achando q ninguem esta logado
// o useState guarda o mesmo router enquanto o app fica aberto
export default function AppRouter() {
    const [router] = useState(() => createBrowserRouter(routes));
    return <RouterProvider router={router}/>;
}
