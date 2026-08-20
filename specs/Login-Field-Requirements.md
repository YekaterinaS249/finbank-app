# Login Field Requirements — FinBank

## 1. Email

**LOGIN-FIELD-EMAIL-001 — Обязательность**
Поле Email обязательно. Пустое значение — ошибка `Введите email.`, login не
выполняется.

**LOGIN-FIELD-EMAIL-002 — Формат**
Email должен соответствовать стандартному формату адреса электронной почты.

**LOGIN-FIELD-EMAIL-003 — Максимальная длина**
Email не должен превышать 254 символа.

**LOGIN-FIELD-EMAIL-004 — Обрезка пробелов**
Ведущие и завершающие пробелы должны обрезаться перед проверкой/lookup.
Внутренние пробелы не удаляются — они делают email невалидным по формату.

**LOGIN-FIELD-EMAIL-005 — Регистронезависимость**
Сравнение email при login должно быть регистронезависимым.

**LOGIN-FIELD-EMAIL-006 — Допустимые символы**
Поле принимает стандартные символы email-адреса без дополнительных
серверных ограничений сверх формата.

**LOGIN-FIELD-EMAIL-007 — Anti-enumeration**
Невалидный формат email на этапе аутентификации не должен раскрывать,
существует ли такой email в системе — итоговое сообщение об ошибке
authentication (`Неверный email или пароль.`) идентично для валидного,
но неверного email.

## 2. Password

**LOGIN-FIELD-PASSWORD-001 — Обязательность**
Поле Password обязательно. Пустое значение — ошибка `Введите пароль.`,
login не выполняется.

**LOGIN-FIELD-PASSWORD-002 — Соответствие политике паролей**
Password field на login не выполняет проверку длины/состава — эти правила
применяются только при регистрации (см.
`Password-Requirements-Acceptance-Criteria.md`).

**LOGIN-FIELD-PASSWORD-003 — Регистрозависимость**
Password всегда сравнивается регистрозависимо (case-sensitive).

**LOGIN-FIELD-PASSWORD-004 — Password.trim() запрещён**
Пароль не должен обрезаться (`trim()`) ни на одном этапе login — ни на
клиенте, ни на сервере, ни перед authentication.

**LOGIN-FIELD-PASSWORD-005 — Copy/paste/autofill**
Поле Password должно поддерживать вставку из буфера обмена и автозаполнение
браузера/менеджера паролей.

**LOGIN-FIELD-PASSWORD-006 — Маскирование**
Password отображается замаскированным (`type="password"`) по умолчанию, с
опциональной кнопкой show/hide.

**LOGIN-FIELD-PASSWORD-007 — Никогда не в URL/логах**
Пароль никогда не должен попадать в URL, query string или логи приложения.

## 3. Login Button

**LOGIN-FIELD-BUTTON-001 — Наличие**
Форма содержит кнопку отправки Login.

**LOGIN-FIELD-BUTTON-002 — Активность**
Кнопка активна по умолчанию (нет блокировки до заполнения полей — сабмит
пустой формы обрабатывается серверной валидацией, см. раздел 1/2 выше).

**LOGIN-FIELD-BUTTON-003 — Однократная отправка**
Повторный клик во время обработки запроса не должен создавать дублирующиеся
запросы (клиентская защита от двойного сабмита — не реализована в этой
версии, см. таблицу ниже).

**LOGIN-FIELD-BUTTON-004 — Доступность**
Кнопка доступна с клавиатуры (стандартный `<button type="submit">`).

**LOGIN-FIELD-BUTTON-005 — Текст**
Кнопка имеет понятный текст ("Log In").

## 4. Общие требования к Login Fields

**LOGIN-FIELD-GENERAL-001 — Labels**
Оба поля имеют явные `<label>`, связанные через `for`/`id`.

**LOGIN-FIELD-GENERAL-002 — Keyboard navigation**
Поля и кнопка доступны через Tab в логичном порядке (email → password →
submit).

**LOGIN-FIELD-GENERAL-003 — Стабильные селекторы**
Рекомендованные `data-testid`: `email-input`, `password-input`,
`login-button`.

**LOGIN-FIELD-GENERAL-004 — Единообразие Web/API**
Все требования этого документа реализуются одинаково предсказуемо в Web
Login и API Login там, где применимо (email trim/case-insensitivity,
password не обрезается, обязательность обоих полей).

**LOGIN-FIELD-GENERAL-005 — Отсутствие утечки данных**
Ни email, ни password не сохраняются в истории браузера отдельно от
стандартного autocomplete-поведения; ошибки не раскрывают, какое из полей
"более неверно".

## 5. Минимальные Boundary Scenarios

**Email:**
- пустой email → `Введите email.`
- email с ведущими/завершающими пробелами, иначе валидный → login проходит
  как обычно (пробелы обрезаны перед lookup);
- email в другом регистре, чем при регистрации (`Jane@Example.com` vs
  `jane@example.com`) → login проходит;
- email длиной > 254 символов → `Введите email.` (API: 422 через
  `@Size(max=254)`);
- email без `@` или без домена → ошибка формата.

