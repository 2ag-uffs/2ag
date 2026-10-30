import {useEffect, useState} from "react";
import {useLocation, useNavigate} from "react-router";
import AppointmentStatusBadge from "../../components/appointment-status-badge/appointment-status-badge.jsx";
import Card from "../../components/card/card.jsx";
import ItemList, {ListItem} from "../../components/item-list/item-list.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import ShownOfTotal from "../../components/shown-of-total/shown-of-total.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, errorMessageOf, getLoggedUser} from "../../services/api.js";
import {modalityLabelOf} from "../../utils/appointment-labels.js";
import {ARCHIVED_APPOINTMENT_NOTICE} from "../../utils/archived-patient.js";
import {formatDate, formatDateTimeWithZone, withBrasiliaTimeNote} from "../../utils/date-format.js";
import {firstNameOf} from "../../utils/initials.js";
import styles from "./dashboard-paciente.module.css";

// painel do paciente (RF03)
//
// mostra so o q o resto do sistema grava: as escalas esperando resposta,
// as proximas consultas, a prescricao q esta valendo e os avisos q ele
// ainda n leu. o q pede alguma acao da pessoa vem primeiro
export default function DashboardPaciente() {
    const navigate = useNavigate();
    const location = useLocation();
    const loggedUser = getLoggedUser();
    // quem acabou de salvar um formulario chega aqui com esse aviso
    const notice = location.state && location.state.notice;

    const [panel, setPanel] = useState(null);
    const [loadError, setLoadError] = useState(null);

    useEffect(() => {
        if (!loggedUser) {
            navigate("/entrar");
            return;
        }
        // o cadastro diz se o acompanhamento acabou, q o painel n traz
        Promise.all([
            apiService.get("/dashboard/patient/" + loggedUser.id),
            apiService.get("/patients/" + loggedUser.id),
        ])
            .then(([dashboard, patient]) => {
                setPanel({...dashboard, isArchived: patient.archived});
            })
            .catch((requestError) => {
                setLoadError(errorMessageOf(requestError,
                    "Não foi possível carregar o painel. Confira sua internet e tente de novo."));
            });
    }, [loggedUser, navigate]);

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!panel) {
        return <SkeletonPage cards={4}/>;
    }

    const firstName = firstNameOf(loggedUser.name);

    return (
        <section className={styles.page}>
            <PageHeader
                title={"Olá, " + firstName}
                subtitle="Acompanhe o seu tratamento e o que está esperando resposta."
                actions={(
                    <>
                        <button type="button" className="button-secondary" onClick={() => navigate("/historico-paciente")}>
                            Meu histórico
                        </button>
                        {/* a api recusa pedido de consulta de quem foi arquivado */}
                        {!panel.isArchived && (
                            <button type="button" className="button" onClick={() => navigate("/agendamento-consulta")}>
                                Agendar consulta
                            </button>
                        )}
                    </>
                )}
            />

            {notice && <p className="aviso" role="status">{notice}</p>}
            {panel.isArchived && <p className="aviso aviso--atencao" role="status">{ARCHIVED_APPOINTMENT_NOTICE}</p>}

            <div className={styles.grid}>
                {/* a lista vem com no maximo cinco, entao o numero eh o total q a api manda */}
                <Card
                    title="Esperando resposta"
                    count={panel.pendingScalesTotal}
                    footer={(
                        <FooterButton path={"/pacientes/" + loggedUser.id + "/escalas"}>
                            Ver todas as avaliações
                        </FooterButton>
                    )}
                >
                    {panel.pendingScales.length === 0 ? (
                        <p className={styles.empty}>Nenhuma escala esperando resposta agora.</p>
                    ) : (
                        <ItemList>
                            {panel.pendingScales.map((scale) => (
                                <ListItem
                                    key={scale.taskId}
                                    title={scale.name}
                                    details={(
                                        <span className={scale.late ? styles.late : undefined}>
                                            {scale.late
                                                ? "O prazo era " + formatDate(scale.deadline)
                                                : "Responda até " + formatDate(scale.deadline)}
                                        </span>
                                    )}
                                    aside={(
                                        <button type="button" className="button button-small" onClick={() => navigate(scale.path)}>
                                            Responder
                                        </button>
                                    )}
                                />
                            ))}
                        </ItemList>
                    )}
                    <ShownOfTotal shown={panel.pendingScales.length} total={panel.pendingScalesTotal}/>
                </Card>

                <Card
                    title="Próximas consultas"
                    footer={<FooterButton path="/agendamento-consulta">Ver minhas consultas</FooterButton>}
                >
                    {panel.upcomingAppointments.length === 0 ? (
                        <p className={styles.empty}>
                            {panel.isArchived ? "Nenhuma consulta marcada." : "Nenhuma consulta marcada. Você pode pedir um horário."}
                        </p>
                    ) : (
                        <ItemList>
                            {panel.upcomingAppointments.map((appointment) => (
                                <ListItem
                                    key={appointment.appointmentId}
                                    title={formatDateTimeWithZone(appointment.dateTime)}
                                    details={modalityLabelOf(appointment.modality)
                                        + (appointment.prescriberName ? " com " + appointment.prescriberName : "")}
                                    aside={<AppointmentStatusBadge appointment={appointment}/>}
                                />
                            ))}
                        </ItemList>
                    )}
                </Card>

                <Card
                    title="Prescrição de agora"
                    footer={<FooterButton path="/minhas-prescricoes">Ver prescrições</FooterButton>}
                >
                    {panel.currentPrescription === null ? (
                        <p className={styles.empty}>Nenhuma prescrição vigente.</p>
                    ) : (
                        <div className={styles.prescription}>
                            <strong>{panel.currentPrescription.productDescription}</strong>
                            <p className={styles.posology}>{panel.currentPrescription.posology}</p>
                            <p className={styles.meta}>
                                Prescrita em {formatDate(panel.currentPrescription.prescribedAt)}
                            </p>
                        </div>
                    )}
                </Card>

                <Card
                    title="Avisos não lidos"
                    count={panel.unreadNotifications}
                    footer={<FooterButton path="/notificacoes">Ver avisos</FooterButton>}
                >
                    {panel.latestNotifications.length === 0 ? (
                        <p className={styles.empty}>Nenhum aviso novo.</p>
                    ) : (
                        <ItemList>
                            {panel.latestNotifications.map((notification) => (
                                <ListItem
                                    key={notification.id}
                                    title={notification.title}
                                    details={withBrasiliaTimeNote(notification.message)}
                                />
                            ))}
                        </ItemList>
                    )}
                    <ShownOfTotal shown={panel.latestNotifications.length} total={panel.unreadNotifications}/>
                </Card>
            </div>
        </section>
    );
}

// o rodape de cada cartao leva pra tela com a lista inteira
function FooterButton({path, children}) {
    const navigate = useNavigate();

    return (
        <button type="button" className="button-secondary button-small" onClick={() => navigate(path)}>
            {children}
        </button>
    );
}
