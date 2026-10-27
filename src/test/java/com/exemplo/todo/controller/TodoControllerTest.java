package com.exemplo.todo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import com.exemplo.todo.dto.PageResponse;
import com.exemplo.todo.dto.TodoCreateRequest;
import com.exemplo.todo.dto.TodoResponse;
import com.exemplo.todo.dto.TodoPatchRequest;
import com.exemplo.todo.exception.TodoConflictException;
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

		verify(service, never()).list(any(), any(), anyInt(), anyInt());
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
		when(service.list(USER_ID, null, 0, 20)).thenReturn(pagina(List.of(
				todo(3L, "C", null, false),
				todo(2L, "B", null, true),
				todo(1L, "A", null, false))));

		mockMvc.perform(get("/api/todos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(3)))
				.andExpect(jsonPath("$.content[0].id").value(3))
				.andExpect(jsonPath("$.content[2].id").value(1))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void deveRepassarAPaginaPedida() throws Exception {
		when(service.list(USER_ID, null, 2, 5)).thenReturn(new PageResponse<>(List.of(), 2, 5, 7, 2));

		mockMvc.perform(get("/api/todos").param("page", "2").param("size", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(2))
				.andExpect(jsonPath("$.size").value(5));

		verify(service).list(USER_ID, null, 2, 5);
	}

	@Test
	void deveRecusarPaginacaoInvalida() throws Exception {
		mockMvc.perform(get("/api/todos").param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("size"));

		mockMvc.perform(get("/api/todos").param("size", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("size"));

		mockMvc.perform(get("/api/todos").param("page", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("page"));

		verify(service, never()).list(any(), any(), anyInt(), anyInt());
	}

	@Test
	void deveFiltrarPorStatus() throws Exception {
		when(service.list(USER_ID, false, 0, 20)).thenReturn(pagina(List.of(todo(1L, "A", null, false))));

		mockMvc.perform(get("/api/todos").param("completed", "false"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].completed").value(false));

		verify(service).list(USER_ID, false, 0, 20);
	}

	@Test
	void deveRetornarListaVazia() throws Exception {
		when(service.list(USER_ID, null, 0, 20)).thenReturn(pagina(List.of()));

		mockMvc.perform(get("/api/todos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.totalPages").value(0));
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
	void deveAtualizarTarefaParcialmente() throws Exception {
		when(service.update(eq(USER_ID), eq(1L), any())).thenReturn(todo(1L, "A", null, true));

		mockMvc.perform(patch("/api/todos/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"completed": true, "version": 0}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(true))
				.andExpect(jsonPath("$.version").value(0));

		ArgumentCaptor<TodoPatchRequest> captor = ArgumentCaptor.forClass(TodoPatchRequest.class);
		verify(service).update(eq(USER_ID), eq(1L), captor.capture());
		TodoPatchRequest recebido = captor.getValue();
		assertThat(recebido.hasCompleted()).isTrue();
		assertThat(recebido.getCompleted()).isTrue();
		assertThat(recebido.getVersion()).isZero();
		assertThat(recebido.hasTitle()).isFalse();
		assertThat(recebido.hasDescription()).isFalse();
	}

	@Test
	void deveDiferenciarDescricaoNulaDeAusente() throws Exception {
		when(service.update(eq(USER_ID), eq(1L), any())).thenReturn(todo(1L, "A", null, false));

		mockMvc.perform(patch("/api/todos/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"description": null}
						"""))
				.andExpect(status().isOk());

		ArgumentCaptor<TodoPatchRequest> captor = ArgumentCaptor.forClass(TodoPatchRequest.class);
		verify(service).update(eq(USER_ID), eq(1L), captor.capture());
		assertThat(captor.getValue().hasDescription()).isTrue();
		assertThat(captor.getValue().getDescription()).isNull();
	}

	@Test
	void deveRejeitarTituloInvalidoNaAtualizacao() throws Exception {
		for (String titulo : new String[] { "t".repeat(101), "   " }) {
			mockMvc.perform(patch("/api/todos/1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"title\": \"" + titulo + "\"}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.path").value("/api/todos/1"))
					.andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
		}

		verify(service, never()).update(any(), any(), any());
	}

	@Test
	void deveResponder409QuandoATarefaMudouEmOutroLugar() throws Exception {
		when(service.update(eq(USER_ID), eq(1L), any())).thenThrow(new TodoConflictException(1L));

		mockMvc.perform(patch("/api/todos/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"completed": true, "version": 3}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.path").value("/api/todos/1"));
	}

	@Test
	void deveResponder500SemExporDetalhesDoErro() throws Exception {
		when(service.findById(USER_ID, 1L)).thenThrow(new IllegalStateException("detalhe interno"));

		mockMvc.perform(get("/api/todos/1"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.message").value("Erro interno no servidor"))
				.andExpect(jsonPath("$.path").value("/api/todos/1"))
				.andExpect(content().string(not(containsString("detalhe interno"))));
	}

	@Test
	void deveManterOStatusDeExcecoesDoSpringNoFormatoPadrao() throws Exception {
		when(service.findById(USER_ID, 1L)).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

		mockMvc.perform(get("/api/todos/1"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value(503))
				.andExpect(jsonPath("$.path").value("/api/todos/1"));
	}

	@Test
	void deveRetornar404AoAtualizarIdInexistente() throws Exception {
		when(service.update(eq(USER_ID), eq(999L), any())).thenThrow(new TodoNotFoundException(999L));

		mockMvc.perform(patch("/api/todos/999")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title": "X"}
						"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void naoDeveExistirMaisPutNemToggle() throws Exception {
		mockMvc.perform(put("/api/todos/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"X\"}"))
				.andExpect(status().isMethodNotAllowed());

		mockMvc.perform(patch("/api/todos/1/toggle"))
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
		return new TodoResponse(id, title, description, completed, CRIADO_EM, ATUALIZADO_EM, 0L);
	}

	// primeira página com o tamanho padrão (20), suficiente para as tarefas dos testes
	private static PageResponse<TodoResponse> pagina(List<TodoResponse> todos) {
		return new PageResponse<>(todos, 0, 20, todos.size(), todos.isEmpty() ? 0 : 1);
	}

}
