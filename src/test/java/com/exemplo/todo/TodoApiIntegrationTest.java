package com.exemplo.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static com.exemplo.todo.CsrfTestSupport.realCsrf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class TodoApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void deveExecutarFluxoCompletoDeUmaTarefa() throws Exception {
		MockHttpSession session = registrar("Ana");
		RequestPostProcessor csrf = realCsrf(mockMvc);

		// criar: campos controlados pelo sistema enviados no POST devem ser ignorados
		MvcResult criado = mockMvc.perform(post("/api/todos").session(session).with(csrf)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title": "Estudar Spring", "description": "Capítulo 3",
						 "id": 99, "completed": true,
						 "createdAt": "2000-01-01T00:00:00Z", "updatedAt": "2000-01-01T00:00:00Z"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(not(99)))
				.andExpect(jsonPath("$.completed").value(false))
				.andExpect(jsonPath("$.createdAt").value(not("2000-01-01T00:00:00Z")))
				.andExpect(jsonPath("$.updatedAt").value(not("2000-01-01T00:00:00Z")))
				.andReturn();

		JsonNode body = objectMapper.readTree(criado.getResponse().getContentAsString());
		long id = body.get("id").asLong();
		Instant createdAt = Instant.parse(body.get("createdAt").asText());
		Instant updatedAt = Instant.parse(body.get("updatedAt").asText());
		assertThat(criado.getResponse().getHeader("Location")).isEqualTo("/api/todos/" + id);

		mockMvc.perform(get("/api/todos/{id}", id).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Estudar Spring"));

		// toggle
		mockMvc.perform(patch("/api/todos/{id}/toggle", id).session(session).with(csrf))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(true));

		mockMvc.perform(get("/api/todos").param("completed", "true").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + id + ")]", hasSize(1)));

		// atualizar sem "completed": o valor atual (true) deve ser mantido
		MvcResult atualizado = mockMvc.perform(put("/api/todos/{id}", id).session(session).with(csrf)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title": "Novo título", "description": null}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Novo título"))
				.andExpect(jsonPath("$.description").isEmpty())
				.andExpect(jsonPath("$.completed").value(true))
				.andReturn();

		JsonNode bodyAtualizado = objectMapper.readTree(atualizado.getResponse().getContentAsString());
		assertThat(Instant.parse(bodyAtualizado.get("createdAt").asText())).isEqualTo(createdAt);
		assertThat(Instant.parse(bodyAtualizado.get("updatedAt").asText())).isAfterOrEqualTo(updatedAt);

		// título longo demais não altera a tarefa
		mockMvc.perform(put("/api/todos/{id}", id).session(session).with(csrf)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"" + "t".repeat(101) + "\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/todos/{id}", id).session(session))
				.andExpect(jsonPath("$.title").value("Novo título"));

		// remover
		mockMvc.perform(delete("/api/todos/{id}", id).session(session).with(csrf))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/todos/{id}", id).session(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.path").value("/api/todos/" + id));
	}

	@Test
	void deveSepararAsTarefasPorUsuario() throws Exception {
		MockHttpSession ana = registrar("Ana");
		MockHttpSession bruno = registrar("Bruno");
		RequestPostProcessor csrf = realCsrf(mockMvc);

		criar(ana, csrf, "Tarefa 1 de Ana");
		criar(ana, csrf, "Tarefa 2 de Ana");
		long deBruno = criar(bruno, csrf, "Tarefa de Bruno");

		mockMvc.perform(get("/api/todos").session(ana))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[*].title", containsInAnyOrder("Tarefa 1 de Ana", "Tarefa 2 de Ana")));

		// tarefa de outro usuário se comporta como inexistente, em todas as operações
		mockMvc.perform(get("/api/todos/{id}", deBruno).session(ana)).andExpect(status().isNotFound());
		mockMvc.perform(put("/api/todos/{id}", deBruno).session(ana).with(csrf)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"Invadida\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(patch("/api/todos/{id}/toggle", deBruno).session(ana).with(csrf))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/todos/{id}", deBruno).session(ana).with(csrf))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/todos/{id}", deBruno).session(bruno))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Tarefa de Bruno"))
				.andExpect(jsonPath("$.completed").value(false));
	}

	// cadastra um usuário novo e devolve a sessão já autenticada
	private MockHttpSession registrar(String nome) throws Exception {
		MockHttpSession session = new MockHttpSession();
		String email = nome.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.com";
		mockMvc.perform(post("/api/auth/register").session(session).with(realCsrf(mockMvc))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "email": "%s", "password": "segredo123"}
						""".formatted(nome, email)))
				.andExpect(status().isCreated());
		return session;
	}

	private long criar(MockHttpSession session, RequestPostProcessor csrf, String title) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/todos").session(session).with(csrf)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"" + title + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

}
