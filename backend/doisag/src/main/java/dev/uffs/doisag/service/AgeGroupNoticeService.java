package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.BrazilianFormat;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

// avisa a prescritora qnd o paciente passa de menor pra adulto ou volta (revisao das regras R9)
// a faixa de idade decide o responsavel no termo e a pesquisa, e quem mexe na data eh o proprio paciente,
// entao quem conhece a idade de verdade fica sabendo, pela data trocada no perfil ou pelo aniversario de 18 anos
@Service
public class AgeGroupNoticeService {

    private static final Logger log = LoggerFactory.getLogger(AgeGroupNoticeService.class);

    // o job olha quem fez 18 anos nesses ultimos dias, entao a api q ficou fora do ar n perde o aviso
    public static final int BIRTHDAY_WINDOW_DAYS = 7;

    public static final String AGE_GROUP_TITLE = "Faixa de idade mudou";
    public static final String BIRTHDAY_TITLE = "Paciente fez 18 anos";

    private final PatientRepository patientRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;

    public AgeGroupNoticeService(PatientRepository patientRepository, NotificationService notificationService,
                                 PlatformTransactionManager transactionManager) {
        this.patientRepository = patientRepository;
        this.notificationService = notificationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // a data trocada no perfil q cruza os 18 anos, pra um lado ou pro outro
    // quem chama ja travou o paciente e grava ele no fim
    public void noticeBirthDateChange(Patient patient, LocalDate oldBirthDate) {
        // conta sem data antes so existe em seed e teste, e ai n tinha faixa nenhuma
        if (oldBirthDate == null) {
            return;
        }
        boolean wasMinor = Patient.isMinor(oldBirthDate);
        if (wasMinor == patient.isMinor()) {
            return;
        }
        String change = wasMinor ? "passou de menor de 18 anos para adulto"
                : "passou de adulto para menor de 18 anos";
        sendToPrescriber(patient, AGE_GROUP_TITLE, patient.getDisplayName()
                + " trocou a data de nascimento no perfil de " + BrazilianFormat.date(oldBirthDate) + " para "
                + BrazilianFormat.date(patient.getBirthDate()) + " e " + change
                + ". Confira a idade na próxima consulta.");
        if (wasMinor) {
            patient.setAdultNoticeOn(LocalDate.now());
        }
    }

    // job do dia: quem fez 18 anos nos ultimos dias e ainda n teve o aviso
    public int sendEighteenthBirthdayNotices(LocalDate today) {
        LocalDate lastBirthDate = today.minusYears(Patient.ADULT_AGE);
        // um dia a mais na ponta pq qm nasceu em 29/02 faz 18 anos no 01/03
        List<Patient> candidates = patientRepository.findAllByBirthDateBetween(
                lastBirthDate.minusDays(BIRTHDAY_WINDOW_DAYS + 1), lastBirthDate)
                .stream()
                .filter(patient -> needsBirthdayNotice(patient, today))
                .toList();

        int sent = 0;
        int failures = 0;
        for (Patient found : candidates) {
            try {
                Boolean wasSent = transactionTemplate.execute(status -> noticeBirthday(found.getId(), today));
                if (Boolean.TRUE.equals(wasSent)) {
                    sent++;
                }
            } catch (RuntimeException exception) {
                failures++;
                log.error("Tarefas do dia: falha no aviso dos 18 anos do paciente {}", found.getId(), exception);
            }
        }
        if (failures > 0) {
            throw new IllegalStateException(failures + " aviso(s) dos 18 anos com falha, "
                    + sent + " enviados nos outros");
        }
        if (sent > 0) {
            log.info("Tarefas do dia: {} avisos de paciente que fez 18 anos", sent);
        }
        return sent;
    }

    // o paciente eh travado e lido de novo, entao a data trocada no perfil no msm instante aparece
    private boolean noticeBirthday(Long patientId, LocalDate today) {
        patientRepository.lockById(patientId);
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null || !needsBirthdayNotice(patient, today)) {
            return false;
        }
        sendToPrescriber(patient, BIRTHDAY_TITLE, patient.getDisplayName() + " fez 18 anos e a conta passou a ser "
                + "de adulto: o aceite do termo agora é do próprio paciente. Converse na consulta sobre o responsável "
                + "e o acesso à conta.");
        patient.setAdultNoticeOn(today);
        patientRepository.save(patient);
        return true;
    }

    // virou adulto nos ultimos dias e o aviso q ja saiu, se saiu, foi de antes, qnd pela data de hoje ainda era menor
    private boolean needsBirthdayNotice(Patient patient, LocalDate today) {
        LocalDate birthDate = patient.getBirthDate();
        if (birthDate == null || Patient.isMinor(birthDate, today)
                || !Patient.isMinor(birthDate, today.minusDays(BIRTHDAY_WINDOW_DAYS))) {
            return false;
        }
        LocalDate noticeDay = patient.getAdultNoticeOn();
        return noticeDay == null || Patient.isMinor(birthDate, noticeDay);
    }

    private void sendToPrescriber(Patient patient, String title, String message) {
        Prescriber prescriber = patient.getPrescriber();
        if (prescriber == null) {
            return;
        }
        notificationService.createNotification(prescriber, title, message, Notification.TYPE_ALERT,
                Notification.patientHistoryLink(patient.getId()));
    }
}
