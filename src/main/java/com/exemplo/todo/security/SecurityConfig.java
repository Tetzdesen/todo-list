package com.exemplo.todo.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {

	// páginas e arquivos estáticos não contêm dados: quem decide ir para a tela de entrada é o JavaScript
	private static final String[] PUBLIC_PATHS = {
			"/", "/index.html", "/login.html", "/css/**", "/js/**", "/favicon.ico",
			"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonSecurityErrorHandler errorHandler,
			SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository,
			ObjectProvider<ClientRegistrationRepository> googleRegistrations,
			GoogleOidcUserService googleOidcUserService) throws Exception {
		// o console H2 é um servlet à parte, fora do Spring MVC
		var h2Console = PathPatternRequestMatcher.withDefaults().matcher("/h2-console/**");

		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_PATHS).permitAll()
						.requestMatchers(h2Console).permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/auth/providers").permitAll()
						.anyRequest().authenticated())
				.securityContext(context -> context.securityContextRepository(securityContextRepository))
				// o token vai num cookie legível pelo JavaScript, que o devolve no cabeçalho X-XSRF-TOKEN
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository)
						.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
						.ignoringRequestMatchers(h2Console))
				.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(errorHandler)
						.accessDeniedHandler(errorHandler))
				.logout(logout -> logout
						.logoutUrl("/api/auth/logout")
						.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable());

		// login com Google só quando GOOGLE_CLIENT_ID está definido (veja GoogleOAuthConfig)
		if (googleRegistrations.getIfAvailable() != null) {
			http.oauth2Login(oauth -> oauth
					.loginPage("/login.html")
					.userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleOidcUserService))
					.defaultSuccessUrl("/", true)
					.failureUrl("/login.html?error=google"));
		}
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		var provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	// mesmo repositório usado pelos filtros, para o AuthController gravar o login na sessão
	@Bean
	SecurityContextRepository securityContextRepository() {
		return new DelegatingSecurityContextRepository(
				new RequestAttributeSecurityContextRepository(),
				new HttpSessionSecurityContextRepository());
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return CookieCsrfTokenRepository.withHttpOnlyFalse();
	}

}
