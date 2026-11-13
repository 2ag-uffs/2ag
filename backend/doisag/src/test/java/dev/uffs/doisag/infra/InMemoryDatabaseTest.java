package dev.uffs.doisag.infra;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

// a api local e a demonstracao rodam com o h2 em memoria do perfil test
// o h2 2.4 n consegue mais avaliar as checagens (check) de uma tabela qnd fecha a conexao q criou ela, a da migracao,
// e recusa todo insert na tabela, entao toda gravacao na trilha virava 409. o pool trocava cada conexao a cada 30 min, entao a api parava
// de gravar meia hora dps de subir
@SpringBootTest
@ActiveProfiles("test")
class InMemoryDatabaseTest {

    @Autowired private DataSource dataSource;

    @Test
    void thePoolNeverClosesTheConnectionThatRanTheMigrations() {
        HikariDataSource pool = (HikariDataSource) dataSource;

        // zero eh conexao sem prazo
        assertThat(pool.getMaxLifetime()).isZero();
        // com menos conexoes paradas q o maximo o pool tbm fecha as q sobram
        assertThat(pool.getMinimumIdle()).isEqualTo(pool.getMaximumPoolSize());
    }
}
