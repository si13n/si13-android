# SI13 Android

SI13 Android is a Kotlin Android application with bottom navigation, Google/Firebase authentication, guest mode, authenticated profile state, and basic Espresso test setup.

## Current Features

- Starts from a single `MainActivity`.
- Uses bottom navigation for the main app sections.
- Provides two main screens:
  - `Home`
  - `Profile`
- Shows a login bottom sheet on startup when the user is not authenticated.
- Allows the user to continue as a guest.
- Supports Google sign-in through Firebase Authentication.
- Persists authentication state locally.
- Shows a guest profile for unauthenticated users.
- Shows Google account data for authenticated users:
  - display name
  - email
  - profile picture when available
- Allows the user to sign out.

## Module Structure

The project has one Android module:

```text
app
```

Application id and namespace:

```text
com.si13.app
```

Main module configuration:

```text
app/build.gradle.kts
```

Dependency versions are managed through the Gradle version catalog:

```text
gradle/libs.versions.toml
```

## Main Screens

### MainActivity

File:

```text
app/src/main/java/com/si13/app/MainActivity.kt
```

Responsibilities:

- loads `activity_main.xml`
- hosts the app navigation graph through `NavHostFragment`
- connects `BottomNavigationView` to `NavController`
- checks authentication state on first creation
- shows `LoginBottomSheet` for unauthenticated users

### HomeFragment

File:

```text
app/src/main/java/com/si13/app/HomeFragment.kt
```

The default screen in the navigation graph.

Layout:

```text
app/src/main/res/layout/fragment_home.xml
```

### ProfileFragment

File:

```text
app/src/main/java/com/si13/app/ProfileFragment.kt
```

Displays either guest state or authenticated user state.

Guest state:

- shows `Guest`
- explains that the app is being used without an account
- shows `Continue with Google`
- hides `Sign out`

Authenticated state:

- shows the user's display name
- shows the user's email
- shows the profile picture when available
- shows `Sign out`
- hides `Continue with Google`

`ProfileFragment` starts Google sign-in directly. It does not open the login bottom sheet.

## Navigation

Navigation is implemented with Android Navigation Component.

Navigation graph:

```text
app/src/main/res/navigation/nav_graph.xml
```

Bottom navigation menu:

```text
app/src/main/res/menu/bottom_nav_menu.xml
```

Current destinations:

- `homeFragment`
- `profileFragment`

`homeFragment` is the start destination.

## Authentication

Authentication uses:

- Firebase Authentication
- Android Credential Manager
- Google Identity `googleid`

Firebase configuration:

```text
app/google-services.json
```

The Google Services Gradle plugin is applied in the app module:

```kotlin
alias(libs.plugins.google.services)
```

### AuthRepository

File:

```text
app/src/main/java/com/si13/app/AuthRepository.kt
```

Stores local authentication state in `SharedPreferences`.

Stored values:

- authenticated flag
- display name
- email
- profile photo URL

This repository is used to decide whether the UI should render guest mode or authenticated mode after app restart.

### GoogleAuthClient

File:

```text
app/src/main/java/com/si13/app/GoogleAuthClient.kt
```

Handles the low-level Google sign-in flow:

- reads `default_web_client_id`
- launches Credential Manager
- receives a Google ID token
- exchanges the token for Firebase credentials
- returns a typed result:
  - success
  - cancelled
  - failure

### GoogleSignInHandler

File:

```text
app/src/main/java/com/si13/app/GoogleSignInHandler.kt
```

Shared UI-layer helper used by:

- `LoginBottomSheet`
- `ProfileFragment`

Responsibilities:

- disables sign-in controls during authentication
- starts `GoogleAuthClient`
- persists the authenticated user through `AuthRepository`
- displays authentication errors in a `TextView`
- calls the success callback when sign-in completes

### LoginBottomSheet

File:

```text
app/src/main/java/com/si13/app/LoginBottomSheet.kt
```

Startup prompt shown only for unauthenticated users.

Contains:

- `Continue with Google`
- `Continue as guest`
- authentication error text

Selecting `Continue as guest` dismisses the bottom sheet and keeps the user in guest mode.

## UI Resources

Main layouts:

```text
app/src/main/res/layout/activity_main.xml
app/src/main/res/layout/bottom_sheet_login.xml
app/src/main/res/layout/fragment_home.xml
app/src/main/res/layout/fragment_profile.xml
```

Google sign-in button resources:

```text
app/src/main/res/drawable/bg_google_sign_in_button.xml
app/src/main/res/drawable/ic_google_g.xml
```

Bottom navigation icons:

```text
app/src/main/res/drawable/ic_home.xml
app/src/main/res/drawable/ic_profile.xml
```

String resources:

```text
app/src/main/res/values/strings.xml
```

Themes:

```text
app/src/main/res/values/themes.xml
app/src/main/res/values-night/themes.xml
```

## Espresso Test Setup

Instrumented UI test dependencies:

- `androidx.test.ext:junit`
- `androidx.test.espresso:espresso-core`
- `androidx.test:core`

Test source set:

```text
app/src/androidTest/java/com/si13/app
```

The current smoke test launches `MainActivity` through `ActivityScenario`.

Stable view IDs are available for future Espresso tests:

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

## Build Commands

Debug build:

```bash
./gradlew assembleDebug
```

Instrumented test APK:

```bash
./gradlew assembleDebugAndroidTest
```

Unit tests:

```bash
./gradlew testDebugUnitTest
```

Instrumented tests on a connected device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

## Main Dependencies

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

## Authentication Flow

Startup flow:

1. `MainActivity` checks `AuthRepository.isAuthenticated()`.
2. If the user is not authenticated, `LoginBottomSheet` is shown.
3. The user can choose:
   - `Continue with Google`
   - `Continue as guest`

Successful Google sign-in:

1. `GoogleAuthClient` receives a Firebase user.
2. `GoogleSignInHandler` stores the user in `AuthRepository`.
3. The UI switches to authenticated state.

Sign out:

1. `FirebaseAuth.getInstance().signOut()` signs out from Firebase.
2. `AuthRepository.clear()` removes local auth data.
3. `ProfileFragment` switches back to guest state.
