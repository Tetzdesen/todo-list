package com.exemplo.todo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Quando {@code completed} é nulo, o valor atual da tarefa é mantido.
 */
public record TodoUpdateRequest(
		@NotBlank @Size(max = 100) String title,
		@Size(max = 500) String description,
		Boolean completed) {
}
