# Registration — Field & Boundary Validation: предложения по реализации

Конкретные, готовые к реализации правила валидации для полей регистрации
FinBank v2 (`firstName`, `lastName`, `email`, `phone`, `dateOfBirth`,
`password`, `confirmPassword`, `termsAccepted`). Каждое правило —
с точным regex/границей, кодом ошибки и примерами валидных/невалидных
значений, чтобы документ можно было один в один превратить и в
`@Valid`-аннотации на бэкенде, и в тест-кейсы у QA.

## 0. Общая стратегия валидации

**Предложение: collect-all, а не fail-fast.**
Регистрационная форма должна возвращать **все** нарушенные правила сразу
одним ответом, а не только первое найденное. Это (а) лучше для UX — не
нужно отправлять форму N раз, чтобы увидеть все ошибки, и (б) лучше для
QA — позволяет писать один тест "невалидная форма" с несколькими битыми
полями и проверять весь массив `details[]` за один запрос, а не гонять
запросы по одному на поле.

```json
{
  "timestamp": "2026-08-19T20:30:00Z",
  "status": 422,
  "error": "VALIDATION_ERROR",
  "message": "Registration request failed validation",
  "details": [
    { "field": "email", "code": "EMAIL_INVALID_FORMAT", "message": "Email must be a valid address" },
    { "field": "password", "code": "PASSWORD_TOO_SHORT", "message": "Password must be at least 12 characters" },
    { "field": "confirmPassword", "code": "PASSWORD_MISMATCH", "message": "Passwords do not match" }
  ]
}
```

**Предложение: у каждого правила — свой стабильный `code`**, отдельно от
человекочитаемого `message`. QA пишет ассерты на `code` (он не меняется
при правках копирайтинга), а `message` — для реального пользователя.
Это устраняет проблему "текст поменяли — сломались все тесты".

**Порядок проверок:** сначала структурные/форматные (required, format,
length, regex) — они не требуют обращения к БД; затем бизнес-проверки,
требующие БД (duplicate email/phone) — выполняются, только если поле уже
прошло форматную валидацию (нет смысла проверять на дубликат заведомо
невалидный email).

---

## 1. `firstName` / `lastName`

| Правило | Значение | Код ошибки |
|---|---|---|
| Required | не должно быть `null`/пустой строкой/строкой из одних пробелов | `FIRST_NAME_REQUIRED` / `LAST_NAME_REQUIRED` |
| Min length | 1 символ после trim | `..._TOO_SHORT` |
| Max length | 60 символов | `..._TOO_LONG` |
| Допустимые символы | буквы (Unicode, поддержка не-латиницы), пробел, дефис, апостроф: `^[\p{L}][\p{L}\s'-]*$` | `..._INVALID_CHARACTERS` |
| Trim | обрезаются пробелы по краям перед валидацией и сохранением | — (нормализация, не ошибка) |

**Граничные значения для тестов:**
| Вход | Ожидаемый результат |
|---|---|
| `""` | 422 `FIRST_NAME_REQUIRED` |
| `"   "` (только пробелы) | 422 `FIRST_NAME_REQUIRED` (после trim — пусто) |
| `"A"` (1 символ) | ✅ валидно (граница) |
| `"A" * 60` (ровно 60) | ✅ валидно (граница) |
| `"A" * 61` (61 символ) | 422 `FIRST_NAME_TOO_LONG` |
| `"Jean-Luc"` | ✅ валидно (дефис разрешён) |
| `"O'Brien"` | ✅ валидно (апостроф разрешён) |
| `"Анна"` | ✅ валидно (кириллица) |
| `"John123"` | 422 `FIRST_NAME_INVALID_CHARACTERS` (цифры) |
| `"<script>alert(1)</script>"` | 422 `FIRST_NAME_INVALID_CHARACTERS` (не должно даже дойти до XSS-проверки на выводе — отсечётся на входе) |
| `"John Smith"` (с пробелом внутри) | ✅ валидно (двойное имя) |

---

## 2. `email`

