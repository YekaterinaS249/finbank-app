# FinBank v2 — Design Document (Application Under Test для QA Automation Framework)

**Тип документа:** Проектный документ перед реализацией (design-first)
**Цель приложения:** не production-банк, а реалистичный, стабильный,
хорошо тестируемый **Application Under Test (AUT)** для демонстрации
профессионального Java QA Automation Framework (UI, API, DB, integration,
E2E, security-oriented, negative/boundary testing).

Это НЕ спецификация того, что уже есть в FinBank v1 (тот проект остаётся
как есть, ниже — проектирование **новой, более полной версии**,
FinBank v2, с нуля учитывающей все требования из брифа).

---

## 1. System Overview

FinBank v2 — веб-банк с базовым, но реалистичным набором fintech-сценариев:
регистрация с email-верификацией, аутентификация по JWT, роли USER/ADMIN,
банковские счета, транзакции (депозит/снятие/перевод), история операций с
пагинацией/фильтрацией/сортировкой, восстановление пароля, rate limiting,
audit log. Все механизмы намеренно упрощены до уровня, достаточного для
тестирования, но без реального KYC/AML/платёжных интеграций.

Ключевой принцип: **каждое бизнес-правило должно быть детерминированным и
проверяемым** — без случайных задержек, без скрытого нестабильного
поведения, с предсказуемыми кодами ошибок и текстами сообщений.

## 2. Architecture

```
┌─────────────┐      HTTPS-ready       ┌───────────────────┐      JDBC       ┌────────────┐
│  Frontend   │ ─────────────────────▶ │   Backend REST API │ ──────────────▶│  Database  │
│ (Thymeleaf  │ ◀───────────────────── │   (Spring Boot)    │ ◀───────────────│(PostgreSQL)│
│  server-    │      JSON / HTML       │                     │                 └────────────┘
│  rendered)  │                        │  - Auth (JWT)       │
└─────────────┘                        │  - Authorization    │      SMTP/API       ┌──────────────┐
                                        │  - Business logic   │ ───────────────────▶│ Email Service │
                                        │  - Rate limiting    │                      │  (Mailtrap /  │
                                        │  - Audit logging    │                      │  test SMTP)   │
                                        └────────────────────┘                      └──────────────┘
```

- **Frontend:** server-rendered HTML (Thymeleaf) поверх того же Spring Boot
  приложения — минимизирует инфраструктурную сложность (не нужен отдельный
  SPA-билд/CORS-контур), но при этом даёт полноценный UI для Playwright.
  Каждый значимый элемент — форма, инпут, кнопка, таблица, дропдаун,
  фильтр, пагинация, модалка — получает `data-testid`.
- **Backend REST API:** тот же Spring Boot процесс, JSON API под `/api/**`,
  полностью независим от UI-контроллеров (как в FinBank v1) — так UI и API
  тестируются как два разных, но согласованных интерфейса к одной бизнес-логике.
- **Database:** PostgreSQL (вместо H2 из v1) — чтобы QA мог реально
  тренировать SQL-проверки, миграции (Flyway), отдельную test-БД,
  консистентность данных между UI/API/DB. Локально поднимается через Docker
  Compose одной командой.
- **Email service:** Mailtrap (или любой SMTP-совместимый test-сервис) —
  реальные письма никуда не уходят, но у Mailtrap есть свой API для
  программного чтения полученных писем, что позволяет автоматизировать
  E2E-сценарий "зарегистрировался → получил письмо → извлёк токен →
  подтвердил email" целиком через тесты, без ручного клика по ссылке.
- **Аутентификация:** JWT (access token), передаётся в заголовке
  `Authorization: Bearer <token>` для API; для UI — обычная серверная сессия
  (как в v1), чтобы не городить SPA-хранение токена в браузере.
- **Rate limiting:** in-memory (bucket per IP + per identifier, sliding
  window), без внешних зависимостей (Redis и т.п. — избыточно для учебного
  проекта), с test-only endpoint для сброса лимитов между тестовыми
  прогонами.

## 3. Modules