**Password:**
- пустой password → `Введите пароль.`;
- password только из пробелов (` `) → не считается "пустым" (не обрезается),
  обрабатывается как обычный неверный пароль → `Неверный email или пароль.`;
- password с ведущими/завершающими пробелами, отличный от сохранённого
  пароля из-за этих пробелов → login должен провалиться (пароль НЕ
  обрезается — пробелы значимы);
- password, вставленный через paste, идентичен введённому вручную → login
  работает одинаково.

---

## Реализация в коде (эта версия FinBank)

| Требование | Где реализовано | Заметки |
|---|---|---|
| LOGIN-FIELD-EMAIL-001 (Web) | Новый `LoginFieldPresenceFilter`, `login.html` (`email-error`) | Редирект на `/login?emailError` до authentication |
| LOGIN-FIELD-EMAIL-001 (API) | `LoginRequest.email` — `@NotBlank(message = "Введите email.")` | |
| LOGIN-FIELD-EMAIL-002/003 | `LoginRequest.email` — `@Email` + `@Size(max=254)`, оба с сообщением `Введите email.` | На вебе формат отдельно не проверяется — см. заметку в `Login-Requirements.md` про LOGIN-WEB-006 (намеренно, чтобы не расходиться с серверной проверкой) |
| LOGIN-FIELD-EMAIL-004 | `FinbankUserDetailsService.loadUserByUsername()` — `email.trim()` перед `findByEmailIgnoreCase` | Единая точка для Web и API — оба пути идут через `AuthenticationManager` → `DaoAuthenticationProvider` → этот метод |
| LOGIN-FIELD-EMAIL-005 | `UserRepository.findByEmailIgnoreCase` (без изменений — уже было) | |
| LOGIN-FIELD-EMAIL-006/007 | Ничего специально не написано — как и раньше, невалидный формат просто не найдёт пользователя → тот же anti-enumeration путь | |
| LOGIN-FIELD-PASSWORD-001 (Web) | `LoginFieldPresenceFilter` | Редирект на `/login?passwordError` |
| LOGIN-FIELD-PASSWORD-001 (API) | `LoginRequest.password` — `@NotBlank(message = "Введите пароль.")` | |
| LOGIN-FIELD-PASSWORD-002 | Ничего не добавлено намеренно | Length/composition не проверяются на login — только presence |
| LOGIN-FIELD-PASSWORD-003 | `Argon2PasswordEncoder.matches()` (без изменений — уже было) | Argon2 сравнение по умолчанию регистрозависимо |
| LOGIN-FIELD-PASSWORD-004 | `AuthApiController.login` передаёт `request.getPassword()` без изменений; `LoginFieldPresenceFilter` проверяет только `isEmpty()`, не `trim()` | Проверено явным тестом `whitespaceOnlyPassword_isTreatedAsPresent_notTrimmed` |
| LOGIN-FIELD-PASSWORD-005 | `login.html` — стандартный `<input type="password">` без блокировки paste/autofill, `autocomplete="current-password"` | |
| LOGIN-FIELD-PASSWORD-006 | `login.html` — `type="password"` + JS-кнопка `toggle-password` (show/hide) | Переключает только `input.type`, значение не читает и никуда не пишет |
| LOGIN-FIELD-PASSWORD-007 | `AuthApiController`/`AuthWebController` не логируют request body; форма — `method="post"`, пароль не попадает в query string | Явного logging-фреймворка с маскированием в проекте нет — но пароль просто никогда не пишется в `System.out`/логгер |
| LOGIN-FIELD-BUTTON-001..005 | `login.html` — `<button type="submit" data-testid="login-button">Log In</button>` | LOGIN-FIELD-BUTTON-003 (защита от двойного сабмита) — **не реализовано**, вне минимального набора |
| LOGIN-FIELD-GENERAL-001/002 | `login.html` — `<label for="...">`, обычный tab-order | |
| LOGIN-FIELD-GENERAL-003 | `login.html` — `data-testid="email-input"/"password-input"/"login-button"` | Переименовано с прежних `login-email`/`login-password`/`login-submit` — **breaking change для уже написанных тестов**, если они были |
| LOGIN-FIELD-GENERAL-004 | См. таблицу выше — email trim/case-insensitivity и password non-trim реализованы в общих компонентах (`FinbankUserDetailsService`, `AuthApiController`), используемых обоими путями | |
| LOGIN-FIELD-GENERAL-005 | Ничего специально не добавлено — уже обеспечивается unified anti-enumeration ответом | |

### ⚠️ Важное изменение: переименование data-testid на login-странице

Если вы уже писали автотесты на прежние селекторы `login-email` /
`login-password` / `login-submit` — их нужно обновить на новые
`email-input` / `password-input` / `login-button`, заданные явно в этой
спецификации (раздел 4, LOGIN-FIELD-GENERAL-003). Форма (`login-form`) и
баннер ошибки (`login-error`) не переименовывались.

### Как это покрыто тестами

- `FinbankUserDetailsServiceTest.emailWithLeadingAndTrailingWhitespace_isTrimmedBeforeLookup`
- `LoginFieldPresenceFilterTest` — 5 тестов: пустой email, пустой password,
  password только из пробелов (не считается пустым), не-login запрос
  проходит насквозь, валидные данные проходят насквозь.
