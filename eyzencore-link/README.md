# Eyzencore Link — плагін для Minecraft сервера

Технічне завдання на плагін, який з'єднує сервер **Eyzencore** (Minecraft 26.2, Vanilla++) із сайтом.
Документ — джерело правди для наступної ітерації: усе, що тут описано, сайт або вже очікує, або має отримати.

## Навіщо

1. **Статистика в профілі.** Сторінка `/profile` показує статистику гравця. Зараз вона бере дані з
   `data/players/<discordId>.json` (див. `lib/stats.ts`), а для тестових акаунтів — демо-дані. Плагін має
   наповнювати ці дані.
2. **Прив'язка Minecraft ↔ Discord.** Гравець входить на сайт через Discord, а в грі має нікнейм/UUID.
   Їх треба зв'язати, щоб знати, чию статистику показувати.
3. **Доступ за заявками (наступний етап).** На сайті гравець подає заявку, адмін її приймає. Плагін пускає на
   сервер лише гравців із прийнятою заявкою. Реєстрацій і паролів (`/register`, `/login`) у грі немає.

## Стек

- Paper API для **26.2** (перевірити актуальну версію Paper та мінімальну Java на момент розробки).
- Gradle (Kotlin DSL), Java.
- HTTP-клієнт: `java.net.http.HttpClient`, JSON: Gson (є в Paper).
- Версія плагіна — у `gradle.properties`, **при кожній зміні піднімати semver** і перезбирати jar.

## Архітектура

```
Minecraft сервер (плагін)  ──HTTPS + Bearer token──▶  Сайт (Next.js, /api/plugin/*)
                                                       └─ data/players/<discordId>.json
                                                       └─ data/links.json
```

- Плагін **тільки ініціює запити** до сайту, сайт до сервера не стукає.
- Усі HTTP-запити — **асинхронно** (`Bukkit.getScheduler().runTaskAsynchronously` або `CompletableFuture`),
  ніколи не в головному потоці. Читання `Statistic` — у головному потоці, відправка — в асинхронному.
- Таймаут запиту 5 с, при помилці — повтор із наростаючою затримкою, не частіше ніж раз на 30 с.

## Конфіг `config.yml`

```yaml
api:
  base-url: "https://eyzencore.net"   # локально: http://localhost:3000
  token: "CHANGE_ME"                  # = PLUGIN_API_TOKEN у .env сайту
stats:
  push-interval-minutes: 5            # періодична відправка для онлайн-гравців
  push-on-quit: true
whitelist:
  enabled: false                      # пускати лише гравців з прийнятою заявкою
  kick-message: "Подай заявку на сайті eyzencore.net"
custom-mobs:
  # Як розпізнати кастомного моба: за scoreboard-тегом або ключем PersistentDataContainer,
  # який ставить датапак/плагін мобів. id має збігатися з id моба на сайті (адмінка → Моби).
  crab:      { tag: "eyzencore.crab" }
  penguin:   { tag: "eyzencore.penguin" }
  deer:      { tag: "eyzencore.deer" }
  monkey:    { tag: "eyzencore.monkey" }
  alligator: { tag: "eyzencore.alligator" }
  ostrich:   { tag: "eyzencore.ostrich" }
  meerkat:   { tag: "eyzencore.meerkat" }
  vulture:   { tag: "eyzencore.vulture" }
  moobloom:  { tag: "eyzencore.moobloom" }
```

> Спершу з'ясувати, як саме реалізовані кастомні моби (датапак не може додати новий тип сутності — найімовірніше
> це ванільна сутність з тегом/моделлю з ресурспаку або окремий плагін/мод) і підлаштувати розпізнавання.

## Команди

| Команда | Хто | Що робить |
|---|---|---|
| `/link <код>` | гравець | Прив'язує Minecraft-акаунт до Discord-акаунта на сайті |
| `/unlink` | гравець | Відв'язує акаунт |
| `/eyzencore reload` | `eyzencore.admin` | Перечитує конфіг |
| `/eyzencore push [нік]` | `eyzencore.admin` | Примусово відправляє статистику |

## Прив'язка акаунта

1. Гравець на сайті в `/profile` натискає «Прив'язати Minecraft» → сайт генерує одноразовий код
   (6 символів, `A-Z0-9`, живе 10 хв) і зберігає `code → discordId`.
2. У грі: `/link K7Q2XM`.
3. Плагін → `POST /api/plugin/link`. Сайт перевіряє код, зберігає зв'язку `uuid ↔ discordId`.
4. Плагін показує результат у чаті й одразу відправляє статистику гравця.

## API сайту

Усі запити: `Authorization: Bearer <PLUGIN_API_TOKEN>`, `Content-Type: application/json`.
Відповідь `401` — неправильний токен, `400` — невалідне тіло, `5xx` — повторити пізніше.

### `POST /api/plugin/link`

```json
{ "code": "K7Q2XM", "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "nickname": "Steve" }
```

Відповіді:

```json
{ "ok": true, "discordUsername": "steve_ua" }
{ "ok": false, "error": "invalid_code" }     // або "expired_code", "already_linked"
```

### `POST /api/plugin/unlink`

```json
{ "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5" }
```

### `POST /api/plugin/stats`

Батч статистики. Плагін шле лише гравців, які були онлайн з моменту попередньої відправки, плюс тих, хто вийшов.
Сайт зберігає запис лише для прив'язаних UUID, решту ігнорує.

