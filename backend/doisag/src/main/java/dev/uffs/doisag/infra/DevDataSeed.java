package dev.uffs.doisag.infra;

import dev.uffs.doisag.dto.PrescriberCreateDTO;
import dev.uffs.doisag.model.Address;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.service.PrescriberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;

// cria contas de teste pra quem esta desenvolvendo
// so roda com SEED_DADOS_TESTE ligado e nunca pode rodar em producao
@Component
@ConditionalOnProperty(name = "api.seed.enabled", havingValue = "true")
public class DevDataSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeed.class);

    // a mesma senha pra todas as contas de teste e ela ja segue a regra de senha forte
    private static final String DEV_PASSWORD = "Senha@123";

    private final UsersRepository usersRepository;
    private final PrescriberRepository prescriberRepository;
    private final PatientRepository patientRepository;
    private final PrescriberService prescriberService;
    private final PasswordEncoder passwordEncoder;
    private final DevDemoData demoData;
    private final String publicUrl;

    public DevDataSeed(UsersRepository usersRepository, PrescriberRepository prescriberRepository,
                       PatientRepository patientRepository, PrescriberService prescriberService,
                       PasswordEncoder passwordEncoder, DevDemoData demoData,
                       @Value("${api.public-url}") String publicUrl) {
        this.usersRepository = usersRepository;
        this.prescriberRepository = prescriberRepository;
        this.patientRepository = patientRepository;
        this.prescriberService = prescriberService;
        this.passwordEncoder = passwordEncoder;
        this.demoData = demoData;
        this.publicUrl = publicUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        // endereco https eh servidor de verdade, e la n pode nascer conta com senha q esta no README
        if (publicUrl.trim().toLowerCase(Locale.ROOT).startsWith("https://")) {
            log.warn("SEED_DADOS_TESTE esta ligado num endereco https e o seed foi ignorado");
            return;
        }
        createAdminIfMissing();
        Prescriber prescriber = createPrescriberIfMissing();
        Patient patient = createPatientIfMissing(prescriber);
        // a demonstracao so entra junto com a paciente principal na primeira subida
        if (patient != null) {
            demoData.create(prescriber, patient, DEV_PASSWORD);
        }
    }

    private void createAdminIfMissing() {
        String email = "admin@email.com";
        if (usersRepository.findByEmail(email).isPresent()) {
            return;
        }

        Admin admin = new Admin();
        admin.setName("Administrador de Teste");
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(DEV_PASSWORD));
        usersRepository.save(admin);
        log.info("seed de desenvolvimento criou o administrador {}", email);
    }

    private Prescriber createPrescriberIfMissing() {
        String email = "prescritor@email.com";
        Prescriber existingPrescriber = prescriberRepository.findByEmail(email).orElse(null);
        if (existingPrescriber != null) {
            return existingPrescriber;
        }

        PrescriberCreateDTO prescriberData = new PrescriberCreateDTO(
                "Ana Lima",
                email,
                "11144477735",
                LocalDate.of(1985, 3, 20),
                "49999000111",
                null,
                "Biomédica",
                "CRBM",
                "12345"
        );
        Prescriber prescriber = prescriberService.create(prescriberData, DEV_PASSWORD);
        log.info("seed de desenvolvimento criou o prescritor {}", email);
        return prescriber;
    }

    // devolve a paciente criada agora ou null quando ela ja existia
    private Patient createPatientIfMissing(Prescriber prescriber) {
        String email = "paciente@email.com";
        if (usersRepository.findByEmail(email).isPresent()) {
            return null;
        }

        Patient patient = new Patient();
        patient.setName("Maria Souza");
        patient.setEmail(email);
        patient.setPassword(passwordEncoder.encode(DEV_PASSWORD));
        patient.setCpf("52998224725");
        patient.setBirthDate(LocalDate.of(1978, 4, 2));
        patient.setPhone("49995678901");
        patient.setAddress(new Address("Rua Nereu Ramos", "512", "Chapecó", "SC", "Brasil"));
        patient.setPrescriber(prescriber);
        Patient savedPatient = patientRepository.save(patient);
        log.info("seed de desenvolvimento criou o paciente {}", email);
        return savedPatient;
    }
}
