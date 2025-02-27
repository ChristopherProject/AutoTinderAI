# Tinder Auto Swiper With AI - Documentation

## Prerequisites
Before using the Tinder Auto Swiper With AI, ensure that you have the following installed on your system:

- **Java 8** or **Java 11**
- **Git** (for cloning the repository)
- **scrcpy** (for screen mirroring)

## Getting Started

### 1. Clone the Repository
To get started, open your terminal or command prompt and run the following command to clone the repository:

```sh
git clone https://github.com/ChristopherProject/AutoTinderAI.git
```

Navigate into the project directory:

```sh
cd AutoTinderAI
```

### 2. Set Up the Environment
Ensure you have **Java 8** or **Java 11** installed. You can check your Java version by running:

```sh
java -version
```

If Java is not installed, download and install it from [Oracle JDK](https://www.oracle.com/java/technologies/javase-downloads.html) or use an open-source alternative like [AdoptOpenJDK](https://adoptopenjdk.net/).

### 3. Install scrcpy and Enable USB Debugging
#### Installing scrcpy
`scrcpy` is required for screen mirroring. You can download `scrcpy-win64-v3.1` from its official repository:

[Download scrcpy](https://github.com/Genymobile/scrcpy/releases)

After downloading, extract the archive to a convenient location.

#### Enabling USB Debugging on Your Phone
To use `scrcpy`, USB debugging must be enabled on your Android device. Follow these steps:

1. Open **Settings** on your Android device.
2. Navigate to **About Phone**.
3. Tap **Build Number** 7 times to enable **Developer Options**.
4. Go back to **Settings** and open **Developer Options**.
5. Find and enable **USB Debugging**.

Once enabled, connect your phone to your PC via USB and run `scrcpy.exe` from the extracted folder.

### 4. Build and Run
To build the project, use:

```sh
./gradlew build   # For Unix/macOS
```

or

```sh
gradlew.bat build  # For Windows
```

Once built, run the application:

```sh
java -jar build/libs/AutoTinderAI.jar
```

### 5. How Data is Collected and Processed
The application collects data by capturing screen images from the Tinder interface using `scrcpy-win64-v3.1`. The process involves:

1. **Screen Capturing:** The application takes screenshots of the Tinder window every millisecond using `scrcpy`.
2. **OCR Analysis:** The captured frames are analyzed using an Optical Character Recognition (OCR) system to extract relevant text and information.
3. **AI Processing:** The extracted data is sent to an external AI system combined with OpenCV for further image processing and analysis.
4. **Model Integration:** Several AI models, including deep learning-based recognition systems, are used to interpret profile information, detect key elements, and make automated decisions on swiping.

### 6. Configuration
Check the configuration files inside the `config/` directory and update them as needed before running the application.

## Additional Information
For any issues or contributions, please refer to the [GitHub repository](https://github.com/ChristopherProject/AutoTinderAI) and open an issue or submit a pull request.

# NOTE: DONT WORK ON IPHONE (ANDROID ONLY)
