package com.exemplo.todo.dto;

import java.time.Instant;

import com.exemplo.todo.model.Todo;

public record TodoResponse(
		Long id,
		String title,
		String description,
		boolean completed,
		Instant createdAt,
		Instant updatedAt) {

	public static TodoResponse from(Todo todo) {
		return new TodoResponse(
				todo.getId(),
				todo.getTitle(),
				todo.getDescription(),
				todo.isCompleted(),
				todo.getCreatedAt(),
				todo.getUpdatedAt());
	}

}
