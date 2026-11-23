# NOTICE

## Атрибуция и лицензии

### amnezia-config-gen / awgconfig.com
Логика генерации конфигураций AmneziaWG для Cloudflare WARP портирована из
проекта **amnezia-config-gen** (https://github.com/HereIamGosu/amnezia-config-gen,
автор HereIamGosu), сайт https://awgconfig.com/. Лицензия исходного проекта —
**AGPL-3.0** (см. LICENSE). Проекты не связаны: awgconfig.com не участвует в
разработке этого приложения.

### AmneziaWG / amneziawg-android (Apache-2.0)
Встроенный движок туннелирования — исходники `org.amnezia.awg.*` в
`app/src/main/java/org/amnezia/awg/` (GoBackend, config-парсер с полями
Jc/Jmin/Jmax/S1-4/H1-4/I1-5, crypto) и нативная библиотека `libwg-go.so`
(4 ABI, `app/src/main/jniLibs/`) из официального клиента AmneziaWG:
https://github.com/amnezia-vpn/amneziawg-android (форк wireguard-android),
Copyright © 2017–2023 WireGuard LLC, Jason A. Donenfeld, AmneziaWG.
Лицензия **Apache-2.0**. Файлы `core/crypto/*` приложения — порт тех же
классов Curve25519/Key/KeyPair. `libwg-go.so` собран из amneziawg-go
(https://github.com/amnezia-vpn/amneziawg-go) в составе релиза
AmneziaWG-3.1.20260814.

**Модификации vendored-кода** (помечены `[awg-config]`): `GoBackend$VpnService`
запускается как foreground-сервис с постоянным уведомлением (Android 14 —
тип `specialUse`) — туннель переживает свайп приложения из «недавних»;
уведомление использует ресурсы `tunnel_notif_*` приложения. Также увеличен
таймаут старта VPN-сервиса (2 c → 5 c) и добавлена защита `protect(fd)` от
fd < 0.

### ZXing (Apache-2.0)
Генерация QR-кодов — ZXing (https://github.com/zxing/zxing), Apache-2.0.

### Cloudflare WARP
Регистрация устройств выполняется через официальный публичный API
`api.cloudflareclient.com` так же, как это делает донорский генератор.
Приложение не связано с Cloudflare; условия использования WARP — на
developers.cloudflare.com. Названия Cloudflare WARP, AmneziaWG и AmneziaVPN
принадлежат их владельцам.
