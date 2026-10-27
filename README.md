# todo-list

API REST simples para gerenciar tarefas (todo-list), com uma interface web e contas de usuário, feita para estudo de Spring Boot.

**Stack:** Java 21, Spring Boot 3.5, Maven, Spring Web, Spring Data JPA, Flyway, Spring Security (sessão + login com Google via OAuth2/OpenID Connect), H2 (em memória), PostgreSQL (produção), Bean Validation, Lombok e springdoc-openapi.

## Como rodar

Pré-requisito: JDK 21. O Maven não precisa estar instalado, porque o projeto usa o Maven Wrapper.

```bash
./mvnw spring-boot:run
```

A aplicação sobe em `http://localhost:8080`.

| Recurso       | URL                                     |
|---------------|-----------------------------------------|
| Interface web | http://localhost:8080/                  |
| Tela de entrada | http://localhost:8080/login.html      |
| API           | http://localhost:8080/api/todos         |
| Swagger UI    | http://localhost:8080/swagger-ui.html   |
| OpenAPI JSON  | http://localhost:8080/v3/api-docs       |
| Console H2    | http://localhost:8080/h2-console        |

No console H2, use a JDBC URL `jdbc:h2:mem:tododb`, o usuário `sa` e a senha vazia.

> O banco é em memória: os dados (inclusive as contas) são perdidos sempre que a aplicação reinicia.

### Contas de usuário

Cada pessoa tem a própria lista de tarefas. Dá para entrar de duas formas:

- **E-mail e senha**: crie a conta na aba **Criar conta** da tela de entrada (senha de 8 a 72 caracteres).
- **Google**: o botão **Continuar com Google** só aparece quando as credenciais do Google estão configuradas (veja abaixo). Se o e-mail verificado do Google já tiver uma conta com senha, a conta passa a ser do Google e a senha deixa de valer: o cadastro com senha não confirma o e-mail, então quem o criou pode não ser o dono do endereço.

Depois de 5 senhas erradas para o mesmo e-mail, ou de 20 tentativas erradas vindas do mesmo IP, o login fica bloqueado por até 15 minutos (resposta `429` com o cabeçalho `Retry-After`). As contagens ficam na memória da aplicação e zeram a cada reinício.

Sem login, a interface leva para a tela de entrada e a API responde `401`. A interface web, o Swagger UI e o console H2 abrem sem login; só os dados exigem autenticação.

### Login com Google (opcional)