| Правило | Значение | Код ошибки |
|---|---|---|
| Required | не пусто | `EMAIL_REQUIRED` |
| Формат | RFC 5322-совместимый паттерн (использовать `jakarta.validation.constraints.Email`, не самодельный regex — самодельные regex почти всегда либо слишком строгие, либо пропускают мусор) | `EMAIL_INVALID_FORMAT` |
| Max length | 254 символа (практический предел RFC 5321 для email) | `EMAIL_TOO_LONG` |
| Нормализация | `trim()` + `toLowerCase()` перед проверкой уникальности и сохранением | — |
| Уникальность | без учёта регистра, проверка на уровне приложения **и** `UNIQUE`-констрейнт в БД (защита от гонки) | `EMAIL_ALREADY_REGISTERED` (409) |

**Граничные значения:**
| Вход | Ожидаемый результат |
|---|---|
| `""` | 422 `EMAIL_REQUIRED` |
| `"plainaddress"` | 422 `EMAIL_INVALID_FORMAT` |
| `"@missing-local.com"` | 422 `EMAIL_INVALID_FORMAT` |
| `"missing-domain@"` | 422 `EMAIL_INVALID_FORMAT` |
| `"two@@at.com"` | 422 `EMAIL_INVALID_FORMAT` |
| `"user@sub.domain.com"` | ✅ валидно |
| `"user+tag@domain.com"` | ✅ валидно (плюс-адресация — распространённый кейс, важно не сломать) |
| `"  jane@test.com  "` | ✅ валидно, сохраняется как `"jane@test.com"` (после trim) |
| `"Jane@Test.com"`, затем повторно `"jane@test.com"` | второй запрос → 409 `EMAIL_ALREADY_REGISTERED` (регистр не важен) |
| email длиной 255 символов | 422 `EMAIL_TOO_LONG` |
| email длиной ровно 254 символа (валидный формат) | ✅ валидно (граница) |

---

## 3. `phone`

**Предложение: хранить и валидировать в формате E.164** (`+` и до 15
цифр, например `+14155552671`) — это международный стандарт, снимает
вопрос "с каким кодом страны" и легко проверяется одним regex.

| Правило | Значение | Код ошибки |
|---|---|---|
| Required | не пусто | `PHONE_REQUIRED` |
| Формат | `^\+[1-9]\d{7,14}$` (обязательный `+`, далее 8–15 цифр, первая цифра после `+` не ноль) | `PHONE_INVALID_FORMAT` |
| Уникальность | точное совпадение (телефон не нормализуется по регистру, но пробелы/дефисы стоит убирать до сохранения — см. ниже) | `PHONE_ALREADY_REGISTERED` (409) |
| Нормализация | удалить пробелы, дефисы, скобки перед валидацией формата (`"+1 (415) 555-2671"` → `"+14155552671"`) — **решение продукта**: либо нормализовать на бэкенде, либо требовать от UI уже нормализованный ввод (см. Open Question ниже) | — |

**Граничные значения:**
| Вход | Ожидаемый результат |
|---|---|
| `""` | 422 `PHONE_REQUIRED` |
| `"14155552671"` (без `+`) | 422 `PHONE_INVALID_FORMAT` |
| `"+0123456789"` (после `+` идёт 0) | 422 `PHONE_INVALID_FORMAT` |
| `"+1234567"` (7 цифр — короче минимума) | 422 `PHONE_INVALID_FORMAT` |
| `"+123456789012345"` (15 цифр — граница максимума) | ✅ валидно |
| `"+1234567890123456"` (16 цифр) | 422 `PHONE_INVALID_FORMAT` |
| `"+1-415-555-2671"` (с разделителями) | зависит от решения по нормализации — см. Open Question |
| `"not-a-phone"` | 422 `PHONE_INVALID_FORMAT` |
| Повторная регистрация с уже занятым номером | 409 `PHONE_ALREADY_REGISTERED` |

**Open Question (нужно решение Product Owner):** нормализовать телефон на
бэкенде (принимать разные форматы ввода и приводить к E.164) или требовать
от фронтенда сразу присылать нормализованный E.164 (например, через
готовый UI-компонент выбора страны + маску ввода)? Рекомендация:
нормализация на фронтенде компонентом ввода телефона — это стандартная
практика (как у большинства банковских приложений) и снимает
неоднозначность форматов на бэкенде.

---

## 4. `dateOfBirth`

