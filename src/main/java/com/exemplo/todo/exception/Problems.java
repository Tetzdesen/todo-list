package com.exemplo.todo.exception;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import com.exemplo.todo.dto.FieldError;

/**
 * Corpo das respostas de erro no formato da RFC 9457 ({@code application/problem+json}): {@code title} é a frase do
 * status, {@code detail} a mensagem e {@code instance} o caminho pedido. Os campos extras são {@code timestamp} e,
 * em erros de validação, {@code fieldErrors}.
 */
public final class Problems {

	private Problems() {
	}

	public static ProblemDetail of(HttpStatus status, String detail, String path, List<FieldError> fieldErrors) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(status.getReasonPhrase());
		problem.setInstance(toUri(path));
		problem.setProperty("timestamp", Instant.now());
		if (fieldErrors != null) {
			problem.setProperty("fieldErrors", fieldErrors);
		}
		return problem;
	}

	// o caminho vem da requisição; se não formar uma URI válida, o campo instance fica de fora
	private static URI toUri(String path) {
		try {
			return URI.create(path);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
