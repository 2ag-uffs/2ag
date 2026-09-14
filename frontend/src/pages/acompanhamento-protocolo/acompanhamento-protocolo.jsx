import {useCallback, useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
import ModalConfirmacao from "../../components/modal/modal-confirmacao.jsx";
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
        apiService.get("/escalas/designaveis")
            .then(setScales)
            .catch(() => setScales([]));

        apiService.get("/pacientes/" + patientId + "/acompanhamento")
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
            await apiService.post("/pacientes/" + patientId + "/acompanhamento", {
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
            await apiService.put("/pacientes/" + patientId + "/acompanhamento/encerrar");
            setNotice("Acompanhamento encerrado. O sistema para de enviar escalas para este paciente.");
            load();
        } catch (requestError) {
            setFormError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
        }
    };

    if (isLoading) {
        return <p>Carregando o acompanhamento...</p>;
    }

    return (
        <section className={styles.page}>
            <header>
                <h1>Acompanhamento automático</h1>
                <p className={styles.subtitle}>
                    O sistema envia as escalas na frequência escolhida, sem ninguém precisar lembrar.
                </p>
            </header>

            {notice && <p className="aviso" role="status">{notice}</p>}
            {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}

            {protocol ? (
                <section className={styles.card}>
                    <h2 className={styles.cardTitle}>Em andamento</h2>
                    <p>
                        <strong>{protocol.patientName}</strong>, de {formatDate(protocol.startDate)} até
                        {" " + formatDate(protocol.endDate)}
                    </p>
                    {protocol.sleepBedTime && (
                        <p className={styles.subtitle}>
                            Programação do diário do sono: dormir às {protocol.sleepBedTime} e levantar
                            às {protocol.sleepWakeTime}
                        </p>
                    )}

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

                    <div className={styles.actions}>
                        <button type="button" className="button-secondary" onClick={() => setIsEndingOpen(true)}>
                            Encerrar acompanhamento
                        </button>
                        <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                            Voltar
                        </button>
                    </div>
                </section>
            ) : (
                <form className={styles.form} onSubmit={create}>
                    <div className={styles.fields}>
                        <label className={styles.field}>
                            <span>Começa em</span>
                            <input
                                type="date"
                                value={startDate}
                                required={true}
                                onChange={(event) => setStartDate(event.target.value)}
                            />
                        </label>
                        <label className={styles.field}>
                            <span>Duração em dias</span>
                            <input
                                type="number"
                                min="1"
                                value={durationDays}
                                required={true}
                                onChange={(event) => setDurationDays(event.target.value)}
                            />
                        </label>
                    </div>

                    <ul className={styles.list}>
                        {scales.map((scale) => (
                            <li key={scale.type} className={styles.item}>
                                <label className={styles.choice}>
                                    <input
                                        type="checkbox"
                                        checked={Boolean(chosen[scale.type])}
                                        onChange={() => toggleScale(scale.type)}
                                    />
                                    <span>
                                        <strong>{scale.name}</strong>
                                        <span className={styles.help}>{scale.description}</span>
                                    </span>
                                </label>
                                {chosen[scale.type] && (
                                    <select
                                        aria-label={"Frequência de " + scale.name}
                                        value={chosen[scale.type]}
                                        onChange={(event) => changePeriodicity(scale.type, event.target.value)}
                                    >
                                        {PERIODICITIES.map((option) => (
                                            <option key={option.value} value={option.value}>{option.label}</option>
                                        ))}
                                    </select>
                                )}
                            </li>
                        ))}
                    </ul>

                    {/* a programacao de horarios q aparece no topo do diario do sono (RF22) */}
                    {chosen.REGISTRO_SONO && (
                        <div className={styles.fields}>
                            <label className={styles.field}>
                                <span>Programação: ir dormir às</span>
                                <input
                                    type="time"
                                    value={sleepBedTime}
                                    onChange={(event) => setSleepBedTime(event.target.value)}
                                />
                            </label>
                            <label className={styles.field}>
                                <span>Levantar às</span>
                                <input
                                    type="time"
                                    value={sleepWakeTime}
                                    onChange={(event) => setSleepWakeTime(event.target.value)}
                                />
                            </label>
                        </div>
                    )}

                    <div className={styles.actions}>
                        <button type="submit" className="button" disabled={isSaving}>
                            {isSaving ? "Iniciando..." : "Iniciar acompanhamento"}
                        </button>
                        <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                            Cancelar
                        </button>
                    </div>
                </form>
            )}

            <ModalConfirmacao
                show={isEndingOpen}
                titulo="Encerrar acompanhamento"
                mensagem="O sistema para de enviar as escalas para este paciente. O acompanhamento fica guardado como encerrado."
                textoConfirmar="Sim, encerrar"
                textoCancelar="Manter ativo"
                onConfirmar={end}
                onCancelar={() => setIsEndingOpen(false)}
            />
        </section>
    );
}
