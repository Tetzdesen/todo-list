import { clearFieldErrors, request, showFieldErrors } from './api.js';

const elements = {
	tabs: document.querySelectorAll('.auth-tab'),
	loginForm: document.querySelector('#login-form'),
	registerForm: document.querySelector('#register-form'),
	google: document.querySelector('#google-login'),
	errorBanner: document.querySelector('#error-banner'),
	errorMessage: document.querySelector('#error-message'),
	errorClose: document.querySelector('#error-close'),
};

// ---------- Mensagens de erro ----------

function showBanner(message) {
	elements.errorMessage.textContent = message;
	elements.errorBanner.hidden = false;
}

function hideBanner() {
	elements.errorBanner.hidden = true;
	elements.errorMessage.textContent = '';
}

// ---------- Abas ----------

function selectTab(selected) {
	for (const tab of elements.tabs) {
		const active = tab === selected;
		tab.setAttribute('aria-selected', String(active));
		tab.tabIndex = active ? 0 : -1;
		document.getElementById(tab.getAttribute('aria-controls')).hidden = !active;
	}
	hideBanner();
	const form = document.getElementById(selected.getAttribute('aria-controls'));
	clearFieldErrors(form);
	form.querySelector('input').focus();
}

// ---------- Envio ----------

// replace: voltar no navegador a partir da lista não reabre a tela de entrada
function goToList() {
	location.replace('./');
}

async function submit(form, path, data) {
	hideBanner();
	clearFieldErrors(form);
	const button = form.querySelector('[type="submit"]');
	button.disabled = true;
	try {
		await request(path, { method: 'POST', body: JSON.stringify(data) });
		goToList();
	} catch (error) {
		const messages = error.fieldErrors.length ? showFieldErrors(form, error.fieldErrors) : [error.message];
		for (const message of messages) {
			showBanner(message);
		}
		// credenciais inválidas: mantém o e-mail e limpa só a senha
		if (form === elements.loginForm && error.status === 401) {
			form.elements.password.value = '';
			form.elements.password.focus();
		}
	} finally {
		button.disabled = false;
	}
}

// ---------- Eventos ----------

for (const tab of elements.tabs) {
	tab.addEventListener('click', () => selectTab(tab));
}

elements.errorClose.addEventListener('click', hideBanner);

elements.loginForm.addEventListener('submit', (event) => {
	event.preventDefault();
	const form = elements.loginForm;
	submit(form, '/api/auth/login', {
		email: form.elements.email.value,
		password: form.elements.password.value,
	});
});

elements.registerForm.addEventListener('submit', (event) => {
	event.preventDefault();
	const form = elements.registerForm;
	submit(form, '/api/auth/register', {
		name: form.elements.name.value,
		email: form.elements.email.value,
		password: form.elements.password.value,
	});
});

// ---------- Início ----------

async function start() {
	if (new URLSearchParams(location.search).get('error') === 'google') {
		showBanner('Não foi possível entrar com o Google. Tente de novo ou use e-mail e senha.');
	}
	// providers também entrega o cookie com o token CSRF, necessário para entrar ou criar a conta
	try {
		const providers = await request('/api/auth/providers');
		elements.google.hidden = !providers.google;
	} catch (error) {
		showBanner(error.message);
		return;
	}
	// quem já tem sessão vai direto para a lista
	try {
		await request('/api/auth/me');
		goToList();
	} catch {
		elements.loginForm.elements.email.focus();
	}
}

start();
