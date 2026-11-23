# Пошаговый план: Android-приложение «AWG Config Generator»

Пошаговый план создания Android-приложения для генерации и пакетной генерации конфигов
AmneziaWG / Cloudflare WARP / MASQUE с подключением к сети через созданный конфиг
(туннелирование всего устройства).

**Донор функций:** https://awgconfig.com/ и его исходники
https://github.com/HereIamGosu/amnezia-config-gen (AGPL-3.0). Логика генерации
портирована в Kotlin и исполняется **локально на устройстве**; наружу ходит только
официальный API регистрации WARP (`api.cloudflareclient.com`) — как у донора.

**Требования:** неоновый тёмный UI в духе awgconfig.com, RU+EN с переключателем,
Android 10+ (API 29), public-репозиторий GitHub, APK со всеми функциями донора,
QR-коды, история генераций, режим keypair, встроенный VPN-туннель.

---

## Этап 0. Исследование донора — выполнено

Изучены исходники amnezia-config-gen и выписана полная карта функций:

| Функция донора | Как переносится в приложение |
|---|---|
| 4 профиля: Legacy 1.5 / AWG 2.0 / AWG 3.0 / AWG 3.1 | `core/awg/AwgProfiles.kt` — те же наборы полей и WARP-safe ограничения |
| Регистрация устройства WARP (POST/PATCH/GET `reg`) | `core/warp/WarpRegistrationApi.kt` — те же запросы, ретраи, классы ошибок |
| Endpoint: hostname / фиксированные IP / TCP-пре-чек 800 мс | `core/warp/EndpointSelector.kt` + встроенный список 11 IP из 5 /24 |
| Порты WARP: 4500, 2408, 500, 1701, 880, 8854 | allowlist в `WarpGenParams` |
| CPS-пакеты I1 (Auto/Static/SIP/STUN/DNS/DTLS/QUIC) | `core/cps/*` — полные бинарные генераторы (STUN с CRC32, DTLS ClientHello, QUIC RFC9001 с header protection) |
| I2–I5 (cps5) — цепочки `<b>/<r>/<t>` | `core/cps/CpsChain.kt` (парсер/валидатор/канонизация) + `ExtraPackets.kt` |
| Маршруты: полный туннель / split по ~40 пресетам, лимит 1000 IPv4 CIDR | `core/routes/RouteCatalog.kt` + ассет `routes.json` из каталога донора |
| DNS-пресеты: Cloudflare/Google/Яндекс/…/malw.link | `core/routes/DnsPresets.kt` |
| Мобильный профиль (Jc=3, IPv4-only), режим роутера | те же константы в `AwgObfuscation` |
| 1–3 конфига за генерацию (разные /24) | пакетный режим WARP (1–3, лимит донора) |
| vpn://-ссылка (qCompress + base64url) | `core/vpnlink/VpnLinkBuilder.kt` |
| История до 20 записей (локально) | `data/HistoryStore.kt` (JSON в filesDir) |
| RU/EN интерфейс | `i18n/Strings.kt` — словари RU/EN + переключатель |
| **Новое (не у донора):** MASQUE | `core/masque/MasqueUriBuilder.kt` — masque:// URI для sing-box/Hiddify-клиентов |
| **Новое:** AWG для своего сервера | экран AWG: host/port/публичный ключ сервера, свои Jc/S/H с пресетами |
| **Новое:** keypair-режим | генерация только пары X25519 |
| **Новое:** встроенный туннель | `tunnel/VpnTunnelController.kt` на `com.wireguard.android:tunnel` (GoBackend, VpnService) — всё устройство |
| **Новое:** пакетная генерация без WARP | AWG-свой-сервер/MASQUE/keypair: до 100 шт., инкрементные IP, ZIP-экспорт |

## Этап 1. Каркас проекта — Gradle + Kotlin + Compose

- Kotlin 2.0.21, AGP 8.5.2, Gradle 8.9, JDK 21 (доступен в среде сборки).
- Jetpack Compose (BOM 2024.10.01) + Material 3; single-module `:app`.
- minSdk 29 (Android 10+), targetSdk/compileSdk 34.
- Зависимости: `com.wireguard.android:tunnel:1.0.20260102` (встроенный WireGuard-бэкенд
  + libwg-go.so на 4 ABI прямо из Maven Central), `com.google.zxing:core` (QR),
  `activity-compose`, `lifecycle-runtime-compose`.
