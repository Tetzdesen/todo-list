package com.exemplo.todo;

import static com.exemplo.todo.CsrfTestSupport.realCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

/**
 * Sessões guardadas no banco pelo Spring Session JDBC, como em produção (localmente elas ficam na memória). A sessão
 * vai e volta só pelo cookie {@code SESSION}, como no navegador, e não por um {@code MockHttpSession}.
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude=")
@AutoConfigureMockMvc
abstract class SessionPersistenceTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void deveGuardarASessaoNoBancoEApagarNoLogout() throws Exception {
		String email = unico("ana") + "@exemplo.com";
		Cookie sessao = cadastrar(email);

		assertThat(sessao.isHttpOnly()).isTrue();
		assertThat(sessoesDe(email)).isEqualTo(1);

		mockMvc.perform(get("/api/auth/me").cookie(sessao))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));

		mockMvc.perform(post("/api/auth/logout").cookie(sessao).with(realCsrf(mockMvc)))
				.andExpect(status().isNoContent());

		assertThat(sessoesDe(email)).isZero();
		mockMvc.perform(get("/api/auth/me").cookie(sessao)).andExpect(status().isUnauthorized());
	}

	@Test
	void loginDeveTrocarOIdDaSessao() throws Exception {
		String email = unico("bia") + "@exemplo.com";
		Cookie antiga = cadastrar(email);

		Cookie nova = mockMvc.perform(post("/api/auth/login").cookie(antiga).with(realCsrf(mockMvc))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "segredo123"}
						""".formatted(email)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getCookie("SESSION");

		assertThat(nova).isNotNull();
		assertThat(nova.getValue()).isNotEqualTo(antiga.getValue());
		mockMvc.perform(get("/api/auth/me").cookie(nova)).andExpect(status().isOk());
		mockMvc.perform(get("/api/auth/me").cookie(antiga)).andExpect(status().isUnauthorized());
	}

	private Cookie cadastrar(String email) throws Exception {
		Cookie sessao = mockMvc.perform(post("/api/auth/register").with(realCsrf(mockMvc))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Ana", "email": "%s", "password": "segredo123"}
						""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getCookie("SESSION");
		assertThat(sessao).isNotNull();
		return sessao;
	}

	private int sessoesDe(String email) {
		return jdbc.queryForObject("select count(*) from spring_session where principal_name = ?", Integer.class,
				email);
	}

	private static String unico(String prefixo) {
		return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
	}

}
