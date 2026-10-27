package com.exemplo.todo.exception;

/**
 * A sessão aponta para uma conta que não existe mais; vira um 401 e a sessão é encerrada.
 */
public class AccountNotFoundException extends RuntimeException {

	public AccountNotFoundException(Long id) {
		super("Conta " + id + " não encontrada");
	}

}
