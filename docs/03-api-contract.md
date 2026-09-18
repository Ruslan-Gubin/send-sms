# Контракт API бекенда

Источник: `../backend/node/shop-back/docs/sms-module.md`, `src/sms/sms.controller.ts`, DTO.

## Общий формат ответа
```json
{
  "data": <T | null>,
  "status": "success | error",
  "message": "string",
  "errors": []
}
```

## 1) GET /sms/outbox?sender_phone=<10-15 цифр>

- **Доступ:** public, без токена
- **Параметр:** `sender_phone` — строки цифр (`/^\d{10,15}$/`), валидация на беке
- **Ответ:** `data: SmsOutbox | null`
  ```json
  {
    "id": 123,
    "phone": "9001234567",
    "message_text": "Код подтверждения: 123456. Действует 5 минут. ...",
    "status": "in_work",
    "sender_phones": ["..."],
    "error_reason": "",
    "device_id": "...",
    "created_at": "2026-09-18T...",
    "updated_at": "2026-09-18T..."
  }
  ```
- Если нет задач — `data: null`.
- Захват атомарный: статус → `in_work`, номер добавляется в `sender_phones`. TTL 5 мин от `updated_at` (протухшие удаляются).
- Ошибки `failed*` остаются ретраябельными для других `sender_phone`.

## 2) POST /sms/outbox/:id/delivered

- Успех отправки SMS, переводит запись в `delivered` (если `status = in_work`).
- Ответ — обновлённая запись или ошибка «Запись не найдена или уже обработана».

## 3) POST /sms/outbox/:id/failed

- Тело:
  ```json
  {
    "error_code": "no_balance | invalid_number | gateway | ...",
    "error_reason": "текст до 500 символов"
  }
  ```
- Маппинг `error_code` → статус записи:
  | error_code | статус |
  |---|---|
  | no_balance / insufficient_balance | failed_no_balance |
  | invalid_number / invalid_recipient | failed_invalid_number |
  | gateway / network / timeout | failed_gateway |
  | любой другой / пусто | failed |
- `error_reason` пишется в поле `error_reason`.

## Замечания для клиента
- `sender_phone` должен быть нормализован `replace(/\D/g, "")` (10–15 цифр). Если приходит из `TelephonyManager` с `+7`, убрать non-digits.
- `BASE_URL` — `Config.BASE_URL`, локальный ПК (`192.168.x.x`) или деплой.
- Устройство не шлёт `device_id` (только `sender_phone`) — `device_id` используется лишь на `request-otp`.
