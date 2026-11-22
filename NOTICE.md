# NOTICE

## Атрибуция и лицензии

### amnezia-config-gen / awgconfig.com
Логика генерации конфигураций AmneziaWG для Cloudflare WARP портирована из
проекта **amnezia-config-gen** (https://github.com/HereIamGosu/amnezia-config-gen,
автор HereIamGosu), сайт https://awgconfig.com/. Лицензия исходного проекта —
**AGPL-3.0** (см. LICENSE). Проекты не связаны: awgconfig.com не участвует в
разработке этого приложения.

### WireGuard (Apache-2.0)
Файлы `core/crypto/Curve25519.kt`, `core/crypto/Key.kt`, `core/crypto/KeyPair.kt`
портированы из wireguard-android / awg-android
(https://git.zx2c4.com/wireguard-android/, https://github.com/amnezia-vpn/awg-android),
Copyright © 2017–2023 WireGuard LLC, Jason A. Donenfeld, AmneziaWG.
Лицензия **Apache-2.0**. Библиотека туннеля `com.wireguard.android:tunnel`
(GoBackend, libwg-go) распространяется по Apache-2.0 через Maven Central.

### ZXing (Apache-2.0)
Генерация QR-кодов — ZXing (https://github.com/zxing/zxing), Apache-2.0.

### Cloudflare WARP
Регистрация устройств выполняется через официальный публичный API
`api.cloudflareclient.com` так же, как это делает донорский генератор.
Приложение не связано с Cloudflare; условия использования WARP — на
developers.cloudflare.com. Названия Cloudflare WARP, AmneziaWG и AmneziaVPN
принадлежат их владельцам.
