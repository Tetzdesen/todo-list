package com.exemplo.todo.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.exemplo.todo.model.User;

/**
 * Usuário guardado na sessão, seja qual for a forma de login. Implementa {@link UserDetails} (e-mail e senha) e
 * {@link OidcUser} (Google), assim o restante da aplicação só precisa do {@link #getId() id} do usuário.
 */
public class AppUserPrincipal implements UserDetails, OidcUser, CredentialsContainer {

	private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

	private final Long id;
	private final String displayName;
	private final String email;
	private String passwordHash;
	private final OidcIdToken idToken;
	private final OidcUserInfo userInfo;

	private AppUserPrincipal(User user, OidcIdToken idToken, OidcUserInfo userInfo) {
		this.id = user.getId();
		this.displayName = user.getName();
		this.email = user.getEmail();
		this.passwordHash = user.getPasswordHash();
		this.idToken = idToken;
		this.userInfo = userInfo;
	}

	public static AppUserPrincipal from(User user) {
		return new AppUserPrincipal(user, null, null);
	}

	public static AppUserPrincipal fromOidc(User user, OidcUser oidcUser) {
		return new AppUserPrincipal(user, oidcUser.getIdToken(), oidcUser.getUserInfo());
	}

	public Long getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getEmail() {
		return email;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	// nome do principal para o Spring Security (Authentication.getName()); o nome de exibição é getDisplayName()
	@Override
	public String getName() {
		return email;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return AUTHORITIES;
	}

	@Override
	public Map<String, Object> getAttributes() {
		return getClaims();
	}

	@Override
	public Map<String, Object> getClaims() {
		return idToken == null ? Map.of() : idToken.getClaims();
	}

	@Override
	public OidcUserInfo getUserInfo() {
		return userInfo;
	}

	@Override
	public OidcIdToken getIdToken() {
		return idToken;
	}

	// o hash da senha não precisa ficar na sessão depois do login
	@Override
	public void eraseCredentials() {
		passwordHash = null;
	}

}
