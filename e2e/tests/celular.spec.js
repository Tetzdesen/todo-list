// Celular (375 px, toque): sem rolagem lateral, alvos de 44 px e campos de 16 px (sem zoom no iPhone).
const { test, expect, createTodo, openList } = require('./fixtures');

async function expectNoHorizontalScroll(page) {
	const fits = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
	expect(fits, 'a página não pode rolar para o lado').toBe(true);
}

async function fontSize(locator) {
	return locator.evaluate((element) => parseFloat(getComputedStyle(element).fontSize));
}

test('a tela de entrada cabe na tela e os campos têm 16 px', async ({ page }) => {
	await page.goto('/login.html');
	await expectNoHorizontalScroll(page);
	expect(await fontSize(page.locator('#login-email'))).toBeGreaterThanOrEqual(16);
	expect(await fontSize(page.locator('#login-password'))).toBeGreaterThanOrEqual(16);
});

test('a lista cabe na tela, com alvos de toque de 44 px', async ({ page, account }) => {
	await createTodo(page.request, 'Uma tarefa com um título comprido para ver se quebra a linha direito');
	await openList(page);
	await expectNoHorizontalScroll(page);

	for (const control of [page.locator('.band [data-theme-toggle]'), page.getByRole('button', { name: 'Sair' })]) {
		const box = await control.boundingBox();
		expect(box.height).toBeGreaterThanOrEqual(44);
		expect(box.width).toBeGreaterThanOrEqual(44);
	}
	expect(await fontSize(page.locator('#create-title'))).toBeGreaterThanOrEqual(16);
});

test('sem espaço, o nome da conta sai da tela mas continua para os leitores de tela', async ({ page, account }) => {
	await openList(page);
	const name = page.locator('#account-name');
	await expect(name).toHaveText(account.name);
	const box = await name.boundingBox();
	expect(box.width).toBeLessThanOrEqual(1);
});

test('a política de privacidade cabe na tela', async ({ page }) => {
	await page.goto('/privacidade.html');
	await expectNoHorizontalScroll(page);
});
