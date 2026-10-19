package com.exemplo.todo;

import static org.assertj.core.api.Assertions.assertThat;
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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
		// criar: campos controlados pelo sistema enviados no POST devem ser ignorados
		MvcResult criado = mockMvc.perform(post("/api/todos")
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

		mockMvc.perform(get("/api/todos/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Estudar Spring"));

		// toggle
		mockMvc.perform(patch("/api/todos/{id}/toggle", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(true));

		mockMvc.perform(get("/api/todos").param("completed", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + id + ")]", hasSize(1)));

		// atualizar sem "completed": o valor atual (true) deve ser mantido
		MvcResult atualizado = mockMvc.perform(put("/api/todos/{id}", id)
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
		mockMvc.perform(put("/api/todos/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"" + "t".repeat(101) + "\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/todos/{id}", id))
				.andExpect(jsonPath("$.title").value("Novo título"));

		// remover
		mockMvc.perform(delete("/api/todos/{id}", id))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/todos/{id}", id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.path").value("/api/todos/" + id));
	}

}
