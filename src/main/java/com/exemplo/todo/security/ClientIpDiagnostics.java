package com.exemplo.todo.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;

/**
 * TEMPORÁRIO: registra como o IP do cliente chega em produção, para decidir em qual cabeçalho o limite de login por
 * IP pode confiar. Remover depois dessa decisão.
 */
public final class ClientIpDiagnostics {

	private static final Logger log = LoggerFactory.getLogger(ClientIpDiagnostics.class);

	private static final String[] HEADERS = {
			"X-Forwarded-For", "X-Real-IP", "CF-Connecting-IP", "True-Client-IP", "Forwarded" };

	private ClientIpDiagnostics() {
	}

	public static void log(HttpServletRequest request) {
		// o ForwardedHeaderFilter (server.forward-headers-strategy=framework) esconde os X-Forwarded-*: os
		// cabeçalhos originais estão na requisição do Tomcat, por baixo dos wrappers
		ServletRequest raw = request;
		while (raw instanceof ServletRequestWrapper wrapper) {
			raw = wrapper.getRequest();
		}
		HttpServletRequest original = (HttpServletRequest) raw;

		StringBuilder line = new StringBuilder("[diagnostico-ip] remoteAddr original=")
				.append(original.getRemoteAddr())
				.append(" | usado pelo limite=").append(request.getRemoteAddr());
		for (String header : HEADERS) {
			line.append(" | ").append(header).append('=').append(original.getHeader(header));
		}
		log.info(line.toString());
	}

}