| Модуль | Ответственность |
|---|---|
| `auth` | Регистрация, email-верификация, логин, логаут, forgot/reset password, JWT issuing/validation |
| `users` | Профиль пользователя, обновление данных, административный просмотр/блокировка пользователей |
| `accounts` | Банковские счета, баланс, статус счёта |
| `transactions` | Депозит, снятие, перевод, история операций |
| `admin` | Административные операции (список пользователей, блокировка, просмотр всех транзакций) |
| `security` | JWT-фильтр, password hashing, rate limiting, audit logging — сквозные механизмы, не самостоятельный бизнес-модуль |
| `testsupport` | Только в non-production профиле: сид тестовых пользователей, очистка данных, сброс rate limit, чтение писем Mailtrap через API-обёртку |

## 4. Database ER Model

```
users
├─ id (PK, UUID)
├─ first_name
├─ last_name
├─ email            (UNIQUE, NOT NULL)
├─ phone            (UNIQUE, NOT NULL)
├─ date_of_birth
├─ password_hash
├─ role             (ENUM: USER, ADMIN)
├─ status           (ENUM: PENDING_VERIFICATION, ACTIVE, BLOCKED)
├─ terms_accepted_at
├─ created_at
└─ updated_at

accounts
├─ id (PK, UUID)
├─ user_id (FK → users.id, NOT NULL)
├─ account_number   (UNIQUE, NOT NULL)
├─ currency         (default 'USD')
├─ balance          (NUMERIC(19,4), NOT NULL, >= 0 constraint)
├─ status           (ENUM: ACTIVE, BLOCKED, CLOSED)
└─ created_at

transactions
├─ id (PK, UUID)
├─ type              (ENUM: DEPOSIT, WITHDRAWAL, TRANSFER)
├─ status            (ENUM: PENDING, COMPLETED, FAILED)
├─ amount            (NUMERIC(19,4), NOT NULL, > 0 constraint)
├─ currency
├─ sender_account_id   (FK → accounts.id, NULLABLE — пусто для DEPOSIT)
├─ receiver_account_id (FK → accounts.id, NULLABLE — пусто для WITHDRAWAL)
├─ description
├─ created_at
└─ failure_reason     (NULLABLE — заполняется при status = FAILED)

verification_tokens
├─ id (PK, UUID)
├─ user_id (FK → users.id, NOT NULL)
├─ token_hash        (UNIQUE, NOT NULL — хранится хэш, не сырой токен)
├─ expires_at
├─ used_at            (NULLABLE)
└─ created_at

password_reset_tokens
├─ id (PK, UUID)
├─ user_id (FK → users.id, NOT NULL)
├─ token_hash        (UNIQUE, NOT NULL)
├─ expires_at
├─ used_at            (NULLABLE)
└─ created_at

audit_logs
├─ id (PK, UUID)
├─ user_id (FK → users.id, NULLABLE — есть события без пользователя, напр. failed login по несуществующему email)
├─ event_type         (REGISTRATION, EMAIL_VERIFIED, LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT,
│                       PASSWORD_RESET_REQUESTED, PASSWORD_RESET_COMPLETED, PROFILE_UPDATED,
│                       TRANSFER_COMPLETED, ADMIN_BLOCK, ADMIN_UNBLOCK)
├─ status             (SUCCESS, FAILURE)
├─ metadata           (JSONB — IP, user-agent, доп. контекст)
└─ created_at

login_attempts   (вспомогательная таблица для rate limiting, опционально —
                   можно и in-memory, но таблица даёт QA возможность
                   проверять rate limiting через SQL, а не только через API)
├─ id (PK, UUID)
├─ identifier         (email или IP)
├─ endpoint           (LOGIN, REGISTER, VERIFY_EMAIL, FORGOT_PASSWORD)
├─ attempted_at
└─ succeeded          (BOOLEAN)
```

**Связи:** `users 1—1 accounts` (для v2 — один счёт на пользователя,
как и в v1, чтобы не усложнять; multi-account — опционально, см. раздел
Optional). `accounts 1—N transactions` (через `sender_account_id` /
`receiver_account_id`). `users 1—N verification_tokens`,
`users 1—N password_reset_tokens`, `users 1—N audit_logs`.

**Констрейнты, важные для тестирования:** `balance >= 0` на уровне БД (а
не только в коде) — так можно написать тест, который пытается обойти
бизнес-логику напрямую через SQL и убедиться, что БД всё равно не пустит
отрицательный баланс; `amount > 0` на транзакциях; `UNIQUE(email)`,
`UNIQUE(phone)` на `users`.

## 5. User Roles

| Роль | Доступ |
|---|---|
| `USER` | Свой профиль, свой счёт, свои транзакции, переводы со своего счёта |
| `ADMIN` | Всё, что доступно USER для собственного аккаунта, плюс: список всех пользователей, просмотр конкретного пользователя, блокировка/разблокировка пользователя, просмотр всех транзакций |