- Структура пакетов: `ui/` (экраны), `core/` (генерация), `tunnel/` (VPN), `data/`
  (история/настройки), `i18n/` (RU/EN).
- Криптография ключей: Curve25519 из wireguard-android (Apache-2.0, файлы скопированы
  в пакет `com.awgconfig.android.core.crypto` с атрибуцией в NOTICE.md).

## Этап 2. Ядро генерации (порт логики донора в Kotlin)

1. `AwgProfiles` + `WarpSafety` — правила полей: S1–S4=0, H1–H4=1–4, MTU=1280,
   DisableCookies только для 3.1; тайминги 3.x (RekeyAfterTime 100–120 и т.д.).
2. `AwgObfuscation` — генерация Jc/Jmin/Jmax (1–25 / 64–800 / +64…1024),
   H-диапазоны для не-WARP (5..2^31-1, полосы ≥65536), S с антикорреляцией
   (S1+56≠S2 и т.п.), mobile/router модификаторы.
3. `CpsChain` — полный парсер цепочек (b/r/rd/rc/t, лимиты 1000/1472/4096,
   совместимость all/modern, канонический вид) — порт `public/static/cps-chain.js`.
4. CPS-генераторы — порт `cpsGenerator.js`+`quic.js`+`dns.js`+`warpCpsPayloads.js`:
   STUN (атрибуты, CRC32 fingerprint), DTLS ClientHello (cloudflare.com),
   QUIC Initial (HKDF/initial salt, AES-GCM, header protection), DNS-ответ,
   SIP INVITE, 3 статических WARP-пейлоада.
5. `WarpRegistrationApi` — те же HTTP-запросы, UA `okhttp/3.12.1`, ретраи
   (3 попытки, 450 мс·2^n + джиттер), классификация ошибок rate_limited/unavailable/rejected.
6. `WarpConfigBuilder` — сборка [Interface]+[Peer] по шаблонам донора для всех
   4 профилей; `VpnLinkBuilder` — vpn:// (zlib + 4-байтный префикс длины + base64url).
7. `RouteCatalog` — ассет routes.json (построен из snapshot донора), агрегация CIDR,
   вырезание /24 подсетей endpoint'ов WARP, лимит 1000 IPv4 + предупреждение.
8. `MasqueUriBuilder` — masque:// URI (host/sni/token/port) + JSON-фрагмент sing-box.
9. Unit-тесты (`app/src/test`): текст конфига по всем профилям, парсер CpsChain,
   vpn:// round-trip, keypair, выбор endpoint.

## Этап 3. UI — неоновый дарк, RU/EN

- Тема: фон #0A0E14/#0D1117, акценты фиолетовый #A78BFA и зелёный #34D399,
  «неоновые» карточки с рамками-свечениями, моношрифт для конфигов.
