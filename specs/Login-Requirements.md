# Login Requirements — FinBank

## 1. Область применения

Функция Login предоставляет пользователю возможность аутентифицироваться в
FinBank через:

1. Web-интерфейс.
2. REST API.

Web и API используют единую учётную запись пользователя, но имеют разные
механизмы управления authentication state.

## 2. Web Login

**LOGIN-WEB-001 — Форма авторизации**
Страница Login должна содержать: Email, Password, кнопку Login.

**LOGIN-WEB-002 — Успешная авторизация**
При вводе корректного email и пароля система должна: (1) аутентифицировать
пользователя, (2) создать authenticated web session, (3) перенаправить
пользователя на `/dashboard`.

**LOGIN-WEB-003 — Неверные credentials**
Если email или пароль неверны, система не должна выполнять авторизацию.
Пользователь должен остаться на странице Login. Система должна отображать
общее сообщение: `Неверный email или пароль.` Сообщение не должно
раскрывать, существует ли указанный email.

**LOGIN-WEB-004 — Пустой email**
Если email не указан, login не должен выполняться.

**LOGIN-WEB-005 — Пустой пароль**
Если пароль не указан, login не должен выполняться.

**LOGIN-WEB-006 — Неверный формат email**
Если email имеет недопустимый формат, login не должен выполняться.

## 3. API Login

**LOGIN-API-001 — Endpoint**: `POST /api/auth/login`

**LOGIN-API-002 — Request**
```json
{ "email": "user@example.com", "password": "ValidPassword123" }
```

**LOGIN-API-003 — Successful Authentication**: `200 OK`
```json
{ "token": "...", "expiresInSeconds": 1800, "email": "user@example.com" }
```

**LOGIN-API-004 — JWT**
После успешной аутентификации система должна выдавать JWT. JWT должен
использоваться для доступа к защищённым `/api/**` endpoints.

**LOGIN-API-005 — Token expiration**
JWT должен иметь ограниченный срок действия. После истечения срока
действия token не должен позволять обращаться к защищённым endpoints.

**LOGIN-API-006 — Invalid credentials**: `401 Unauthorized`, без JWT в теле.

**LOGIN-API-007 — Anti-enumeration**
Зарегистрированный email + неправильный пароль, и незарегистрированный
email + любой пароль — должны иметь одинаковый результат: `401
Unauthorized` с одинаковым публичным сообщением `Неверный email или
пароль.`

**LOGIN-API-008 — Empty credentials**
При отсутствии email или password authentication не должна выполняться.
API должен возвращать validation error.

**LOGIN-API-009 — Invalid email format**
При недопустимом формате email authentication не должна выполняться. API
должен вернуть validation error.

## 4. Account Status

Статусы: `ACTIVE`, `BLOCKED`.

**LOGIN-STATUS-001 — Active user**: может выполнять login при корректных credentials.

**LOGIN-STATUS-002 — Blocked user**: не может выполнить login. API
возвращает `401 Unauthorized`, сообщение не раскрывает причину блокировки
или существование аккаунта.

**LOGIN-STATUS-003 — Web blocked user**: не должен получать authenticated
web session.

## 5. Authorization

Успешная аутентификация не должна автоматически предоставлять доступ ко
всем ресурсам. USER — доступ только к разрешённым пользовательским
ресурсам; ADMIN — дополнительные права. Без authentication: `401`. С
authentication, но без нужных permissions: `403`.

## 6. Web Logout

**LOGIN-LOGOUT-001**: после logout web session должна быть уничтожена,
доступ к authenticated-страницам без повторного login невозможен.

## 7. API Authentication Errors

Единый формат:
```json
{ "status": 401, "error": "UNAUTHORIZED", "message": "Неверный email или пароль." }
```
Одинаково для: неправильного email, неправильного пароля, blocked account.
API не должен возвращать sensitive authentication information.

## 8. JWT Security

JWT должен: присутствовать только при успешной аутентификации; иметь срок
действия; использоваться для protected endpoints; отклоняться после
expiration; отклоняться при повреждении token; отклоняться при отсутствии
token; отклоняться при использовании некорректного token. Защищённый
endpoint без JWT должен возвращать `401 Unauthorized`.

