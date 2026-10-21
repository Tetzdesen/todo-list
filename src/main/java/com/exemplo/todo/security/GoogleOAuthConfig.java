package com.exemplo.todo.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Login com Google, ativo só quando {@code GOOGLE_CLIENT_ID} está definido. Sem ele, a aplicação sobe só com e-mail
 * e senha (o Spring Boot falharia com um registro de cliente sem id, por isso o registro não fica nas properties).
 */
@Configuration
@ConditionalOnExpression("!'${GOOGLE_CLIENT_ID:}'.isBlank()")
public class GoogleOAuthConfig {

	@Bean
	ClientRegistrationRepository clientRegistrationRepository(@Value("${GOOGLE_CLIENT_ID}") String clientId,
			@Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret) {
		return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
				.clientId(clientId)
				.clientSecret(clientSecret)
				.build());
	}

}
