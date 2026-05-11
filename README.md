# DFW Flight Companion

A native Android application built as a capstone project that serves as a flight companion tool for Dallas/Fort Worth International Airport (DFW). The app provides travelers with real-time flight information, airport navigation assistance, and related travel utilities, backed by Firebase Cloud Functions and a systems simulation module.

## Tech Stack

- **Android** (Kotlin + Jetpack Compose)
- **Firebase** (Cloud Functions — Node.js/JavaScript)
- **Gradle** (Kotlin DSL) for build management

## Prerequisites

- [Android Studio](https://developer.android.com/studio) (latest stable)
- Android SDK (API level appropriate for the project — check `app/build.gradle.kts`)
- [Node.js](https://nodejs.org/) (v18+) and npm
- [Firebase CLI](https://firebase.google.com/docs/cli)
  ```bash
  npm install -g firebase-tools
  ```
- A Firebase project with Firestore and Cloud Functions enabled
- A `google-services.json` file placed at `app/google-services.json`

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/GamingSlayerNS/DFW-Flight-Companion.git
cd DFW-Flight-Companion
```

### 2. Configure Firebase

1. Go to the [Firebase Console](https://console.firebase.google.com/) and create or select a project.
2. Register an Android app and download `google-services.json`.
3. Place the file at `app/google-services.json`.
4. Log in via the Firebase CLI:
   ```bash
   firebase login
   firebase use --add   # select your project
   ```

### 3. Install Cloud Functions dependencies

```bash
cd functions
npm install
cd ..
```

### 4. Open and build the Android app

1. Open the project root in Android Studio.
2. Let Gradle sync complete.
3. Connect an Android device or start an emulator.
4. Run the app via **Run ▶ Run 'app'**.

## Running Cloud Functions locally (optional)

```bash
firebase emulators:start --only functions
```

## Deploying Cloud Functions

```bash
firebase deploy --only functions
```

## Project Structure

```
DFW-Flight-Companion/
├── app/                    # Android application source (Kotlin/Compose)
├── functions/              # Firebase Cloud Functions (Node.js)
├── systems-simulation/     # Simulation/modeling module
├── gradle/                 # Gradle wrapper files
├── build.gradle.kts        # Top-level Gradle build config
├── firebase.json           # Firebase project configuration
└── .firebaserc             # Firebase project alias
```
