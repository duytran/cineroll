# Preparing a machine for Android development

> Written for someone building their first Android app, on Ubuntu 26.04 (x86_64). Do these steps once, before Task 1 of the [shoot-one-Roll plan](plans/2026-11-24-shoot-one-roll.md). Budget about 45 minutes, most of it downloads.

## The words you will meet

| Term | What it is |
|---|---|
| **JDK** | Java Development Kit. Gradle and the Android tools run on Java, even though Cineroll is written in Kotlin. This project needs **JDK 17**. |
| **Android Studio** | The official editor (IDE) for Android. It also installs and manages the other tools below. |
| **Android SDK** | A folder of tools and libraries: one "platform" per Android version (each Android version has an API number; API 37 is the newest one at the time of writing), build tools, and the emulator. |
| **Gradle** | The build tool. It downloads libraries, compiles the code and runs tests. The project calls it through `./gradlew` (the "wrapper"), which fetches the right Gradle version by itself. |
| **adb** | Android Debug Bridge. A command-line tool that talks to a phone or emulator: install apps, read logs, list devices. |
| **Emulator / AVD** | A virtual phone running on your computer. An AVD (Android Virtual Device) is one saved virtual phone. |
| **KVM** | Linux's hardware virtualization. The emulator is slow to unusable without it. |

## What you need

- **Disk:** about 15 GB free (Studio ~3 GB, SDK and emulator image ~8 GB, Gradle cache ~4 GB). Checked on this machine: 32 GB free.
- **RAM:** 16 GB or more is comfortable; 8 GB is the floor. Checked: 29 GB.
- **CPU virtualization (KVM):** needed for the emulator. Checked on this machine: `/dev/kvm` exists and your user can read and write it, so nothing to do.
- **A way to run the app:** an emulator (step 6) or a real Android phone (step 7). You need one of them for Tasks 2, 3, 5 and 6.

## Steps

### 1. Install JDK 17

```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

Check:

```bash
java -version
```

Expected: a line containing `openjdk version "17`. If it shows another version, see Troubleshooting.

### 2. Install Android Studio

Use Google's own download, not a repackaged one. (The `android-studio` snap on this machine is published by "Snapcrafters", a community group, not Google.)

1. Open <https://developer.android.com/studio> in a browser and download the Linux `.tar.gz` file.
2. Unpack it somewhere you own, for example your home folder:

   ```bash
   mkdir -p ~/tools
   tar -xzf ~/Downloads/android-studio-*-linux.tar.gz -C ~/tools
   ```

3. Start it:

   ```bash
   ~/tools/android-studio/bin/studio.sh
   ```

4. In the setup wizard choose **Standard**, accept the licences, and let it download the SDK. It installs into `~/Android/Sdk`. Leave that location as it is: the rest of this guide uses it.
5. Optional, so you can start it from the app menu: in Studio, open **Tools > Create Desktop Entry**.

### 3. Add the SDK pieces this project needs

In Android Studio: **More Actions (or the gear icon) > SDK Manager**.

- **SDK Platforms** tab: tick **Android API 37** (the setup wizard usually installs it already).
- **SDK Tools** tab: tick **Android SDK Build-Tools**, **Android SDK Command-line Tools (latest)**, **Android SDK Platform-Tools** and **Android Emulator**.
- Press **Apply** and wait for the downloads.

The project builds against API 37 and runs on Android 10 (API 29) and newer.

### 4. Tell your shell where the SDK is

Add these lines to the end of `~/.zshrc`:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin"
```

Then open a new terminal and check:

```bash
adb version
```

Expected: `Android Debug Bridge version ...`.

### 5. Install Gradle once, to create the project's wrapper

The repo has no `gradlew` yet; Task 1 creates it. That one-time command needs a real Gradle on your machine. The `gradle` package in Ubuntu is too old, so use SDKMAN, a small version manager:

```bash
curl -s "https://get.sdkman.io" | bash
```

Open a new terminal, then:

```bash
sdk install gradle 9.6.0
gradle --version
```

Expected: `Gradle 9.6.0`. After Task 1 commits the wrapper, you will use `./gradlew` and can ignore this Gradle.

### 6. Create a virtual phone (emulator)

In Android Studio: **More Actions > Virtual Device Manager > Create Device**.

1. Pick a Pixel phone profile and press **Next**.
2. Pick the system image **API 37**, type **Google APIs**, architecture **x86_64** (download it if asked) and press **Next**.
3. Press **Show Advanced Settings** and set **Back camera** to **VirtualScene**. Cineroll needs a back camera, and this gives the emulator a fake 3D room to point at. (Move the view with Alt and the mouse.)
4. **Finish**, then press the play button next to the device.

Check from the terminal while it runs:

```bash
adb devices
```

Expected: one line ending in `device`, for example `emulator-5554   device`.

### 7. Or use a real Android phone

A real phone shows how the Film actually looks, so it is worth doing once the app builds.

1. On the phone: **Settings > About phone**, tap **Build number** seven times.
2. **Settings > System > Developer options**, turn on **USB debugging**.
3. Plug the phone into the computer with a data cable, and accept the "Allow USB debugging?" prompt on the phone.
4. Check:

   ```bash
   adb devices
   ```

   Expected: one line ending in `device`. If it says `unauthorized`, unplug, replug and accept the prompt again.

### 8. Point the project at the SDK

Open the `cineroll` folder in Android Studio (**Open**, not **New Project**) once the project has files from Task 1. Studio writes `local.properties` for you. To do it by hand, from the repo root:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

`local.properties` is git-ignored, so it never gets committed.

## Check everything

```bash
java -version      # openjdk 17
adb version        # Android Debug Bridge
gradle --version   # Gradle 9.6.0
adb devices        # your emulator or phone listed as "device"
```

When Task 1 is done, this should also succeed from the repo root:

```bash
./gradlew :app:testDebugUnitTest
```

## Troubleshooting

- **`java -version` shows another version.** Several JDKs are installed. Pick 17 with `sudo update-alternatives --config java`, or set `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` in `~/.zshrc`.
- **Emulator says `/dev/kvm` permission denied.** Run `sudo usermod -aG kvm $USER`, then log out and back in.
- **Emulator is very slow or the window stays black.** Close other heavy apps; in the AVD's advanced settings set **Graphics** to **Hardware**; cold-boot it once from Device Manager (the three-dot menu).
- **`SDK location not found` from Gradle.** `local.properties` is missing or points to the wrong folder. See step 8.
- **`adb: command not found`.** Step 4 was not applied; open a new terminal after editing `~/.zshrc`.
- **A Gradle download is stuck or fails halfway.** Run the command again; Gradle resumes. If the disk is full, check `du -sh ~/.gradle ~/Android`.
- **Phone shows as `unauthorized` or does not show.** Try another USB cable (some only charge), unplug and replug, accept the prompt, and run `adb kill-server` then `adb devices`.

## Android words you will meet in this project

| Word | Where it shows up in Cineroll |
|---|---|
| `AndroidManifest.xml` | The app's identity card: its name, the camera permission, which screen starts. Task 1 and Task 6. |
| Activity | One screen entry point. `MainActivity` hosts the whole UI. Task 6. |
| Jetpack Compose | How the screens are written: Kotlin functions that describe the UI. Task 6. |
| CameraX | Google's library for the camera: live preview and taking a full-resolution picture. Tasks 5 and 6. |
| `MediaStore` | How an app adds a picture to the phone's gallery. Task 5. |
| Instrumented test | A test that runs on the emulator or phone (`androidTest`). The other kind, a unit test, runs on your computer only. Tasks 2, 3, 5, 6. |