| Правило | Значение | Код ошибки |
|---|---|---|
| Required | не пусто | `DATE_OF_BIRTH_REQUIRED` |
| Формат | ISO-8601 `YYYY-MM-DD` | `DATE_OF_BIRTH_INVALID_FORMAT` |
| Не в будущем | `dateOfBirth <= today` | `DATE_OF_BIRTH_IN_FUTURE` |
| Минимальный возраст | 18 лет на момент регистрации (порог — `Regulatory-dependent`, см. предыдущую спецификацию; для FinBank v2 фиксируем 18 как значение по умолчанию) | `USER_UNDERAGE` |
| Разумный максимум | не более 120 лет назад (защита от опечаток типа `1900-01-01`, не жёсткий бизнес-запрет, а health-check данных) | `DATE_OF_BIRTH_UNREALISTIC` |

**Граничные значения:**
| Вход | Ожидаемый результат |
|---|---|
| Дата = сегодня минус ровно 18 лет | ✅ валидно (граница — "исполнилось 18 сегодня") |
| Дата = сегодня минус 18 лет + 1 день (завтра будет 18) | 422 `USER_UNDERAGE` |
| Дата в будущем (`2099-01-01`) | 422 `DATE_OF_BIRTH_IN_FUTURE` |
| `"01/01/2000"` (неверный формат) | 422 `DATE_OF_BIRTH_INVALID_FORMAT` |
| `"1850-01-01"` (нереалистично старая) | 422 `DATE_OF_BIRTH_UNREALISTIC` |
| `""` | 422 `DATE_OF_BIRTH_REQUIRED` |

---

## 5. `password` / `confirmPassword`

| Правило | Значение | Код ошибки |
|---|---|---|
| Required (оба поля) | не пусто | `PASSWORD_REQUIRED` / `CONFIRM_PASSWORD_REQUIRED` |
| Min length | 12 символов | `PASSWORD_TOO_SHORT` |
| Max length | 64 символа | `PASSWORD_TOO_LONG` |
| Состав | минимум 1 буква и 1 цифра (без обязательных спецсимволов — по NIST 800-63B избегаем избыточных composition rules) | `PASSWORD_MISSING_LETTER` / `PASSWORD_MISSING_DIGIT` |
| Не равен email/имени | пароль не должен содержать email (локальную часть) или имя целиком, регистронезависимо | `PASSWORD_CONTAINS_PERSONAL_INFO` |
| Совпадение с confirmPassword | `password == confirmPassword` (точное совпадение, включая регистр) | `PASSWORD_MISMATCH` |
| Пробелы | разрешены внутри и по краям — **не** обрезаются (пробел — валидный символ пароля, trim пароля — известная антипаттерн-ошибка, ломает пароли типа парольных фраз) | — |

**Граничные значения:**
| Вход (password / confirmPassword) | Ожидаемый результат |
|---|---|
| `"Passw0rd1234"` / то же самое | ✅ валидно |
| `""` / `""` | 422 `PASSWORD_REQUIRED`, `CONFIRM_PASSWORD_REQUIRED` |
| 11 символов (граница снизу) | 422 `PASSWORD_TOO_SHORT` |
| 12 символов, есть буква и цифра (граница снизу, валидная) | ✅ валидно |
| 64 символа (граница сверху, валидная) | ✅ валидно |
| 65 символов | 422 `PASSWORD_TOO_LONG` |
| `"aaaaaaaaaaaa"` (12 букв, без цифр) | 422 `PASSWORD_MISSING_DIGIT` |
| `"123456789012"` (12 цифр, без букв) | 422 `PASSWORD_MISSING_LETTER` |
| `"Password1234"` / `"Password1235"` (не совпадают) | 422 `PASSWORD_MISMATCH` |
| email = `jane@test.com`, password = `"jane12345678"` | 422 `PASSWORD_CONTAINS_PERSONAL_INFO` |
| `"Pässwörd1234"` (не-ASCII символы) | ✅ валидно — Unicode в пароле разрешён |
| `"Pass word 1234"` (с пробелами) | ✅ валидно — пробелы не запрещены и не обрезаются |

---

## 6. `termsAccepted`

