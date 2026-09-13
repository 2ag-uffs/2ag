package dev.uffs.doisag.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// o docker pergunta aqui se a api subiu e se o banco responde
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> checkHealth() {
        try {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (DataAccessException exception) {
            // banco fora do ar entao a api ainda n esta pronta
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
        }
    }
}
