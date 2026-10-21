package com.exemplo.todo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.exemplo.todo.model.User;
import com.exemplo.todo.repository.UserRepository;

class AppUserDetailsServiceTest {

	private final UserRepository repository = mock(UserRepository.class);
	private final AppUserDetailsService service = new AppUserDetailsService(repository);

	@Test
	void deveCarregarContaComSenhaPeloEmailNormalizado() {
		User ana = new User("Ana", "ana@exemplo.com");
		ana.setPasswordHash("hash");
		when(repository.findByEmail("ana@exemplo.com")).thenReturn(Optional.of(ana));

		UserDetails details = service.loadUserByUsername("  ANA@exemplo.com ");

		assertThat(details.getUsername()).isEqualTo("ana@exemplo.com");
		assertThat(details.getPassword()).isEqualTo("hash");
	}

	@Test
	void deveRecusarContaCriadaSoPeloGoogle() {
		User bia = new User("Bia", "bia@gmail.com");
		bia.setGoogleId("google-123");
		when(repository.findByEmail("bia@gmail.com")).thenReturn(Optional.of(bia));

		assertThatThrownBy(() -> service.loadUserByUsername("bia@gmail.com"))
				.isInstanceOf(UsernameNotFoundException.class);
	}

	@Test
	void deveRecusarEmailInexistente() {
		when(repository.findByEmail("x@exemplo.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.loadUserByUsername("x@exemplo.com"))
				.isInstanceOf(UsernameNotFoundException.class);
	}

}
