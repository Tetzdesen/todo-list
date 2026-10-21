package com.exemplo.todo.exception;

/**
 * Erro de validação de um campo que só pode ser verificado no serviço; vira um 400 com {@code fieldErrors}.
 */
public class InvalidFieldException extends RuntimeException {

	private final String field;

	public InvalidFieldException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String getField() {
		return field;
	}

}
