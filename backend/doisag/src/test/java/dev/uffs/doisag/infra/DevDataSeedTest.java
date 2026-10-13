package dev.uffs.doisag.infra;

import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.service.PrescriberService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DevDataSeedTest {

    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final PrescriberRepository prescriberRepository = mock(PrescriberRepository.class);
    private final PatientRepository patientRepository = mock(PatientRepository.class);
    private final PrescriberService prescriberService = mock(PrescriberService.class);
    private final DevDemoData demoData = mock(DevDemoData.class);

    // SEED_DADOS_TESTE esquecido no servidor criaria um admin com a senha q esta no README
    @Test
    void seedDoesNothingWhenThePublicAddressIsHttps() {
        runSeedWith("https://2ag.exemplo.com");

        verifyNoInteractions(usersRepository, prescriberRepository, patientRepository, prescriberService, demoData);
    }

    // o filtro de origem aceita o endereco escrito assim, entao a trava tbm precisa aceitar
    @Test
    void uppercaseSchemeAndSpacesDoNotBypassTheLock() {
        runSeedWith("  HTTPS://2ag.exemplo.com");

        verifyNoInteractions(usersRepository, prescriberRepository, patientRepository, prescriberService, demoData);
    }

    // o compose no proprio computador roda em https://localhost e o seed continua valendo la
    @Test
    void httpsOnLocalhostIsNotARealServer() {
        assertThat(DevDataSeed.isRealServer("https://localhost")).isFalse();
        assertThat(DevDataSeed.isRealServer("https://localhost:8443/")).isFalse();
        assertThat(DevDataSeed.isRealServer("http://localhost:5173")).isFalse();
        assertThat(DevDataSeed.isRealServer("https://localhost.exemplo.com")).isTrue();
        assertThat(DevDataSeed.isRealServer("https://2ag.exemplo.com")).isTrue();
    }

    private void runSeedWith(String publicUrl) {
        DevDataSeed seed = new DevDataSeed(usersRepository, prescriberRepository, patientRepository,
                prescriberService, mock(PasswordEncoder.class), demoData, publicUrl);
        seed.run(null);
    }
}
