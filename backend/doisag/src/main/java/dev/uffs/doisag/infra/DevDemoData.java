package dev.uffs.doisag.infra;

import dev.uffs.doisag.dto.AnamnesisDTO;
import dev.uffs.doisag.dto.AppointmentRequestDTO;
import dev.uffs.doisag.dto.AppointmentScheduleDTO;
import dev.uffs.doisag.dto.AvailabilityDTO;
import dev.uffs.doisag.dto.AvailabilityPeriodDTO;
import dev.uffs.doisag.dto.ConsultationRecordDTO;
import dev.uffs.doisag.dto.DoseEscalationStepDTO;
import dev.uffs.doisag.dto.PrescriptionComponentDTO;
import dev.uffs.doisag.dto.PrescriptionCreateDTO;
import dev.uffs.doisag.dto.ProtocolItemDTO;
import dev.uffs.doisag.dto.ScaleResponseCreateDTO;
import dev.uffs.doisag.dto.ScaleResponseDTO;
import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.Cannabinoid;
import dev.uffs.doisag.enums.ConcentrationUnit;
import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.enums.Spectrum;
import dev.uffs.doisag.model.Address;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.service.AnamnesisService;
import dev.uffs.doisag.service.AppointmentService;
import dev.uffs.doisag.service.AvailabilityService;
import dev.uffs.doisag.service.ConsultationService;
import dev.uffs.doisag.service.PatientArchiveService;
import dev.uffs.doisag.service.PrescriptionService;
import dev.uffs.doisag.service.ScaleResponseService;
import dev.uffs.doisag.service.ScaleTaskService;
import dev.uffs.doisag.service.TreatmentProtocolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// dados ficticios pra desenvolver e pra gravar a demonstracao das telas
// tudo passa pelos servicos de verdade entao as regras do sistema valem aqui tbm
// nomes cpfs e historias sao inventados
@Component
@ConditionalOnProperty(name = "api.seed.enabled", havingValue = "true")
public class DevDemoData {

    private static final Logger log = LoggerFactory.getLogger(DevDemoData.class);

    // se esse paciente ja existe a demonstracao ja foi criada antes
    private static final String MARKER_EMAIL = "joao.almeida@email.com";

    private static final String[] HAMILTON_ITEMS = {
            "humorAnsioso", "tensao", "medos", "insonia", "intelectual", "humorDeprimido",
            "somatizacoesMotoras", "somatizacoesSensoriais", "sintomasCardiovasculares", "sintomasRespiratorios",
            "sintomasGastrointestinais", "sintomasGeniturinarios", "sintomasAutonomicos", "comportamentoNaEntrevista",
    };

    // o q a maria escreveu em alguns dias do diario
    private static final Map<Integer, String> MARIA_COMMENTS = Map.of(
            0, "Primeiro dia com o óleo. Um pouco de sonolência pela manhã.",
            9, "Dormi melhor e acordei só duas vezes.",
            23, "Dia corrido no trabalho, dormi tarde.",
            30, "Comecei o óleo novo hoje.",
            41, "Consegui caminhar sem dor forte.",
            55, "Semana boa, quase sem precisar da dipirona."
    );

    private final UsersRepository usersRepository;
    private final PatientRepository patientRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AvailabilityService availabilityService;
    private final ConsultationService consultationService;
    private final PrescriptionService prescriptionService;
    private final AnamnesisService anamnesisService;
    private final ScaleResponseService scaleResponseService;
    private final ScaleTaskService scaleTaskService;
    private final TreatmentProtocolService treatmentProtocolService;
    private final AppointmentService appointmentService;
    private final PatientArchiveService patientArchiveService;

    public DevDemoData(UsersRepository usersRepository, PatientRepository patientRepository,
                       NotificationRepository notificationRepository, PasswordEncoder passwordEncoder,
                       AvailabilityService availabilityService, ConsultationService consultationService,
                       PrescriptionService prescriptionService, AnamnesisService anamnesisService,
                       ScaleResponseService scaleResponseService, ScaleTaskService scaleTaskService,
                       TreatmentProtocolService treatmentProtocolService, AppointmentService appointmentService,
                       PatientArchiveService patientArchiveService) {
        this.usersRepository = usersRepository;
        this.patientRepository = patientRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.availabilityService = availabilityService;
        this.consultationService = consultationService;
        this.prescriptionService = prescriptionService;
        this.anamnesisService = anamnesisService;
        this.scaleResponseService = scaleResponseService;
        this.scaleTaskService = scaleTaskService;
        this.treatmentProtocolService = treatmentProtocolService;
        this.appointmentService = appointmentService;
        this.patientArchiveService = patientArchiveService;
    }

