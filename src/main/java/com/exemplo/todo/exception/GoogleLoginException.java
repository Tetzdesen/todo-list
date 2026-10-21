package com.exemplo.todo.exception;

/**
 * Login com Google recusado (e-mail não verificado ou já vinculado a outra conta Google).
 */
public class GoogleLoginException extends RuntimeException {

	public GoogleLoginException(String message) {
		super(message);
	}

}
