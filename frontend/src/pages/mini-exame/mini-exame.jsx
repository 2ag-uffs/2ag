import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
import {FiCheckCircle} from "react-icons/fi";
import Card from "../../components/card/card.jsx";
import {FormActions} from "../../components/form-section/form-section.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import ScaleForm from "../../components/scale-form/scale-form.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {formatDateTime} from "../../utils/date-format.js";
import {answersPayload} from "../../utils/scale-answers.js";
import styles from "./mini-exame.module.css";

const CONNECTION_ERROR_MESSAGE = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

// Mini-Exame do Estado Mental (RF26)
//
// eh o prescritor q aplica, durante a consulta, entao a tela nasce
// dentro da consulta e n na central de escalas do paciente (RN09)
export default function MiniExame() {
    const {appointmentId} = useParams();
    const navigate = useNavigate();

    const [definition, setDefinition] = useState(null);
    const [appointment, setAppointment] = useState(null);
    const [loadError, setLoadError] = useState(null);
    const [values, setValues] = useState({});
    const [savedExam, setSavedExam] = useState(null);
    const [formError, setFormError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        let isCurrentRequest = true;

        Promise.all([
            apiService.get("/scales/definitions/mini-exame"),
            apiService.get("/appointments/" + appointmentId),
        ])
            .then(([loadedDefinition, loadedAppointment]) => {
                if (isCurrentRequest) {
                    setDefinition(loadedDefinition);
                    setAppointment(loadedAppointment);
                    setLoadError(null);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível abrir o exame. Confira sua internet e tente de novo.");
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [appointmentId]);

    const changeAnswer = (key, value) => {
        setValues((currentValues) => ({...currentValues, [key]: value}));
    };

    const save = async (event) => {
        event.preventDefault();
        setFormError(null);
        setIsSaving(true);
        try {
            const exam = await apiService.post("/scales/mental-state-exam/appointments/" + appointmentId, {
                answers: answersPayload(definition.items, values),
            });
            setSavedExam(exam);
        } catch (requestError) {
            setFormError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
        } finally {
            setIsSaving(false);
        }
    };

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!definition || !appointment) {
        return <SkeletonPage cards={1}/>;
    }

    return (
        <section className={styles.page}>
            <PageHeader
                title={definition.title}
                subtitle={appointment.patientName + " · consulta de " + formatDateTime(appointment.dateTime)}
            />

            {savedExam ? (
                <Card>
                    <div className={styles.result}>
                        <FiCheckCircle className={styles.resultIcon} aria-hidden="true"/>
                        <div>
                            <p className={styles.score}>{savedExam.score} de {definition.maxScore}</p>
                            {savedExam.scoreBand && <p className={styles.hint}>{savedExam.scoreBand}</p>}
                        </div>
                    </div>
                    <p className={styles.hint}>O resultado fica no histórico do paciente junto com a consulta.</p>
                    <div className={styles.nextSteps}>
                        <button
                            type="button"
                            className="button"
                            onClick={() => navigate("/paciente/" + appointment.patientId + "/historico")}
                        >
                            Ver histórico do paciente
                        </button>
                    </div>
                </Card>
            ) : (
                <form className={styles.form} onSubmit={save}>
                    <p className={styles.hint}>{definition.instruction}</p>
                    {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}

                    <ScaleForm
                        items={definition.items}
                        values={values}
                        onChange={changeAnswer}
                        disabled={isSaving}
                    />

                    <FormActions>
                        <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                            Voltar
                        </button>
                        <button type="submit" className="button" disabled={isSaving}>
                            {isSaving ? "Salvando..." : "Salvar exame"}
                        </button>
                    </FormActions>
                </form>
            )}
        </section>
    );
}
