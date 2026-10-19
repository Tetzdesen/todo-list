# todo-list

API REST simples para gerenciar tarefas (todo-list), com uma interface web, feita para estudo de Spring Boot.

**Stack:** Java 21, Spring Boot 3.5, Maven, Spring Web, Spring Data JPA, H2 (em memória), Bean Validation, Lombok e springdoc-openapi.

## Como rodar

Pré-requisito: JDK 21. O Maven não precisa estar instalado, porque o projeto usa o Maven Wrapper.

```bash
./mvnw spring-boot:run
```

A aplicação sobe em `http://localhost:8080`.

| Recurso      | URL                                     |
|--------------|-----------------------------------------|
| Interface web | http://localhost:8080/                |
| API          | http://localhost:8080/api/todos         |
| Swagger UI   | http://localhost:8080/swagger-ui.html   |
| OpenAPI JSON | http://localhost:8080/v3/api-docs       |
| Console H2   | http://localhost:8080/h2-console        |

No console H2, use a JDBC URL `jdbc:h2:mem:tododb`, o usuário `sa` e a senha vazia.

> O banco é em memória: os dados são perdidos sempre que a aplicação reinicia.

### Interface web

A página em `http://localhost:8080/` é feita em HTML, CSS e JavaScript puro, sem build. O próprio Spring Boot a serve a partir de `src/main/resources/static/`. Nela é possível listar as tarefas (com filtro **Todas / Pendentes / Concluídas**), criar, editar, concluir/reabrir e remover tarefas. Os erros de validação da API aparecem junto aos campos.

Com a aplicação rodando via `./mvnw spring-boot:run`, alterações nesses arquivos aparecem depois de recompilar (`./mvnw process-resources` ou o build da IDE) e recarregar a página.

### Testes

```bash
./mvnw test     # só os testes
./mvnw verify   # build completo
```

## Endpoints

| Método   | Caminho                    | Descrição                                          | Sucesso          |
|----------|----------------------------|----------------------------------------------------|------------------|
| `GET`    | `/api/todos`               | Lista as tarefas (da mais recente para a mais antiga); filtro opcional `?completed=true\|false` | `200 OK` |
| `GET`    | `/api/todos/{id}`          | Busca uma tarefa                                   | `200 OK`         |
| `POST`   | `/api/todos`               | Cria uma tarefa (`title` obrigatório, `description` opcional) | `201 Created` + `Location` |
| `PUT`    | `/api/todos/{id}`          | Atualiza `title`, `description` e `completed` (se `completed` for omitido, o valor atual é mantido) | `200 OK` |
| `PATCH`  | `/api/todos/{id}/toggle`   | Alterna entre concluída e pendente (sem corpo)     | `200 OK`         |
| `DELETE` | `/api/todos/{id}`          | Remove uma tarefa                                  | `204 No Content` |

### Regras de validação

- `title`: obrigatório, não pode ser vazio nem só espaços e tem no máximo 100 caracteres.
- `description`: opcional, com no máximo 500 caracteres.
- `id`, `completed`, `createdAt` e `updatedAt` enviados no `POST` são ignorados.

### Formato de erro

Todas as respostas 4xx usam o mesmo formato. Erros de validação incluem também `fieldErrors`:

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
| Tarefa inexistente ou rota inexistente    | `404`  |
| Método HTTP não suportado no caminho      | `405`  |
| `Content-Type` diferente de JSON          | `415`  |

## Exemplos com `curl`

Criar uma tarefa:

```bash
curl -i -X POST http://localhost:8080/api/todos \
  -H 'Content-Type: application/json' \
  -d '{"title": "Estudar Spring", "description": "Capítulo 3"}'
```

Listar todas as tarefas e listar só as pendentes:

```bash
curl http://localhost:8080/api/todos
curl 'http://localhost:8080/api/todos?completed=false'
```

Buscar uma tarefa:

```bash
curl http://localhost:8080/api/todos/1
```

Atualizar uma tarefa:

```bash
curl -X PUT http://localhost:8080/api/todos/1 \
  -H 'Content-Type: application/json' \
  -d '{"title": "Estudar Spring Boot", "description": null, "completed": true}'
```

Alternar a conclusão:

```bash
curl -X PATCH http://localhost:8080/api/todos/1/toggle
```

Remover uma tarefa:

```bash
curl -i -X DELETE http://localhost:8080/api/todos/1
```

Erro de validação:

```bash
curl -i -X POST http://localhost:8080/api/todos \
  -H 'Content-Type: application/json' \
  -d '{"title": "   "}'
```

## Estrutura

```
src/main/java/com/exemplo/todo
├── TodoApplication.java
├── controller/   TodoController
├── service/      TodoService
├── repository/   TodoRepository
├── model/        Todo
├── dto/          TodoCreateRequest, TodoUpdateRequest, TodoResponse, ErrorResponse, FieldError
└── exception/    TodoNotFoundException, GlobalExceptionHandler

src/main/resources/static
├── index.html      estrutura da página
├── css/styles.css  estilos (tema claro e escuro)
└── js/app.js       chamadas à API e renderização
```
