# Spectra

[English](../README.md) | Русский

![Скриншот из приложения](./screenshot.jpg)

Это приложение для Android, обертка для [ядра Xray](https://github.com/XTLS/Xray-core).

Для взаимодействия с ядром используется [AndroidLibXrayLite от 2dust](https://github.com/2dust/AndroidLibXrayLite).

Проект нестабилен, поэтому может кардинально меняться со временем.

Основные источники вдохновения: [sing-box-for-android](https://github.com/SagerNet/sing-box-for-android), [v2rayNG](https://github.com/2dust/v2rayNG).

## Что сломано

- Система подписок (vless:// и так далее). Импортируются, но надо написать нормальный парсер и редактор конфига.

- Пока нормально работает полный конфиг Xray. Пример конфига:

```json
{
    "log": {
        "loglevel": "warning"
    },
    "dns": {
        "queryStrategy": "UseIPv4",
        "servers": [
            {
                "address": "https://1.1.1.1/dns-query"
            }
        ]
    },
    "routing": {
        "domainStrategy": "AsIs",
        "rules": [
            {
                "type": "field",
                "inboundTag": [
                    "tun-in"
                ],
                "port": "53",
                "outboundTag": "dns-out"
            }
        ]
    },
    "inbounds": [
        {
            "protocol": "tun",
            "tag": "tun-in",
            "settings": {
                "MTU": 9000
            },
            "sniffing": {
                "enabled": true,
                "destOverride": [
                    "http",
                    "tls"
                ]
            }
        }
    ],
    "outbounds": [
        {
            "tag": "direct-out",
            "protocol": "freedom",
            "settings": {
                "targetStrategy": "UseIPv4"
            }
        },
        {
            "protocol": "dns",
            "tag": "dns-out",
            "settings": {
                "nonIPQuery": "skip"
            }
        },
        {
            "tag": "block-out",
            "protocol": "blackhole",
            "settings": {
                "response": {
                    "type": "http"
                }
            }
        }
    ]
}
```

## Что реализовано (на бумаге)

- Английская, русская локализация

- Поддержка Android TV

- Android VPN <-> Xray TUN

- Экран логов Xray

- Импорт конфигов
  - Через URL
  - Вручную
  - По QR коду (в одной локальной сети)
  - Из файла,
  - Локальной ссылкой (spectra.local)

- Автоматическое обновление конфигов, добавленных по URL, каждые n-минут.
Если сервис запущен и текущий конфиг обновился, то сервис перезапускается.

- Поделиться конфигом (сканировать QR код)

- Группы

- Добавление своих гео-файлов Xray

## Почему на бумаге

На данный момент, 99% кода написано нейросетью Gemini, за что ей огромное спасибо. Я понемногу тестирую, указываю на баги, и совсем немного правлю код.
Это сократило время разработки в кучу раз, но минусы такого подхода понятны.

Пока так.

## Дополнительно

### Поддерживаемые ссылки

Для импорта конфигов по ссылке, нужно включить "Поддерживаемые ссылки" для приложения в настройках Android.

При запуске приложения должен возникать диалог, побуждающий вас это сделать.

Схема такая:

`https://spectra.local/import?data=base64`

где base64:

`name=Base&autoupdate=true&autoupdateinterval=60&url=https://example.com/config.json`

- name: имя профиля
- autoupdate: true | false, включить автообновление?
- autoupdateinterval: интервал автообновления, в минутах.
- url: ссылка на json конфиг Xray.

Вся эта строка должна быть закодирована в base64.

## Сборка

## AndroidLibXrayLite

В [директорию app/libs](../app/libs) положить `libv2ray.aar` (обязательно) и `libv2ray-sources.jar` (если нужно, для типов в IDE).

Их можно скачать или скомпилировать самому из репозитория AndroidLibXrayLite, по ссылке в начале README.

После этого можно собирать проект в Android Studio, как обычно.
