package com.exemplo.todo;

import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// sessões no PostgreSQL, como em produção; as mesmas regras de Docker do PostgresIntegrationTest
@Testcontainers
@DisabledIf("com.exemplo.todo.PostgresIntegrationTest#semDockerForaDoCi")
class PostgresSessionPersistenceTest extends SessionPersistenceTestBase {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

}
