package com.exemplo.todo.exception;

/**
 * A tarefa mudou desde que o cliente a leu (a {@code version} enviada é antiga); vira um 409.
 */
public class TodoConflictException extends RuntimeException {

	public TodoConflictException(Long id) {
		super("A tarefa " + id + " foi alterada em outro lugar. A lista foi recarregada; tente de novo.");
	}

}
