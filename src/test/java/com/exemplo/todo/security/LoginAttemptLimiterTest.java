package com.exemplo.todo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.exemplo.todo.exception.TooManyLoginAttemptsException;

class LoginAttemptLimiterTest {

	private final AjustavelClock clock = new AjustavelClock(Instant.parse("2026-01-01T10:00:00Z"));
	private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

	@Test
	void deveBloquearOEmailNaQuintaFalhaAteOFimDaJanela() {
		falhar("ana@exemplo.com", "1.1.1.1", 4);
		assertThatCode(() -> limiter.checkAllowed("ana@exemplo.com", "1.1.1.1")).doesNotThrowAnyException();

		falhar("ana@exemplo.com", "1.1.1.1", 1);
		clock.avancar(Duration.ofMinutes(5));

		assertThatThrownBy(() -> limiter.checkAllowed("ANA@exemplo.com ", "2.2.2.2"))
				.isInstanceOfSatisfying(TooManyLoginAttemptsException.class,
						ex -> assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofMinutes(10)));

		clock.avancar(Duration.ofMinutes(10));
		assertThatCode(() -> limiter.checkAllowed("ana@exemplo.com", "1.1.1.1")).doesNotThrowAnyException();
	}

	@Test
	void deveBloquearOIpQueTestaMuitasContas() {
		for (int i = 0; i < 20; i++) {
			limiter.recordFailure("conta" + i + "@exemplo.com", "1.1.1.1");
		}

		assertThatThrownBy(() -> limiter.checkAllowed("outra@exemplo.com", "1.1.1.1"))
				.isInstanceOf(TooManyLoginAttemptsException.class);
		assertThatCode(() -> limiter.checkAllowed("outra@exemplo.com", "2.2.2.2")).doesNotThrowAnyException();
	}

	@Test
	void loginCertoDeveZerarAsFalhasDoEmailMasNaoDoIp() {
		falhar("ana@exemplo.com", "1.1.1.1", 4);
		limiter.recordSuccess("ana@exemplo.com");
		falhar("ana@exemplo.com", "1.1.1.1", 4);

		assertThatCode(() -> limiter.checkAllowed("ana@exemplo.com", "1.1.1.1")).doesNotThrowAnyException();

		falhar("bruno@exemplo.com", "1.1.1.1", 12);
		assertThatThrownBy(() -> limiter.checkAllowed("carla@exemplo.com", "1.1.1.1"))
				.isInstanceOf(TooManyLoginAttemptsException.class);
	}

	@Test
	void falhaDepoisDaJanelaDeveComecarUmaContagemNova() {
		falhar("ana@exemplo.com", "1.1.1.1", 4);
		clock.avancar(LoginAttemptLimiter.WINDOW);
		falhar("ana@exemplo.com", "1.1.1.1", 4);

		assertThatCode(() -> limiter.checkAllowed("ana@exemplo.com", "1.1.1.1")).doesNotThrowAnyException();
	}

	private void falhar(String email, String ip, int vezes) {
		for (int i = 0; i < vezes; i++) {
			limiter.recordFailure(email, ip);
		}
	}

	private static class AjustavelClock extends Clock {

		private Instant agora;

		AjustavelClock(Instant agora) {
			this.agora = agora;
		}

		void avancar(Duration duracao) {
			agora = agora.plus(duracao);
		}

		@Override
		public Instant instant() {
			return agora;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

}
