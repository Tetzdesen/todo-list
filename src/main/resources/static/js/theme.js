// Tema claro ou noturno. A escolha fica salva no navegador; sem escolha, segue o tema do sistema.
// Carregado no <head> sem defer, para o tema valer antes de a página aparecer (sem piscar claro).
(() => {
	const STORAGE_KEY = 'tema';
	const root = document.documentElement;
	const systemDark = window.matchMedia('(prefers-color-scheme: dark)');

	// o armazenamento pode estar bloqueado (janela anônima, cookies desativados): aí o tema não é lembrado
	function savedTheme() {
		try {
			return localStorage.getItem(STORAGE_KEY);
		} catch {
			return null;
		}
	}

	function saveTheme(theme) {
		try {
			localStorage.setItem(STORAGE_KEY, theme);
		} catch {
			// segue funcionando, só não lembra na próxima visita
		}
	}

	function currentTheme() {
		return savedTheme() ?? (systemDark.matches ? 'dark' : 'light');
	}

	// o botão mostra o tema para o qual vai mudar
	function apply(theme) {
		root.dataset.theme = theme;
		for (const button of document.querySelectorAll('[data-theme-toggle]')) {
			button.textContent = theme === 'dark' ? 'Modo claro' : 'Modo noturno';
		}
	}

	apply(currentTheme());

	systemDark.addEventListener('change', () => {
		if (!savedTheme()) {
			apply(currentTheme());
		}
	});

	document.addEventListener('DOMContentLoaded', () => {
		apply(currentTheme());
		for (const button of document.querySelectorAll('[data-theme-toggle]')) {
			button.addEventListener('click', () => {
				const next = root.dataset.theme === 'dark' ? 'light' : 'dark';
				saveTheme(next);
				apply(next);
			});
		}
	});
})();
