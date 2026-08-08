# Zapret для macOS

Локальный обход DPI без сторонних серверов и без VPN. Трафик остаётся у вас на машине: приложение ставит движок и чуть «ломает» сигнатуры, по которым режут Discord, YouTube и другие сайты.

Основа — [bol-van/zapret](https://github.com/bol-van/zapret). Стратегии и списки — в духе [Flowseal](https://github.com/Flowseal/zapret-discord-youtube). Управление — через приложение **Zapret**.

## Что сейчас умеет

- Обход DPI на macOS для HTTPS/QUIC и голоса Discord (пакетный desync, не «прокси в облако»).
- Готовые стратегии (в т.ч. SIMPLE FAKE и FAKE TLS AUTO); в настройках можно **подобрать стратегию** автопробой Discord / YouTube.
- Редактируемые списки доменов и IP; сброс к пакету одним кликом.
- Рядом с корпоративным VPN — только **split-tunnel** (обход идёт через обычный Wi‑Fi/Ethernet, туннель не трогаем).
- Опционально — прокси для **Telegram Desktop** (MTProto → WebSocket). Сайт `web.telegram.org` этим не чинится, если режут IP Telegram целиком.

Это **не VPN** и не анонимайзер: цель — пройти местный DPI, а не сменить страну или спрятать трафик.

## Установка

macOS 12+, пароль администратора при первой установке движка.

**Homebrew**

```bash
brew tap nikitaSobolev2/zapret2 https://github.com/nikitaSobolev2/zapret2
brew install --cask zapret
```

Обновление: `brew update && brew upgrade --cask zapret`.

**DMG** — с [Releases](https://github.com/nikitaSobolev2/zapret2/releases): перетащить в «Программы».

Если Gatekeeper ругается (сборка без подписи Apple):

```bash
xattr -cr /Applications/Zapret.app
```

## Как пользоваться

1. Запустите **Zapret**, нажмите кнопку питания (один раз спросят пароль).
2. Дождитесь статуса «Работает».
3. Если Discord/YouTube всё ещё тупят — **Настройки → Подобрать стратегию**, либо выберите профиль вручную и нажмите **Применить**.
4. Для Telegram Desktop: в настройках оставьте прокси включённым → **Открыть в Telegram**.
5. Удобно: «Вкл/выкл без пароля» — дальше стоп/старт без диалога sudo.

Удаление: Настройки → **Удалить…** (лучше «приложение и движок»). Для cask ещё: `brew uninstall --cask zapret`.

## Ограничения

- Нужен обычный интернет с шлюзом (Wi‑Fi/Ethernet). Полный tunnel-VPN «всего трафика» обычно мешает.
- Не все сети и не все блокировки обходятся; иногда помогает другая стратегия или правка списков.
- Автоподбор пробует готовые профили — он не изобретает новые трюки под ваш провайдер.

## Документация и исходники

- [macos-app.md](macos-app.md) — детали приложения и релиза  
- [manual.md](manual.md) / [manual.en.md](manual.en.md) — полный мануал upstream zapret2 (Linux/роутеры/Lua)  
- Благодарности по движку: [`engine/NOTICE.md`](../engine/NOTICE.md)

## Поддержать upstream

Если хотите поддержать автора оригинального zapret:

USDT ERC `0x3d52Ce15B7Be734c53fc9526ECbAB8267b63d66E`  
USDT TRC `TEzAAtn4VhndqEaAyuCM78xh5W2gCjwWEo`  
BTC `bc1qhqew3mrvp47uk2vevt5sctp7p2x9m7m5kkchve`  
ETH `0x3d52Ce15B7Be734c53fc9526ECbAB8267b63d66E`