1. No [Google Cloud Console](https://console.cloud.google.com/apis/credentials), crie uma credencial **ID do cliente OAuth** do tipo **Aplicativo da Web**.
2. Em **URIs de redirecionamento autorizados**, adicione `http://localhost:8080/login/oauth2/code/google` (e, em produção, `https://<seu-app>.onrender.com/login/oauth2/code/google`).
3. Rode a aplicação com as variáveis de ambiente:

```bash
GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=... ./mvnw spring-boot:run
```

Sem essas variáveis, a aplicação funciona normalmente, só com e-mail e senha.

### Interface web

A página em `http://localhost:8080/` é feita em HTML, CSS e JavaScript puro, sem build. O próprio Spring Boot a serve a partir de `src/main/resources/static/`. Nela é possível listar as tarefas (nos filtros **Todas / Pendentes / Concluídas**), criar, editar, concluir/reabrir e apagar tarefas. Os erros de validação da API aparecem junto aos campos. A barra superior mostra o usuário logado, o botão **Sair** e o botão que troca entre o **modo claro** e o **modo noturno** (a escolha fica salva no navegador; sem escolha, vale o tema do sistema).

Com a aplicação rodando via `./mvnw spring-boot:run`, alterações nesses arquivos aparecem depois de recompilar (`./mvnw process-resources` ou o build da IDE) e recarregar a página.

### Testes

```bash
./mvnw test     # só os testes
./mvnw verify   # build completo
```

## Deploy (Render)

O `Dockerfile` gera a imagem da aplicação. Em produção, defina:

| Variável                 | Valor                                                    |
|--------------------------|----------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE` | `prod` (usa PostgreSQL, HTTPS atrás do proxy e cookie de sessão seguro) |
| `DATABASE_URL`, `DB_USER`, `DB_PASSWORD` | conexão com o PostgreSQL                 |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | opcionais, para o login com Google     |

O schema do banco é criado e atualizado pelas migrações do Flyway em `src/main/resources/db/migration`; o Hibernate só confere se ele bate com as entidades (`ddl-auto=validate`). Para mudar o schema, crie um novo arquivo `V<n>__descricao.sql` e nunca altere uma migração que já rodou.

Um banco criado antes do Flyway (pelo antigo `ddl-auto=update`) é marcado como versão 1 no primeiro deploy, e as migrações seguintes rodam normalmente. A `V2` **apaga as tarefas sem dono** (criadas antes das contas de usuário) e torna o dono obrigatório. Para guardar essas tarefas, rode antes do deploy `UPDATE todos SET owner_id = <id do usuário> WHERE owner_id IS NULL;`.

As sessões ficam na memória da aplicação: a cada deploy ou reinício, todos precisam entrar de novo.

## Endpoints

### Autenticação

| Método | Caminho                          | Descrição                                                  | Sucesso       |
|--------|----------------------------------|------------------------------------------------------------|---------------|
| `GET`  | `/api/auth/providers`            | Informa se o login com Google está disponível (`{"google": true\|false}`); sem login | `200 OK` |
| `POST` | `/api/auth/register`             | Cria a conta (`name`, `email`, `password`) e já entra       | `201 Created` |
| `POST` | `/api/auth/login`                | Entra com `email` e `password`                              | `200 OK`      |
| `GET`  | `/api/auth/me`                   | Dados do usuário logado (`id`, `name`, `email`, `providers`) | `200 OK`     |
| `POST` | `/api/auth/logout`               | Encerra a sessão                                            | `204 No Content` |
| `GET`  | `/oauth2/authorization/google`   | Inicia o login com Google (redireciona para o Google)       | `302`         |

### Tarefas (exigem login)

Cada usuário só vê e altera as próprias tarefas; uma tarefa de outra pessoa responde `404`, como se não existisse.

| Método   | Caminho                    | Descrição                                          | Sucesso          |
|----------|----------------------------|----------------------------------------------------|------------------|
| `GET`    | `/api/todos`               | Lista as tarefas em páginas (da mais recente para a mais antiga); parâmetros opcionais `completed=true\|false`, `page` (a partir de 0) e `size` (1 a 100, padrão 20) | `200 OK` |
| `GET`    | `/api/todos/{id}`          | Busca uma tarefa                                   | `200 OK`         |
| `POST`   | `/api/todos`               | Cria uma tarefa (`title` obrigatório, `description` opcional) | `201 Created` + `Location` |
| `PATCH`  | `/api/todos/{id}`          | Altera só os campos enviados (`title`, `description`, `completed`); `version` opcional evita sobrescrever mudanças de outra aba | `200 OK` |
| `DELETE` | `/api/todos/{id}`          | Remove uma tarefa                                  | `204 No Content` |

### Sessão e token CSRF

A autenticação usa o cookie de sessão (`JSESSIONID`). Requisições que alteram dados (`POST`, `PUT`, `PATCH`, `DELETE`), inclusive cadastro, login e logout, precisam também do token anti-CSRF: o servidor o entrega no cookie `XSRF-TOKEN` (por exemplo, ao chamar `GET /api/auth/providers`) e ele deve voltar no cabeçalho `X-XSRF-TOKEN`. Sem o token, a resposta é `403`.

No **Swagger UI**, entre antes pela interface web na mesma janela do navegador (o cookie de sessão é enviado sozinho) e, nas operações que alteram dados, copie o valor do cookie `XSRF-TOKEN` (DevTools → Application → Cookies) para um cabeçalho `X-XSRF-TOKEN`.

### Paginação

`GET /api/todos` devolve uma página:

```json
{
  "content": [ { "id": 3, "title": "Comprar pão", "...": "..." } ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

### Alteração e conflitos

Cada tarefa tem um número `version`, que sobe a cada alteração. Mande no `PATCH` a `version` que você leu: se a tarefa mudou desde então (em outra aba, por exemplo), a resposta é `409` e nada é alterado. Sem `version`, a alteração é aplicada sobre o estado atual.

```json
{ "completed": true, "version": 3 }
```

### Regras de validação

- `title`: obrigatório, não pode ser vazio nem só espaços e tem no máximo 100 caracteres.
- `description`: opcional, com no máximo 500 caracteres.
- `id`, `completed`, `createdAt`, `updatedAt` e `version` enviados no `POST` são ignorados.
- `PATCH`: campos ausentes não mudam; `"description": null` apaga a descrição; `title` e `completed` não podem ser `null`.
- Listagem: `page` a partir de 0 e `size` de 1 a 100.
- Cadastro: `name` obrigatório (até 100 caracteres), `email` válido (até 254, único sem diferenciar maiúsculas) e `password` com 8 a 72 caracteres (no máximo 72 bytes: letras acentuadas contam 2).

### Formato de erro

Todas as respostas de erro usam o mesmo formato (erros inesperados são `500`, sem detalhes internos). Erros de validação incluem também `fieldErrors`:

```json
{
  "timestamp": "2026-10-19T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Dados inválidos",
  "path": "/api/todos",
  "fieldErrors": [
    { "field": "title", "message": "não deve estar em branco" }
  ]
}
```

| Situação                                  | Status |
|-------------------------------------------|--------|
| Dados inválidos ou JSON malformado        | `400`  |
| `id` que não é número (`/api/todos/abc`)  | `400`  |
| Sem login, ou e-mail/senha inválidos      | `401`  |
| Token CSRF ausente ou inválido            | `403`  |
| Tarefa inexistente, de outro usuário, ou rota inexistente | `404`  |
| Método HTTP não suportado no caminho      | `405`  |
| E-mail já cadastrado, ou tarefa alterada em outro lugar (`version` antiga) | `409`  |
| `Content-Type` diferente de JSON          | `415`  |
| Muitas tentativas de login erradas        | `429`  |

## Exemplos com `curl`

O `curl` guarda os cookies (sessão e token CSRF) no arquivo `cookies.txt`. A função `csrf` lê o token atual do arquivo, porque ele muda a cada login.

```bash
csrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' cookies.txt | tail -1; }

# pega o token CSRF
curl -s -c cookies.txt -b cookies.txt http://localhost:8080/api/auth/providers

# cria a conta (e já entra)
curl -i -c cookies.txt -b cookies.txt -X POST http://localhost:8080/api/auth/register \
  -H "X-XSRF-TOKEN: $(csrf)" -H 'Content-Type: application/json' \
  -d '{"name": "Ana", "email": "ana@exemplo.com", "password": "segredo123"}'

# ou entra numa conta existente
curl -i -c cookies.txt -b cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H "X-XSRF-TOKEN: $(csrf)" -H 'Content-Type: application/json' \
  -d '{"email": "ana@exemplo.com", "password": "segredo123"}'
```

Criar uma tarefa:

```bash
curl -i -b cookies.txt -X POST http://localhost:8080/api/todos \
  -H "X-XSRF-TOKEN: $(csrf)" -H 'Content-Type: application/json' \
  -d '{"title": "Estudar Spring", "description": "Capítulo 3"}'
```

Listar as tarefas, só as pendentes e a segunda página de 10 em 10:

```bash
curl -b cookies.txt http://localhost:8080/api/todos
curl -b cookies.txt 'http://localhost:8080/api/todos?completed=false'
curl -b cookies.txt 'http://localhost:8080/api/todos?page=1&size=10'
```

Buscar uma tarefa:

```bash
curl -b cookies.txt http://localhost:8080/api/todos/1
```

Mudar o título e apagar a descrição:

```bash
curl -b cookies.txt -X PATCH http://localhost:8080/api/todos/1 \
  -H "X-XSRF-TOKEN: $(csrf)" -H 'Content-Type: application/json' \
  -d '{"title": "Estudar Spring Boot", "description": null}'
```

Concluir, só se ninguém alterou a tarefa desde a leitura da versão 0:

```bash
curl -b cookies.txt -X PATCH http://localhost:8080/api/todos/1 \
  -H "X-XSRF-TOKEN: $(csrf)" -H 'Content-Type: application/json' \
  -d '{"completed": true, "version": 0}'
```

Remover uma tarefa:

```bash
curl -i -b cookies.txt -X DELETE http://localhost:8080/api/todos/1 -H "X-XSRF-TOKEN: $(csrf)"
```

Erro de validação:

```bash
curl -i -b cookies.txt -X POST http://localhost:8080/api/todos \
  -H "X-XSRF-TOKEN: $(csrf)" -H 'Content-Type: application/json' \
  -d '{"title": "   "}'
```

Sair:

```bash
curl -i -c cookies.txt -b cookies.txt -X POST http://localhost:8080/api/auth/logout -H "X-XSRF-TOKEN: $(csrf)"
```

## Estrutura

```
src/main/java/com/exemplo/todo
├── TodoApplication.java
├── controller/   TodoController, AuthController
├── service/      TodoService, UserService
├── repository/   TodoRepository, UserRepository
├── model/        Todo, User
├── security/     SecurityConfig, AppUserPrincipal, AppUserDetailsService, GoogleOAuthConfig,
│                 GoogleOidcUserService, JsonSecurityErrorHandler, LoginAttemptLimiter
├── dto/          TodoCreateRequest, TodoPatchRequest, TodoResponse, RegisterRequest, LoginRequest,
│                 UserResponse, PageResponse, ErrorResponse, FieldError
└── exception/    TodoNotFoundException, EmailAlreadyUsedException, InvalidFieldException,
                  GoogleLoginException, AccountNotFoundException, TooManyLoginAttemptsException,
                  TodoConflictException, GlobalExceptionHandler

src/main/resources/db/migration   migrações do Flyway (V1 schema inicial, V2 dono obrigatório, V3 versão da tarefa)

src/main/resources/static
├── index.html      lista de tarefas
├── login.html      tela de entrada (entrar, criar conta, Google)
├── css/styles.css  estilos (tema claro e escuro)
└── js/
    ├── api.js      chamadas à API, token CSRF e erros por campo
    ├── app.js      lista de tarefas e sessão
    ├── login.js    tela de entrada
    └── theme.js    modo claro e noturno
```