    public void create(Prescriber prescriber, Patient maria, String password) {
        if (usersRepository.findByEmail(MARKER_EMAIL).isPresent()) {
            return;
        }

        // um erro aqui n pode impedir a api de subir pq a demonstracao eh so um extra
        try {
            LocalDate today = LocalDate.now();
            LocalDateTime demoStart = LocalDateTime.now();
            createAvailability(prescriber);

            Patient joao = createPatient("João Pedro Almeida", MARKER_EMAIL, "391846205",
                    LocalDate.of(1990, 11, 20), "49991234567",
                    new Address("Rua das Palmeiras", "210", "Chapecó", "SC", "Brasil"), prescriber, password);
            Patient renata = createPatient("Renata Dias Carvalho", "renata.dias@email.com", "274153869",
                    LocalDate.of(1965, 2, 8), "49992345678",
                    new Address("Avenida Getúlio Vargas", "1450", "Chapecó", "SC", "Brasil"), prescriber, password);
            Patient paulo = createPatient("Paulo Nunes", "paulo.nunes@email.com", "815362947",
                    LocalDate.of(1983, 7, 30), "49993456789",
                    new Address("Rua Marechal Deodoro", "88", "Xaxim", "SC", "Brasil"), prescriber, password);
            Patient carla = createPatient("Carla Menezes", "carla.menezes@email.com", "603918274",
                    LocalDate.of(1972, 9, 14), "49994567890",
                    new Address("Rua Clevelândia", "35", "Chapecó", "SC", "Brasil"), prescriber, password);

            // primeiro o historico das semanas passadas
            createMariaPast(prescriber, maria, today);
            createJoaoPast(prescriber, joao, today);
            createRenataPast(prescriber, renata, today);
            createCarlaPast(prescriber, carla, today);

            // os avisos do historico nasceriam todos com a hora de agora entao saem
            deleteNotificationsSince(demoStart, List.of(prescriber, maria, joao, renata, paulo, carla));

            // depois o q aconteceu nos ultimos dias e fica como aviso n lido
            createMariaRecent(prescriber, maria, today);
            createJoaoRecent(prescriber, joao, today);
            createRenataRecent(prescriber, renata, today);
            // paciente novo so com a ficha de avaliacao inicial pra responder
            scaleTaskService.assign(paulo.getId(), ScaleType.ANAMNESE, today, 7);

            log.info("seed de desenvolvimento criou os dados de demonstracao");
        } catch (RuntimeException exception) {
            log.warn("seed de desenvolvimento n conseguiu criar os dados de demonstracao", exception);
        }
    }

    // segunda a sexta de manha e de tarde com consultas de meia hora
    private void createAvailability(Prescriber prescriber) {
        List<AvailabilityPeriodDTO> periods = new ArrayList<>();
        for (int dayOfWeek = 1; dayOfWeek <= 5; dayOfWeek++) {
            periods.add(new AvailabilityPeriodDTO(dayOfWeek, LocalTime.of(8, 0), LocalTime.of(12, 0)));
            periods.add(new AvailabilityPeriodDTO(dayOfWeek, LocalTime.of(13, 30), LocalTime.of(17, 30)));
        }
        availabilityService.replaceAvailability(prescriber.getId(), new AvailabilityDTO(30, periods));
    }

