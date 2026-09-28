# rest-api

Not a bug demo: Products CRUD REST API (Spring Boot + JPA + Redis cache). Curl examples in `curls.md`; Bruno collection in `bruno/` (create ok, validation error, get-by-id not found).

## Stack
Spring Boot 4.1.1, Java 17, webmvc, validation, data-jpa, H2 (+h2console), cache + data-redis, actuator.

## Commands
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test` (ProductControllerValidationTest = @WebMvcTest w/ @MockitoBean; RestApiApplicationTests = full @SpringBootTest)

## Config
- H2 in-mem `jdbc:h2:mem:products`; ddl-auto=update; show-sql=true; console at /h2-console.
- Redis cache: spring.cache.type=redis, TTL 60000ms, host localhost:6379. Redis is external (no docker-compose here); ask before Docker.
- Port default 8080 (curls.md uses :8080). No env vars.

## Endpoints
`/products` (POST, GET), `/products/{id}` (GET, DELETE).

## Layout
`com.example.restapi`: `controller`, `service`, `repository`, `model` (Product), `exception` (ProductNotFoundException, GlobalExceptionHandler).

## Gotchas
- `.mcp.json` configures Atlassian MCP (mcp-remote); `.claude/skills` has jira-mcp-lookup, mysql-mcp-query.
- `pr-autocheck.md` = /loop prompt to watch PR CI (`gh`, `mvn clean test`).
