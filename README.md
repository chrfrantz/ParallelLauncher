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

**Important: ParallelLauncher requires JDK 1.8 to function, since it relies on the ability to control the SecurityManager, which is not longer supported in more recent versions of Java. See below for details.**

# Dependencies

* Third-party libraries
  * [Apache commons-io.jar](https://commons.apache.org/proper/commons-io/) (Version 2.4)
* Runtime environment
  * Developed and tested on Oracle 1.8 and OpenJDK 8 ([Recommended download](https://adoptium.net/temurin/releases/?version=8)). 

# Common problems

* Wrong JDK version: If the launched instances do not open a console window but rather close those immediately, enable ```redirectStdErrForLaunchedProcesses = true;``` in the main method of your ParallelLauncher instance (the file that extends ParallelLauncher to configure it for your specific application). This will write a log file upon instantiation that contains stderr output.
  * Check the log file in the project directory upon launching (order files by creation time to see a file that ends on "_Console" adjacent to the generated batch file used to control the execution). It will likely show the following content: 
```Exception in thread "main" java.lang.UnsupportedOperationException: Setting a Security Manager is not supported
	at java.base/java.lang.System.setSecurityManager(System.java:286)
	at org.christopherfrantz.parallelLauncher.util.wrappers.WrapperExecutable.addExitCodeCapturingShutdownHook(WrapperExecutable.java:189)
	at org.christopherfrantz.parallelLauncher.util.wrappers.WrapperExecutable.main(WrapperExecutable.java:100)
```
  * This indicates that the launched instances are run with a JDK version different than 1.8 (later versions disable the SecurityManager and produce the error above). The fix is to review the PATH variable to contain an explicit reference to JDK 1.8's \bin folder that contains java.exe and javac.exe (in Windows). Verify by running "java -version" in a new command line window to see the JDK version.
