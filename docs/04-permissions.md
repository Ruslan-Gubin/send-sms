# Пермишены

| Пермишен | Тип | Зачем |
|---|---|---|
| `INTERNET` | normal | Сеть к бекенду |
| `SEND_SMS` | dangerous (runtime) | Отправка SMS |
| `READ_PHONE_STATE` | dangerous (runtime, optional) | Префилл номера устройства (`getLine1Number`) |
| `POST_NOTIFICATIONS` | dangerous (runtime, API33+) | Уведомление ForegroundService |
| `RECEIVE_BOOT_COMPLETED` | normal | Автозапуск после перезагрузки |
| `FOREGROUND_SERVICE` | normal | Запуск ForegroundService |
| `FOREGROUND_SERVICE_SPECIAL_USE` | normal | Тип сервиса `specialUse` (API 34+) |

## Runtime-запросы
- При нажатии Старт — `SEND_SMS` + `POST_NOTIFICATIONS` (на Android 13). Отказ → лог «разрешение не выдано», сервис не стартует.
- `READ_PHONE_STATE` — запрашивается только при клике «Подставить мой номер», не блокирует старт.

## Манифест
- `android:usesCleartextTraffic="true"` на `<application>` — бекенд HTTP на локальном IP.
- Сервис: `android:foregroundServiceType="specialUse"` + property `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE="smsPump"`.
- `BootReceiver` экспортирован, фильтр `BOOT_COMPLETED`.
