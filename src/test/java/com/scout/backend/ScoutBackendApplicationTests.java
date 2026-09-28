package com.scout.backend;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScoutBackendApplicationTests {
    private static final String USER = "0cb40591-a91d-451f-a27f-076b4792b3dd";
    private static final String ISSUER = "https://test-project.supabase.co/auth/v1";
    private static final ECKey KEY;
    private static final HttpServer JWKS;
    static {
        try {
            KEY = new ECKeyGenerator(Curve.P_256).keyID("test-key").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            JWKS.start();
        } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }
    @AfterAll static void stopJwks() { JWKS.stop(0); }
    @Value("${local.server.port}") int port;
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private String token(String issuer, String audience, String subject, String role,
                         Instant expiration, ECKey key) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience)
            .subject(subject).claim("role", role).issueTime(new Date());
        if (expiration != null) claims.expirationTime(Date.from(expiration));
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType.JWT).keyID("test-key").build(), claims.build());
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }
    private String validToken() throws Exception {
        return token(ISSUER, "authenticated", USER, "authenticated", Instant.now().plusSeconds(300), KEY);
    }
    @Test void healthIsPublicAndDoesNotExposeDetails() throws Exception {
        var response = get("/actuator/health", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP").doesNotContain("components", "details");
    }
    @Test void sessionRequiresBearerToken() throws Exception {
        assertThat(get("/v1/session", null).statusCode()).isEqualTo(401);
        assertThat(get("/v1/session", "malformed").statusCode()).isEqualTo(401);
    }
    @Test void signedSupabaseTokenReturnsOnlyUserIdWithoutSessionCookie() throws Exception {
        var response = get("/v1/session", validToken());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"userId\":\"" + USER + "\"}");
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }
    @Test void rejectsWrongIssuerAudienceExpiredAndMissingExpiry() throws Exception {
        var future = Instant.now().plusSeconds(300);
        for (String token : new String[] {
            token("https://other.supabase.co/auth/v1", "authenticated", USER, "authenticated", future, KEY),
            token(ISSUER, "other", USER, "authenticated", future, KEY),
            token(ISSUER, "authenticated", USER, "authenticated", Instant.now().minusSeconds(300), KEY),
            token(ISSUER, "authenticated", USER, "authenticated", null, KEY)
        }) assertThat(get("/v1/session", token).statusCode()).isEqualTo(401);
    }
    @Test void rejectsServiceKeysAndMissingOrInvalidUser() throws Exception {
        for (String subject : new String[] {null, "not-a-user", USER}) {
            var token = token(ISSUER, "authenticated", subject,
                USER.equals(subject) ? "service_role" : "authenticated", Instant.now().plusSeconds(300), KEY);
            assertThat(get("/v1/session", token).statusCode()).isEqualTo(401);
        }
    }
    @Test void rejectsIncorrectSignature() throws Exception {
        var otherKey = new ECKeyGenerator(Curve.P_256).generate();
        assertThat(get("/v1/session", token(ISSUER, "authenticated", USER, "authenticated",
            Instant.now().plusSeconds(300), otherKey)).statusCode()).isEqualTo(401);
    }
    @Test void unrelatedRoutesAreDeniedEvenWithValidToken() throws Exception {
        assertThat(get("/actuator/env", validToken()).statusCode()).isEqualTo(403);
        assertThat(get("/v1/tabs/swipe", validToken()).statusCode()).isEqualTo(403);
    }
}
