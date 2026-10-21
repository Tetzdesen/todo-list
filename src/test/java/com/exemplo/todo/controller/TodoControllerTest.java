package com.exemplo.todo.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.exemplo.todo.dto.TodoCreateRequest;
import com.exemplo.todo.dto.TodoResponse;
import com.exemplo.todo.dto.TodoUpdateRequest;
import com.exemplo.todo.exception.TodoNotFoundException;
import com.exemplo.todo.model.User;
import com.exemplo.todo.security.AppUserDetailsService;
import com.exemplo.todo.security.AppUserPrincipal;
import com.exemplo.todo.security.GoogleOidcUserService;
import com.exemplo.todo.security.JsonSecurityErrorHandler;
import com.exemplo.todo.security.SecurityConfig;
import com.exemplo.todo.service.TodoService;

@WebMvcTest(TodoController.class)
@Import({ SecurityConfig.class, JsonSecurityErrorHandler.class })
class TodoControllerTest {

	private static final Long USER_ID = 7L;

	private static final Instant CRIADO_EM = Instant.parse("2026-01-01T10:00:00Z");
	private static final Instant ATUALIZADO_EM = Instant.parse("2026-01-01T11:00:00Z");

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@MockitoBean
	private TodoService service;

	@MockitoBean
	private AppUserDetailsService userDetailsService;

	@MockitoBean
	private GoogleOidcUserService googleOidcUserService;

	// todas as requisições saem autenticadas como o usuário de teste e com o token CSRF
	@BeforeEach
	void setUp() {
		User ana = new User("Ana", "ana@exemplo.com");
		ana.setId(USER_ID);
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.defaultRequest(get("/").with(user(AppUserPrincipal.from(ana))).with(csrf().asHeader()))
				.build();
	}

	@Test
	void deveResponder401SemUsuarioAutenticado() throws Exception {
		MockMvc semLogin = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		semLogin.perform(get("/api/todos"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));

		verify(service, never()).list(any(), any());
	}

