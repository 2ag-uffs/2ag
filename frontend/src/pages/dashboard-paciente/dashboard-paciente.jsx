import {useEffect, useState} from "react";
import {useLocation, useNavigate} from "react-router";
import AppointmentStatusBadge from "../../components/appointment-status-badge/appointment-status-badge.jsx";
import Card from "../../components/card/card.jsx";
import ItemList, {ListItem} from "../../components/item-list/item-list.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import {modalityLabelOf} from "../../utils/appointment-labels.js";
import {formatDate, formatDateTime} from "../../utils/date-format.js";
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
        apiService.get("/dashboard/patient/" + loggedUser.id)
            .then(setPanel)
            .catch((requestError) => {
                setLoadError(requestError instanceof ApiError
                    ? requestError.message
                    : "Não foi possível carregar o painel. Confira sua internet e tente de novo.");
            });
    }, [loggedUser, navigate]);

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!panel) {
        return <SkeletonPage cards={4}/>;
    }

    // o painel chama a pessoa pelo primeiro nome e pula titulo tipo dr.
    const firstName = loggedUser.name.split(" ").find((word) => !word.endsWith(".")) || loggedUser.name;

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
                        <button type="button" className="button" onClick={() => navigate("/agendamento-consulta")}>
                            Agendar consulta
                        </button>
                    </>
                )}
            />

            {notice && <p className="aviso" role="status">{notice}</p>}

            <div className={styles.grid}>
                <Card
                    title="Esperando resposta"
                    count={panel.pendingScales.length}
                    footer={(
                        <button
                            type="button"
                            className="button-secondary button-small"
                            onClick={() => navigate("/pacientes/" + loggedUser.id + "/escalas")}
                        >
                            Ver todas as avaliações
                        </button>
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
                </Card>

                <Card
                    title="Próximas consultas"
                    footer={(
                        <button
                            type="button"
                            className="button-secondary button-small"
                            onClick={() => navigate("/agendamento-consulta")}
                        >
                            Ver minhas consultas
                        </button>
                    )}
                >
                    {panel.upcomingAppointments.length === 0 ? (
                        <p className={styles.empty}>Nenhuma consulta marcada. Você pode pedir um horário.</p>
                    ) : (
                        <ItemList>
                            {panel.upcomingAppointments.map((appointment) => (
                                <ListItem
                                    key={appointment.appointmentId}
                                    title={formatDateTime(appointment.dateTime)}
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
                    footer={(
                        <button
                            type="button"
                            className="button-secondary button-small"
                            onClick={() => navigate("/minhas-prescricoes")}
                        >
                            Ver prescrições
                        </button>
                    )}
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
                    count={panel.latestNotifications.length}
                    footer={(
                        <button
                            type="button"
                            className="button-secondary button-small"
                            onClick={() => navigate("/notificacoes")}
                        >
                            Ver avisos
                        </button>
                    )}
                >
                    {panel.latestNotifications.length === 0 ? (
                        <p className={styles.empty}>Nenhum aviso novo.</p>
                    ) : (
                        <ItemList>
                            {panel.latestNotifications.map((notification) => (
                                <ListItem
                                    key={notification.id}
                                    title={notification.title}
                                    details={notification.message}
                                />
                            ))}
                        </ItemList>
                    )}
                </Card>
            </div>
        </section>
    );
}
