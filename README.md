# Rocket
[![Build Status](https://app.bitrise.io/app/52c42ba694bf5d08/status.svg?token=gnh_CwBUvp3PAWHIQoDDVQ&branch=master)](https://app.bitrise.io/app/52c42ba694bf5d08)

Getting Involved
----------------

We encourage you to participate in this open source project. We love Pull Requests, Bug Reports, ideas, (security) code reviews or any kind of positive contribution. Please read the [Community Participation Guidelines](https://www.mozilla.org/en-US/about/governance/policies/participation/).

* Issues: [https://github.com/RocketScientists/Rocket/issues](https://github.com/RocketScientists/Rocket/issues)

Build instructions
------------------

1. Clone the repository:

  ```shell
  git clone https://github.com/RocketScientists/Rocket
  ```
2. Since we're using submodule, run:

  ```shell
git submodule init
git submodule update
  ```


3. Open Android Studio and select File->Open and select Rocket to open the project. Make sure to select the right build variant in Android Studio: **focusWebkitDebug**




Build instructions regarding Firebase
------------------

We're leveraging Firebase to offer some extra functionalities. However, Firebase is optional so normally you should be able to just develop on **focusWebkitDebug**.


Pull request checks
----
To minimize the chance you are blocked by our build checks, you can self check these locally:
1. (build) run `./gradlew clean checkstyle assembleFocusWebkitDebug lint findbugs assembleAndroidTest ktlint`
2. (size check) run `python tools/metrics/apk_size.py focus webkit`
3. (Unit test) run `./gradlew testFocusWebkitDebugUnitTest`
4. (UI test) run `./gradlew connectedAndroidTest`

ktlint
----
- Run `./gradlew ktlintApplyToIDEA` to make your IDE align with ktlint
- Run `./gradlew ktlint` to run check
- If you want to go extreme, run `./gradlew ktlintExec -Parg="-a -F"`. This will use Android rule and gives you a lot of complains about max length, but we are not using it right now.
- See https://ktlint.github.io/ for details.

Docs
----

* [Content blocking](docs/contentblocking.md)
* [Translations](docs/translations.md)
* [Search](docs/search.md)
* [Telemetry](docs/telemetry.md)

License
-------

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/