    // a paciente principal da demonstracao com dor lombar e sono ruim
    // oito semanas de diario com duas consultas e troca de oleo no meio
    private void createMariaPast(Prescriber prescriber, Patient maria, LocalDate today) {
        LocalDate firstDay = today.minusDays(56);

        anamnesisService.create(new AnamnesisDTO(
                firstDay.minusDays(3), "Professora", "Dor lombar crônica e dificuldade para dormir",
                "Hérnia de disco lombar diagnosticada em 2021", "Fisioterapia e anti-inflamatórios, com pouca melhora",
                "Dipirona quando a dor piora", "Mãe com hipertensão", null, null, "Onívora", "Não",
                "Socialmente", "68.5", "165", null, "Caminhada duas vezes por semana",
                "Acordo várias vezes durante a noite", "Ansiedade leve nos dias de mais dor",
                "Lombar, quase todos os dias, intensidade 7 de 10",
                "Dormir melhor e depender menos de remédio para dor", "Sim", null), maria);

        Appointment firstConsultation = registerConsultation(maria, prescriber, pastWorkingDay(firstDay).atTime(9, 0),
                "Dor lombar crônica há três anos, pior à noite, com despertares frequentes.",
                "Sem déficit neurológico. Dor à palpação paravertebral lombar.", null,
                "Lombalgia crônica com insônia secundária",
                "Iniciar óleo de CBD isolado com escalonamento semanal e reavaliar em quatro semanas.", "124/82");
        LocalDate secondConsultationDay = pastWorkingDay(today.minusDays(28));
        prescribe(firstConsultation, "Óleo de CBD isolado", Spectrum.ISOLADO,
                List.of(component(Cannabinoid.CBD, "3")), "3 gotas sublinguais à noite",
                List.of(new DoseEscalationStepDTO(1, "3 gotas à noite", null),
                        new DoseEscalationStepDTO(2, "4 gotas à noite", "se não houver sonolência pela manhã"),
                        new DoseEscalationStepDTO(3, "5 gotas à noite", null)),
                30, secondConsultationDay);

        Appointment secondConsultation = registerConsultation(maria, prescriber, secondConsultationDay.atTime(9, 30),
                null, null, "Melhora parcial da dor e do sono. Sonolência leve pela manhã só na primeira semana.",
                "Lombalgia crônica com insônia secundária",
                "Trocar para óleo full spectrum com THC em baixa concentração e manter o diário.", "120/80");
        prescribe(secondConsultation, "Óleo full spectrum", Spectrum.FULL_SPECTRUM,
                List.of(component(Cannabinoid.CBD, "3"), component(Cannabinoid.THC, "0.2")),
                "5 gotas sublinguais à noite", List.of(), 60, nextWorkingDay(today.plusDays(7)));

        // os dois ultimos dias ficam pro createMariaRecent
        for (int day = 0; day <= 53; day++) {
            if (day % 9 == 4 || day % 13 == 7) {
                continue; // dia q ela esqueceu de preencher
            }
            ScaleResponseDTO response = mariaFollowUpDay(maria, firstDay, day);
            // o prescritor ja olhou tudo q tem mais de duas semanas
            if (firstDay.plusDays(day).isBefore(today.minusDays(14))) {
                scaleResponseService.review(response.id(), prescriber);
            }
        }

        ScaleResponseDTO firstHamilton = hamilton(maria, firstDay, 3, 2, 1, 3, 2, 2, 2, 1, 1, 1, 2, 0, 2, 2);
        scaleResponseService.review(firstHamilton.id(), prescriber);
        hamilton(maria, today.minusDays(14), 1, 1, 0, 1, 1, 1, 1, 1, 0, 0, 1, 0, 1, 1);

        treatmentProtocolService.create(maria.getId(), new TreatmentProtocolCreateDTO(today.minusDays(30), 90,
                LocalTime.of(23, 0), LocalTime.of(7, 0), List.of(
                new ProtocolItemDTO(ScaleType.ACOMPANHAMENTO_SEMANAL, null, Periodicity.SEMANAL),
                new ProtocolItemDTO(ScaleType.REGISTRO_SONO, null, Periodicity.QUINZENAL))), prescriber);
    }

    private void createMariaRecent(Prescriber prescriber, Patient maria, LocalDate today) {
        // a semana atual do diario comecou anteontem e ja tem dois dias preenchidos
        scaleTaskService.assign(maria.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL, today.minusDays(2), 7);
        LocalDate firstDay = today.minusDays(56);
        mariaFollowUpDay(maria, firstDay, 54);
        mariaFollowUpDay(maria, firstDay, 55);

        scaleTaskService.assign(maria.getId(), ScaleType.REGISTRO_SONO, today, 7);
        scaleTaskService.assign(maria.getId(), ScaleType.ESCALA_PITTSBURGH, today, 7);

        appointmentService.schedule(new AppointmentScheduleDTO(maria.getId(),
                nextWorkingDay(today.plusDays(7)).atTime(9, 0), AppointmentModality.PRESENCIAL, null),
                prescriber.getId());
    }

