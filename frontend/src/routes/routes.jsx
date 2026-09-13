import {createBrowserRouter, Navigate, RouterProvider} from "react-router";
import AcompanhamentoSemanalPaciente
    from "../pages/acompanhamento-semanal-paciente/acompanhamento-semanal-paciente.jsx";
import AcompanhamentoSemanalPrescritor
    from "../pages/acompanhamento-semanal-prescritor/acompanhamento-semanal-prescritor.jsx";
import ConsultaClinica from "../pages/consulta-clinica/consulta-clinica.jsx";
import DadosConsultorio from "../pages/dados-consultorio/dados-consultorio.jsx";
import DashboardPaciente from "../pages/dashboard-paciente/dashboard-paciente.jsx";
import DashboardPrescritor from "../pages/dashboard-prescritor/dashboard-prescritor.jsx";
import Login from "../pages/login/login.jsx";
import Prescricao from "../pages/prescricao/prescricao.jsx";
import SignUp from "../pages/sign-up/sign-up.jsx";
import DiarioSono from "../pages/diario-sono/diario-sono.jsx";
import MiniExame from "../pages/mini-exame/mini-exame-estado-mental.jsx"
import AgendamentoConsultaPaciente from "../pages/agendamento-consulta-paciente/agendamento-consulta-paciente.jsx";
import AgendamentoPrescritor from "../pages/agendamento-consulta-prescritor/agendamento-consulta-prescritor.jsx";
import EscalaPaciente from "../pages/escala-clinica-paciente/escala-clinica-paciente.jsx"
import SelecaoEscalas from "../pages/selecao-escalas/selecao-escalas.jsx";
import HistoricoPaciente from "../pages/historico-clinico-paciente/historico-clinico-paciente.jsx";
import HamAScale from "../pages/escala-hamilton/escala-hamilton.jsx";
import HistoricoClinicoPrescritor from "../pages/historico-clinico-prescritor/historico-clinico-prescritor.jsx";
import ListaPaciente from "../pages/lista-paciente/lista-paciente.jsx";
import Anamnese from "../pages/anamnese/anamnese.jsx";
import NotificacoesPrescritor from "../pages/notificacoes-prescritor/notificacoes-prescritor.jsx";
import NotificacoesPaciente from "../pages/notificacoes-paciente/notificacoes-paciente.jsx";
import Perfil from "../pages/perfil/perfil.jsx";
import Progresso from "../pages/progresso/progresso.jsx";
import DiarioDor from "../pages/diario-dor/diario-dor.jsx";
import DiarioTea from "../pages/diario-tea/diario-tea.jsx";
import EscalaPittsburgh from "../pages/escala-pittsburgh/escala-pittsburgh.jsx";


const router = createBrowserRouter([
    {
        path: "/login",
        element: <Login/>,
    },
    {
        path: "/", //direciona para o login
        element: <Navigate to="/login" replace/>,
    },
    {
        path: "/acompanhamento-prescritor",
        element: <AcompanhamentoSemanalPrescritor/>,
    },
    {
        path: "/acompanhamento-paciente",
        element: <AcompanhamentoSemanalPaciente/>,
    },
    {
        path: "/dados-consultorio",
        element: <DadosConsultorio/>,
    },
    {
        path: "/dashboard-paciente",
        element: <DashboardPaciente/>,
    },
    {
        path: "/dashboard-prescritor",
        element: <DashboardPrescritor/>,
    },
    {
        // a prescricao sai de dentro de uma consulta, entao precisa saber
        // de qual consulta ela veio
        path: "/consulta/:appointmentId/prescricao",
        element: <Prescricao/>,
    },
    {
        path: "/consulta",
        element: <ConsultaClinica/>,
    },
    {
        path: "/sign-up",
        element: <SignUp/>,
    },
    {
        path: "/diario-sono",
        element: <DiarioSono/>,
    },
    {
        path: "/mini-exame",
        element: <MiniExame/>
    },
    {
        path: "/agendamento-consulta",
        element: <AgendamentoConsultaPaciente/>
    },
    {
        path: "/agendamento-prescritor",
        element: <AgendamentoPrescritor/>
    },
    {
        // alterei para a rota esperar um id de paciente
        path: "/pacientes/:patientId/escalas",
        element: <EscalaPaciente/>
    },
    {
        path: "/paciente/:pacienteId/selecao-escalas",
        element: <SelecaoEscalas/>
    },
    {
        path: "/historico-paciente",
        element: <HistoricoPaciente/>
    },
    {
        path: "/escala-hamilton",
        element: <HamAScale/>
    },
    {
        path: "/paciente/:pacienteId/historico",
        element: <HistoricoClinicoPrescritor/>
    },
    {
        path: "/lista-paciente",
        element: <ListaPaciente/>
    },
    {
        path: "/anamnese",
        element: <Anamnese/>
    },
    {
        path: "/notificacoes-prescritor",
        element: <NotificacoesPrescritor/>
    },
    {
        path: "/notificacoes-paciente",
        element: <NotificacoesPaciente/>
    },
    {
        path: "/perfil",
        element: <Perfil/>
    },
    {
        // o paciente vendo o proprio progresso (RF27)
        path: "/progresso",
        element: <Progresso/>
    },
    {
        // o prescritor vendo o progresso de um paciente dele (RF28)
        path: "/paciente/:patientId/progresso",
        element: <Progresso/>
    },
    {
        // essas tres rotas existiam so como comentario, e o ScaleType do
        // backend ja apontava pra elas. designar uma dessas escalas
        // levava o paciente pra uma tela em branco
        path: "/escala-pittsburgh",
        element: <EscalaPittsburgh/>
    },
    {
        path: "/diario-dor",
        element: <DiarioDor/>
    },
    {
        path: "/diario-tea",
        element: <DiarioTea/>
    },
]);

export default function Routes() {
    return <RouterProvider router={router}/>;

}