Ролей ровно две — расширять до RBAC с гранулярными правами избыточно для
целей проекта (см. Not Required).

## 6. User States

**Состояние пользователя (`users.status`):**
```
PENDING_VERIFICATION ──(email подтверждён)──▶ ACTIVE ──(admin block)──▶ BLOCKED
                                                  ▲                         │
                                                  └─────(admin unblock)─────┘
```

**Состояние счёта (`accounts.status`):** создаётся вместе с переходом
пользователя в `ACTIVE`; `ACTIVE` / `BLOCKED` (синхронизируется с
блокировкой пользователя — если ADMIN блокирует пользователя, его счёт
тоже блокируется для операций) / `CLOSED` (зарезервировано, закрытие счёта
— Optional, не обязательно для MVP).

**Состояние транзакции:** `PENDING → COMPLETED` или `PENDING → FAILED`.
Поскольку вся бизнес-логика синхронна и детерминирована (без реальных
платёжных шлюзов), `PENDING` в подавляющем большинстве случаев существует
крайне короткое время (в рамках одной транзакции БД) — но статус
сохраняется в модели специально, чтобы у QA была возможность писать тесты
на разные статусы и, при желании, симулировать "зависшую" транзакцию через
test-support API для более сложных сценариев.

## 7. Main Business Flows

**Flow A — Регистрация → верификация → логин:**
`POST /api/auth/register` → пользователь создан в `PENDING_VERIFICATION`,
письмо с токеном отправлено на email → `POST /api/auth/verify-email` (или
переход по ссылке в UI) → статус `ACTIVE`, создаётся `account` с балансом
1000 USD → `POST /api/auth/login` → JWT.

**Flow B — Перевод денег:**
`POST /api/transfers` (JWT sender) → проверка аутентификации →
авторизации (sender владеет счётом-источником) → существования receiver →
статуса обоих счетов (`ACTIVE`) → достаточности средств → списание у
sender + зачисление receiver в одной транзакции БД → создание записи
`transactions` (`COMPLETED`) → audit log `TRANSFER_COMPLETED` → ответ с
деталями транзакции.

**Flow C — Forgot / Reset password:**
`POST /api/auth/forgot-password` (email) → **всегда** `200 OK` с одинаковым
сообщением независимо от существования email (anti-enumeration — см.
раздел Security) → если email существует, письмо с reset-токеном →
`POST /api/auth/reset-password` (token + new password) → пароль обновлён →
все существующие access-токены пользователя логически инвалидированы (см.
Security) → `POST /api/auth/login` с новым паролем.

**Flow D — Admin блокировка:**
`PUT /api/admin/users/{id}/block` (JWT ADMIN) → пользователь → `BLOCKED`,
его счёт → `BLOCKED`, audit log `ADMIN_BLOCK` → дальнейшие попытки логина
этого пользователя возвращают `403` с понятным кодом `ACCOUNT_BLOCKED`.

**Flow E — E2E "полный круг" (готовый сценарий для демонстрации E2E-теста):**
регистрация → чтение письма из Mailtrap API → верификация → логин →
получение JWT → просмотр счёта → перевод другому пользователю → проверка
истории транзакций (обе стороны) → логаут → попытка обратиться к
защищённому endpoint после логаута → `401`.

## 8. API Endpoint List

Единый формат ошибок для всех эндпоинтов:
```json
{
  "timestamp": "2026-08-19T20:30:00Z",
  "status": 422,
  "error": "VALIDATION_ERROR",
  "message": "Request is invalid",
  "details": [ { "field": "amount", "message": "must be greater than zero" } ]
}
```

