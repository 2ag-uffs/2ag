import {matchRoutes} from "react-router";
import AppLayout from "../components/app-layout/app-layout.jsx";
import PageLoader from "../components/page-loader/page-loader.jsx";
import RouteError from "../components/route-error/route-error.jsx";
import StatusPage from "../components/status-page/status-page.jsx";
import PageTitle from "./page-title.jsx";
import {
    redirectHome,
    redirectLoggedUserHome,
    redirectLoggedUserHomeUnlessInvite,
    redirectLoggedUserHomeUnlessToken,
    requireRole,
} from "./route-guards.js";

// cada tela vira um arquivo separado q so eh baixado quando a rota abre
// enquanto baixa a tela anterior continua na frente e a troca fica suave
function lazyPage(importPage) {
    return async () => {
        const pageModule = await importPage();
        return {Component: pageModule.default};
    };
}

// o handle de cada rota leva o nome fixo q vai pro titulo da aba ("Agenda · 2AG")
// o nome nunca sai de dado: a tela de uma escala eh so "Escala", sem o nome dela nem o do paciente
// o AppRouter (app-router.jsx) monta o router com esta tabela
export const routes = [
    {
        // a raiz so cuida do titulo da aba, entao ele vale tbm pras telas de fora do layout
        element: <PageTitle/>,
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
                handle: {title: "Entrar"},
                loader: redirectLoggedUserHome,
                lazy: lazyPage(() => import("../pages/login/login.jsx")),
            },
            {
                path: "/cadastro",
                handle: {title: "Criar conta"},
                loader: redirectLoggedUserHomeUnlessInvite,
                lazy: lazyPage(() => import("../pages/sign-up/sign-up.jsx")),
            },
            {
                path: "/esqueci-senha",
                handle: {title: "Recuperar senha"},
                loader: redirectLoggedUserHome,
                lazy: lazyPage(() => import("../pages/forgot-password/forgot-password.jsx")),
            },
            {
                path: "/redefinir-senha",
                handle: {title: "Criar senha nova"},
                loader: redirectLoggedUserHomeUnlessToken,
                lazy: lazyPage(() => import("../pages/reset-password/reset-password.jsx")),
            },
            {
                // sem o desvio de quem esta logado: a pessoa pode confirmar no mesmo navegador do perfil
                // a conta aberta pode ser outra, entao a tela sai dela pela api dps de confirmar
                path: "/confirmar-email",
                handle: {title: "Confirmar e-mail novo"},
                lazy: lazyPage(() => import("../pages/confirm-email/confirm-email.jsx")),
            },
            {
                // daqui pra baixo so entra quem esta logado e todas as telas usam o mesmo layout
                loader: requireRole(),
                element: <AppLayout/>,
                children: [
                    {
                        loader: requireRole("PATIENT"),
                        // erro numa tela fica dentro do layout e o menu continua
                        errorElement: <RouteError/>,
                        children: [
                            {
                                path: "/painel-paciente",
                                handle: {title: "Início"},
                                lazy: lazyPage(() => import("../pages/dashboard-paciente/dashboard-paciente.jsx")),
                            },
                            {
                                path: "/agendamento-consulta",
                                handle: {title: "Consultas"},
                                lazy: lazyPage(() => import("../pages/agendamento-consulta-paciente/agendamento-consulta-paciente.jsx")),
                            },
                            {
                                path: "/pacientes/:patientId/escalas",
                                handle: {title: "Escalas"},
                                lazy: lazyPage(() => import("../pages/escala-clinica-paciente/escala-clinica-paciente.jsx")),
                            },
                            {
                                path: "/impressao",
                                handle: {title: "Impressão do histórico"},
                                lazy: lazyPage(() => import("../pages/impressao/impressao.jsx")),
                            },
                            {
                                path: "/historico-paciente",
                                handle: {title: "Histórico"},
                                lazy: lazyPage(() => import("../pages/historico-clinico-paciente/historico-clinico-paciente.jsx")),
                            },
                            {
                                path: "/minhas-prescricoes",
                                handle: {title: "Prescrições"},
                                lazy: lazyPage(() => import("../pages/my-prescriptions/my-prescriptions.jsx")),
                            },
                            {
                                path: "/anamnese",
                                handle: {title: "Anamnese"},
                                lazy: lazyPage(() => import("../pages/anamnese/anamnese.jsx")),
                            },
                            {
                                path: "/escalas/:slug",
                                handle: {title: "Escala"},
                                lazy: lazyPage(() => import("../pages/escala/escala.jsx")),
                            },
                            {
                                path: "/progresso",
                                handle: {title: "Progresso"},
                                lazy: lazyPage(() => import("../pages/progresso/progresso.jsx")),
                            },
                        ],
                    },
                    {
                        loader: requireRole("PRESCRIBER"),
                        errorElement: <RouteError/>,
                        children: [
                            {
                                path: "/painel-prescritor",
                                handle: {title: "Início"},
                                lazy: lazyPage(() => import("../pages/dashboard-prescritor/dashboard-prescritor.jsx")),
                            },
                            {
                                path: "/lista-paciente",
                                handle: {title: "Pacientes"},
                                lazy: lazyPage(() => import("../pages/lista-paciente/lista-paciente.jsx")),
                            },
                            {
                                path: "/progresso-pacientes",
                                handle: {title: "Progresso"},
                                lazy: lazyPage(() => import("../pages/progresso/progresso.jsx")),
                            },
                            {
                                path: "/indicadores",
                                handle: {title: "Indicadores"},
                                lazy: lazyPage(() => import("../pages/indicadores/indicadores.jsx")),
                            },
                            {
                                path: "/paciente/:patientId/consulta/nova",
                                handle: {title: "Nova consulta"},
                                lazy: lazyPage(() => import("../pages/consultation-record/consultation-record.jsx")),
                            },
                            {
                                path: "/consulta/:appointmentId/registro",
                                handle: {title: "Registro da consulta"},
                                lazy: lazyPage(() => import("../pages/consultation-record/consultation-record.jsx")),
                            },
                            {
                                path: "/consulta/:appointmentId/prescricao",
                                handle: {title: "Prescrição"},
                                lazy: lazyPage(() => import("../pages/prescription-form/prescription-form.jsx")),
                            },
                            {
                                path: "/consulta/:appointmentId/mini-exame",
                                handle: {title: "Escala"},
                                lazy: lazyPage(() => import("../pages/mini-exame/mini-exame.jsx")),
                            },
                            {
                                path: "/agendamento-prescritor",
                                handle: {title: "Agenda"},
                                lazy: lazyPage(() => import("../pages/agendamento-consulta-prescritor/agendamento-consulta-prescritor.jsx")),
                            },
                            {
                                path: "/agenda/disponibilidade",
                                handle: {title: "Horários de atendimento"},
                                lazy: lazyPage(() => import("../pages/agenda-disponibilidade/agenda-disponibilidade.jsx")),
                            },
                            {
                                path: "/paciente/:patientId/selecao-escalas",
                                handle: {title: "Enviar escalas"},
                                lazy: lazyPage(() => import("../pages/selecao-escalas/selecao-escalas.jsx")),
                            },
                            {
                                path: "/paciente/:patientId/historico",
                                handle: {title: "Histórico"},
                                lazy: lazyPage(() => import("../pages/historico-clinico-prescritor/historico-clinico-prescritor.jsx")),
                            },
                            {
                                path: "/paciente/:patientId/impressao",
                                handle: {title: "Impressão do histórico"},
                                lazy: lazyPage(() => import("../pages/impressao/impressao.jsx")),
                            },
                            {
                                path: "/paciente/:patientId/progresso",
                                handle: {title: "Progresso"},
                                lazy: lazyPage(() => import("../pages/progresso/progresso.jsx")),
                            },
                            {
                                path: "/paciente/:patientId/acompanhamento",
                                handle: {title: "Acompanhamento automático"},
                                lazy: lazyPage(() => import("../pages/acompanhamento-protocolo/acompanhamento-protocolo.jsx")),
                            },
                            {
                                path: "/paciente/:patientId/auditoria",
                                handle: {title: "Histórico de acesso"},
                                lazy: lazyPage(() => import("../pages/patient-audit/patient-audit.jsx")),
                            },
                        ],
                    },
                    {
                        // a propria conta abre pra todo perfil e eh por ela q o admin troca a senha inicial
                        loader: requireRole("PATIENT", "PRESCRIBER", "ADMIN"),
                        errorElement: <RouteError/>,
                        children: [
                            {
                                path: "/perfil",
                                handle: {title: "Perfil"},
                                lazy: lazyPage(() => import("../pages/profile/profile.jsx")),
                            },
                        ],
                    },
                    {
                        loader: requireRole("PATIENT", "PRESCRIBER"),
                        errorElement: <RouteError/>,
                        children: [
                            {
                                path: "/notificacoes",
                                handle: {title: "Avisos"},
                                lazy: lazyPage(() => import("../pages/notificacoes/notificacoes.jsx")),
                            },
                            {
                                path: "/escalas/resposta/:responseId",
                                handle: {title: "Resposta de escala"},
                                lazy: lazyPage(() => import("../pages/resposta-escala/resposta-escala.jsx")),
                            },
                        ],
                    },
                    {
                        loader: requireRole("ADMIN"),
                        errorElement: <RouteError/>,
                        children: [
                            {
                                path: "/administracao",
                                handle: {title: "Prescritores"},
                                lazy: lazyPage(() => import("../pages/admin/admin-prescribers.jsx")),
                            },
                            {
                                path: "/administracao/pacientes",
                                handle: {title: "Pacientes"},
                                lazy: lazyPage(() => import("../pages/admin/admin-patients.jsx")),
                            },
                            {
                                path: "/administracao/auditoria",
                                handle: {title: "Auditoria"},
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
                        message="O endereço pode ter mudado ou não existe mais. Toque em Ir para o início."
                    />
                ),
            },
        ],
    },
];

// na primeira abertura o codigo da tela do endereco comeca a baixar junto com a pergunta de quem esta logado
// o navegador guarda o import, entao o lazy do router resolve na hora dps
// a falha aqui fica calada pq o router baixa de novo e mostra o erro dele
export function preloadRouteOf(pathname) {
    const matches = matchRoutes(routes, pathname) || [];
    matches.forEach((match) => {
        if (typeof match.route.lazy === "function") {
            match.route.lazy().catch(() => {});
        }
    });
}
