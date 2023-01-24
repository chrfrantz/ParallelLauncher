# ParallelLauncher
Automates compilation and launching of Java executables with instance-specific parameterization alongside consideration of JDK choice, CPU core allocation, as well as multi-OS support. Further includes MetaLauncher to coordinate execution of multiple ParallelLauncher instances. Includes various helper utilities to generate parameter sets. Note that this framework has been written based on the JVM 8 security architecture and relies on features that are no longer accessible in more recent Java versions.

Developer: Christopher Frantz

# Features

* Automates scheduling and process monitoring of Java executables by ParallelLauncher
* Accounts for processor core allocations to ensure responsiveness of OS
* Provides additional MetaLauncher that coordinates multiple ParallelLauncher instances
* Facilitates instance-specific parameterization (via parameter file generation and extraction tools)
* Provides command-line interaction (e.g., launching of additional executables, status inquiry).
* Supports both Windows and Linux

# Usage

Please consult 'example' subdirectory to see illustrative use of launchers with various features.

# Dependencies

* Third-party libraries
  * [Apache commons-io.jar](https://commons.apache.org/proper/commons-io/) (Version 2.4)
* Runtime environment
  * Developed and tested on Oracle 1.8 and OpenJDK 8 ([Recommended download](https://adoptium.net/temurin/releases/?version=8)).  
