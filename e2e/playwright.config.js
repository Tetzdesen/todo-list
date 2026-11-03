// Testes de ponta a ponta da interface web. O Playwright sobe a aplicação (H2 em memória) antes dos testes.
const { defineConfig } = require('@playwright/test');

const PORT = process.env.PORT ?? '8080';
const BASE_URL = `http://localhost:${PORT}`;

module.exports = defineConfig({
	testDir: './tests',
	// cada teste cria a própria conta, então podem rodar em paralelo sem um ver as tarefas do outro
	fullyParallel: true,
	forbidOnly: !!process.env.CI,
	retries: process.env.CI ? 1 : 0,
	reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
	use: {
		baseURL: BASE_URL,
		// o navegador fica em inglês de propósito: as mensagens de validação devem vir em português mesmo assim
		locale: 'en-US',
		trace: 'retain-on-failure',
		screenshot: 'only-on-failure',
	},
	projects: [
		{
			name: 'computador',
			testIgnore: /celular\.spec\.js/,
			use: { browserName: 'chromium', viewport: { width: 1280, height: 800 } },
		},
		{
			name: 'celular',
			testMatch: /celular\.spec\.js/,
			use: { browserName: 'chromium', viewport: { width: 375, height: 740 }, isMobile: true, hasTouch: true },
		},
	],
	webServer: {
		// no CI, o jar que o "./mvnw verify" acabou de gerar; na máquina, a aplicação direto do código
		command: process.env.E2E_SERVER_COMMAND ?? 'cd .. && ./mvnw -q spring-boot:run',
		url: `${BASE_URL}/login.html`,
		// na máquina, aproveita a aplicação se ela já estiver rodando
		reuseExistingServer: !process.env.CI,
		timeout: 180_000,
		stdout: 'ignore',
		stderr: 'pipe',
	},
});