| Правило | Значение | Код ошибки |
|---|---|---|
| Required | должно быть `true`; `false`/отсутствие поля — ошибка | `TERMS_NOT_ACCEPTED` |
| Тип | строго `boolean`, не строка `"true"` (важно для API-контракта — строгая типизация в JSON) | `TERMS_ACCEPTED_INVALID_TYPE` |

**Граничные значения:**
| Вход | Ожидаемый результат |
|---|---|
| `true` | ✅ валидно |
| `false` | 422 `TERMS_NOT_ACCEPTED` |
| поле отсутствует в JSON | 422 `TERMS_NOT_ACCEPTED` |
| `"true"` (строка вместо boolean, только для API) | 400 (ошибка десериализации JSON — Jackson отклонит до валидации) |

---

## 7. Duplicate email / duplicate phone — предложение по реализации

**Двухуровневая защита (рекомендуется):**
1. **Application-level pre-check** — быстрый `SELECT exists(...)` перед
   попыткой сохранения, чтобы вернуть понятную `409` с конкретным полем
   (`EMAIL_ALREADY_REGISTERED` или `PHONE_ALREADY_REGISTERED` — по
   отдельности, а не общей ошибкой "данные заняты").
2. **DB-level UNIQUE constraint** на `users.email` и `users.phone` — ловит
   race condition, когда два запроса прошли pre-check одновременно.
   Ловим `DataIntegrityViolationException` на уровне сервиса и
   транслируем в тот же `409`-контракт, что и pre-check — **клиент не
   должен видеть разницу**, попал ли дубликат через pre-check или через
   гонку на БД.

**Если заняты и email, и phone одновременно** — вернуть **оба** нарушения
в `details[]` за один ответ (см. общую стратегию collect-all в разделе 0):
```json
{
  "status": 409,
  "error": "CONFLICT",
  "details": [
    { "field": "email", "code": "EMAIL_ALREADY_REGISTERED" },
    { "field": "phone", "code": "PHONE_ALREADY_REGISTERED" }
  ]
}
```

---

## 8. Пример реализации (Jakarta Bean Validation, иллюстративно)

```java
public class RegisterRequest {

    @NotBlank(message = "{firstName.required}")
    @Size(min = 1, max = 60, message = "{firstName.size}")
    @Pattern(regexp = "^[\\p{L}][\\p{L}\\s'-]*$", message = "{firstName.invalidCharacters}")
    private String firstName;

    // lastName — аналогично

    @NotBlank(message = "{email.required}")
    @Email(message = "{email.invalidFormat}")
    @Size(max = 254, message = "{email.tooLong}")
    private String email;

    @NotBlank(message = "{phone.required}")
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "{phone.invalidFormat}")
    private String phone;

    @NotNull(message = "{dateOfBirth.required}")
    @PastOrPresent(message = "{dateOfBirth.inFuture}")
    private LocalDate dateOfBirth;

    @NotBlank(message = "{password.required}")
    @Size(min = 12, max = 64, message = "{password.size}")
    private String password;

    @NotBlank(message = "{confirmPassword.required}")
    private String confirmPassword;

    @AssertTrue(message = "{terms.notAccepted}")
    private boolean termsAccepted;
}
```

Проверки, которые не выражаются одной аннотацией (совпадение паролей,
буква+цифра в пароле, пароль не содержит email/имя, возраст 18+,
"нереалистичная" дата рождения, наличие обоих дубликатов сразу) —
предлагается вынести в один класс `@RegistrationBusinessRulesValidator`
(class-level `@Valid`-валидатор поверх всего DTO, не поле-level), чтобы у
него был доступ сразу ко всем полям объекта одновременно.

---

## Итог: что это даёт QA

Каждая строка из таблиц выше — это готовый тест-кейс с точным входным
значением и точным ожидаемым `code`. При написании автотестов достаточно
пройтись по этому документу построчно — параметризованный тест
(`@ParameterizedTest` в JUnit 5, как в нашем API-каркасе) на 40+ граничных
значений можно собрать почти без дополнительного анализа требований.

Готова: (а) собрать все эти граничные значения в отдельный CSV/файл,
который можно прямо подключить как `@CsvSource`/`@MethodSource` для
JUnit 5, либо (б) двигаться дальше — к следующему полю функционала
(email-верификация, логин и т.д.). Что предпочитаешь?
