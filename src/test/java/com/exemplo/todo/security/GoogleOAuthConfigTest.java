package com.exemplo.todo.security;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Com credenciais (fictícias) do Google, o login com Google fica disponível. O caso sem credenciais é coberto por
 * {@code AuthIntegrationTest}.
 */
@SpringBootTest(properties = { "GOOGLE_CLIENT_ID=id-ficticio", "GOOGLE_CLIENT_SECRET=segredo-ficticio" })
@AutoConfigureMockMvc
class GoogleOAuthConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void deveInformarGoogleDisponivel() throws Exception {
		mockMvc.perform(get("/api/auth/providers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.google").value(true));
	}

	@Test
	void deveRedirecionarParaOGoogle() throws Exception {
		mockMvc.perform(get("/oauth2/authorization/google"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", startsWith("https://accounts.google.com/o/oauth2/v2/auth")));
	}

	@Test
	void deveContinuarRespondendo401NaApiSemLogin() throws Exception {
		mockMvc.perform(get("/api/todos")).andExpect(status().isUnauthorized());
	}

}