	@Test
	void deveCriarTarefaComStatus201ELocation() throws Exception {
		when(service.create(USER_ID, new TodoCreateRequest("Estudar Spring", "Capítulo 3")))
				.thenReturn(todo(1L, "Estudar Spring", "Capítulo 3", false));

		mockMvc.perform(post("/api/todos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title": "Estudar Spring", "description": "Capítulo 3"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/todos/1"))
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.title").value("Estudar Spring"))
				.andExpect(jsonPath("$.description").value("Capítulo 3"))
				.andExpect(jsonPath("$.completed").value(false))
				.andExpect(jsonPath("$.createdAt").value("2026-01-01T10:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-01-01T11:00:00Z"));
	}

	@Test
	void deveCriarTarefaSemDescricao() throws Exception {
		when(service.create(USER_ID, new TodoCreateRequest("Comprar pão", null)))
				.thenReturn(todo(2L, "Comprar pão", null, false));

		mockMvc.perform(post("/api/todos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title": "Comprar pão"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.description").isEmpty());
	}

	@Test
	void deveRejeitarTituloEmBrancoComFormatoDeErroPadrao() throws Exception {
		mockMvc.perform(post("/api/todos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title": "   "}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").exists())
				.andExpect(jsonPath("$.path").value("/api/todos"))
				.andExpect(jsonPath("$.fieldErrors", hasSize(1)))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
				.andExpect(jsonPath("$.fieldErrors[0].message").exists());

		verify(service, never()).create(any(), any());
	}

	@Test
	void deveRejeitarTituloAusente() throws Exception {
		mockMvc.perform(post("/api/todos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
	}

	@Test
	void deveRejeitarDescricaoLongaDemais() throws Exception {
		String descricao = "d".repeat(501);

		mockMvc.perform(post("/api/todos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"X\", \"description\": \"" + descricao + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("description"));
	}

	@Test
	void deveRejeitarJsonMalformado() throws Exception {
		mockMvc.perform(post("/api/todos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").exists())
				.andExpect(jsonPath("$.path").value("/api/todos"))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void deveListarTodasAsTarefas() throws Exception {
		when(service.list(USER_ID, null)).thenReturn(List.of(
				todo(3L, "C", null, false),
				todo(2L, "B", null, true),
				todo(1L, "A", null, false)));

		mockMvc.perform(get("/api/todos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0].id").value(3))
				.andExpect(jsonPath("$[2].id").value(1));
	}

	@Test
	void deveFiltrarPorStatus() throws Exception {
		when(service.list(USER_ID, false)).thenReturn(List.of(todo(1L, "A", null, false)));

		mockMvc.perform(get("/api/todos").param("completed", "false"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].completed").value(false));

		verify(service).list(USER_ID, false);
	}

	@Test
	void deveRetornarListaVazia() throws Exception {
		when(service.list(USER_ID, null)).thenReturn(List.of());

		mockMvc.perform(get("/api/todos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void deveBuscarTarefaPorId() throws Exception {
		when(service.findById(USER_ID, 1L)).thenReturn(todo(1L, "A", "desc", false));

		mockMvc.perform(get("/api/todos/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.title").value("A"))
				.andExpect(jsonPath("$.description").value("desc"))
				.andExpect(jsonPath("$.completed").value(false))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.updatedAt").exists());
	}

	@Test
	void deveRetornar404AoBuscarIdInexistente() throws Exception {
		when(service.findById(USER_ID, 999L)).thenThrow(new TodoNotFoundException(999L));

		mockMvc.perform(get("/api/todos/999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").exists())
				.andExpect(jsonPath("$.path").value("/api/todos/999"))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void deveRetornar400ParaIdInvalido() throws Exception {
		mockMvc.perform(get("/api/todos/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.path").value("/api/todos/abc"));
	}

	@Test
	void deveAtualizarTarefa() throws Exception {
		TodoUpdateRequest request = new TodoUpdateRequest("Novo título", null, true);
		when(service.update(USER_ID, 1L, request)).thenReturn(todo(1L, "Novo título", null, true));

		mockMvc.perform(put("/api/todos/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title": "Novo título", "description": null, "completed": true}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Novo título"))
				.andExpect(jsonPath("$.description").isEmpty())
				.andExpect(jsonPath("$.completed").value(true));
	}

	@Test
	void deveRejeitarAtualizacaoComTituloDe101Caracteres() throws Exception {
		String titulo = "t".repeat(101);

		mockMvc.perform(put("/api/todos/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"" + titulo + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.path").value("/api/todos/1"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("title"));

		verify(service, never()).update(any(), any(), any());
	}

	@Test
	void deveRetornar404AoAtualizarIdInexistente() throws Exception {
		when(service.update(eq(USER_ID), eq(999L), any())).thenThrow(new TodoNotFoundException(999L));

		mockMvc.perform(put("/api/todos/999")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title": "X"}
						"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void deveAlternarConclusaoSemCorpo() throws Exception {
		when(service.toggle(USER_ID, 1L)).thenReturn(todo(1L, "A", null, true));

		mockMvc.perform(patch("/api/todos/1/toggle"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(true));
	}

	@Test
	void deveReabrirTarefaConcluida() throws Exception {
		when(service.toggle(USER_ID, 1L)).thenReturn(todo(1L, "A", null, false));

		mockMvc.perform(patch("/api/todos/1/toggle"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(false));
	}

	@Test
	void deveRetornar404AoAlternarIdInexistente() throws Exception {
		when(service.toggle(USER_ID, 999L)).thenThrow(new TodoNotFoundException(999L));

		mockMvc.perform(patch("/api/todos/999/toggle"))
				.andExpect(status().isNotFound());
	}

	@Test
	void deveRemoverTarefaComStatus204() throws Exception {
		mockMvc.perform(delete("/api/todos/1"))
				.andExpect(status().isNoContent());

		verify(service).delete(USER_ID, 1L);
	}

	@Test
	void deveRetornar404AoRemoverIdInexistente() throws Exception {
		doThrow(new TodoNotFoundException(999L)).when(service).delete(USER_ID, 999L);

		mockMvc.perform(delete("/api/todos/999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.path").value("/api/todos/999"));
	}

	@Test
	void deveRetornar415NoFormatoPadraoParaContentTypeNaoSuportado() throws Exception {
		mockMvc.perform(post("/api/todos")
				.contentType(MediaType.TEXT_PLAIN)
				.content("x"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.status").value(415))
				.andExpect(jsonPath("$.message").exists())
				.andExpect(jsonPath("$.path").value("/api/todos"));
	}

	@Test
	void deveRetornar405NoFormatoPadraoParaMetodoNaoSuportado() throws Exception {
		mockMvc.perform(delete("/api/todos"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(header().exists("Allow"))
				.andExpect(jsonPath("$.status").value(405))
				.andExpect(jsonPath("$.message").exists())
				.andExpect(jsonPath("$.path").value("/api/todos"));
	}

	@Test
	void deveRetornar404NoFormatoPadraoParaRotaInexistente() throws Exception {
		mockMvc.perform(get("/api/nada"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.message").exists())
				.andExpect(jsonPath("$.path").value("/api/nada"));
	}

	private static TodoResponse todo(Long id, String title, String description, boolean completed) {
		return new TodoResponse(id, title, description, completed, CRIADO_EM, ATUALIZADO_EM);
	}

}