    // um dia do diario da maria
    // a dor cai e o resto melhora aos poucos com um sobe e desce de um ponto
    private ScaleResponseDTO mariaFollowUpDay(Patient maria, LocalDate firstDay, int day) {
        int wobble = (day * 7) % 3 - 1;
        int drops = Math.min(3 + day / 7, 5);
        int pain = trend(8, 3, day, 55) + wobble;
        int sleep = trend(3, 7, day, 55) - wobble;
        int mood = trend(4, 7, day, 55) + day % 2;
        int calm = trend(4, 7, day, 55) - day % 2;
        int energy = trend(4, 7, day, 55);
        return followUpDay(maria, firstDay.plusDays(day), 0, drops, pain, sleep, mood, calm, energy,
                MARIA_COMMENTS.get(day));
    }

    // ansiedade q melhorou mas ainda aperta no fim da tarde
    private void createJoaoPast(Prescriber prescriber, Patient joao, LocalDate today) {
        LocalDate consultationDay = pastWorkingDay(today.minusDays(20));
        Appointment consultation = registerConsultation(joao, prescriber, consultationDay.atTime(10, 0),
                "Crises de ansiedade à tarde e dificuldade para desligar à noite.",
                "Taquicardia leve durante a consulta.", null, "Transtorno de ansiedade generalizada",
                "Iniciar CBD isolado em dose baixa e reavaliar em três semanas.", "130/85");
        prescribe(consultation, "Óleo de CBD isolado", Spectrum.ISOLADO, List.of(component(Cannabinoid.CBD, "2")),
                "2 gotas pela manhã e 2 gotas à tarde", List.of(), 60, null);

        ScaleResponseDTO firstHamilton = hamilton(joao, consultationDay, 3, 3, 2, 2, 2, 2, 2, 2, 2, 1, 2, 1, 2, 2);
        scaleResponseService.review(firstHamilton.id(), prescriber);

        LocalDate firstDay = today.minusDays(14);
        for (int day = 0; day <= 11; day++) {
            if (day % 6 == 4) {
                continue;
            }
            ScaleResponseDTO response = joaoFollowUpDay(joao, firstDay, day);
            if (firstDay.plusDays(day).isBefore(today.minusDays(7))) {
                scaleResponseService.review(response.id(), prescriber);
            }
        }
    }

    private void createJoaoRecent(Prescriber prescriber, Patient joao, LocalDate today) {
        LocalDate firstDay = today.minusDays(14);
        joaoFollowUpDay(joao, firstDay, 12);
        joaoFollowUpDay(joao, firstDay, 13);
        hamilton(joao, today.minusDays(3), 2, 2, 1, 2, 1, 1, 1, 1, 1, 1, 1, 0, 2, 2);

        appointmentService.request(joao.getId(), new AppointmentRequestDTO(
                nextWorkingDay(today.plusDays(3)).atTime(10, 0), AppointmentModality.REMOTA,
                "Queria revisar a dose, ainda fico ansioso no fim da tarde."));

        // consulta de hoje pro painel do prescritor se ainda der tempo no dia
        LocalDateTime now = LocalDateTime.now();
        if (now.getHour() < 20) {
            LocalDateTime nextHour = now.withMinute(0).withSecond(0).withNano(0).plusHours(1);
            appointmentService.schedule(new AppointmentScheduleDTO(joao.getId(), nextHour,
                    AppointmentModality.REMOTA, null), prescriber.getId());
        }
    }

    private ScaleResponseDTO joaoFollowUpDay(Patient joao, LocalDate firstDay, int day) {
        String comment = null;
        if (day == 0) {
            comment = "Sem efeito colateral até agora.";
        }
        if (day == 13) {
            comment = "Ainda fico ansioso no fim da tarde.";
        }
        return followUpDay(joao, firstDay.plusDays(day), 2, 2, 2, trend(5, 7, day, 13), trend(5, 7, day, 13),
                trend(3, 6, day, 13), trend(5, 6, day, 13), comment);
    }

