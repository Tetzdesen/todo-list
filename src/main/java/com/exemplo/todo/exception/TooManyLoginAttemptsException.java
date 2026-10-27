package com.exemplo.todo.exception;

import java.time.Duration;

/**
 * Login bloqueado por excesso de tentativas erradas; vira um 429 com o cabeçalho {@code Retry-After}.
 */
public class TooManyLoginAttemptsException extends RuntimeException {

	private final Duration retryAfter;

	public TooManyLoginAttemptsException(Duration retryAfter) {
		super("Muitas tentativas de login");
		this.retryAfter = retryAfter;
	}

	public Duration getRetryAfter() {
		return retryAfter;
	}

}
