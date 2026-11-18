import {useEffect, useState} from "react";
import {useBlocker, useNavigate, useParams} from "react-router";
import {FiAlertCircle, FiCheckCircle} from "react-icons/fi";
import Card from "../../components/card/card.jsx";
import ConfirmModal from "../../components/confirm-modal/confirm-modal.jsx";
import {FormActions} from "../../components/form-section/form-section.jsx";
import BusyButton from "../../components/form/busy-button.jsx";
import {useFocusFirstError} from "../../components/form/focus-first-error.js";
import {useFocusWhenShown} from "../../components/form/focus-when-shown.js";
import PageHeader from "../../components/page-header/page-header.jsx";
import ScaleForm from "../../components/scale-form/scale-form.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, errorMessageOf, getLoggedUser} from "../../services/api.js";
import {ARCHIVED_EXAM_NOTICE} from "../../utils/archived-patient.js";
import {formatDateTime, isInTheFuture} from "../../utils/date-format.js";
import {useLeaveWarning} from "../../utils/leave-warning.js";
import {answersPayload, missingItemsOf} from "../../utils/scale-answers.js";
import styles from "./mini-exame.module.css";

function ExamResult({exam, maxScore, resultRef}) {
    // exame salvo antes da trava q ficou sem escore ou sem faixa
    const isScoreMissing = exam.score === null || exam.score === undefined;
    if (isScoreMissing) {
        return (
            <div className={styles.result} role="status" tabIndex={-1} ref={resultRef}>
                <FiAlertCircle className={styles.resultIconWarning} aria-hidden="true"/>
                <div>
                    <p className={styles.score}>Exame incompleto: sem escore</p>
                    <p className={styles.hint}>
                        Para refazer, anule este exame no histórico do paciente e aplique de novo.
                    </p>
                </div>
            </div>
        );
    }
    return (
        <div className={styles.result} role="status" tabIndex={-1} ref={resultRef}>
            <FiCheckCircle className={styles.resultIcon} aria-hidden="true"/>
            <div>
                <p className={styles.score}>{exam.score} de {maxScore}</p>
                <p className={styles.hint}>
                    {exam.scoreBand || "Sem faixa: a escolaridade não foi registrada."}
                </p>
            </div>
        </div>
    );
}

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
    // so o exame salvo agora leva o foco pro resultado, o aplicado antes abre sem mexer no foco
    const [wasSavedNow, setWasSavedNow] = useState(false);
    const [formError, setFormError] = useState(null);
    const [missingKeys, setMissingKeys] = useState([]);
    const [isSaving, setIsSaving] = useState(false);
    // salvar com item em branco leva o foco pro primeiro q falta, como na escala do paciente
    const [formRef, focusFirstError] = useFocusFirstError();

    useEffect(() => {
        let isCurrentRequest = true;

        Promise.all([
            apiService.get("/scales/definitions/mini-exame"),
            apiService.get("/appointments/" + appointmentId),
            // exame ja aplicado nessa consulta, q vem vazio qnd ainda n tem nenhum
            apiService.get("/scales/mental-state-exam/appointments/" + appointmentId),
        ])
            .then(([loadedDefinition, loadedAppointment, appliedExam]) => {
                if (isCurrentRequest) {
                    setDefinition(loadedDefinition);
                    setAppointment(loadedAppointment);
                    // com exame aplicado a tela abre no resultado, senao o prescritor
                    // preenche tudo de novo pra so dai descobrir q n pode salvar
                    setSavedExam(appliedExam || null);
                    setLoadError(null);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(errorMessageOf(requestError,
                        "Não foi possível abrir o exame. Confira sua internet e tente de novo."));
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [appointmentId]);

    const changeAnswer = (key, value) => {
        setValues((currentValues) => ({...currentValues, [key]: value}));
        setMissingKeys((currentKeys) => currentKeys.filter((currentKey) => currentKey !== key));
    };

    // sair com alguma resposta marcada e n salva pergunta antes, como no registro da consulta
    // a resposta limpa volta a ficar em branco, e enquanto salva ou dps de salvo n pergunta
    const hasUnsavedChanges = !isSaving && savedExam === null
        && Object.values(values).some((value) => String(value).trim() !== "");
    const exitBlocker = useBlocker(({currentLocation, nextLocation}) => hasUnsavedChanges
        && getLoggedUser() !== null
        && currentLocation.pathname !== nextLocation.pathname);
    useLeaveWarning(hasUnsavedChanges);
    const resultRef = useFocusWhenShown(wasSavedNow);

    const save = async (event) => {
        event.preventDefault();
        setFormError(null);

        const missingItems = missingItemsOf(definition.items, values);
        if (missingItems.length > 0) {
            setMissingKeys(missingItems.map((item) => item.key));
            focusFirstError();
            return;
        }

        setIsSaving(true);
        try {
            const exam = await apiService.post("/scales/mental-state-exam/appointments/" + appointmentId, {
                answers: answersPayload(definition.items, values),
            });
            setSavedExam(exam);
            setWasSavedNow(true);
        } catch (requestError) {
            setFormError(errorMessageOf(requestError));
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

    const missingLabels = definition.items
        .filter((item) => missingKeys.includes(item.key))
        .map((item) => item.label);

    return (
        <section className={styles.page}>
            <PageHeader
                title={definition.title}
                subtitle={appointment.patientName + " · consulta de " + formatDateTime(appointment.dateTime)}
            />

            {savedExam ? (
                <Card>
                    <ExamResult exam={savedExam} maxScore={definition.maxScore} resultRef={resultRef}/>
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
            ) : appointment.patientArchived ? (
                // o exame ja aplicado continua abrindo no resultado, so o novo espera reativar
                // (decisao de 17/11/2026)
                <Card>
                    <p className="aviso">{ARCHIVED_EXAM_NOTICE}</p>
                    <div className={styles.nextSteps}>
                        <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                            Voltar
                        </button>
                    </div>
                </Card>
            ) : isInTheFuture(appointment.dateTime) ? (
                <Card>
                    <p className="aviso aviso--atencao">
                        Esta consulta ainda não aconteceu. O Mini-Exame é aplicado durante o atendimento.
                    </p>
                    <div className={styles.nextSteps}>
                        <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                            Voltar
                        </button>
                    </div>
                </Card>
            ) : (
                <form ref={formRef} className={styles.form} onSubmit={save}>
                    <p className={styles.hint}>{definition.instruction}</p>

                    <ScaleForm
                        items={definition.items}
                        bands={definition.bands}
                        values={values}
                        onChange={changeAnswer}
                        disabled={isSaving}
                        missingKeys={missingKeys}
                    />

                    {/* perto do botao pq no topo o aviso some num form comprido */}
                    {missingLabels.length > 0 && (
                        <p className="aviso aviso--atencao" role="alert">
                            Falta preencher: {missingLabels.join(", ")}.
                        </p>
                    )}
                    {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}

                    <FormActions>
                        <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                            Voltar
                        </button>
                        <BusyButton type="submit" className="button" isBusy={isSaving} busyText="Salvando...">
                            Salvar exame
                        </BusyButton>
                    </FormActions>
                </form>
            )}

            <ConfirmModal
                show={exitBlocker.state === "blocked"}
                title="Sair sem salvar"
                message="O que você preencheu ainda não foi salvo e vai se perder. Quer mesmo sair?"
                confirmText="Sim, sair"
                cancelText="Continuar preenchendo"
                onConfirm={() => exitBlocker.proceed()}
                onCancel={() => exitBlocker.reset()}
            />
        </section>
    );
}
