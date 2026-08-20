# FinBank — учебное fintech-приложение

Небольшой веб-банк на **Spring Boot 3 / Java 17**, специально сделанный как
"подопытный кролик" для практики автотестирования: есть и серверный HTML UI
(Thymeleaf), и REST API с JWT — то есть можно тренироваться и на Playwright,
и на API-тестах (REST Assured / JUnit 5), которые мы собирали раньше.

Данные хранятся в базе H2 в памяти — при каждом перезапуске приложение
поднимается "с чистого листа" плюс сид демо-пользователей, так что тесты
всегда стартуют с предсказуемого состояния.

## Функциональность

- **Регистрация и логин.** Веб-UI — сессия/форма (Spring Security form
  login). API — `POST /api/auth/register`, `POST /api/auth/login`, выдают
  JWT. Логин реализован по спецификации `specs/Login-Requirements.md` —
  единый anti-enumeration ответ `401` с текстом `"Неверный email или
  пароль."` для неверных данных, несуществующего email и заблокированного
  аккаунта; поле `User.status` (`ACTIVE`/`BLOCKED`) определяет, пускает ли
  логин. Полевые правила (email trim/case-insensitivity, password никогда
  не обрезается, обязательность полей, `data-testid`) — отдельно в
  `specs/Login-Field-Requirements.md`. **Важно:** `data-testid` на странице
  логина переименованы на `email-input`/`password-input`/`login-button` —
  если у вас уже есть тесты на старые `login-email`/`login-password`/
  `login-submit`, их нужно обновить.
- **Счета и балансы.** При регистрации клиенту открывается текущий счёт с
  балансом 1000.00 USD. `GET /api/accounts` — список счетов текущего
  пользователя.
- **Переводы между счетами.** Форма `/transfer` и `POST /api/transfers`.
  Есть проверки: сумма > 0, перевод не на тот же счёт, перевод только со
  своего счёта, достаточно средств, совпадение валют. История операций —
  `/accounts/{номер}/transactions` и `GET /api/accounts/{номер}/transactions`.

## Демо-пользователи (создаются автоматически при старте)

| Email | Пароль | Статус |
|---|---|---|
| demo@finbank.dev | FinbankDemoPassphrase | ACTIVE |
| alice@finbank.dev | FinbankDemoPassphrase | ACTIVE |
| blocked@finbank.dev | FinbankDemoPassphrase | BLOCKED — логин должен всегда падать с 401 |

Точные номера их счетов печатаются в консоль при запуске приложения —
смотрите блок `FinBank demo data ready` в логах.

## Запуск

Требуется JDK 17+ и Maven 3.9+. Интернет нужен один раз — при первой
сборке Maven скачает зависимости с Maven Central.

```bash
mvn spring-boot:run
```

Приложение поднимется на `http://localhost:8080`.

- UI: `http://localhost:8080/login`
- Swagger UI (описание REST API): `http://localhost:8080/swagger-ui.html`
- H2-консоль (посмотреть данные в БД): `http://localhost:8080/h2-console`
  (JDBC URL: `jdbc:h2:mem:finbank`, user: `sa`, пароль пустой)

## Карта эндпоинтов

**Веб (сессия, форма-логин):**
- `GET/POST /register`
- `GET /login`, `POST /login` (обрабатывает Spring Security)
- `GET /logout`
- `GET /dashboard`
- `GET/POST /transfer`
- `GET /accounts/{accountNumber}/transactions`

**API (JWT, `Authorization: Bearer <token>`):**
- `POST /api/auth/register` — тело: `{firstName, lastName, email, phone, dateOfBirth, password, confirmPassword, termsAccepted}`.
  Правила валидации полей (границы, regex, коды ошибок) — см.
  `specs/Registration-Field-Validation-Rules.md`; правила пароля обновлены
  и заданы отдельно в `specs/Password-Requirements-Acceptance-Criteria.md`
  (15–128 символов, без требований к составу, Argon2id).
- `POST /api/auth/login` — тело: `{email, password}` → `{token, expiresInSeconds, email}`
- `GET /api/accounts` — счета текущего пользователя
- `GET /api/accounts/{accountNumber}/transactions`
- `POST /api/transfers` — тело: `{fromAccountNumber, toAccountNumber, amount, description}`

Ошибки API возвращаются в едином формате. Для регистрации (и в целом для
полевых ошибок) `details[]` содержит стабильный `code` на каждое поле —
именно на него стоит завязывать ассерты в тестах, а не на текст `message`:
```json
{
  "timestamp": "...",
  "status": 422,
  "error": "VALIDATION_ERROR",
  "message": "Registration request failed validation: 2 error(s)",
  "details": [
    { "field": "email", "code": "EMAIL_INVALID_FORMAT", "message": "Email must be a valid address" },
    { "field": "password", "code": "PASSWORD_TOO_SHORT", "message": "Password must be at least 15 characters" }
  ],
  "path": "/api/auth/register"
}
```
Ошибки без разбивки по полям (например, `InsufficientFundsException` при
переводе) возвращают `details: []` и текст в `message`.

## Идеи, что тестировать (готовый чек-лист для тест-кейсов)

**UI:**
- успешная регистрация → редирект на логин → успешный логин → дашборд;
- регистрация с уже существующим email → ошибка, форма не сбрасывается;
- регистрация с коротким паролем / невалидным email → ошибки валидации под
  полями;
- логин с неверным паролем → баннер ошибки, пользователь остаётся на /login;
- перевод на несуществующий счёт;
- перевод суммы больше баланса → ошибка "Insufficient funds";
- перевод на тот же счёт → ошибка;
- перевод с отрицательной/нулевой суммой;
- перевод дробных сумм (проверка округления, например 33.335);
- успешный перевод → баланс на дашборде и в истории транзакций обновился
  корректно у обеих сторон;
- логаут → защищённые страницы недоступны без повторного логина.

**API:**
- регистрация/логин — счастливый путь, дублирующийся email, невалидные
  данные (400);
- запрос `/api/accounts` без токена → 401;
- запрос `/api/accounts` с истёкшим/битым токеном → 401;
- перевод через API с чужого счёта (не принадлежащего пользователю по
  токену) → 400;
- перевод между счетами в разных валютах (если добавите валюту, отличную
  от USD, вручную через H2-консоль) → ошибка "Cross-currency";
- гонка: два параллельных перевода с одного счёта на грани баланса — не
  должно уйти в минус (в сервисе уже стоит pessimistic lock — хороший повод
  написать тест, который это доказывает или, наоборот, найдёт баг).

## Известные "педагогические" особенности (не баги, а вводные)

- Кросс-валютные переводы не поддерживаются (нет конвертации курса) —
  специально, чтобы было что попросить "доделать" или протестировать как
  ограничение.
- JWT-секрет захардкожен для простоты демо — в реальном проекте так делать
  нельзя, вынесено в `finbank.jwt.secret` (env `FINBANK_JWT_SECRET`).
- Проверка авторизации на чужой счёт при просмотре истории транзакций через
  API возвращает 400, а не 403 — реальный API, скорее всего, вернёт 403;
  это осознанное упрощение, при желании поправьте под свои требования.
