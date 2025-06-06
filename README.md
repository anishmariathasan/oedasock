# Foot Cuff Control App

This Android application is designed to control a foot cuff used to prevent edema. The app communicates with a **Seeed XIAO ESP32-C6 microcontroller** via BLE to manage the inflation and deflation of the cuff based on strain readings. It features an emergency stop functionality, manual control buttons, a progress bar to visualize the cuff's inflation/deflation state, and BLE connectivity for controlling the microcontroller. The app also includes login functionality where users can input their biometrics (weight, height, age, and gender) and track their weight and pressure over time. Users can save their pressure data via a button, and graphs of the weight and pressure over time can be viewed. There is also logout functionality. It was created for an Imperial College London module project. The NordicSemiconductor Kotlin BLE Library has been used as a wrapper around the native API as BLE in Android is a pain.

## Features

- **Emergency Stop**: Instantly stops the inflation/deflation process and deflates the cuff to baseline (no inflation) when needed.
- **Manual Inflate/Deflate Buttons**: Allows the user to override the automatic inflation/deflation process with manual control for inflating or deflating the cuff as necessary.
- **Inflation/Deflation Control**: The app allows the user to inflate or deflate the foot cuff based on the strain reading to maintain optimal comfort and prevent swelling.
- **Progress Bar (1-10)**: The app displays a progress bar from 1 to 10 to show the current inflation/deflation state of the cuff. The color of the bar changes as the cuff inflates or deflates, providing a visual representation of the status.
- **BLE Connectivity**: The app connects to a **Seeed XIAO ESP32-C6 microcontroller** over BLE to control the inflation and deflation of the cuff in real-time, adjusting the pressure based on strain readings.
- **Cuff Pressure Monitoring**: The app monitors the foot cuff’s pressure, ensuring that it inflates when the foot starts swelling and deflates when pressure decreases.
- **Login Functionality**: Users can log in, input biometrics (weight, height, age, gender), and save this information.
- **Biometric Tracking**: Users can view graphs of their weight and pressure over time.
- **Logout Functionality**: Users can log out of the app at any time.

## How It Works

1. **Login and Biometrics**:
    - Users can log in to the app, enter their biometrics (weight, height, age, gender), and save this information.
    - Once logged in, users can track their weight and pressure over time.
    - Weight and pressure data are saved, and users can view graphs representing these over time.

2. **BLE Connection**:
    - The app will show the status of the BLE connection.
    - Once connected, it communicates with the **Seeed XIAO ESP32-C6 microcontroller** to send commands for inflating or deflating the cuff.

3. **Inflation/Deflation Process**:
    - The app displays a progress bar ranging from 1 (fully deflated) to 10 (fully inflated).
    - As the cuff inflates or deflates, the progress bar's color will change, providing a visual indicator.
    - The cuff pressure is automatically adjusted based on the strain reading, inflating or deflating as necessary to maintain optimal pressure.

4. **Emergency Stop**:
    - If immediate action is needed, press the **Emergency Stop** button. This will halt the inflation/deflation process and deflate the cuff to baseline (no inflation).

5. **Manual Inflate/Deflate Buttons**:
    - The app includes manual control buttons to override the automatic inflation/deflation process, allowing the user to manually inflate or deflate the cuff as necessary.

6. **Weight and Pressure Graphs**:
    - Users can view their weight and pressure over time through easy-to-read graphs, helping track their health progress.

7. **Logout Functionality**:
    - Users can log out at any time, ensuring that their data is securely stored and not accessible without authentication.

## Installation

To install and run the app:

1. **Clone the repository** or download the project as a ZIP file.
2. Open the project in **Android Studio**.
3. Build the project and run the app on a device with BLE capabilities.
4. Pair your phone with the **Seeed XIAO ESP32-C6 microcontroller** over BLE to begin controlling the foot cuff.

## Requirements

- **Android 7.0 (Nougat)** or higher (`minSdk = 24`).
- **Android 13 (API level 33)** or higher recommended (`targetSdk = 35`).

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE.txt](LICENSE.txt) file for details.

## Acknowledgments

- Thank you to all the contributors and open-source libraries used in this project, especially NordicSemiconductor for their Kotlin-BLE library.

## Primary Contributor

The primary contributor to the mobile app development is **Anish Mariathasan**.
