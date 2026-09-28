package com.scout.backend.session;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bootstrap diagnostic only; feature endpoints will use the component contract. */
@RestController
public class SessionController {
    @GetMapping("/v1/session")
    public SessionResponse session(@AuthenticationPrincipal Jwt jwt) {
        return new SessionResponse(jwt.getSubject());
    }

    public record SessionResponse(String userId) {}
}
