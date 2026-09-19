# PumpSms — отправитель SMS для shop

Личный Android-пумпер: каждые 10с забирает задачу с `GET /sms/outbox?sender_phone=...`, шлёт SMS, отчитывается `POST /sms/outbox-delivered/:id` / `POST /sms/outbox-failed/:id`. Работает в фоне (экран потух) и после перезагрузки.

## Быстрый старт
```bash
# 1) Подправить адрес бекенда
# app/src/main/java/ru/pumpsms/Config.kt → BASE_URL = "http://192.168.1.XXX:4010"
# бекенд должен слушать 0.0.0.0, телефон в той же Wi-Fi

# 2) Сборка (требует JDK 17 и Android SDK)
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk  → установить на устройство

# 3) На устройстве: дать SEND_SMS + POST_NOTIFICATIONS, ввести номер устройства, Старт
# 4) В Shop вызвать POST /sms/request-otp → SMS уходит, статус delivered
```

Docs: `docs/01-plan.md` … `docs/05-oem-background.md`.