    // dor neuropatica nos pes com uma escala q passou do prazo sem resposta
    private void createRenataPast(Prescriber prescriber, Patient renata, LocalDate today) {
        Appointment consultation = registerConsultation(renata, prescriber,
                pastWorkingDay(today.minusDays(40)).atTime(14, 0),
                "Dor em queimação nos pés, pior à noite.", "Sensibilidade diminuída nos dois pés.", null,
                "Neuropatia periférica dolorosa", "Iniciar óleo broad spectrum e acompanhar a dor pelo diário.",
                "136/88");
        prescribe(consultation, "Óleo broad spectrum", Spectrum.BROAD_SPECTRUM,
                List.of(component(Cannabinoid.CBD, "5"), component(Cannabinoid.CBG, "1")),
                "4 gotas sublinguais à noite", List.of(), 90, nextWorkingDay(today.plusDays(10)));

        // ela preenche dia sim dia n e os dois ultimos ficam pro createRenataRecent
        LocalDate firstDay = today.minusDays(21);
        for (int day = 0; day < 18; day = day + 2) {
            ScaleResponseDTO response = renataFollowUpDay(renata, firstDay, day);
            if (firstDay.plusDays(day).isBefore(today.minusDays(7))) {
                scaleResponseService.review(response.id(), prescriber);
            }
        }

        // enviada ha doze dias com uma semana de prazo entao ja venceu
        scaleTaskService.assign(renata.getId(), ScaleType.ESCALA_HAMILTON, today.minusDays(12), 7);
    }

    private void createRenataRecent(Prescriber prescriber, Patient renata, LocalDate today) {
        LocalDate firstDay = today.minusDays(21);
        renataFollowUpDay(renata, firstDay, 18);
        renataFollowUpDay(renata, firstDay, 20);

        LocalDateTime now = LocalDateTime.now();
        if (now.getHour() < 19) {
            LocalDateTime laterToday = now.withMinute(30).withSecond(0).withNano(0).plusHours(2);
            appointmentService.schedule(new AppointmentScheduleDTO(renata.getId(), laterToday,
                    AppointmentModality.PRESENCIAL, null), prescriber.getId());
        }
    }

    private ScaleResponseDTO renataFollowUpDay(Patient renata, LocalDate firstDay, int day) {
        String comment = null;
        if (day == 0) {
            comment = "Ainda acordo com os pés queimando.";
        }
        return followUpDay(renata, firstDay.plusDays(day), 0, 4, trend(7, 5, day, 20), trend(4, 6, day, 20),
                trend(5, 6, day, 20), trend(5, 6, day, 20), trend(4, 6, day, 20), comment);
    }

    // paciente q teve alta e ficou no arquivo com o prontuario guardado
    private void createCarlaPast(Prescriber prescriber, Patient carla, LocalDate today) {
        registerConsultation(carla, prescriber, pastWorkingDay(today.minusDays(90)).atTime(15, 0),
                "Insônia inicial havia seis meses, com dificuldade para pegar no sono.", null,
                "Sono regular depois do ajuste de rotina.", "Insônia inicial",
                "Alta do acompanhamento com orientação de higiene do sono.", "118/76");
        patientArchiveService.archive(carla.getId(), prescriber);
    }

    private Patient createPatient(String name, String email, String firstNineCpfDigits, LocalDate birthDate,
                                  String phone, Address address, Prescriber prescriber, String password) {
        Patient patient = new Patient();
        patient.setName(name);
        patient.setEmail(email);
        patient.setPassword(passwordEncoder.encode(password));
        patient.setCpf(validCpf(firstNineCpfDigits));
        patient.setBirthDate(birthDate);
        patient.setPhone(phone);
        patient.setAddress(address);
        patient.setPrescriber(prescriber);
        return patientRepository.save(patient);
    }

    private Appointment registerConsultation(Patient patient, Prescriber prescriber, LocalDateTime dateTime,
                                             String complaint, String exam, String evolution, String diagnosis,
                                             String plan, String bloodPressure) {
        ConsultationRecordDTO record = new ConsultationRecordDTO(dateTime, AppointmentModality.PRESENCIAL,
                complaint, exam, evolution, diagnosis, plan, null, bloodPressure, null, null);
        return consultationService.register(patient.getId(), record, prescriber);
    }

