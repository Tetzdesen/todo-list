// Páginas e arquivos que abrem sem login.
const { test, expect } = require('./fixtures');

test('a política de privacidade abre sem login, com e sem .html', async ({ page }) => {
	for (const path of ['/privacidade', '/privacidade.html']) {
		await page.goto(path);
		await expect(page.getByRole('heading', { level: 1 })).toHaveText('Política de Privacidade');
	}
});

test('o ícone da aba e a cor da barra do navegador estão declarados', async ({ page, request }) => {
	await page.goto('/login.html');
	await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', '#16473e');
	const icon = await page.locator('link[rel="icon"]').getAttribute('href');
	const response = await request.get(`/${icon}`);
	expect(response.status()).toBe(200);
	expect(response.headers()['content-type']).toContain('image/svg+xml');
});