| Method | URL | Auth | Роль | Успех | Ошибки |
|---|---|---|---|---|---|
| POST | `/api/auth/register` | нет | — | `201` + user summary (без токена — см. Security REQ ниже) | `400` validation, `409` duplicate email/phone |
| POST | `/api/auth/verify-email` | нет | — | `200` статус `ACTIVE` | `400` invalid token, `409` already used, `410` expired |
| POST | `/api/auth/resend-verification` | нет (email в body) | — | `200` (всегда одинаковый ответ) | `429` rate limited |
| POST | `/api/auth/login` | нет | — | `200` + JWT | `401` invalid credentials / unverified / blocked (единый generic-текст, см. Security), `404` не используется намеренно (anti-enumeration) |
| POST | `/api/auth/logout` | JWT | USER/ADMIN | `204` | `401` |
| POST | `/api/auth/forgot-password` | нет | — | `200` (всегда одинаковый ответ) | `429` rate limited |
| POST | `/api/auth/reset-password` | нет | — | `200` | `400` invalid token, `409` used, `410` expired, `422` password validation |
| GET | `/api/users/me` | JWT | USER/ADMIN | `200` profile | `401` |
| PUT | `/api/users/me` | JWT | USER/ADMIN | `200` updated profile | `401`, `422` validation |
| GET | `/api/accounts` | JWT | USER/ADMIN | `200` список своих счетов | `401` |
| GET | `/api/accounts/{id}` | JWT | владелец / ADMIN | `200` | `401`, `403` чужой счёт (для USER), `404` |
| GET | `/api/transactions` | JWT | USER/ADMIN | `200` пагинированный список своих транзакций, поддержка `?type=&status=&from=&to=&page=&size=&sort=` | `401`, `422` невалидные query-параметры |
| GET | `/api/transactions/{id}` | JWT | участник транзакции / ADMIN | `200` | `401`, `403`, `404` |
| POST | `/api/transfers` | JWT | USER/ADMIN | `201` transaction | `400/422` invalid amount, `404` receiver not found, `409` insufficient funds, `403` blocked/inactive account, `401` |
| GET | `/api/admin/users` | JWT | ADMIN | `200` пагинированный список пользователей, фильтр по `status`/`role` | `401`, `403` |
| GET | `/api/admin/users/{id}` | JWT | ADMIN | `200` | `401`, `403`, `404` |
| PUT | `/api/admin/users/{id}/block` | JWT | ADMIN | `200` | `401`, `403`, `404`, `409` уже заблокирован |
| PUT | `/api/admin/users/{id}/unblock` | JWT | ADMIN | `200` | `401`, `403`, `404`, `409` уже активен |
| GET | `/api/admin/transactions` | JWT | ADMIN | `200` все транзакции с пагинацией/фильтрами | `401`, `403` |
| POST | `/api/test/seed-users` *(только non-prod профиль)* | test API-key | — | `201` созданные тестовые пользователи с заданными состояниями | `403` если вызван вне test-профиля |
| POST | `/api/test/reset` *(только non-prod профиль)* | test API-key | — | `204` очистка тестовых данных + сброс rate limit счётчиков | `403` вне test-профиля |
| GET | `/api/test/mailbox/{email}` *(только non-prod профиль)* | test API-key | — | `200` последние письма для email (проксирует Mailtrap API) | `403` вне test-профиля |

Полный список полей request/response для каждого эндпоинта — следующий шаг
после утверждения этого документа (детальные DTO опишу отдельно, чтобы не
раздувать design-документ раньше согласования архитектуры в целом).

## 9. Security Mechanisms

- **Password hashing:** BCrypt (cost 12) или Argon2id — как в v1.
- **JWT:** короткоживущий access token (например, 30 минут), подпись
  HMAC-SHA256 с секретом из переменной окружения; при смене пароля
  (`reset-password`) — логическая инвалидация всех выданных ранее токенов
  через `passwordChangedAt` timestamp в токене/пользователе, сверяемый при
  каждой проверке JWT (простой способ без token-blacklist в БД).
- **Authorization:** проверяется на backend в каждом контроллере/сервисе
  (не полагаемся на скрытие кнопок в UI); `403` для попытки USER получить
  доступ к чужим данным или admin-эндпоинтам, `401` для отсутствующего/
  невалидного/просроченного токена.
- **HTTPS-ready:** приложение не форсирует TLS само (это ответственность
  reverse proxy/деплоя), но настроено без hardcoded HTTP-only
  предположений (secure cookie flags, HSTS-заголовок — включаемые опции).
- **Input validation:** Bean Validation (`jakarta.validation`) на всех DTO,
  серверная валидация — источник истины, клиентская — только UX.
- **SQL injection:** параметризованные запросы через JPA/Hibernate
  (никакого конкатенационного SQL); отдельный **security-тест-кейс**:
  попытка передать `' OR '1'='1` в поля email/search — должно быть
  обработано как обычная строка, не как SQL.
- **XSS:** серверный рендеринг через Thymeleaf экранирует вывод по
  умолчанию; отдельный тест-кейс на `<script>` в `firstName`/`description`
  транзакции.