```json
{
  "server": "eyzencore",
  "sentAt": "2026-10-01T21:45:00Z",
  "players": [
    {
      "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
      "nickname": "Steve",
      "online": false,
      "firstJoin": "2026-06-14T18:20:00Z",
      "lastSeen": "2026-10-01T21:45:00Z",
      "playtime": 7385,
      "deaths": 37,
      "mobKills": 1284,
      "playerKills": 3,
      "blocksMined": 48210,
      "blocksPlaced": 21907,
      "distance": 412350,
      "advancements": 64,
      "advancementsTotal": 122,
      "customMobKills": { "crab": 12, "alligator": 4, "vulture": 9 }
    }
  ]
}
```

Відповідь: `{ "ok": true, "stored": 1, "ignored": 0 }`.

Сайт пише кожного гравця в `data/players/<discordId>.json` у форматі `PlayerStats` з `lib/stats.ts`
(те саме тіло гравця + поле `updatedAt`).

### `GET /api/plugin/whitelist` (етап заявок)

```json
{ "uuids": ["069a79f4-44e9-4726-a5be-fca90e38aaf5"], "nicknames": ["Steve"] }
```

Плагін кешує список і оновлює раз на хвилину. Перевірка — в `AsyncPlayerPreLoginEvent`:
якщо `whitelist.enabled` і гравця немає в списку → `disallow(KICK_WHITELIST, kick-message)`.
Якщо сайт недоступний — використовувати останній успішний кеш (зберігати на диск).

## Звідки брати кожне поле

| Поле | Одиниці | Джерело в Paper API |
|---|---|---|
| `playtime` | хвилини | `Statistic.PLAY_ONE_MINUTE` (це тіки!) `/ 20 / 60` |
| `deaths` | шт | `Statistic.DEATHS` |
| `mobKills` | шт | `Statistic.MOB_KILLS` |
| `playerKills` | шт | `Statistic.PLAYER_KILLS` |
| `blocksMined` | шт | сума `Statistic.MINE_BLOCK` по всіх `Material`, де `isBlock()` |
| `blocksPlaced` | шт | сума `Statistic.USE_ITEM` по всіх `Material`, де `isBlock()` (у ванілі розміщення блока = використання предмета) |
| `distance` | метри | сума `WALK_ONE_CM`, `SPRINT_ONE_CM`, `CROUCH_ONE_CM`, `SWIM_ONE_CM`, `WALK_ON_WATER_ONE_CM`, `WALK_UNDER_WATER_ONE_CM`, `CLIMB_ONE_CM`, `FLY_ONE_CM`, `AVIATE_ONE_CM`, `HORSE_ONE_CM`, `BOAT_ONE_CM`, `MINECART_ONE_CM`, `PIG_ONE_CM`, `STRIDER_ONE_CM` `/ 100` |
| `advancements` | шт | `Bukkit.advancementIterator()`, без ключів `recipes/*`, `player.getAdvancementProgress(a).isDone()` |
| `advancementsTotal` | шт | кількість тих самих досягнень без `recipes/*` (рахувати один раз при старті) |
| `firstJoin` | ISO 8601 UTC | `player.getFirstPlayed()` |
| `lastSeen` | ISO 8601 UTC | онлайн — поточний час, офлайн — `player.getLastSeen()` |
| `online` | bool | `player.isOnline()` |
| `customMobKills` | `{ id: шт }` | власний лічильник: `EntityDeathEvent`, вбивця — гравець, сутність має тег з `custom-mobs` → +1; зберігати в PDC гравця |

Суми по `Material` — дорогі: рахувати лише для гравців з батчу, а список блокових матеріалів кешувати при старті.

## Що доробити на сайті разом з плагіном

- [ ] `PLUGIN_API_TOKEN` у `.env.local` / `.env.example`, перевірка токена через `crypto.timingSafeEqual`.
- [ ] `app/api/plugin/link/route.ts`, `unlink`, `stats`, `whitelist`.
- [ ] Генерація коду прив'язки в `/profile` (server action), зберігання в `data/links.json`
      (`codes: { code: { discordId, expiresAt } }`, `links: { uuid: discordId }`).
- [ ] Запис `data/players/<discordId>.json` — його вже читає `getPlayerStats` у `lib/stats.ts`.
- [ ] Блок «Прив'язати Minecraft» у профілі замість заглушки «Статистики ще немає».
- [ ] Сторінка заявки `/apply` + розділ «Заявки» в адмінці (прийняти / відхилити, коментар),
      статус заявки в профілі, сповіщення в Discord через вебхук.
- [ ] Валідація тіла запитів (обмеження розміру батчу, типів і діапазонів чисел).

## Перевірка

1. Запустити сайт локально (`npm run dev`), у `config.yml` вказати `http://localhost:3000`.
2. На сайті увійти через Discord → профіль → отримати код → `/link <код>` у грі.
3. Пограти кілька хвилин, вийти → у профілі з'явиться статистика, `data/players/<discordId>.json` оновився.
4. Неправильний токен → сайт повертає `401`, плагін пише попередження в консоль і не спамить повторами.

---

## Збірка плагіна

```bash
cd eyzencore-link
# Java 25+
./gradlew jar
```

Jar: `build/libs/EyzencoreLink-<version>.jar` → покласти в `plugins/`.
У `plugins/EyzencoreLink/config.yml` вказати `api.token` (= `PLUGIN_API_TOKEN` сайту).
