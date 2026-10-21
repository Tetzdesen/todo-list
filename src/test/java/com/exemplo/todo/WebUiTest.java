package com.exemplo.todo;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class WebUiTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void devePaginaInicialEncaminharParaIndexHtml() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("index.html"));
	}

	@Test
	void deveServirIndexHtml() throws Exception {
		mockMvc.perform(get("/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<title>Tarefas</title>")));
	}

	@Test
	void deveServirJavaScript() throws Exception {
		mockMvc.perform(get("/js/app.js"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/javascript"));
	}

	@Test
	void deveServirCss() throws Exception {
		mockMvc.perform(get("/css/styles.css"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/css"));
	}

	@Test
	void deveServirTelaDeEntradaSemLogin() throws Exception {
		mockMvc.perform(get("/login.html"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Continuar com Google")));
	}

	@Test
	void deveServirScriptsDaTelaDeEntradaSemLogin() throws Exception {
		mockMvc.perform(get("/js/login.js"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/javascript"));
		mockMvc.perform(get("/js/api.js"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/javascript"));
		mockMvc.perform(get("/js/theme.js"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/javascript"));
	}

}
