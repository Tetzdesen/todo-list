package com.exemplo.todo.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.exemplo.todo.model.User;
import com.exemplo.todo.repository.UserRepository;

@Service
public class AppUserDetailsService implements UserDetailsService {

	private final UserRepository repository;

	public AppUserDetailsService(UserRepository repository) {
		this.repository = repository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		// conta criada só pelo Google não tem senha: é recusada como se o e-mail não existisse
		return repository.findByEmail(User.normalizeEmail(email))
				.filter(user -> user.getPasswordHash() != null)
				.map(AppUserPrincipal::from)
				.orElseThrow(() -> new UsernameNotFoundException("E-mail ou senha inválidos"));
	}

}
