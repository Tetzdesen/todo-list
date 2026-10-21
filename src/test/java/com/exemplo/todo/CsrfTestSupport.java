package com.exemplo.todo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;

/**
 * Token CSRF de verdade, obtido como o navegador obtém: lendo o cookie {@code XSRF-TOKEN} e devolvendo-o no
 * cabeçalho {@code X-XSRF-TOKEN}.
 * <p>
 * Nos testes {@code @SpringBootTest} não usamos o {@code csrf()} do spring-security-test: ele troca para sempre o
 * repositório de tokens do filtro no contexto compartilhado, e o cookie deixa de ser gravado nos testes seguintes.
 */
public final class CsrfTestSupport {

	private CsrfTestSupport() {
	}

	public static RequestPostProcessor realCsrf(MockMvc mockMvc) throws Exception {
		Cookie token = mockMvc.perform(get("/api/auth/providers")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		return request -> {
			List<Cookie> cookies = new ArrayList<>();
			if (request.getCookies() != null) {
				cookies.addAll(Arrays.asList(request.getCookies()));
			}
			cookies.add(token);
			request.setCookies(cookies.toArray(Cookie[]::new));
			request.addHeader("X-XSRF-TOKEN", token.getValue());
			return request;
		};
	}

}
