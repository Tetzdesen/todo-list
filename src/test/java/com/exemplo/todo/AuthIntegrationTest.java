package com.exemplo.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static com.exemplo.todo.CsrfTestSupport.realCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.exemplo.todo.model.User;
import com.exemplo.todo.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Test
	void deveCadastrarJaAutenticadoEComEmailNormalizado() throws Exception {
		String email = unico("Ana") + "@Exemplo.com";
		MockHttpSession session = new MockHttpSession();

		register(session, "Ana", email, "segredo123")
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/auth/me"))
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.name").value("Ana"))
				.andExpect(jsonPath("$.email").value(email.toLowerCase()))
				.andExpect(jsonPath("$.providers[0]").value("password"))
				.andExpect(content().string(not(containsString("segredo123"))))
				.andExpect(content().string(not(containsString("passwordHash"))));

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email.toLowerCase()));

		User salvo = userRepository.findByEmail(email.toLowerCase()).orElseThrow();
		assertThat(salvo.getPasswordHash()).isNotEqualTo("segredo123").startsWith("$2");
	}

	@Test
	void deveRecusarEmailJaCadastradoSemDiferenciarMaiusculas() throws Exception {
		String email = unico("ana") + "@exemplo.com";
		register(new MockHttpSession(), "Ana", email, "segredo123").andExpect(status().isCreated());

		register(new MockHttpSession(), "Outra", email.toUpperCase(), "segredo123")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.path").value("/api/auth/register"));
	}

	@Test
	void deveValidarOCadastro() throws Exception {
		register(new MockHttpSession(), "Ana", unico("a") + "@exemplo.com", "1234567")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("password"));

		register(new MockHttpSession(), "Ana", "ana.exemplo.com", "segredo123")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("email"));

		register(new MockHttpSession(), "   ", unico("a") + "@exemplo.com", "segredo123")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));

		// 40 caracteres acentuados = 80 bytes: dentro do limite de caracteres, fora do limite do BCrypt
		register(new MockHttpSession(), "Ana", unico("a") + "@exemplo.com", "é".repeat(40))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
	}

	@Test
	void deveEntrarComEmailESenha() throws Exception {
		String email = unico("ana") + "@exemplo.com";
		register(new MockHttpSession(), "Ana", email, "segredo123");

		MockHttpSession session = new MockHttpSession();
		login(session, email.toUpperCase(), "segredo123")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Ana"))
				.andExpect(cookie().exists("XSRF-TOKEN"));

		mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
	}

	@Test
	void deveRecusarCredenciaisInvalidasComAMesmaMensagem() throws Exception {
		String email = unico("ana") + "@exemplo.com";
		register(new MockHttpSession(), "Ana", email, "segredo123");

		login(new MockHttpSession(), email, "errada123")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));

		login(new MockHttpSession(), unico("ninguem") + "@exemplo.com", "segredo123")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));

		login(new MockHttpSession(), email, "é".repeat(40))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
	}

	@Test
	void deveRecusarLoginComSenhaEmContaCriadaPeloGoogle() throws Exception {
		User bia = new User("Bia", unico("bia") + "@gmail.com");
		bia.setGoogleId(unico("google"));
		userRepository.saveAndFlush(bia);

		login(new MockHttpSession(), bia.getEmail(), "qualquer123")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
	}

	@Test
	void deveResponder401EmMeSemSessao() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.path").value("/api/auth/me"));
	}

	@Test
	void deveInformarProvedoresSemLoginEGerarTokenCsrf() throws Exception {
		mockMvc.perform(get("/api/auth/providers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.google").value(false))
				.andExpect(cookie().exists("XSRF-TOKEN"));
	}

	@Test
	void deveSairEncerrandoASessao() throws Exception {
		MockHttpSession session = new MockHttpSession();
		register(session, "Ana", unico("ana") + "@exemplo.com", "segredo123");

		mockMvc.perform(post("/api/auth/logout").session(session).with(realCsrf(mockMvc)))
				.andExpect(status().isNoContent());

		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void deveExigirTokenCsrfNoLogin() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"a@exemplo.com\", \"password\": \"segredo123\"}"))
				.andExpect(status().isForbidden());
	}

	private ResultActions register(MockHttpSession session, String name, String email, String password)
			throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.session(session)
				.with(realCsrf(mockMvc))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "email": "%s", "password": "%s"}
						""".formatted(name, email, password)));
	}

	private ResultActions login(MockHttpSession session, String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.session(session)
				.with(realCsrf(mockMvc))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s"}
						""".formatted(email, password)));
	}

	// o banco H2 é compartilhado entre os testes do contexto: cada teste usa e-mails próprios
	private static String unico(String prefixo) {
		return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
	}

}