    private void prescribe(Appointment consultation, String product, Spectrum spectrum,
                           List<PrescriptionComponentDTO> components, String posology,
                           List<DoseEscalationStepDTO> steps, int durationDays, LocalDate nextConsultation) {
        PrescriptionCreateDTO prescription = new PrescriptionCreateDTO(product, "Associação Flor do Oeste",
                "L2026-" + consultation.getId(), spectrum, components, "30 ml", posology, "Sublingual", steps,
                "Agitar o frasco antes de usar. Pingar embaixo da língua e esperar um minuto antes de engolir.",
                "Evitar dirigir nas primeiras semanas se sentir sonolência.",
                "Melhora gradual dos sintomas ao longo das semanas.", null, durationDays, nextConsultation);
        prescriptionService.create(prescription, consultation.getId());
    }

    private PrescriptionComponentDTO component(Cannabinoid cannabinoid, String concentration) {
        return new PrescriptionComponentDTO(cannabinoid, new BigDecimal(concentration), ConcentrationUnit.PERCENTUAL);
    }

    // um dia do acompanhamento semanal q eh preenchido dia a dia
    // em sono humor ansiedade e disposicao quanto maior melhor e na dor quanto maior pior
    private ScaleResponseDTO followUpDay(Patient patient, LocalDate day, int morningDrops, int afternoonDrops,
                                         int pain, int sleep, int mood, int calm, int energy, String comment) {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("gotasManha", morningDrops);
        answers.put("gotasTarde", afternoonDrops);
        answers.put("dor", scoreLimit(pain));
        answers.put("sono", scoreLimit(sleep));
        answers.put("humor", scoreLimit(mood));
        answers.put("ansiedade", scoreLimit(calm));
        answers.put("disposicao", scoreLimit(energy));
        if (comment != null) {
            answers.put("comentario", comment);
        }
        return scaleResponseService.answer(patient.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL,
                new ScaleResponseCreateDTO(day, day, answers));
    }

    // os 14 itens da escala de hamilton na ordem do formulario
    private ScaleResponseDTO hamilton(Patient patient, LocalDate day, int... values) {
        Map<String, Object> answers = new LinkedHashMap<>();
        for (int index = 0; index < HAMILTON_ITEMS.length; index++) {
            answers.put(HAMILTON_ITEMS[index], values[index]);
        }
        return scaleResponseService.answer(patient.getId(), ScaleType.ESCALA_HAMILTON,
                new ScaleResponseCreateDTO(day, day, answers));
    }

    // os avisos criados desde o inicio da demonstracao pra essas contas
    private void deleteNotificationsSince(LocalDateTime start, List<Users> users) {
        for (Users user : users) {
            List<Notification> seedNotifications = new ArrayList<>();
            for (Notification notification : notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())) {
                if (!notification.getCreatedAt().isBefore(start)) {
                    seedNotifications.add(notification);
                }
            }
            notificationRepository.deleteAll(seedNotifications);
        }
    }

    // valor q vai de start ate end aos poucos conforme os dias passam
    private int trend(int start, int end, int day, int lastDay) {
        return Math.round(start + (end - start) * (float) day / lastDay);
    }

    // as notas da ficha vao de 0 a 10
    private int scoreLimit(int value) {
        return Math.max(0, Math.min(10, value));
    }

    // a clinica atende de segunda a sexta
    private boolean isWeekend(LocalDate day) {
        return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    // data no passado q cai no fim de semana volta pra sexta
    private LocalDate pastWorkingDay(LocalDate day) {
        LocalDate result = day;
        while (isWeekend(result)) {
            result = result.minusDays(1);
        }
        return result;
    }

    // data no futuro q cai no fim de semana vai pra segunda
    private LocalDate nextWorkingDay(LocalDate day) {
        LocalDate result = day;
        while (isWeekend(result)) {
            result = result.plusDays(1);
        }
        return result;
    }

    // monta um cpf valido a partir de 9 numeros pra passar na conferencia do cadastro
    private String validCpf(String firstNineDigits) {
        String digits = firstNineDigits;
        for (int round = 0; round < 2; round++) {
            int sum = 0;
            int weight = digits.length() + 1;
            for (int index = 0; index < digits.length(); index++) {
                sum = sum + Character.getNumericValue(digits.charAt(index)) * (weight - index);
            }
            int rest = (sum * 10) % 11;
            digits = digits + (rest == 10 ? 0 : rest);
        }
        return digits;
    }
}
