package com.exemplo.todo.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alteração parcial de uma tarefa: só os campos presentes no corpo mudam. {@code "description": null} apaga a
 * descrição; {@code title} e {@code completed}, se vierem, não podem ser nulos. Com {@code version}, a alteração é
 * recusada (409) quando a tarefa mudou desde que foi lida.
 */
public class TodoPatchRequest {

	// null aqui é "ausente" ou "nulo"; quem diferencia é hasTitle()
	@Pattern(regexp = "(?s).*\\S.*", message = "não deve estar em branco")
	@Size(max = 100)
	private String title;

	@Size(max = 500)
	private String description;

	private Boolean completed;

	private Long version;

	// o Jackson só chama o setter de um campo presente no JSON, mesmo quando o valor é null
	private boolean titlePresent;
	private boolean descriptionPresent;
	private boolean completedPresent;

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
		this.titlePresent = true;
	}

	public boolean hasTitle() {
		return titlePresent;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
		this.descriptionPresent = true;
	}

	public boolean hasDescription() {
		return descriptionPresent;
	}

	public Boolean getCompleted() {
		return completed;
	}

	public void setCompleted(Boolean completed) {
		this.completed = completed;
		this.completedPresent = true;
	}

	public boolean hasCompleted() {
		return completedPresent;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
