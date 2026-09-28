# Scout backend

Java 21 + Spring Boot 4.1.1 mobile BFF. Supabase provides Auth and PostgreSQL;
Spring Boot runs on a separate Java/container host. No Edge Functions are needed.

```text
iOS → Supabase Auth (sign in / refresh)
iOS → Spring Boot (Bearer access token) → Supabase PostgreSQL
```

## What exists

- Maven wrapper and executable Spring Boot JAR.
- Public health, liveness and readiness endpoints with details hidden.
- `GET /v1/session`: validates a Supabase access token and returns `{"userId":"..."}`.
- Stateless ES256/RS256 verification through JWKS, including issuer, audience,
  expiry, authenticated role and UUID subject checks. Other routes are denied.
- Optional PostgreSQL profile, JDBC driver and small connection pool.
- CI builds the JAR and tests JWT handling plus a real PostgreSQL connection.

This is a foundation. Swipe/component endpoints, domain tables, migrations,
Storage integration and deployment are not implemented yet. `/v1/session` is a
bootstrap diagnostic, not the proposed component-response contract.

## Run locally

Install JDK 21. Maven is downloaded by the wrapper; no global Maven install is needed.

```sh
cp .env.example .env
# Edit .env with your project URL. Do not commit credentials.
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

Only source an environment file you wrote/trust. Spring does not load `.env`
automatically. The default profile has no database connection and makes no
schema changes. `SUPABASE_URL` is required; there is no mock-auth mode.

```sh
curl http://localhost:8080/actuator/health
# Expected: HTTP 200, status UP
curl http://localhost:8080/v1/session
# Expected: HTTP 401
curl -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" http://localhost:8080/v1/session
# Expected with a valid user access token: HTTP 200 and that user's ID
```

Use a real signed-in user's access token from your development client. A
publishable/anon key is not a user access token. Do not paste tokens into tickets
or logs. Signing out does not immediately revoke an already-issued JWT; offline
validation accepts it until expiry. Sensitive future actions need their own
account/status authorization checks.

## Connect Supabase

1. Create a development Supabase project. Copy the project URL into `SUPABASE_URL`.
2. Under Auth signing keys, use an asymmetric key (ES256 or RS256). Legacy HS256
   projects must migrate first. The backend reads public keys from
   `<project-url>/auth/v1/.well-known/jwks.json`; no signing secret or service-role
   key is needed. Use a freshly issued user token after changing keys.
3. Configure iOS Supabase Auth with the project URL and publishable key. Send its
   access token to this BFF; keep refresh-token handling in the auth client.
4. For database access, copy a **direct or session-pooler** connection from
   Supabase's Connect panel. Use port 5432, not the transaction pooler at 6543.
   Direct connections may require IPv6; the session pooler supports IPv4.
5. Enable `SPRING_PROFILES_ACTIVE=postgres`. Set `DATABASE_URL` as a JDBC URL,
   `DATABASE_USERNAME` and `DATABASE_PASSWORD` separately. Use certificate-verified
   TLS (`sslmode=verify-full` plus the downloaded Supabase root certificate), as
   shown in `.env.example`. Match the host and username to your connection mode;
   pooler usernames include the project reference.
6. Before the first persisted feature, create a restricted runtime DB role and
   an unexposed `app` schema through reviewed migrations. Use a separate migration
   owner. Grant the runtime role only the schema/table privileges it needs;
   do not deploy with the project owner/postgres account. This scaffold intentionally
   does not create roles, tables or alter existing Supabase data.
7. Restart and check `/actuator/health/readiness`. With the PostgreSQL profile,
   readiness requires a working database; liveness does not depend on the DB.

**Authorization:** a verified JWT does not set PostgreSQL `auth.uid()` on JDBC
connections. Java domain services/repositories must scope access to the verified
user and enforce visibility/blocks. Test cross-user reads and writes before
shipping features. RLS for direct Data API/Storage access is a separate policy
boundary. Keep the `app` schema out of exposed Data API schemas. Never place
DB passwords or service-role/secret keys in iOS.

## Tests and CI

```sh
./mvnw --batch-mode --no-transfer-progress verify
```

JWT tests use local signed tokens and a local JWKS server; they need no Supabase
account. The PostgreSQL test skips locally unless `TEST_POSTGRES=true` and the
three database variables are provided. CI supplies an isolated PostgreSQL 17
service and runs that test too. Use a disposable test database, never production.

Documentation-only changes skip Java; Java-only changes skip documentation.
See [.github/CI.md](.github/CI.md) for the required status and filter rules.

## Deploy later

Build with `./mvnw verify`, then run:

```sh
java -jar target/scout-backend-0.0.1-SNAPSHOT.jar
```

Choose a Java/container host with HTTPS, outbound access to Supabase and secret
management. Set the same environment variables there, including `PORT` if the
host requires it. Enable the PostgreSQL profile when deploying database features.
Use readiness for traffic routing and liveness for process restarts. No hosting
resources or Supabase project are provisioned by this repository.

Keep separate development/production projects. Add reviewed schema migrations,
a deployment pipeline and one complete authenticated feature before opening all
dependent feature tickets for implementation.

## References

- [Supabase with Spring Boot](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot)
- [Supabase signing keys](https://supabase.com/docs/guides/auth/signing-keys)
- [Spring Security JWT validation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Supabase database security](https://supabase.com/docs/guides/database/secure-data)
