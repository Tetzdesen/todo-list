package com.exemplo.todo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TodoCreateRequest(
		@NotBlank @Size(max = 100) String title,
		@Size(max = 500) String description) {
}