- Экраны: Home (карточки WARP/AWG/MASQUE/keypair + статус туннеля), WARP-генератор
  (профиль → настройки → результат), AWG-свой-сервер, MASQUE, Batch, Result
  (превью со скрытым PrivateKey, QR, копирование, .conf, vpn://), History,
  Tunnel (подключение/статистика), Settings (язык, DNS по умолчанию, о программе).
- Переключатель RU/EN в настройках (CompositionLocal, без перезапуска).

## Этап 4. QR, история, keypair, batch

- QR: zxing → Bitmap → Compose-канвас; диалог с QR для конфига/ключа/ссылки.
- История: до 20 записей в filesDir/history.json, повторное открытие/экспорт/очистка.
- Keypair: пара X25519, QR обоих ключей, копирование.
- Batch: WARP 1–3; AWG/MASQUE/keypair — до 100, уникальные ключи и инкрементные IP,
  экспорт ZIP со всеми .conf, шаринг.

## Этап 5. Встроенный VPN-туннель (туннелирование всего устройства)

- `GoBackend` из `com.wireguard.android:tunnel` + объявление
  `GoBackend$VpnService` в манифесте.
- Поток: генерация конфига → «Подключиться» → `VpnService.prepare()` (системный
  диалог) → `backend.setState(UP, config)`.
- Конфиг для туннеля собирается из модели (PrivateKey, Address, DNS, MTU,
  PublicKey/AllowedIPs/Endpoint) — AWG-поля обфускации в стандартный WireGuard-бэкенд
  не передаются (WARP-пир — стандартный WireGuard, туннель поднимается).
- Экран туннеля: состояние UP/DOWN, rx/tx статистика, кнопка отключения;
  индикатор на главном экране.
- Ограничение честно указано в UI: конфиги для собственного AWG-сервера (обфускация
  протокола) встроенным туннелем не подключаются — их нужно импортировать в
  AmneziaVPN/AmneziaWG (кнопка «Экспорт для клиента Amnezia» готовит .conf и vpn://).

## Этап 6. Сборка APK

- В песочнице: Android SDK (cmdline-tools → platform-34 + build-tools 34.0.0),
  Gradle 8.9, `assembleRelease` + подпись сгенерированным keystore (keytool).
- В репозитории: GitHub Actions workflow `.github/workflows/build.yml` —
  checkout → JDK 17 → Gradle build → артефакт APK на каждый push/тег.
- Выход: `AWGConfigGenerator-vX.Y.Z.apk` (universal, 4 ABI) в релизах.

## Этап 7. Публикация GitHub

- Public-репозиторий `awg-config-android` (токен пользователя).
- Состав: исходники, PLAN.md, README.md (RU), LICENSE (AGPL-3.0 — как у донора),
  NOTICE.md (атрибуция amnezia-config-gen, WireGuard LLC Apache-2.0, awg-android,
  zxing), CI-workflow.
- Теги `vX.Y.Z` → релизы с APK.

## Этап 8. Критерии готовности v1.0

- [ ] Все 4 профиля AWG генерируются, текст конфига соответствует донору
      (в т.ч. I1–I5, тайминги, mobile/router).
- [ ] Регистрация WARP с устройства работает, ошибки Cloudflare показываются по-русски.
- [ ] Полный туннель и split по пресетам (лимит 1000 CIDR с предупреждением).
- [ ] Пакетная генерация: WARP 1–3, остальные до 100, ZIP-экспорт.
- [ ] QR, копирование, .conf, vpn://, история 20 записей, keypair, MASQUE.
- [ ] Подключение встроенного туннеля по свежесгенерированному WARP-конфигу
      с туннелированием всего устройства, статистика rx/tx.
- [ ] RU/EN переключатель, неоновый тёмный UI.
- [ ] APK собран и приложен, репо запушен, CI зелёный.

## Дорожная карта после v1.0

- v1.1: встроенный бэкенд AmneziaWG (awg-go через gomobile) — полный AWG-протокол
  в туннеле для своих серверов; скрипт сборки в репо.
- v1.2: live-списки маршрутов (opencck/itdoginfo) с офлайн-фолбэком, как у донора.
- v1.3: импорт существующих .conf, редактор, always-on VPN.

---

## v1.1.0 — движок AmneziaWG + реальная проверка + «Мои конфиги»

1. **Единое ядро туннеля**: `tunnel/TunnelCore.kt` поверх движка `org.amnezia.awg`
   (amneziawg-android v3.1.20260814, Apache-2.0): Java-исходники в
   `app/src/main/java/org/amnezia/awg/`, `libwg-go.so` (4 ABI) из официального
   релизного APK — `app/src/main/jniLibs/`. Maven `com.wireguard.android:tunnel` удалён.
   WARP-конфиги: AWG-поля стрипаются (пир — стандартный WG). Собственные AWG-серверы:
   конфиг как есть (Jc/Jmin/Jmax/S1-4/H1-4/I1-5 нативно).
2. **ConfigChecker** (`core/check`): структурный разбор парсером движка, DNS endpoint
   c латентностью, masque:// URI. **TunnelCore.realTest**: туннель → handshake (uapi) →
   cdn-cgi/trace сквозь туннель → выходной IP + warp → disconnect.
3. **ResultScreen**: карточка «Проверка» (быстро/реально) с чек-листом и вердиктом.
4. **ConfigsScreen «Мои конфиги»** (замена Истории, 100 записей, стабильные id):
   фильтры, мультивыбор, пакетный быстрый/реальный тест с прогрессом, ZIP, удаление.
