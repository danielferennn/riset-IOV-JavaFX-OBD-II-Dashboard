# OBD2 Telemetry Dashboard

A JavaFX desktop application that displays live vehicle telemetry from an **OBD-II ELM327** adapter over a serial port exposed by the operating system — for example a USB serial port, or Bluetooth SPP when the OS exposes it as a serial port. The app does not implement Bluetooth transport itself; it only talks to the serial port the OS presents.

The project also includes a **simulation mode**, so the dashboard and the data-reading flow can be exercised without an adapter or a vehicle.

## About

OBD2 Telemetry Dashboard is a small research/learning project built around three concerns:

- **Serial communication** with an ELM327-compatible adapter (`jSerialComm`), including basic AT initialization and periodic polling of standard OBD-II PIDs.
- **Parsing** of raw ELM327 responses (Mode 01 PIDs, `ATRV` voltage, and Mode 03 stored trouble codes) with strict validation.
- **A JavaFX dashboard** that shows telemetry cards, progress bars, charts, a communication log, and a Diagnostic Trouble Code (DTC) list, with a built-in language selector.

The application is intended for monitoring and study. Its readings are **not** a replacement for professional diagnostic equipment, and it must not be operated while driving (see [Safety](#safety)).

## Features

- Serial port selection and baud rate selection.
- Connection to an ELM327 adapter using `jSerialComm`.
- Adapter initialization with basic AT commands (`ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`).
- Periodic OBD-II telemetry polling with per-PID scheduling.
- Simulation mode for testing without hardware.
- JavaFX dashboard with telemetry cards, progress bars, live charts, a communication log, and a DTC list.
- In-app language selector with **Indonesian** and **English**; the choice is persisted between launches.
- Unit tests covering OBD-II response parsing (including Mode 03), DTC presentation, localization, and the `SimulationService` lifecycle.

## Tech Stack

| Area | Technology |
| --- | --- |
| Language / runtime | Java 21 (`maven.compiler.release` = 21) |
| Build | Maven |
| UI | JavaFX 21.0.6 (`javafx-controls`, `javafx-fxml`) |
| Serial I/O | jSerialComm 2.11.2 |
| Testing | JUnit 5 (Jupiter) 5.11.4 |
| Packaging/run plugin | `javafx-maven-plugin` 0.0.8 |

Maven coordinates: `com.example:obd2-telemetry-dashboard:1.0.0`.

## Requirements

- **JDK 21** — the project compiles with `release 21`; older JDKs will fail to build.
- **Maven** — any recent 3.x. The project does not pin a Maven minimum version.
- Internet access to Maven Central on the first build, to resolve JavaFX, jSerialComm, and JUnit.

Verify your toolchain:

```bash
java -version
mvn -version
```

For real (non-simulated) operation you additionally need:

- An OBD-II **ELM327** adapter.
- A **serial port exposed by the OS** (a USB serial port, or Bluetooth SPP when the OS presents it as a serial port).
- A **vehicle that supports OBD-II**.

## Getting the Source

This source repository does not contain a prebuilt installer or packaged application, so the program is obtained by cloning the source and building it locally.

```bash
git clone https://github.com/danielferennn/riset-IOV-JavaFX-OBD-II-Dashboard.git
cd riset-IOV-JavaFX-OBD-II-Dashboard
```

## Running

From the project root, run with the JavaFX Maven plugin (recommended for development; the configured plugin launches the app using the JavaFX dependencies declared in `pom.xml`):

```bash
mvn clean javafx:run
```

This is the supported way to start the app in this repository.

### Build a JAR

```bash
mvn clean package
```

Build output is written to `target/`. Note that the produced JAR is **not a standalone bundle**: the JavaFX runtime still has to be available in the environment for it to run, so `mvn clean javafx:run` remains the recommended launch method.

## Using Simulation

Simulation mode exercises the dashboard without any hardware:

1. Start the application.
2. Toggle **Simulation** on.
3. The dashboard updates telemetry automatically (the simulated frame is generated every 500 ms).
4. Toggle **Simulation** off, or press **Disconnect**, to stop it.

The simulation service produces plausible values for every displayed PID, so all cards and both charts stay active.

## Using an ELM327 Adapter

1. Connect the ELM327 adapter to the vehicle.
2. Turn the vehicle ignition on.
3. Connect the adapter over USB, or pair Bluetooth SPP and confirm the OS exposes it as a serial port.
4. Press **Refresh Port**.
5. Select the adapter port (for example `COM3` on Windows).
6. Select the baud rate. The default is `38400`; available values are `9600`, `38400`, `57600`, and `115200`.
7. Press **Connect**.

Serial settings used by the app: 8 data bits, 1 stop bit, no parity. Commands use a 2-second timeout, the adapter reset uses 4 seconds, and a Mode 03 DTC read uses 4 seconds. Outgoing commands and incoming responses are shown in the communication log.

### Language

1. On first launch, the interface language is **Indonesian**.
2. Use the in-app language selector to switch between **Indonesian** (`Bahasa Indonesia`) and **English**.
3. The selection is saved as a user preference and reused on the next launch. If the stored value is missing or unrecognized, the app falls back to Indonesian.

Buttons are referred to in this README by their English labels; when Indonesian is active, they appear with the equivalent Indonesian text.

## Supported OBD Commands

| Command | Data | Unit |
| --- | --- | --- |
| `010C` | Engine RPM | rpm |
| `010D` | Vehicle speed | km/h |
| `0105` | Coolant temperature | °C |
| `0104` | Engine load | % |
| `0111` | Throttle position | % |
| `010F` | Intake air temperature | °C |
| `012F` | Fuel level | % |
| `010B` | Intake manifold pressure | kPa |
| `ATRV` | Adapter / battery voltage | V |
| `03` | Stored Diagnostic Trouble Codes | — |

Not every vehicle supports every PID. When the vehicle returns `NO DATA` (or another ELM327 error such as `STOPPED`, `UNABLE TO CONNECT`, `ERROR`, or `?`), the app logs the affected PID as unavailable and keeps polling the others; it does not stop the whole session.

## Tests and Build

Run the test suite:

```bash
mvn test
```

The test suite contains **43 tests** across four classes:

| Test class | Tests | Focus |
| --- | --- | --- |
| `parser/ObdResponseParserTest` | 21 | PID parsing, voltage, Mode 03 validation |
| `util/LocalizationTest` | 12 | Language default/persistence, bundle parity |
| `controller/DtcPresentationTest` | 8 | DTC list rendering states |
| `service/SimulationServiceTest` | 2 | Simulation stop barrier / restart |

## Project Layout

```text
.
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/obd2dashboard
    │   │   ├── Main.java
    │   │   ├── config/AppConfig.java
    │   │   ├── controller/DashboardController.java
    │   │   ├── controller/DtcPresentation.java
    │   │   ├── exception/ObdConnectionException.java
    │   │   ├── exception/ObdParseException.java
    │   │   ├── model/ConnectionStatus.java
    │   │   ├── model/ObdPid.java
    │   │   ├── parser/ObdResponseParser.java
    │   │   ├── service/Elm327Service.java
    │   │   ├── service/ObdEventListener.java
    │   │   ├── service/ObdService.java
    │   │   ├── service/SerialPortService.java
    │   │   ├── service/SimulationService.java
    │   │   ├── util/HexUtils.java
    │   │   └── util/Localization.java
    │   └── resources
    │       ├── com/example/obd2dashboard/i18n/messages_en.properties
    │       ├── com/example/obd2dashboard/i18n/messages_id.properties
    │       ├── css/dashboard.css
    │       └── view/dashboard.fxml
    └── test/java/com/example/obd2dashboard
        ├── controller/DtcPresentationTest.java
        ├── parser/ObdResponseParserTest.java
        ├── service/SimulationServiceTest.java
        └── util/LocalizationTest.java
```

## Behavior Notes

### DTC reading (Mode 03)

Mode 03 responses are validated structurally, not by merely searching for the `43` marker:

- An empty payload (for example a response that is only `43`) is treated as **invalid** and raises an `ObdParseException`.
- A payload whose bytes are all zero (for example `43 00` or `43 00 00`) is a **valid empty response** and is shown as "No DTCs".
- A payload with non-zero bytes must consist of complete byte pairs (an even number of bytes). A single non-zero byte or a leftover unpaired byte makes the response **invalid**.
- If any line carrying the `43` marker is truncated, the whole response is treated as a failure — no partial result is reported as success.

When a new DTC read is started, the previous result is cleared immediately. If the read fails, the error and its details are written to the communication log.

### Connection and simulation lifecycle

Each telemetry producer (ELM327 or simulation) uses a single worker thread. For an active producer/session, `stop()` waits for any in-flight telemetry callback to finish, then emits a `DISCONNECTED` status at the barrier and only after that completes its future. If the simulation is already idle or already closed, `stop()` emits nothing and returns an already-completed future (a no-op). This contract lets the controller wait for `stop()` before switching modes. Once a service is closed via `close()`, it cannot be started again.

## Troubleshooting

- **Wrong Java version / build fails with "release 21" or unsupported class.** The project requires JDK 21. Check `java -version` and `JAVA_HOME`, and point them at a JDK 21 installation.
- **No serial port listed.** Confirm the adapter is plugged in and its driver is installed, then press **Refresh Port**. On Bluetooth, ensure the SPP device is paired and exposed as a serial port.
- **Port cannot be opened / permission denied (Linux).** Grant the user access to serial devices (for example, add the user to the `dialout` group and re-login).
- **Port busy.** If the log shows a message like "Failed to open X. The port may be in use by another application.", close any other program using the port (serial monitor, vendor tool, another instance of this app) and reconnect.
- **Connection fails, or a PID shows `NO DATA`.** Verify the ignition is on, the baud rate is correct (default `38400`), and the vehicle supports OBD-II. `NO DATA` is logged per PID and does not stop the other readings.
- **Maven cannot resolve dependencies.** The first build needs access to Maven Central. Check your network/proxy settings and retry; a failed or offline first build will leave dependencies unresolved.

## Safety

This application is built for monitoring and learning. Its readings are not intended as a substitute for professional diagnostic tools.

Do not operate the application while driving.
