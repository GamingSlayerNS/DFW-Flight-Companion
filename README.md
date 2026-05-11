# DFW Flight Companion

A native Android application built as a capstone project that serves as a flight companion tool for Dallas/Fort Worth International Airport (DFW). The app provides travelers with real-time flight information, airport navigation assistance, and related travel utilities, backed by Firebase Cloud Functions and a systems simulation module.

## Tech Stack

- **Android** (Kotlin + Jetpack Compose)
- **Firebase** (Cloud Functions — Node.js/JavaScript, Firestore, Auth, Hosting, Genkit)
- **Gradle** (Kotlin DSL) for build management

## Prerequisites

- [Android Studio](https://developer.android.com/studio) (latest stable)
- Android SDK (API level appropriate for the project — check `app/build.gradle.kts`)
- [Node.js](https://nodejs.org/) (v18+) and npm
- [Firebase CLI](https://firebase.google.com/docs/cli)
  ```bash
  npm install -g firebase-tools
  ```
- A Firebase project with Firestore, Auth, Cloud Functions, and Hosting enabled
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

### 4. Install Systems Simulation dependencies

```bash
cd systems-simulation
npm install
cd ..
```

### 5. Open and build the Android app

1. Open the project root in Android Studio.
2. Let Gradle sync complete.
3. Connect an Android device or start an emulator.
4. Run the app via **Run ▶ Run 'app'**.

## Running the App

The project has three independently runnable components. For full local development, run all three.

### Android App

Open the project in Android Studio and press **Run ▶ Run 'app'**, or build from the command line:

```bash
./gradlew assembleDebug
```

The resulting APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Cloud Functions (local emulator)

Run the Firebase emulator suite to serve functions locally without deploying:

```bash
firebase emulators:start --only functions,firestore,auth
```

The emulator UI is available at `http://localhost:4000` by default.

To deploy functions to production:

```bash
firebase deploy --only functions
```

### Systems Simulation

The `systems-simulation` module is a web-based simulation served via Firebase Hosting. To run it locally:

```bash
cd systems-simulation
npm start
```

Or serve it through the Firebase emulator alongside other services:

```bash
firebase emulators:start --only hosting
```

To deploy the simulation to Firebase Hosting:

```bash
firebase deploy --only hosting
```

## Running Everything Together

To spin up all emulated services (functions, Firestore, Auth, and Hosting) in one command:

```bash
firebase emulators:start
```

## Project Structure

```
DFW-Flight-Companion/
├── app/                    # Android application source (Kotlin/Compose)
├── functions/              # Firebase Cloud Functions (Node.js/Genkit)
├── systems-simulation/     # Web-based simulation module (Firebase Hosting)
├── gradle/                 # Gradle wrapper files
├── build.gradle.kts        # Top-level Gradle build config
├── firebase.json           # Firebase project configuration
└── .firebaserc             # Firebase project alias
```
