package com.exemplo.todo.model;

import java.time.Instant;
import java.util.Locale;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	// sempre em minúsculas: a unicidade no banco vale sem diferenciar maiúsculas
	@Setter(AccessLevel.NONE)
	@Column(nullable = false, unique = true, length = 254)
	private String email;

	// nulo para contas criadas só pelo Google
	private String passwordHash;

	@Column(unique = true)
	private String googleId;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public User(String name, String email) {
		this.name = name;
		this.email = normalizeEmail(email);
	}

	public static String normalizeEmail(String email) {
		return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

}
