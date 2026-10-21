package com.exemplo.todo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * O limite de 72 caracteres na senha vem do BCrypt, que não considera o que passa de 72 bytes.
 */
public record RegisterRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Email @Size(max = 254) String email,
		@NotNull @Size(min = 8, max = 72, message = "a senha deve ter entre 8 e 72 caracteres") String password) {

	// a senha nunca aparece em logs
	@Override
	public String toString() {
		return "RegisterRequest[name=" + name + ", email=" + email + "]";
	}

}
