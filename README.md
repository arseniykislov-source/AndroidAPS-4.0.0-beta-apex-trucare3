# AAPS
* Check the wiki: https://wiki.aaps.app
* Want to contribute? Please read [CONTRIBUTING.md](CONTRIBUTING.md) first — note that AI-generated pull requests are not welcome.
*  Everyone who’s been looping with AAPS needs to fill out the form after 3 days of looping  https://docs.google.com/forms/d/14KcMjlINPMJHVt28MDRupa4sz4DDIooI4SrW0P3HSN8/viewform?c=0&w=1

[![Support Server](https://img.shields.io/discord/629952586895851530.svg?label=Discord&logo=Discord&colorB=7289da&style=for-the-badge)](https://discord.gg/4fQUWHZ4Mw)

[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/master.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/master)
[![Crowdin](https://d322cqt584bo4o.cloudfront.net/androidaps/localized.svg)](https://translations.aaps.app/project/androidaps)
[![Documentation Status](https://readthedocs.org/projects/androidaps/badge/?version=latest)](https://wiki.aaps.app/en/latest/?badge=latest)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/master/graph/badge.svg?token=EmklfIV6bH)](https://codecov.io/gh/nightscout/AndroidAPS)

DEV: 
[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/dev/graph/badge.svg?token=EmklfIV6bH)](https://codecov.io/gh/nightscout/AndroidAPS/tree/dev)

<img src="https://cdn.iconscout.com/icon/free/png-256/bitcoin-384-920569.png" srcset="https://cdn.iconscout.com/icon/free/png-512/bitcoin-384-920569.png 2x" alt="Bitcoin Icon" width="100">

3KawK8aQe48478s6fxJ8Ms6VTWkwjgr9f2

# AndroidAPS 4.0.0 — Apex TruCare III Pump Driver

⚠️ **DISCLAIMER / ПРЕДУПРЕЖДЕНИЕ О БЕЗОПАСНОСТИ**
This is an experimental open-source build intended for personal testing and educational purposes only. AndroidAPS is not a certified medical device. Use this code at your own risk. Always verify insulin delivery directly on your pump.
Это экспериментальная сборка с открытым исходным кодом, предназначенная исключительно для личного тестирования. Программа не является сертифицированным медицинским изделием. Используйте на свой страх и риск и всегда сверяйте подачу инсулина по экрану самой помпы.

---

### Credits & Acknowledgments / Об авторах

* **Original Driver & Bluetooth Protocol Reverse Engineering:** Roman Richter ([tdrkDev](https://github.com)) — based on his initial WIP implementation (PR `#3896`). Almost the entire core logic and communication protocol of the driver belong to him.
* **Porting & Adaptation to AndroidAPS 4.0.0:** [Ваше Имя и Фамилия / Ваш ник на GitHub] — updated dependencies, resolved compatibility issues, and adapted the driver to build and run on the AndroidAPS 4.0.0 codebase.

* **Оригинальный драйвер и реверс-инжиниринг протокола:** Роман Рихтер ([tdrkDev](https://github.com)) — за основу взята его первоначальная реализация (PR `#3896`). Почти вся базовая логика драйвера и протокол обмена данными принадлежат ему.
* **Адаптация и перенос на AndroidAPS 4.0.0:** [Ваше Имя и Фамилия / Ваш ник на GitHub] — обновление зависимостей, устранение ошибок совместимости и доработка кода для стабильной сборки и работы на ядре AAPS 4.0.0.

---

### Status / Текущий статус
* Successfully builds in Android Studio without manual folder setup.
* Actively tested in a real closed-loop setup (`OpenAPSSMB` on AAPS 4.0.0): Bluetooth connection, battery/reservoir telemetry, bolus history sync, and temporary basal rates (TBR) work reliably.
