# SI13 Android

Учебное Android-приложение на Kotlin. Проект используется для изучения Android-разработки, навигации, авторизации через Google/Firebase и подготовки UI к Espresso-тестам.

## Что умеет приложение

- Запускает `MainActivity` как единственную entry point activity.
- Показывает основной экран с bottom navigation.
- Поддерживает два раздела:
  - `Home`
  - `Profile`
- Для незалогиненного пользователя при старте показывает login bottom sheet.
- Позволяет продолжить работу как guest.
- Позволяет войти через Google.
- Сохраняет состояние авторизации локально.
- Показывает guest profile для незалогиненного пользователя.
- Показывает Google account data для залогиненного пользователя:
  - имя
  - email
  - avatar, если он доступен
- Позволяет выйти из аккаунта.

## Модули

Проект содержит один Android-модуль:

```text
app
```

Package/application id:

```text
com.si13.app
```

Основная конфигурация модуля находится в:

```text
app/build.gradle.kts
```

Версии зависимостей вынесены в:

```text
gradle/libs.versions.toml
```

## Основные экраны

### MainActivity

Файл:

```text
app/src/main/java/com/si13/app/MainActivity.kt
```

`MainActivity`:

- загружает `activity_main.xml`
- настраивает `NavHostFragment`
- подключает `BottomNavigationView` к `NavController`
- при первом запуске проверяет auth state
- если пользователь не залогинен, показывает `LoginBottomSheet`

### HomeFragment

Файл:

```text
app/src/main/java/com/si13/app/HomeFragment.kt
```

Простой стартовый экран приложения. Использует layout:

```text
app/src/main/res/layout/fragment_home.xml
```

### ProfileFragment

Файл:

```text
app/src/main/java/com/si13/app/ProfileFragment.kt
```

Показывает разные состояния профиля:

- guest mode
- authenticated mode

В guest mode отображается:

- статус `Guest`
- описание guest-режима
- кнопка `Continue with Google`

В authenticated mode отображается:

- имя пользователя
- email
- avatar, если доступен
- кнопка `Sign out`

`ProfileFragment` не открывает login bottom sheet. Кнопка `Continue with Google` запускает Google sign-in напрямую.

## Навигация

Навигация сделана через Android Navigation Component.

Nav graph:

```text
app/src/main/res/navigation/nav_graph.xml
```

Bottom navigation menu:

```text
app/src/main/res/menu/bottom_nav_menu.xml
```

В `nav_graph.xml` сейчас два destination:

- `homeFragment`
- `profileFragment`

`HomeFragment` является стартовым экраном.

## Авторизация

Авторизация сделана через:

- Firebase Authentication
- Google Sign-In через Credential Manager
- Google Identity `googleid`

Firebase config:

```text
app/google-services.json
```

Google Services plugin подключен в Gradle:

```kotlin
alias(libs.plugins.google.services)
```

### AuthRepository

Файл:

```text
app/src/main/java/com/si13/app/AuthRepository.kt
```

Отвечает за локальное состояние авторизации через `SharedPreferences`.

Хранит:

- authenticated flag
- display name
- email
- photo URL

Используется для того, чтобы приложение знало, показывать guest UI или authenticated UI после перезапуска.

### GoogleAuthClient

Файл:

```text
app/src/main/java/com/si13/app/GoogleAuthClient.kt
```

Отвечает за низкоуровневый Google sign-in flow:

- получает `default_web_client_id`
- запускает Credential Manager
- получает Google ID token
- передает token в Firebase Auth
- возвращает результат:
  - success
  - cancelled
  - failure

### GoogleSignInHandler

Файл:

```text
app/src/main/java/com/si13/app/GoogleSignInHandler.kt
```

Общий helper для UI-слоя.

Используется в:

- `LoginBottomSheet`
- `ProfileFragment`

Отвечает за:

- отключение кнопок на время sign-in
- запуск `GoogleAuthClient`
- сохранение пользователя в `AuthRepository`
- показ ошибки в `TextView`
- вызов success callback

### LoginBottomSheet

Файл:

```text
app/src/main/java/com/si13/app/LoginBottomSheet.kt
```

Показывается только как стартовый prompt для незалогиненного пользователя.

Содержит:

- `Continue with Google`
- `Continue as guest`
- поле для ошибки авторизации

Если пользователь выбирает `Continue as guest`, bottom sheet закрывается.

## UI и ресурсы

Основные layout-файлы:

```text
app/src/main/res/layout/activity_main.xml
app/src/main/res/layout/bottom_sheet_login.xml
app/src/main/res/layout/fragment_home.xml
app/src/main/res/layout/fragment_profile.xml
```

Google button оформлена кастомно:

```text
app/src/main/res/drawable/bg_google_sign_in_button.xml
app/src/main/res/drawable/ic_google_g.xml
```

Иконки bottom navigation:

```text
app/src/main/res/drawable/ic_home.xml
app/src/main/res/drawable/ic_profile.xml
```

Строки:

```text
app/src/main/res/values/strings.xml
```

Темы:

```text
app/src/main/res/values/themes.xml
app/src/main/res/values-night/themes.xml
```

## Espresso preparation

В проекте подключены зависимости для instrumented UI-тестов:

- `androidx.test.ext:junit`
- `androidx.test.espresso:espresso-core`
- `androidx.test:core`

Тесты находятся в:

```text
app/src/androidTest/java/com/si13/app
```

Простой smoke-test запускает `MainActivity` через `ActivityScenario`.

Для будущих Espresso-тестов у ключевых UI-элементов есть стабильные ids, например:

- `main`
- `nav_host_fragment`
- `bottom_navigation`
- `home_title_text`
- `profile_status_text`
- `profile_message_text`
- `profile_email_text`
- `profile_picture_image`
- `profile_sign_in_button`
- `profile_sign_in_text`
- `profile_sign_out_button`
- `sign_in_with_google_button`
- `sign_in_with_google_text`
- `continue_as_guest_button`
- `login_error_text`

## Сборка

Debug build:

```bash
./gradlew assembleDebug
```

Сборка APK с instrumented tests:

```bash
./gradlew assembleDebugAndroidTest
```

Unit tests:

```bash
./gradlew testDebugUnitTest
```

Instrumented tests на подключенном устройстве или эмуляторе:

```bash
./gradlew connectedDebugAndroidTest
```

## Важные зависимости

Основные библиотеки:

- AndroidX AppCompat
- AndroidX Core KTX
- Material Components
- ConstraintLayout
- Navigation Component
- Firebase Auth
- Firebase BoM
- Credential Manager
- Google Identity `googleid`
- Coil
- Espresso

## Текущее поведение авторизации

При первом запуске:

1. `MainActivity` проверяет `AuthRepository.isAuthenticated()`.
2. Если пользователь не authenticated, открывается `LoginBottomSheet`.
3. Пользователь может выбрать:
   - `Continue with Google`
   - `Continue as guest`

При успешном Google sign-in:

1. `GoogleAuthClient` получает Firebase user.
2. `GoogleSignInHandler` сохраняет user в `AuthRepository`.
3. UI переключается в authenticated state.

При sign out:

1. Вызывается `FirebaseAuth.getInstance().signOut()`.
2. Локальные данные очищаются через `AuthRepository.clear()`.
3. Profile переключается в guest state.