- **CSRF:** включена стандартная защита Spring Security для
  сессионных (UI) форм; для JWT-based API CSRF неприменим (stateless) —
  явно задокументировано, чтобы не считалось недосмотром.
- **Rate limiting:** отдельный in-memory лимитер на `login`, `register`,
  `verify-email`/`resend-verification`, `forgot-password` — например, 5
  попыток / 15 минут на пару (IP, identifier); при превышении — `429` с
  полем `retryAfterSeconds`.
- **Excessive login attempts:** после N неудачных попыток логина подряд
  для одного email — временная блокировка попыток (не блокировка
  аккаунта целиком, отдельно от admin-block) с тем же `429`-контрактом.
- **Verification/reset tokens:** генерируются криптографически стойким
  генератором случайных чисел, хранится только **хэш** токена в БД (не сам
  токен — на случай утечки БД), одноразовые (`used_at`), с TTL.
- **Anti-enumeration:** `login` и `forgot-password` не должны различимо
  сообщать "такого email не существует" — единый generic-ответ для
  invalid-email / invalid-password / non-existing (все — один и тот же
  `401 INVALID_CREDENTIALS` для логина; `forgot-password` всегда `200`
  независимо от существования email). Это явное отличие от FinBank v1,
  где выбор был в пользу прямого сообщения — здесь выбираем
  anti-enumeration вариант как более show-casing для security-testing.

## 10. Testability Considerations

- **Test data через API:** `/api/test/seed-users` создаёт пользователей в
  любом нужном состоянии (`PENDING_VERIFICATION`, `ACTIVE`, `BLOCKED`) с
  предсказуемыми email/паролями, плюс варианты с балансом/без
  транзакций/с недостаточным балансом — без необходимости проходить весь
  UI-флоу вручную перед каждым тестом.
- **Очистка данных:** `/api/test/reset` — трункейт тестовых таблиц (или
  удаление данных, созданных в рамках test-run по метке), доступен только
  в non-production профиле, защищён отдельным test API-key (не JWT
  обычного пользователя).
- **Отдельная test-БД:** конфигурация через Spring-профиль (`test`) с
  отдельным `application-test.yml`, поднимается через тот же
  Docker Compose как второй контейнер Postgres на другом порту — тесты
  никогда не трогают dev/demo базу.
- **Test email service:** Mailtrap — у него есть собственный REST API для
  чтения входящих писем программно; `/api/test/mailbox/{email}` — тонкая
  прокси-обёртка над этим API, чтобы автотесты не хранили Mailtrap-креды
  напрямую и могли одним вызовом получить последний verification/reset
  токен без парсинга реального email-клиента.
- **Предсказуемость:** никаких `Thread.sleep`/случайных искусственных
  задержек в бизнес-логике; все асинхронные по природе вещи (отправка
  письма) либо синхронны в test-профиле, либо имеют явный webhook/поллинг
  контракт, а не "подожди и надейся".
- **Стабильные селекторы:** `data-testid` на каждый интерактивный элемент
  UI, соглашение по неймингу — `data-testid="{page}-{element}-{action?}"`
  (например, `register-email-input`, `transfer-submit-button`,
  `transactions-table-row`).
- **Предсказуемые сообщения об ошибках:** каждый код ошибки (`error`
  поле в JSON) — стабильная строка-константа (enum-подобный код), а не
  свободный текст, который может незаметно поменяться и сломать тесты,
  завязанные на текст.
- **Локальный запуск:** `docker-compose up` поднимает Postgres (+ вторую
  test-БД) и Mailtrap-совместимый SMTP-контейнер (или используется
  облачный Mailtrap sandbox — не требует контейнера); `mvn spring-boot:run`
  — само приложение. Никаких внешних облачных зависимостей, кроме
  опционального Mailtrap-аккаунта.

## 11. Assumptions

1. Один пользователь — один счёт (`users 1—1 accounts`); мультивалютность
   и мультисчёт — намеренно не в MVP (см. Optional).
2. Email — основной идентификатор для логина; телефон собирается и
   валидируется, но не используется для входа (не OTP-логин).
3. JWT-инвалидация при смене пароля реализована через сравнение
   `passwordChangedAt`, а не через полноценный token-blacklist/refresh-token
   ротацию — осознанное упрощение.
