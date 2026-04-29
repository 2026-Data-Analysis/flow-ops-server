# Render + Neon deployment

This project is now configured to deploy on Render with Docker and PostgreSQL on Neon.

## What changed

- The app reads `PORT`, which Render provides automatically.
- The app exposes `/actuator/health` for Render health checks.
- The app accepts `JDBC_DATABASE_URL` for Neon.
- Flyway uses the main datasource by default and can be overridden with Spring Flyway env vars when needed.
- The Dockerfile now builds the JAR on Render with a multi-stage build.
- `render.yaml` is included for Blueprint deploys.

## Recommended Neon setup

Use a direct Neon JDBC URL as the first deployment default:

`jdbc:postgresql://.../neondb?sslmode=require`

Also set the Neon role credentials separately:

- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`

Why direct first:

- This service runs Flyway migrations on startup.
- Neon documents that pooled connections can be problematic for migrations and similar tools.

If you later want a pooled app connection:

- Set `JDBC_DATABASE_URL` to the pooled JDBC URL.
- Set `SPRING_FLYWAY_URL` to the direct JDBC URL.
- Set `SPRING_FLYWAY_USER` to the direct DB user.
- Set `SPRING_FLYWAY_PASSWORD` to the direct DB password.

## Required Render env vars

- `APP_ALLOWED_ORIGINS`
- `JDBC_DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`

## Optional Render env vars

- `SPRING_FLYWAY_URL`
- `SPRING_FLYWAY_USER`
- `SPRING_FLYWAY_PASSWORD`
- `DB_POOL_MAX_SIZE`
- `JASYPT_ENCRYPTOR_PASSWORD`
- `GITHUB_TOKEN`
- `GITHUB_MOCK_ENABLED`
- `AI_SERVER_BASE_URL`
- `AI_SERVER_API_KEY`

## Deploy

1. Push this repository.
2. In Render, create a new Blueprint and select this repository.
3. When prompted for secret values from `render.yaml`, enter:
   - `APP_ALLOWED_ORIGINS`
   - `JDBC_DATABASE_URL`
   - `DATABASE_USERNAME`
   - `DATABASE_PASSWORD`
4. Deploy.

## Notes

- `region` is set to `singapore` in `render.yaml`. Change it before first deploy if you want a different region that better matches your Neon database.
- For local Docker Compose, the existing `DB_*` variables still work.
- For IDE/local runs, use the `local` Spring profile and keep `src/main/resources/application-local.yml` untracked.
- The datasource first reads `JDBC_DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. If they are not set, it falls back to the encrypted `ENC(...)` values in `application.yml`.
- If the app falls back to encrypted `ENC(...)` values, set `JASYPT_ENCRYPTOR_PASSWORD` in Render and in your local environment.

## Jasypt

This project is configured to read encrypted properties through `jasypt-spring-boot`.

- Environment variable: `JASYPT_ENCRYPTOR_PASSWORD`
- Optional algorithm variables:
  - `JASYPT_ENCRYPTOR_ALGORITHM`
  - `JASYPT_ENCRYPTOR_IV_GENERATOR_CLASSNAME`
- Property format: `ENC(...)`

Example:

`spring.datasource.password: ENC(encrypted-value)`

Keep the Jasypt password out of Git. Store it only in local environment variables and Render environment variables.
