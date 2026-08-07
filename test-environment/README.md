# Локальное тестовое окружение

Минимальный стенд Semaphore UI `v2.19.7` с SQLite.

## Запуск

```bash
docker compose -f test-environment/compose.yml up -d
```

После запуска UI доступен по адресу <http://localhost:3000>.

- пользователь: `admin`
- пароль: `test-password`

## Состояние и логи

```bash
docker compose -f test-environment/compose.yml ps
docker compose -f test-environment/compose.yml logs -f semaphore
```

## Остановка

```bash
docker compose -f test-environment/compose.yml down
```

SQLite хранится в именованном Docker volume и сохраняется между перезапусками.

## Быстрая проверка

```bash
curl http://localhost:3000/api/ping
```

Ожидаемый ответ: `pong`.

## API smoke

Smoke создаёт отдельный проект, проверяет его и удаляет в блоке cleanup:

```bash
node test-environment/api-smoke.mjs
```

Адрес стенда и учётную запись можно переопределить:

```bash
SEMAPHORE_BASE_URL=http://localhost:3000 \
SEMAPHORE_USERNAME=admin \
SEMAPHORE_PASSWORD=test-password \
node test-environment/api-smoke.mjs
```
