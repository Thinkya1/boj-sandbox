# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java/com/bin/sandbox`: core sandbox service; main entry `SandboxApplication`, templates, and language-specific sandboxes.
- Subpackages follow intent: `controller` (HTTP APIs), `manager` (dispatch), `config`, `constant`, `model`, `utils`.
- `src/main/resources`: `application.yml`, sample code under `testCode`, and policy-related files under `security` and `unsafe`.
- `src/test/java/com/bin/sandbox`: JUnit tests (currently `*Tests.java`).
- `docker/`: language-specific Docker image definitions (`java`, `python`, `javascript`, `gcc`).
- `tmpCode/` and `target/`: runtime/build outputs; do not commit.

## Build, Test, and Development Commands
- `mvn -q -DskipTests spring-boot:run`: run the service locally (default port `8099`, see `src/main/resources/application.yml`).
- `mvn test`: run unit tests (Spring Boot + JUnit 5).
- `mvn -DskipTests package`: build the runnable jar.
- `docker pull openjdk:8u342-jre-slim-buster`: pre-pull the base image if Docker pulls are flaky.

## Coding Style & Naming Conventions
- Java 8, Spring Boot 2.7.6; keep formatting consistent with existing files (4-space indentation).
- Packages stay lower-case; classes use UpperCamelCase; constants use `UPPER_SNAKE_CASE`.
- Place new sandbox implementations alongside existing templates under `com.bin.sandbox`; keep controllers thin and move logic to manager/utils.
- Use UTF-8 (no BOM) for all text files.

## Testing Guidelines
- Use JUnit 5 from `spring-boot-starter-test`; name tests `*Tests.java`.
- Add tests for new sandbox branches or manager dispatch changes.
- Run `mvn test` before pushing.

## Commit & Pull Request Guidelines
- Git history favors short, direct messages; many use Conventional Commit-style prefixes (`feat:`, `docs:`, `refactor:`), sometimes with a Chinese colon (`：`).
- Keep commits scoped and imperative (e.g., `feat: add python sandbox`).
- PRs should include: purpose, tests run, config changes (especially `application.yml`), and a `curl` example if API behavior changes.
- PR descriptions and review comments should be written in Chinese.

## Configuration & Runtime Notes
- Docker remote API defaults to `tcp://localhost:2375`; adjust in `application.yml` if needed.
- The sandbox writes to `tmpCode/` during execution and cleans up afterward; failures should still remove temp files.
