import {useCallback, useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
import {FiCalendar, FiMoon} from "react-icons/fi";
import Card from "../../components/card/card.jsx";
import ChoiceCard from "../../components/choice-card/choice-card.jsx";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import FormSection, {FieldRow, FormActions} from "../../components/form-section/form-section.jsx";
import TextField from "../../components/form/text-field.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {formatDate, toIsoDate} from "../../utils/date-format.js";
import styles from "./acompanhamento-protocolo.module.css";

const CONNECTION_ERROR_MESSAGE = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

const PERIODICITIES = [
    {value: "SEMANAL", label: "Toda semana"},
    {value: "QUINZENAL", label: "A cada 15 dias"},
    {value: "MENSAL", label: "Uma vez por mês"},
];

// o acompanhamento automatico de 90 dias (RF32)
//
// a prescritora escolhe uma vez quais escalas e de quanto em quanto
// tempo, e o sistema envia sozinho dali em diante. antes ela designava
// formulario por formulario, paciente por paciente
export default function AcompanhamentoProtocolo() {
    const navigate = useNavigate();
    const {patientId} = useParams();

    const [protocol, setProtocol] = useState(null);
    const [scales, setScales] = useState([]);
    const [chosen, setChosen] = useState({});
    const [startDate, setStartDate] = useState(toIsoDate(new Date()));
    const [durationDays, setDurationDays] = useState(90);
    const [sleepBedTime, setSleepBedTime] = useState("");
    const [sleepWakeTime, setSleepWakeTime] = useState("");

    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [formError, setFormError] = useState(null);
    const [notice, setNotice] = useState(null);
    const [isEndingOpen, setIsEndingOpen] = useState(false);

    const load = useCallback(() => {
        setIsLoading(true);
        apiService.get("/scales/assignable")
            .then(setScales)
            .catch(() => setScales([]));

        apiService.get("/patients/" + patientId + "/treatment-protocol")
            .then(setProtocol)
            .catch((requestError) => {
                // 404 aqui so quer dizer que ainda n existe acompanhamento
                if (requestError instanceof ApiError && requestError.status === 404) {
                    setProtocol(null);
                } else {
                    setFormError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
                }
            })
            .finally(() => setIsLoading(false));
    }, [patientId]);

    useEffect(load, [load]);

    const toggleScale = (type) => {
        setChosen((current) => {
            const copy = {...current};
            if (copy[type]) {
                delete copy[type];
            } else {
                copy[type] = "SEMANAL";
            }
            return copy;
        });
    };

    const changePeriodicity = (type, periodicity) => {
        setChosen((current) => ({...current, [type]: periodicity}));
    };

    const create = async (event) => {
        event.preventDefault();
        setFormError(null);

        const items = Object.entries(chosen).map(([scaleType, periodicity]) => ({scaleType, periodicity}));
        if (items.length === 0) {
            setFormError("Escolha ao menos uma escala.");
            return;
        }

        setIsSaving(true);
        try {
            await apiService.post("/patients/" + patientId + "/treatment-protocol", {
                startDate,
                durationDays: Number(durationDays),
                sleepBedTime: sleepBedTime === "" ? null : sleepBedTime,
                sleepWakeTime: sleepWakeTime === "" ? null : sleepWakeTime,
                items,
            });
            setNotice("Acompanhamento iniciado. O sistema vai enviar as escalas sozinho.");
            load();
        } catch (requestError) {
            setFormError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
        } finally {
            setIsSaving(false);
        }
    };

    const end = async () => {
        setIsEndingOpen(false);
        setFormError(null);
        try {
            await apiService.put("/patients/" + patientId + "/treatment-protocol/end");
            setNotice("Acompanhamento encerrado. O sistema para de enviar escalas para este paciente.");
            load();
        } catch (requestError) {
            setFormError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
        }
    };

    if (isLoading) {
        return <SkeletonPage cards={2}/>;
    }

    return (
        <section className={styles.page}>
            <PageHeader
                title="Acompanhamento automático"
                subtitle="O sistema envia as escalas na frequência escolhida, sem ninguém precisar lembrar."
            />

            {notice && <p className="aviso" role="status">{notice}</p>}
            {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}

            {protocol ? (
                <Card
                    title="Em andamento"
                    headerAction={<span className={styles.status}>Ativo</span>}
                    footer={(
                        <>
                            <button
                                type="button"
                                className="button-danger button-small"
                                onClick={() => setIsEndingOpen(true)}
                            >
                                Encerrar acompanhamento
                            </button>
                            <button type="button" className="button-secondary button-small" onClick={() => navigate(-1)}>
                                Voltar
                            </button>
                        </>
                    )}
                >
                    <div className={styles.facts}>
                        <span className={styles.fact}>
                            <FiCalendar aria-hidden="true"/>
                            {protocol.patientName}: {formatDate(protocol.startDate)} até {formatDate(protocol.endDate)}
                        </span>
                        {protocol.sleepBedTime && (
                            <span className={styles.fact}>
                                <FiMoon aria-hidden="true"/>
                                Dormir às {protocol.sleepBedTime} e levantar às {protocol.sleepWakeTime}
                            </span>
                        )}
                    </div>

                    <div className={styles.tableWrap}>
                        <table className={styles.table}>
                            <thead>
                            <tr>
                                <th>Escala</th>
                                <th>Frequência</th>
                            </tr>
                            </thead>
                            <tbody>
                            {protocol.items.map((item) => (
                                <tr key={item.scaleType}>
                                    <td>{item.scaleName}</td>
                                    <td>
                                        {(PERIODICITIES.find((option) => option.value === item.periodicity) || {}).label}
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                </Card>
            ) : (
                <form className={styles.form} onSubmit={create}>
                    <FormSection title="Período" disabled={isSaving}>
                        <FieldRow>
                            <TextField
                                label="Começa em"
                                name="startDate"
                                type="date"
                                value={startDate}
                                required={true}
                                onChange={(event) => setStartDate(event.target.value)}
                            />
                            <TextField
                                label="Duração em dias"
                                name="durationDays"
                                type="number"
                                min="1"
                                value={durationDays}
                                required={true}
                                onChange={(event) => setDurationDays(event.target.value)}
                            />
                        </FieldRow>
                    </FormSection>

                    <FormSection
                        title="Escalas e frequência"
                        description="Marque as escalas e escolha de quanto em quanto tempo cada uma é enviada."
                        disabled={isSaving}
                    >
                        <ul className={styles.list}>
                            {scales.map((scale) => (
                                <li key={scale.type}>
                                    <ChoiceCard
                                        title={scale.name}
                                        description={scale.description}
                                        checked={Boolean(chosen[scale.type])}
                                        onChange={() => toggleScale(scale.type)}
                                    >
                                        {chosen[scale.type] && (
                                            <select
                                                className={styles.select}
                                                aria-label={"Frequência de " + scale.name}
                                                value={chosen[scale.type]}
                                                onChange={(event) => changePeriodicity(scale.type, event.target.value)}
                                            >
                                                {PERIODICITIES.map((option) => (
                                                    <option key={option.value} value={option.value}>{option.label}</option>
                                                ))}
                                            </select>
                                        )}
                                    </ChoiceCard>
                                </li>
                            ))}
                        </ul>
                    </FormSection>

                    {/* a programacao de horarios q aparece no topo do diario do sono (RF22) */}
                    {chosen.REGISTRO_SONO && (
                        <FormSection
                            title="Programação do diário do sono"
                            description="Aparece no topo do diário, para o paciente lembrar dos horários combinados."
                            disabled={isSaving}
                        >
                            <FieldRow>
                                <TextField
                                    label="Ir dormir às"
                                    name="sleepBedTime"
                                    type="time"
                                    value={sleepBedTime}
                                    onChange={(event) => setSleepBedTime(event.target.value)}
                                />
                                <TextField
                                    label="Levantar às"
                                    name="sleepWakeTime"
                                    type="time"
                                    value={sleepWakeTime}
                                    onChange={(event) => setSleepWakeTime(event.target.value)}
                                />
                            </FieldRow>
                        </FormSection>
                    )}

                    <FormActions>
                        <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                            Cancelar
                        </button>
                        <button type="submit" className="button" disabled={isSaving}>
                            {isSaving ? "Iniciando..." : "Iniciar acompanhamento"}
                        </button>
                    </FormActions>
                </form>
            )}

            <ConfirmModal
                show={isEndingOpen}
                title="Encerrar acompanhamento"
                message="O sistema para de enviar as escalas para este paciente. O acompanhamento fica guardado como encerrado."
                confirmText="Sim, encerrar"
                cancelText="Manter ativo"
                onConfirm={end}
                onCancel={() => setIsEndingOpen(false)}
            />
        </section>
    );
}
