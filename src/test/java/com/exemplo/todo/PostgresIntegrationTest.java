package com.exemplo.todo;

import static com.exemplo.todo.CsrfTestSupport.realCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A aplicação contra um PostgreSQL de verdade, como em produção: as migrações do Flyway precisam rodar e o schema
 * resultante precisa bater com as entidades ({@code ddl-auto=validate}). Os outros testes usam H2.
 * <p>
 * Precisa de Docker. Fora do CI, sem Docker, o teste é pulado; no CI (variável {@code CI} definida) ele roda sempre,
 * para nunca ser pulado sem ninguém perceber.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisabledIf("semDockerForaDoCi")
class PostgresIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private Flyway flyway;

	static boolean semDockerForaDoCi() {
		return System.getenv("CI") == null && !DockerClientFactory.instance().isDockerAvailable();
	}

	@Test
	void deveAplicarTodasAsMigracoes() {
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
	}

	@Test
	void deveExecutarOFluxoDeUmaTarefaNoPostgres() throws Exception {
		MockHttpSession session = new MockHttpSession();
		RequestPostProcessor csrf = realCsrf(mockMvc);
		mockMvc.perform(post("/api/auth/register").session(session).with(csrf)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Ana", "email": "ana@exemplo.com", "password": "segredo123"}
						"""))
				.andExpect(status().isCreated());

		MvcResult criado = mockMvc.perform(post("/api/todos").session(session).with(csrf)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title": "Estudar Spring", "description": "Capítulo 3"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.version").value(0))
				.andReturn();
		JsonNode body = objectMapper.readTree(criado.getResponse().getContentAsString());
		long id = body.get("id").asLong();
		Instant createdAt = Instant.parse(body.get("createdAt").asText());

		mockMvc.perform(patch("/api/todos/{id}", id).session(session).with(csrf)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"completed": true, "description": null, "version": 0}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(true))
				.andExpect(jsonPath("$.description").isEmpty())
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(patch("/api/todos/{id}", id).session(session).with(csrf)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"completed": false, "version": 0}
						"""))
				.andExpect(status().isConflict());

		// a data gravada com fuso (timestamp with time zone) volta igual
		MvcResult lido = mockMvc.perform(get("/api/todos").param("completed", "true").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(id))
				.andReturn();
		JsonNode primeiro = objectMapper.readTree(lido.getResponse().getContentAsString()).get("content").get(0);
		assertThat(Instant.parse(primeiro.get("createdAt").asText())).isEqualTo(createdAt);

		mockMvc.perform(delete("/api/todos/{id}", id).session(session).with(csrf))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/todos/{id}", id).session(session))
				.andExpect(status().isNotFound());
	}

}
