package com.scout.backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "SUPABASE_URL=https://test-project.supabase.co")
@ActiveProfiles("postgres")
class PostgresConnectionTests {
    @Autowired JdbcTemplate jdbc;
    @Value("${local.server.port}") int port;

    @Test void connectsToPostgresAndIncludesDatabaseInReadiness() throws Exception {
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL");
        var request = HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/actuator/health/readiness")).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP").doesNotContain("components", "details");
    }
}
