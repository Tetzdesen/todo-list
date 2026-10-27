package com.exemplo.todo.security;

import static com.exemplo.todo.CsrfTestSupport.realCsrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.exemplo.todo.model.User;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void devePermitirPaginasEDocumentacaoSemLogin() throws Exception {
		mockMvc.perform(get("/")).andExpect(status().isOk());
		mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	void deveResponder401NoFormatoPadraoSemLogin() throws Exception {
		mockMvc.perform(get("/api/todos"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.title").value("Unauthorized"))
				.andExpect(jsonPath("$.detail").value("Autenticação necessária"))
				.andExpect(jsonPath("$.instance").value("/api/todos"));
	}

	@Test
	void deveResponder403NoFormatoPadraoSemTokenCsrf() throws Exception {
		mockMvc.perform(post("/api/todos")
				.with(user(principal()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"X\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.detail").value("Token CSRF ausente ou inválido"))
				.andExpect(jsonPath("$.instance").value("/api/todos"));
	}

	@Test
	void deveAceitarTokenCsrfNoCabecalho() throws Exception {
		mockMvc.perform(delete("/api/todos/999").with(user(principal())).with(realCsrf(mockMvc)))
				.andExpect(status().isNotFound());
	}

	private static AppUserPrincipal principal() {
		User user = new User("Ana", "ana@exemplo.com");
		user.setId(-1L);
		return AppUserPrincipal.from(user);
	}

}