4. Mailtrap (или аналог) используется исключительно как test/sandbox SMTP
   — реальная доставка писем пользователям не предполагается ни на каком
   этапе.
5. Один и тот же Spring Boot процесс обслуживает и UI, и API (монолит) —
   микросервисная архитектура не нужна для целей демонстрации QA-фреймворка
   и только усложнила бы локальный запуск.
6. `PENDING`-статус транзакции существует в модели данных, но в MVP все
   транзакции завершаются синхронно (`COMPLETED`/`FAILED` сразу) — реальная
   асинхронная обработка не требуется.

## 12. Features Intentionally Excluded (Future Enhancement)

- Полноценный KYC/AML, sanctions screening.
- Интеграция с реальными платёжными шлюзами / реальными банками.
- Работа с реальными деньгами.
- Сложная anti-fraud система (риск-скоринг, device fingerprinting).
- Биометрическая аутентификация.
- Полноценный MFA (TOTP/SMS-OTP) — можно добавить позже как Optional.
- Соответствие конкретному законодательству/юрисдикции.
- Distributed, production-grade инфраструктура (Kubernetes, message
  queues, микросервисы, распределённый rate limiting через Redis-кластер).
- Мультивалютность и конвертация по курсу.
- Мультисчёт на одного пользователя.

---

## MVP (обязательно для полноценного QA Automation Framework)

- Регистрация с полной валидацией (все поля из брифа, duplicate
  email/phone, boundary/invalid characters).
- Email-верификация (token, expiration, invalid/used token, resend) —
  через Mailtrap с программным чтением писем.
- Логин с JWT, включая все негативные сценарии (invalid email/password,
  unverified, blocked, non-existing).
- Роли USER/ADMIN, разграничение доступа с `401`/`403`.
- Профиль пользователя (просмотр + обновление части полей).
- Банковский счёт, автосоздание при активации, стартовый баланс 1000 USD.
- Транзакции: DEPOSIT, WITHDRAWAL, TRANSFER с полным набором статусов.
- Перевод денег со всеми обязательными проверками и негативными кейсами
  из брифа (insufficient balance, invalid/zero/negative amount,
  non-existing receiver, self-transfer, blocked/inactive account,
  unauthorized, invalid JWT).
- История транзакций: пагинация, сортировка, фильтрация (тип, статус,
  диапазон дат), доступна и в UI, и в API.
- Forgot/Reset password с полным набором сценариев (valid/non-existing
  email, expired/invalid/used token, password validation).
- Logout с корректной инвалидацией доступа к защищённым эндпоинтам.
- Единый формат ошибок API + стандартные HTTP-коды.
- Rate limiting минимум на login/register/verify-email/forgot-password.
- Audit log для всех перечисленных в брифе событий.
- UI: все 11 страниц из брифа с `data-testid` на ключевых элементах.
- Test-support API: seed users в разных состояниях, reset test data,
  доступ к test-mailbox.
- Отдельная test-БД, детерминированное поведение, без искусственных
  задержек.

## Optional (можно добавить позже)

- Множественные счета на пользователя, разные валюты.
- Простой MFA (TOTP) как отдельный переключаемый флаг.
- Экспорт истории транзакций (CSV/PDF) — хороший повод для теста генерации
  файлов, но не критично для core-фреймворка.
- WebSocket/уведомления о входящем переводе в реальном времени.
- Более гранулярные роли (например, `SUPPORT` с read-only доступом).
- Soft-delete / закрытие счёта (`CLOSED` статус).
- Полноценная refresh-token ротация вместо простой инвалидации по
  `passwordChangedAt`.
- OpenAPI/Swagger UI (полезно для QA, но не блокер для написания тестов).

## Not Required (не стоит реализовывать для этого учебного проекта)

- KYC/AML, sanctions screening, соответствие законодательству.
- Реальные платёжные интеграции / реальные деньги.
- Продвинутая anti-fraud/риск-скоринг система.
- Биометрия, сложный MFA (push-подтверждения и т.п.).
- Распределённая production-инфраструктура (Kubernetes, message brokers,
  распределённый rate limiter).
- Мультиязычность UI, полная WCAG-сертификация (базовая доступность —
  да, полная сертификация — нет).

---

Жду подтверждения по этому документу. После апрува перехожу к следующему
шагу — детальным DTO/JSON-схемам для каждого эндпоинта из раздела 8 (или,
если предпочитаешь, сразу к реализации кода FinBank v2 по этому дизайну).
Что выбираешь?
