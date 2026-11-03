// Tela de entrada: cadastro, validação, mostrar a senha, entrar e sair.
const crypto = require('node:crypto');
const { test, expect, registerAccount, openList } = require('./fixtures');

test.beforeEach(async ({ page }) => {
	await page.goto('/login.html');
});

test('mostra a tela de entrada com o painel decorativo escondido dos leitores de tela', async ({ page }) => {
	await expect(page).toHaveTitle('Entrar · Tarefas');
	await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible();
	await expect(page.locator('.hero-list')).toHaveAttribute('aria-hidden', 'true');
});

test('cadastro inválido liga cada erro ao campo e foca o primeiro campo inválido', async ({ page }) => {
	await page.getByRole('button', { name: 'Criar conta' }).click();
	const name = page.locator('#register-name');
	const email = page.locator('#register-email');
	const password = page.locator('#register-password');
	await expect(name).toBeFocused();

	await email.fill('invalido');
	await password.fill('123');
	await page.locator('#register-form [type="submit"]').click();

	// o servidor lista o e-mail primeiro, mas o foco vai para o primeiro na ordem da tela
	await expect(name).toBeFocused();
	for (const field of [name, email, password]) {
		await expect(field).toHaveAttribute('aria-invalid', 'true');
	}
	await expect(name).toHaveAccessibleDescription('não deve estar em branco');
	// a dica da senha continua ligada ao campo, junto com o erro
	await expect(password).toHaveAttribute('aria-describedby', /^register-password-hint field-error-\d+$/);

	// corrigir desfaz a ligação com o erro e mantém só a dica
	await name.fill('Ana');
	await email.fill(`ana.${crypto.randomUUID()}@exemplo.test`);
	await password.fill('curta');
	await page.locator('#register-form [type="submit"]').click();
	await expect(password).toBeFocused();
	await expect(name).not.toHaveAttribute('aria-invalid');
	await expect(name).not.toHaveAttribute('aria-describedby');
});

test('o botão Mostrar exibe e esconde a senha', async ({ page }) => {
	const password = page.locator('#login-password');
	const toggle = page.locator('#login-form [data-password-toggle]');
	await password.fill('segredo');

	await toggle.click();
	await expect(password).toHaveAttribute('type', 'text');
	await expect(toggle).toHaveAccessibleName('Ocultar senha');

	await toggle.click();
	await expect(password).toHaveAttribute('type', 'password');
	await expect(toggle).toHaveAccessibleName('Mostrar senha');
});

test('cadastro válido entra direto na lista', async ({ page }) => {
	await page.getByRole('button', { name: 'Criar conta' }).click();
	await page.locator('#register-name').fill('Ana Souza');
	await page.locator('#register-email').fill(`ana.${crypto.randomUUID()}@exemplo.test`);
	await page.locator('#register-password').fill(crypto.randomBytes(12).toString('hex'));
	await page.locator('#register-form [type="submit"]').click();

	await expect(page).toHaveURL('/');
	await expect(page.locator('#account-name')).toHaveText('Ana Souza');
	await expect(page.locator('#account-avatar')).toHaveText('AS');
	await expect(page.locator('#empty-message')).toBeVisible();
});

test('senha errada mantém o e-mail, limpa a senha e mostra o aviso', async ({ page, playwright, baseURL }) => {
	// conta criada por outro cliente, para esta página continuar sem sessão
	const api = await playwright.request.newContext({ baseURL });
	const account = await registerAccount(api);
	await api.dispose();

	await page.locator('#login-email').fill(account.email);
	await page.locator('#login-password').fill('senha-errada-123');
	await page.locator('#login-form [type="submit"]').click();

	await expect(page.locator('#error-banner')).toBeVisible();
	await expect(page.locator('#login-email')).toHaveValue(account.email);
	await expect(page.locator('#login-password')).toHaveValue('');
	await expect(page.locator('#login-password')).toBeFocused();

	await page.locator('#login-password').fill(account.password);
	await page.locator('#login-form [type="submit"]').click();
	await expect(page).toHaveURL('/');
});

test('Sair volta para a entrada e a lista exige login de novo', async ({ page, account }) => {
	await openList(page);
	await page.getByRole('button', { name: 'Sair' }).click();
	await expect(page).toHaveURL(/\/login\.html$/);

	await page.goto('/');
	await expect(page).toHaveURL(/\/login\.html$/);
});

test('quem já tem sessão vai direto para a lista', async ({ page, account }) => {
	await page.goto('/login.html');
	await expect(page).toHaveURL('/');
});
