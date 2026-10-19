package com.exemplo.todo.exception;

public class TodoNotFoundException extends RuntimeException {

	public TodoNotFoundException(Long id) {
		super("Tarefa " + id + " não encontrada");
	}

}
