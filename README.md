# DeepSeek Chat App (Android)

Android-приложение чата с LLM `deepseek-chat`

## Стек

- Kotlin
- Jetpack Compose + Material3
- Single Activity + Compose Navigation
- MVVM
- Clean Architecture (`data / domain / presentation`)
- Koin (DI)
- Room (DB)
- Retrofit + OkHttp (SSE streaming, чтение чанков)
- Coroutines + Flow
- DataStore Preferences (active session)

## Где указать API key

1. В корне проекта создайте файл `local.properties` (если его нет).
2. Добавьте:

```properties
DEEPSEEK_API_KEY=your_api_key_here
```

> Ключ прокидывается в `BuildConfig.DEEPSEEK_API_KEY`.

## Как задать baseUrl

В том же `local.properties` задайте:

```properties
DEEPSEEK_BASE_URL=https://api.deepseek.com/v1/
```

По умолчанию используется `https://api.deepseek.com/v1/`.

Если нужен OpenAI-compatible путь с версией API, можно задать, например:

```properties
DEEPSEEK_BASE_URL=https://api.deepseek.com/v1/
```

В коде используется endpoint `chat/completions`, поэтому итоговый URL будет `BASE_URL + chat/completions`.

## Как запустить

1. Откройте проект `DeepSeekChatApp` в Android Studio (последняя стабильная версия).
2. Убедитесь, что установлен Android SDK (API 35).
3. Синхронизируйте Gradle.
4. Запустите `app` на эмуляторе/устройстве (`minSdk 26`).

## Контекст сессии

- По умолчанию перед каждым запросом в API собирается весь контекст сообщений текущей сессии из Room + новое сообщение пользователя.
- Для новой сессии (до первого отправленного сообщения) можно включить `Context: summary + последние 10`.
- В режиме summary применяется пошаговый алгоритм:
  - последние 10 сообщений всегда отправляются в LLM «как есть»;
  - сообщения старше последних 10 помечаются как `READY_FOR_SUMMARY` и тоже отправляются «как есть»;
  - когда `READY_FOR_SUMMARY` набирается 10, отправляется отдельный запрос в LLM на сжатие этих 10 сообщений;
  - после сжатия эти 10 сообщений помечаются как `SUMMARIZED` и больше не отправляются «как есть»;
  - вместо них в контекст добавляется `contextSummary`;
  - для следующей десятки сжатие выполняется с учетом текущего `contextSummary`, а summary заменяется новым.
- Для каждой сессии контекст независим.
- Сессии сортируются по `updatedAt`.
