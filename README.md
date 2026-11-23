# AWG Config Generator (Android)

Неоновое Android-приложение (Kotlin + Jetpack Compose) для **генерации и пакетной
генерации конфигов AmneziaWG / Cloudflare WARP / MASQUE** — и **встроенного VPN-туннеля**:
свежесгенерированный WARP- или AWG-конфиг можно сразу включить и туннелировать весь трафик устройства,
не выходя из приложения. Движок — настоящий AmneziaWG (amneziawg-android, Apache-2.0): с 1.1.0
поддерживается и обфускация Jc/Jmin/Jmax/S1-4/H1-4/I1-5 для собственных серверов, и реальная
проверка валидности конфигов вплоть до handshake и выходного IP.

Функциональный «донор» — генератор https://awgconfig.com/
([amnezia-config-gen](https://github.com/HereIamGosu/amnezia-config-gen), AGPL-3.0).
Вся логика генерации портирована в Kotlin и работает локально на устройстве; наружу
ходит только официальный API регистрации Cloudflare WARP.

![CI](https://github.com/mx57/awg-config-android/actions/workflows/build.yml/badge.svg)

## Возможности

- **Единое ядро туннеля (AmneziaWG)**: org.amnezia.awg GoBackend + libwg-go.so (4 ABI) —
  подключение WARP-конфигов и собственных AWG-серверов, статистика rx/tx, handshake по uapi.
- **Реальная проверка конфигов**: разбор парсером движка (форматы ключей/адресов/AllowedIPs),
  DNS endpoint с замером; «реальный» тест — поднимает туннель, ждёт handshake,
  измеряет выходной IP (cdn-cgi/trace) и флаг WARP, затем гасит туннель.
- **«Мои конфиги»**: раздел со всеми генерациями (до 100), фильтры WARP/AWG/MASQUE,
  мультивыбор, быстрый и реальный **пакетный тест**, ZIP-экспорт, удаление.

- **WARP-конфиги AmneziaWG**: 4 профиля — Legacy 1.5 / AWG 2.0 / AWG 3.0 / AWG 3.1
  (WARP-safe: S1–S4=0, H1–H4=1–4, MTU 1280; тайминги 3.x, ContentPaddingAddition, DisableCookies 3.1).
- **Регистрация WARP с устройства** через официальный API (POST/PATCH/GET reg,
  ретраи, человеческие тексты ошибок Cloudflare).
- **CPS-пакеты I1–I5**: Auto / Static / SIP / STUN / DNS / DTLS / QUIC (полные
  бинарные генераторы, порт донора), цепочки `<b>/<r>/<t>` с валидатором.
- **Маршруты**: полный туннель или split по ~40 пресетам (YouTube, Telegram, Discord,
  WhatsApp, Instagram, X, Netflix, Steam, ChatGPT, Google, GitHub и др.), лимит 1000 IPv4 CIDR,
  вырезание подсетей endpoint'ов WARP, +IPv6.
- **DNS-пресеты**: Cloudflare, Google, Яндекс, Яндекс Безопасный, AdGuard, Quad9, Comss.one, malw.link.
- **Endpoint'ы**: hostname `engage.cloudflareclient.com`, фиксированные IP (11 шт. из 5 /24),
  «умный» выбор по TCP-латентности; порты 4500 / 2408 / 500 / 1701 / 880 / 8854.
- **Профили устройств**: мобильный (Jc=3, IPv4-only) и роутерный (малые Jc/Jmin/Jmax).
- **vpn://-ссылки** для AmneziaVPN (qCompress + base64url, как у донора).
- **MASQUE**: генератор masque://-URI (+JSON sing-box) — экспериментально.
- **AWG для своего сервера**: host/port/публичный ключ, Jc/Jmin/Jmax/S/H с пресетами.
- **Keypair-режим**: только пара ключей X25519.
- **Пакетная генерация**: WARP 1–3; AWG/MASQUE/keypair — до 100 с уникальными ключами
  и инкрементными IP, экспорт ZIP.
- **QR-коды** для конфигов и ключей.
- **История** до 20 генераций (локально, можно очистить).
- **Встроенный VPN-туннель** (WireGuard GoBackend): подключение по конфигу
  с туннелированием всего устройства, статистика rx/tx.
- **RU / EN** интерфейс с переключателем, тёмный неоновый UI.

## Сборка

Требуется JDK 17+ и Android SDK (platform 34).

```bash
./gradlew assembleRelease        # APK: app/build/outputs/apk/release/
./gradlew test                    # unit-тесты ядра генерации
```

Или заберите готовый APK из GitHub Releases / артефактов Actions.

## Использование

1. Выберите профиль (AWG 2.0 — по умолчанию) и, при желании, маршруты/DNS/endpoint/порт.
2. Нажмите «Сгенерировать» — приложение зарегистрирует новое устройство WARP и соберёт конфиг.
3. Скачайте `.conf`, скопируйте `vpn://`, отсканируйте QR — или нажмите
   **«Подключиться»**: туннель поднимется на устройстве целиком.
4. Для конфигов под собственный AWG-сервер используйте экран «AWG (свой сервер)» и
   импортируйте .conf в AmneziaVPN/AmneziaWG.

## Лицензия

AGPL-3.0 (см. LICENSE и NOTICE.md). Проект неофициальный, не связан с Cloudflare
и командой Amnezia; вы сами отвечаете за соблюдение законов своей страны.
