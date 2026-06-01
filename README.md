# Wallpaper Changer

A modern, high-performance Android application built with Jetpack Compose that automatically rotates the device wallpaper (Home screen, Lock screen, or both) every time the screen wakes up. 

It supports local gallery albums as well as selecting photos from **Google Photos / Cloud Picker** using persistable URI access (without needing any broad storage permissions).

## Key Features

- **Instant Responsiveness**: Decoupled from screen-on latency using a **background preloading cache**. The next wallpaper is preloaded, cropped, scaled, and blurred in the background (`Dispatchers.IO`) before the screen wakes, applying it instantly (within 1ms) upon display activation.
- **Always-On Display (AOD) Support**: Integrates `DisplayManager.DisplayListener` to detect wake events when AOD is active, overcoming OS limitations where standard `ACTION_SCREEN_ON` broadcasts are not sent.
- **Google Photos Support**: Direct support for cloud-only and synchronized albums using the Android Photo Picker with persistable read permissions.
- **Smart Scaling Modes**:
  - **Fill Screen (Crop)**: Crops the image to match the device aspect ratio, centering horizontally/vertically.
  - **Fit Screen (Blurred Background)**: Fits the full image on the screen, adding a beautiful, dimmed, and blurred background to fill the remaining bars.
- **Survives Reboots**: Automatically registers a `BOOT_COMPLETED` receiver to resume background operation after device restarts.
- **Premium Dark UI**: Built with Material 3, custom gradients, dynamic status indicator pulses, and interactive animations.

---

## How to Compile & Install

### Prerequisites
- Java Development Kit (JDK 17)
- Android SDK

### 1. Configure the Android SDK Location
Create a file named `local.properties` in the root directory of the project and specify the path to your Android SDK:

```properties
sdk.dir=/path/to/your/Android/Sdk
```
*(On Linux, this is typically `/home/username/Android/Sdk`)*

### 2. Compile the Project
To compile the application and generate a debug APK, run:

```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:  
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Deploy and Install
Make sure you have an Android device connected with USB debugging enabled. Verify the connection by running:

```bash
adb devices
```

Install the APK directly onto your device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
*(Alternatively, you can run `./gradlew installDebug` to build and install in a single step)*

### 4. Enable Background Execution (Optional but Recommended)
On some OEM ROMs (Xiaomi, Samsung, Pixel), the system may kill background services aggressively to save battery. For reliable wallpaper changes, go to your phone's settings:
1. Locate **Wallpaper Changer** under Apps.
2. Select **Battery / Battery Saver**.
3. Set battery optimization to **Unrestricted** (or "No restrictions").
