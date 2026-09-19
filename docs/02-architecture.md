# Архитектура

## Структура модулей

```
MainActivity (Compose UI)  ◄──►  SharedPreferences (senderPhone, isRunning)
        │
        ▼
PumpService (ForegroundService)
  ├─ PumpLoop (Coroutine while isActive, delay 10_000)
  ├─ NetworkMonitor.hasInternet()
  ├─ OutboxApi.getTask / markDelivered / markFailed
  ├─ SmsSender.send(phone, text)  (suspend, BroadcastReceiver)
  └─ AppLogger (последние 30 событий → StateFlow на UI)
```

## Поток данных
1. UI записывает `senderPhone` в `SharedPreferences`.
2. Кнопка Старт → `startForegroundService(PumpService)` + `POST_NOTIFICATIONS` grant.
3. `PumpService.onCreate` — создаёт `NotificationChannel`, запускает `notification`, корутину `SupervisorJob + Dispatchers.IO`.
4. Цикл: проверка интернета → GET → если задача есть — `SmsSender.send` (ожидание sent broadcast) → POST → лог → `delay(10_000)`.
5. Остановка — `stopService` + `cancel` Job.

## Фон
- `ForegroundService` c `foregroundServiceType="specialUse"` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE="smsPump"` + runtime `POST_NOTIFICATIONS`.
- `BootReceiver` → при `BOOT_COMPLETED` рестарт сервиса, если ранее был включён (`isRunning=true` в префах).
- Если сервис падает — `START_STICKY` (перезапуск системой при возможности).

## Логирование
`AppLogger` — `MutableStateFlow<List<LogEntry>>`, ограничено 30. На каждый шаг — время + текст (на русском). UI подписывается, экран всегда показывает последние события.

## Ошибки/SMS
`SmsSender` используется через `PendingIntent` на результат:
- `RESULT_OK` → delivered
- `RESULT_ERROR_NO_SERVICE / RADIO_OFF` → `network`
- `RESULT_ERROR_NULL_PDU` → `gateway`
- `GENERIC_FAILURE` → `invalid_number` (эвристика) иначе `failed` + `error_reason`

Статус кодируется в `error_code` для бекенда (`docs/03-api-contract.md`).

## Конфигурация
Адрес бекенда и интервал опроса задаются прямо в приложении (SharedPreferences `pump`: ключи `baseUrl`, `pollIntervalMs`), дефолты — `Config.DEFAULT_BASE_URL` / `Config.DEFAULT_POLL_INTERVAL_MS`. Применяются при старте сервиса в `PumpService.onStartCommand`, смена требует Stop → Start. На устройстве должно работать `usesCleartextTraffic="true"` для HTTP.

## Сборка
- `minSdk 26`, `targetSdk 34`, `compileSdk 34`
- AGP 8.5.2 + Kotlin 1.9.24
- Gradle 8.7, JVM 17
