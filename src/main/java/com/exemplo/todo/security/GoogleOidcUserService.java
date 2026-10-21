package com.exemplo.todo.security;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.exemplo.todo.exception.GoogleLoginException;
import com.exemplo.todo.service.UserService;

/**
 * Troca o usuário devolvido pelo Google pela conta da aplicação, para a sessão guardar sempre um
 * {@link AppUserPrincipal}.
 */
@Service
public class GoogleOidcUserService extends OidcUserService {

	private final UserService userService;

	public GoogleOidcUserService(UserService userService) {
		this.userService = userService;
	}

	@Override
	public OidcUser loadUser(OidcUserRequest userRequest) {
		return toAppUser(super.loadUser(userRequest));
	}

	AppUserPrincipal toAppUser(OidcUser google) {
		try {
			var user = userService.findOrCreateFromGoogle(google.getSubject(), google.getEmail(),
					Boolean.TRUE.equals(google.getEmailVerified()), google.getFullName());
			return AppUserPrincipal.fromOidc(user, google);
		} catch (GoogleLoginException ex) {
			throw new OAuth2AuthenticationException(new OAuth2Error("google_login_refused", ex.getMessage(), null),
					ex);
		}
	}

}
