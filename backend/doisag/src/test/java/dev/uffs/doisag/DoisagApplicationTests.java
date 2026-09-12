package dev.uffs.doisag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// sobe o contexto inteiro usando o perfil de teste, ou seja com h2 em
// memoria. se este teste passa, a aplicacao consegue inicializar sem
// nenhum banco instalado na maquina
@SpringBootTest
@ActiveProfiles("test")
class DoisagApplicationTests {

	@Test
	void contextLoads() {
	}

}