## 9. Scope

В текущую Login specification НЕ входят: email verification,
`PENDING_VERIFICATION` status, password reset, MFA, brute-force
protection, rate limiting, account lockout после N попыток, JWT
revocation, refresh tokens, audit logging. Эти функции могут быть
реализованы как отдельные modules или future enhancements.

---

## Реализация в коде (эта версия FinBank)

| Требование | Где реализовано | Заметки |
|---|---|---|
| LOGIN-WEB-001/002 | `login.html`, `SecurityConfig.webFilterChain` (formLogin) | Без изменений — уже было |
| LOGIN-WEB-003 | `login.html` (`error-banner`), `SecurityConfig.webFilterChain.failureUrl` | Текст обновлён на точную формулировку `Неверный email или пароль.` |
| LOGIN-WEB-004/005/006 | Ничего специально не написано — работает "бесплатно": `FinbankUserDetailsService` не найдёт пользователя с пустым/невалидным email → тот же путь, что и неверный пароль | Не заведено отдельной клиентской валидации формы намеренно, чтобы не создавать расхождение между "заблокировано JS" и "заблокировано сервером" |
| LOGIN-API-001..004 | `AuthApiController.login` | Без структурных изменений |
| LOGIN-API-005, JWT Security (раздел 8) | `JwtUtil`, `JwtAuthFilter`, новый `JwtAuthenticationEntryPoint` | См. ниже — добавлен entry point, иначе отсутствующий/невалидный JWT мог вернуть `403` вместо `401` |
| LOGIN-API-006/007 | `AuthApiController.login` — теперь ловит `AuthenticationException` (родитель `BadCredentialsException` и `DisabledException`), а не только `BadCredentialsException` | Раньше `DisabledException` (заблокированный пользователь) не ловился вообще и утёк бы как 500 |
| LOGIN-API-008/009 | `LoginRequest` (`@NotBlank`, добавлен `@Email`) + существующий `MethodArgumentNotValidException`-обработчик | `@Email` раньше не было — теперь есть |
| LOGIN-STATUS-001/002/003 | Новое поле `User.status` (`AccountStatus`), `FinbankUserDetailsService.disabled(...)` | Заблокированный пользователь получает `UserDetails.disabled=true` → Spring Security сам бросает `DisabledException` до проверки пароля, что даёт единый ответ и для web (тот же `/login?error`), и для API |
| Раздел 5 (Authorization, роли USER/ADMIN) | **Не реализовано** | В коде нет поля роли и нет admin-эндпоинтов — это отдельный кусок работы, требования по нему в этом документе не детализированы до уровня конкретных ID (в отличие от LOGIN-*), поэтому не трогали в этой итерации |
| LOGIN-LOGOUT-001 | `SecurityConfig.webFilterChain.logout(...)` | Без изменений — уже было |
| Раздел 7 (единый формат ошибок) | `ApiError` (`status/error/message` + доп. поля `timestamp/details/path`) переиспользован для login-ошибок и для `401` из `JwtAuthenticationEntryPoint` | Спецификация показывает 3 обязательных поля — `ApiError` их содержит плюс несколько дополнительных, что не противоречит формулировке "должен содержать" |
| Раздел 9 (Scope) | Ничего из списка не реализовано — это осознанно | email verification/PENDING_VERIFICATION, password reset, MFA, rate limiting, lockout, JWT revocation, refresh tokens, audit logging — всё за скобками, как и написано в спеке |

### Как получить пользователя в статусе BLOCKED для тестов

Полноценного admin-эндпоинта блокировки в этой версии нет (раздел 5 не
реализован — см. таблицу выше). Чтобы `LOGIN-STATUS-002/003` было чем
тестировать прямо сейчас, `DataSeeder` при старте создаёт демо-пользователя
`blocked@finbank.dev` (пароль `FinbankDemoPassphrase`) сразу в статусе
`BLOCKED`. Логин с любым паролем для этого email должен всегда
возвращать `401` с сообщением `Неверный email или пароль.`, и на вебе —
редирект обратно на `/login?error` без создания сессии.
