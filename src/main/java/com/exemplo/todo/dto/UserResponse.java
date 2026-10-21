package com.exemplo.todo.dto;

import java.util.ArrayList;
import java.util.List;

import com.exemplo.todo.model.User;

/**
 * {@code providers} lista as formas de login da conta: {@code password} e/ou {@code google}.
 */
public record UserResponse(
		Long id,
		String name,
		String email,
		List<String> providers) {

	public static UserResponse from(User user) {
		List<String> providers = new ArrayList<>();
		if (user.getPasswordHash() != null) {
			providers.add("password");
		}
		if (user.getGoogleId() != null) {
			providers.add("google");
		}
		return new UserResponse(user.getId(), user.getName(), user.getEmail(), List.copyOf(providers));
	}

}
