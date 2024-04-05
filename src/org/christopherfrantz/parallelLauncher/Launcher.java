package org.christopherfrantz.parallelLauncher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.christopherfrantz.parallelLauncher.util.ConfigFileEntryHandler;
import org.christopherfrantz.parallelLauncher.util.processhandlers.LinuxProcessReader;
import org.christopherfrantz.parallelLauncher.util.processhandlers.ProcessReader;
import org.christopherfrantz.parallelLauncher.util.processhandlers.WindowsProcessReader;

/**
 * Abstract launcher on which ParallelLauncher and MetaLauncher are based.
 *
 * @author Christopher Frantz
 *
 */
public abstract class Launcher {
	
	/**
	 * Prefix for uniform console output
	 */
	private static final String PREFIX = "ParallelLauncher: ";

	/**
	 * The launcher's start time.
	 */
	protected static final Long startTime = System.currentTimeMillis();
	
	/**
	 * Name of the launcher class itself (the one the user implements).
	 */
	protected static Class launcherClass = null;
	
	/**
	 * Default specialisation of launcher - for easier printing of duration.
	 */
	private static Class defaultClass = null;
	
	/**
	 * Prints the execution runtime and resolves the default class to identify the launcher.
	 */
	protected static void printDuration() {
		
		Duration duration = getElapsedRuntime();
		
		System.out.println(ParallelLauncher.getCurrentTimeString(true) + ": " + 
				defaultClass.getSimpleName() + ": Execution finished. Total Runtime: " + 
				duration.getExplicitRepresentation());
	}
	
	/**
	 * Structure holding duration-related information.
	 */
	public static class Duration {
		
		public final long elapsedDays;
		public final long elapsedHours;
		public final long elapsedMinutes;
		public final long elapsedSeconds;
		
		public Duration(long elapsedDays, long elapsedHours, long elapsedMinutes, long elapsedSeconds) {
			this.elapsedDays = elapsedDays;
			this.elapsedHours = elapsedHours;
			this.elapsedMinutes = elapsedMinutes;
			this.elapsedSeconds = elapsedSeconds;
		}
		
		/**
		 * Returns a simple representation of time indicating days, hours, minutes, and seconds.
		 * @return
		 */
		public String getSimpleRepresentation() {
			return this.elapsedDays + ":" + 
					(this.elapsedHours < 10 ? ("0" + this.elapsedHours) : elapsedHours) + ":" +
					(this.elapsedMinutes < 10 ? ("0" + this.elapsedMinutes) : elapsedMinutes) + ":" +
					(this.elapsedSeconds < 10 ? ("0" + this.elapsedSeconds) : elapsedSeconds);
		}
		
		/**
		 * Returns an explicit representation spelling out the individual duration components (xx days, xx hours, etc.).
		 * @return
		 */
		public String getExplicitRepresentation() {
			return String.format("%d days, %d hours, %d minutes, %d seconds", 
				    this.elapsedDays, this.elapsedHours, this.elapsedMinutes, this.elapsedSeconds);
		}
 		
		@Override
		public String toString() {
			return getExplicitRepresentation();
		}
		
	}
	
	/**
	 * Returns the runtime since the launcher has been started
	 * @return Duration object holding values for different time units.
	 */
	protected static Duration getElapsedRuntime() {
		long runtime = System.currentTimeMillis() - startTime;
		return generateDuration(runtime);
	}
	
	/**
	 * Prints the future date based on input runtime (calculated into the future) 
	 * and generates String of given format.
	 * @param runtime predicted remaining runtime to be added to current date
	 * @param dateFormat date format as per constants of DateFormat class (e.g. DateFormat.FULL).
	 * @return String representation of future date time.
	 */
	protected static String calculateFutureDate(long runtime, int dateFormat) {
		Date futureDate = new Date(System.currentTimeMillis() + runtime);
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG);
		return df.format(futureDate);
	}
	
	/**
	 * Calculates human-readable output of duration for given runtime.
	 * @param runtime
	 * @return
	 */
	protected static Duration generateDuration(long runtime) {
		//Inspired by: http://www.mkyong.com/java/java-time-elapsed-in-days-hours-minutes-seconds/
		
		long secondsInMilli = 1000;
		long minutesInMilli = secondsInMilli * 60;
		long hoursInMilli = minutesInMilli * 60;
		long daysInMilli = hoursInMilli * 24;
 
		long elapsedDays = runtime / daysInMilli;
		runtime = runtime % daysInMilli;
 
		long elapsedHours = runtime / hoursInMilli;
		runtime = runtime % hoursInMilli;
 
		long elapsedMinutes = runtime / minutesInMilli;
		runtime = runtime % minutesInMilli;
 
		long elapsedSeconds = runtime / secondsInMilli;
		
		return new Launcher.Duration(elapsedDays, elapsedHours, elapsedMinutes, elapsedSeconds);
	}
	
	/**
     * Instance of ProcessReader relevant to retrieve information about existing processes. 
     * Is initialised during call to main method.
     */
    protected static ProcessReader processReader = null;
    
    static {
		/** 
		 * Initialise ProcessReader depending on OS.
		 */
		if (processReader == null) {
			if (ProcessReader.runsOnLinux()) {
				processReader = new LinuxProcessReader();
			} else if (ProcessReader.runsOnWindows()) {
				processReader = new WindowsProcessReader();
			} else {
				throw new RuntimeException(PREFIX + "ParallelLauncher could not detect Operating System. Sorry :(");
			}
		}
    }
	
	/**
	 * Detect the launching class if not assigned manually. 
	 * Assigns default class in case detection fails.
	 * @param defaultClass
	 */
	protected static void detectLauncherClass(Class defaultClass) {
		
		//Save reference to default class for printing of duration.
		Launcher.defaultClass = defaultClass;
		
		/**
		 * Checking if launcher class is specified. If not use own class as launcher.
		 */
		if(launcherClass == null){
			boolean successfulInference = false;
			//try to infer launcherClass name
			try{
				StackTraceElement launcherClassElement = 
						(StackTraceElement)Thread.currentThread().getStackTrace()[Thread.currentThread().getStackTrace().length - 1];
				if(launcherClassElement.getMethodName().equals("main")){
					launcherClass = Class.forName(launcherClassElement.getClassName());
					successfulInference = true;
					if(debug){
						System.out.println(PREFIX + "Detected Launcher class name " + launcherClass.getSimpleName());
					}
				} else {
					System.err.println(PREFIX + "Detected Launcher class '" + launcherClass.getSimpleName() + "' does not contain main method.");
				}
			} catch (ClassNotFoundException e){
				System.err.println(PREFIX + "Launcher class could not be inferred.");
			}
			if(!successfulInference){
				System.err.println("Please assign your launcher subclass (extends ParallelLauncher or MetaLauncher) to the launcherClass field of Parallel- or MetaLauncher " 
					+ System.getProperty("line.separator")
					+ "(see ParallelLauncher javadoc for example)). Trying to use ParallelLauncher as launcher class itself ...");
				launcherClass = defaultClass;
			}
		}
	}
	
	/**
	 * Debug switch for extended output
	 */
	public static boolean debug = false;
	
	/**
	 * Helper method to toggle debug mode.
	 */
	public static void toggleDebugMode(){
		if(debug){
			debug = false;
			ProcessReader.debug = false;
			System.out.println(PREFIX + "Disabled debug mode.");
		} else {
			debug = true;
			ProcessReader.debug = true;
			System.out.println(PREFIX + "Enabled debug mode.");
		}
	}
	
	/**
	 * Awaits user input on keyboard and blocks for specified 
	 * time or until the user enters something. 
	 * See {@link #awaitUserInput(long, String, String...)}
	 * for a more refined variant.
	 * @param maxWaitingTime Maximum waiting time
	 */
	protected static void awaitUserInput(int maxWaitingTime){
		System.out.println("Press return in console to check immediately.");
		try{
			BufferedReader br = new BufferedReader(
			        new InputStreamReader(System.in));
			int totalSleeptime = 0;
			//read console and check if user entered anything
			while(!br.ready() && totalSleeptime < maxWaitingTime){
				totalSleeptime += 200;
				Thread.sleep(200);
			}
			if(br.ready()){
				br.readLine();
				//if so, start recheck immediately - overriding original sleep time
				if(debug){
					System.out.println("Keyboard console input detected, doing recheck immediately ...");
				}
			} else {
				if(debug){
					System.out.println("Waiting timed out, doing recheck ...");
				}
			}
			//br.close();
		} catch (IOException e) {
			//if console input makes problems
			e.printStackTrace();
		} catch (InterruptedException e) {
			//if thread sleep fails
			e.printStackTrace();
		}
	}
	
	/**
	 * Awaits user input on keyboard and blocks for specified 
	 * time or until the user enters something. Depending on input,
	 * the method returns different values:<BR>
	 * - timeout: 0<BR>
	 * - user pressed key (without any further input): 1<BR>
	 * - input matches element from specialInput entries: 2 onwards<BR>
	 * SpecialInput is an arbitrarily sized array of pairs that 
	 * indicate the input to be matched and the associated action description.
	 * Return values for the first pair is 2, the second pair 3 and so on. 
	 * Special input values containing null are ignored.
	 * 
	 * @param maxWaitingTime Maximum waiting time
	 * @param action String representation for action (for console output)
	 * @param specialInput Pairs of testing input and action description.
	 * @return Returns 1 if user has pressed key,
	 * 		2 or higher if user entered value specified in specialInput parameter prior to pressing enter, 
	 * 		else 0 (simply timed out). Return -1 upon error.
	 */
	protected static int awaitUserInput(long maxWaitingTime, String action, String... specialInput){
		StringBuilder builder = new StringBuilder("Press return in console to ").append(action);
		HashMap<String, Integer> specialInputs = new HashMap<>();
		if(specialInput != null && specialInput.length > 0){
			
			if(specialInput.length % 2 != 0) {
				System.err.println("Action options need to be specified as pairs of detected input and associated action description.");
				return -1;
			}
			//construct console description and determine return values
			for(int i = 0; i < specialInput.length / 2; i++) {
				if(i == specialInput.length / 2 - 1) {
					builder.append(" or ");
				} else {
					builder.append(", ");
				}
				//perform linebreak after each statement - better readability
				builder.append(System.getProperty("line.separator"));
				builder.append("enter '").append(specialInput[i * 2]).append("' to ").append(specialInput[i * 2 + 1]);
				//save special input and associate return value for later reference
				specialInputs.put(specialInput[i * 2], i + 2);
			}
		}
		System.out.println(builder.append(".").toString());
		int returnValue = 0;
		try{
			BufferedReader br = new BufferedReader(
				new InputStreamReader(System.in));
			long totalSleeptime = 0;
			//read console and check if user entered anything
			while(!br.ready() && totalSleeptime < maxWaitingTime){
				totalSleeptime += 200;
				Thread.sleep(200);
			}
			if(br.ready()){
				String userInput = br.readLine();
				//if so, start recheck immediately - overriding original sleep time
				if(debug){
					System.out.println("Keyboard console input detected, performing action " + action + " immediately ...");
				}
				//user entered specialInput
				for(Entry<String, Integer> entry: specialInputs.entrySet()) {
					if(entry.getKey() != null && userInput.startsWith(entry.getKey())) {
						returnValue = entry.getValue();
						break;
					}
				}
				if(returnValue == 0) {
					//user just pressed enter, no special characters
					returnValue = 1;
				}
			} else {
				if(debug){
					System.out.println("Waiting timed out, doing recheck ...");
				}
			}
			//br.close();
		} catch (IOException e) {
			//if console input makes problems
			e.printStackTrace();
		} catch (InterruptedException e) {
			//if thread sleep fails
			e.printStackTrace();
		}
		return returnValue;
	}
	
	
	//////////////////////////////// Configuration File Handling Stuff ///////////////////////////////////
	
	/**
  	 * Filename holding information about maximum number of parallel launchers and
  	 * processes to be started. Is used to check config at runtime if 
  	 * ParallelLauncher.configFileCheckForLauncherConfiguration is activated.
  	 */
  	public static final String PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE = "ParallelLauncher_RuntimeConfig";
  	
  	/**
  	 * Filename holding information with respect to MetaLauncher configuration.
  	 */
  	public static final String META_LAUNCHER_RUNTIME_CONFIG_FILE = "MetaLauncher_RuntimeConfig";
  	
  	/**
  	 * File holding the zero-based index identifying this machine with respect to the used parameter set.
  	 * Content defaults to 0.
  	 */
  	public static final String META_LAUNCHER_MACHINE_ID_FILE = "MetaLauncher_MachineID";
  	
  	/**
  	 * Key used in config file {@link #META_LAUNCHER_MACHINE_ID_FILE} to identify the local instance's
  	 * machine id.
  	 */
  	public static final String MACHINE_ID = "MACHINE_ID";
  	
  	/**
  	 * Key used in config file {@link #META_LAUNCHER_RUNTIME_CONFIG_FILE} to specify maximum number of 
  	 * concurrently active MetaLaunchers.
  	 */
  	protected static final String MAX_RUNNING_METALAUNCHERS_KEY = "MAXIMUM_NUMBER_OF_RUNNING_METALAUNCHERS";
  	
  	/**
  	 * Key used in config file {@link #META_LAUNCHER_RUNTIME_CONFIG_FILE} to specify maximum number of active 
  	 * or queued
	 * launchers scheduled by MetaLauncher.
  	 */
  	protected static final String MAX_QUEUED_LAUNCHERS_BY_METALAUNCHER_KEY = "MAXIMUM_NUMBER_OF_QUEUED_LAUNCHERS_BY_METALAUNCHER";
  	
  	/**
  	 * Key used in config file {@link #PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE} to specify maximum number of parallel launchers allowed.
  	 */
  	protected static final String MAX_PARALLEL_LAUNCHERS_KEY = "MAXIMUM_NUMBER_OF_PARALLEL_LAUNCHERS";
  	
  	/**
  	 * Key used in config file {@link #PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE} to specify maximum number of parallel processes allowed.
  	 */
  	protected static final String MAX_PARALLEL_PROCESSES_KEY = "MAXIMUM_NUMBER_OF_PARALLEL_PROCESSES";
  	
  	/**
  	 * Key used in config file {@link #PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE} to specify the duration of 
  	 * the longest-running launched process.
  	 */
  	protected static final String LAST_PROCESS_DURATION = "LAST_PROCESS_EXECUTION_DURATION";
  	
  	/**
  	 * Key used in config file {@value #PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE} to specify the start time of the 
  	 * first launched class of the currently running ParallelLauncher instance.
  	 */
  	protected static final String CURRENT_PROCESS_STARTTIME = "CURRENT_PROCESS_EXECUTION_START_TIME"; 
	
	/**
  	 * Symbol used as separator for key and values in config file.
  	 */
  	private static final String CONFIG_FILE_KEY_VALUE_SEPARATOR = "=";
  	
  	/**
  	 * OS-dependent folder separator
  	 */
  	protected static final String FOLDER_SEPARATOR = ProcessReader.runsOnWindows() ? "\\" : "/";
  	
  	/**
  	 * OS-dependent classpath element separator/delimiter as returned by System.getProperty("java.class.path")
  	 */
  	protected static final String CLASSPATH_SEPARATOR = ProcessReader.runsOnWindows() ? ";" : ":";
  	
  	/**
  	 * OS-dependent jar executable name
  	 */
  	protected static final String JAR_EXECUTABLE = ProcessReader.runsOnWindows() ? "jar.exe" : "jar";
	
  	/**
  	 * Map holding config file-configuration key associations.
  	 */
	protected static HashMap<String,ArrayList<ConfigFileEntryHandler>> configEntryHandler = new HashMap<>();
	
	/**
	 * Adds a handler for configuration file entries. Checks whether the handler has already been added.
	 * @param handler
	 */
	protected static void addConfigEntryHandler(final ConfigFileEntryHandler handler) {
		if(!configEntryHandler.containsKey(handler.getConfigFile())) {
			ArrayList<ConfigFileEntryHandler> handlersForKey = new ArrayList<ConfigFileEntryHandler>();
			handlersForKey.add(handler);
			configEntryHandler.put(handler.getConfigFile(), handlersForKey);
		} else {
			if(!configEntryHandler.get(handler.getConfigFile()).contains(handler)) {
				configEntryHandler.get(handler.getConfigFile()).add(handler);
			}
		}
	}
	
	/**
	 * Loads config file entry handlers.
	 */
	protected static void loadConfigEntryHandler() {
		
		if(ParallelLauncher.configFileCheckForLauncherConfiguration){
			
			//MetaLauncher configuration
			ConfigFileEntryHandler<Integer> maxMetaLauncherHandler = new ConfigFileEntryHandler<Integer>(META_LAUNCHER_RUNTIME_CONFIG_FILE, 
					MAX_RUNNING_METALAUNCHERS_KEY, MetaLauncher.maxNumberOfMetaLaunchersRunningAtOneTime) {
				
				@Override
				public boolean readEntry(String entryLine) {
					if(entryLine.startsWith(key)){
						try{
							Integer newMaxNumberOfRunningMetaLaunchers 
								= Integer.parseInt(entryLine.substring(entryLine.indexOf(CONFIG_FILE_KEY_VALUE_SEPARATOR) 
										+ CONFIG_FILE_KEY_VALUE_SEPARATOR.length()).trim());
							if(!newMaxNumberOfRunningMetaLaunchers.equals(MetaLauncher.maxNumberOfMetaLaunchersRunningAtOneTime)){
								MetaLauncher.maxNumberOfMetaLaunchersRunningAtOneTime = newMaxNumberOfRunningMetaLaunchers;
								System.out.println("MetaLauncher: Updated maximum number of permissible active MetaLaunchers to " + 
										MetaLauncher.maxNumberOfMetaLaunchersRunningAtOneTime);
							}
						} catch (NumberFormatException e){
							System.err.println("Failed to read maximum number of permissible MetaLaunchers from config file (Value: " 
									+ entryLine.substring(entryLine.indexOf(CONFIG_FILE_KEY_VALUE_SEPARATOR) 
									+ CONFIG_FILE_KEY_VALUE_SEPARATOR.length()).trim() + ").");
						}
						return true;
					}
					return false;
				}
	
				@Override
				public String constructEntry() {
					return new StringBuilder(key).append(CONFIG_FILE_KEY_VALUE_SEPARATOR).
							append(MetaLauncher.maxNumberOfMetaLaunchersRunningAtOneTime).toString();
				}
			};
			addConfigEntryHandler(maxMetaLauncherHandler);
			
			
			ConfigFileEntryHandler<Integer> maxQueuedLaunchersByMetaLauncherHandler = new ConfigFileEntryHandler<Integer>(META_LAUNCHER_RUNTIME_CONFIG_FILE, 
					MAX_QUEUED_LAUNCHERS_BY_METALAUNCHER_KEY, MetaLauncher.maxNumberOfQueuedOrRunningLaunchersAtOneTime) {
				
				@Override
				public boolean readEntry(String entryLine) {
					if(entryLine.startsWith(key)){
						try{
							Integer newMaxNumberOfQueuedLaunchers 
								= Integer.parseInt(entryLine.substring(entryLine.indexOf(CONFIG_FILE_KEY_VALUE_SEPARATOR) 
										+ CONFIG_FILE_KEY_VALUE_SEPARATOR.length()).trim());
							if(!newMaxNumberOfQueuedLaunchers.equals(MetaLauncher.maxNumberOfQueuedOrRunningLaunchersAtOneTime)){
								MetaLauncher.maxNumberOfQueuedOrRunningLaunchersAtOneTime = newMaxNumberOfQueuedLaunchers;
								System.out.println("MetaLauncher: Updated maximum number of queued or active launchers to " +
										MetaLauncher.maxNumberOfQueuedOrRunningLaunchersAtOneTime);
							}
						} catch (NumberFormatException e){
							System.err.println("Failed to read maximum number of queued launchers (by MetaLauncher) from config file (Value: "
									+ entryLine.substring(entryLine.indexOf(CONFIG_FILE_KEY_VALUE_SEPARATOR) 
									+ CONFIG_FILE_KEY_VALUE_SEPARATOR.length()).trim() + ").");
						}
						return true;
					}
					return false;
				}
	
				@Override
				public String constructEntry() {
					return new StringBuilder(key).append(CONFIG_FILE_KEY_VALUE_SEPARATOR).
							append(MetaLauncher.maxNumberOfQueuedOrRunningLaunchersAtOneTime).toString();
				}
			};
			addConfigEntryHandler(maxQueuedLaunchersByMetaLauncherHandler);
			
			// add machine header - checks whether it had been added before (e.g. via initializeMachineId())
			addConfigEntryHandler(machineIdEntryHandler);
			
			//ParallelLauncher configuration
			ConfigFileEntryHandler<Integer> maxActiveLaunchersHandler = new ConfigFileEntryHandler<Integer>(PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE,
                    MAX_PARALLEL_LAUNCHERS_KEY, ParallelLauncher.maxNumberOfActiveParallelLaunchers) {
				
				@Override
				public boolean readEntry(String entryLine) {
					if(entryLine.startsWith(key)){
						try{
							Integer newMaxNumberOfActiveParallelLaunchers 
								= Integer.parseInt(entryLine.substring(entryLine.indexOf(CONFIG_FILE_KEY_VALUE_SEPARATOR) 
										+ CONFIG_FILE_KEY_VALUE_SEPARATOR.length()).trim());
							if(!newMaxNumberOfActiveParallelLaunchers.equals(ParallelLauncher.maxNumberOfActiveParallelLaunchers)){
								ParallelLauncher.maxNumberOfActiveParallelLaunchers = newMaxNumberOfActiveParallelLaunchers;
								System.out.println(PREFIX + "Updated maximum number of active launchers to " +
										ParallelLauncher.maxNumberOfActiveParallelLaunchers);
							}
						} catch (NumberFormatException e){
							System.err.println(PREFIX + "Failed to read maximum number of parallel launchers from config file (Value: "
									+ entryLine.substring(entryLine.indexOf(CONFIG_FILE_KEY_VALUE_SEPARATOR) 
									+ CONFIG_FILE_KEY_VALUE_SEPARATOR.length()).trim() + ").");
						}
						return true;
					}
					return false;
				}
	
				@Override
				public String constructEntry() {
					return new StringBuilder(key).append(CONFIG_FILE_KEY_VALUE_SEPARATOR).
							append(ParallelLauncher.maxNumberOfActiveParallelLaunchers).toString();
				}
			};
			addConfigEntryHandler(maxActiveLaunchersHandler);
			
			ConfigFileEntryHandler<Integer> maxActiveProcessesHandler = new ConfigFileEntryHandler<Integer>(PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE,
                    MAX_PARALLEL_PROCESSES_KEY, ParallelLauncher.maxNumberOfRunningLaunchedProcesses) {
				
				@Override
				public boolean readEntry(String entryLine) {
					if(entryLine.startsWith(key)){
						try{
							Integer newMaxNumberOfRunningLaunchedProcesses
								= Integer.parseInt(entryLine.substring(entryLine.indexOf(CONFIG_FILE_KEY_VALUE_SEPARATOR) 
										+ CONFIG_FILE_KEY_VALUE_SEPARATOR.length()).trim());
							if(!newMaxNumberOfRunningLaunchedProcesses.equals(ParallelLauncher.maxNumberOfRunningLaunchedProcesses)){
								ParallelLauncher.maxNumberOfRunningLaunchedProcesses = newMaxNumberOfRunningLaunchedProcesses;
								System.out.println(PREFIX + "Updated maximum number of running processes to " + 
										ParallelLauncher.maxNumberOfRunningLaunchedProcesses);
							}
						} catch (NumberFormatException e){
							System.err.println("Failed to read maximum number of parallel processes from config file (Value: " 
									+ entryLine.substring(entryLine.indexOf(CONFIG_FILE_KEY_VALUE_SEPARATOR) 
									+ CONFIG_FILE_KEY_VALUE_SEPARATOR.length()).trim() + ").");
						}
						return true;
					}
					return false;
				}
	
				@Override
				public String constructEntry() {
					return new StringBuilder(key).append(CONFIG_FILE_KEY_VALUE_SEPARATOR).
							append(ParallelLauncher.maxNumberOfRunningLaunchedProcesses).toString();
				}
				
			};
			addConfigEntryHandler(maxActiveProcessesHandler);
		}
		
		//Adaptive queue timing stuff - not quite working yet
		if(ParallelLauncher.adaptQueueCheckTimingDynamically){
			ConfigFileEntryHandler<Long> longestDurationHandler = new ConfigFileEntryHandler<Long>(PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE,
                    LAST_PROCESS_DURATION, ParallelLauncher.lastLaunchersLongestProcessDuration) {
				
				@Override
				public boolean readEntry(String entryLine) {
					if(entryLine.startsWith(key)){
						try{
							Long newLastLaunchersLongestProcessDuration
								= Long.parseLong(entryLine.substring(entryLine.indexOf(CONFIG_FILE_KEY_VALUE_SEPARATOR) 
										+ CONFIG_FILE_KEY_VALUE_SEPARATOR.length()).trim());
							if(!newLastLaunchersLongestProcessDuration.equals(ParallelLauncher.lastLaunchersLongestProcessDuration)){
								ParallelLauncher.lastLaunchersLongestProcessDuration = newLastLaunchersLongestProcessDuration;
								System.out.println(PREFIX + "Updated duration of last longest-running process to " + 
										ParallelLauncher.lastLaunchersLongestProcessDuration);
							}
						} catch (NumberFormatException e){
							System.err.println("Failed to read duration of last process from config file (Value: " 
									+ entryLine.substring(entryLine.indexOf(CONFIG_FILE_KEY_VALUE_SEPARATOR) 
									+ CONFIG_FILE_KEY_VALUE_SEPARATOR.length()).trim() + ").");
						}
						return true;
					}
					return false;
				}
	
				@Override
				public String constructEntry() {
					return new StringBuilder(key).append(CONFIG_FILE_KEY_VALUE_SEPARATOR).
							append(ParallelLauncher.maxNumberOfActiveParallelLaunchers).toString();
				}
			};
			addConfigEntryHandler(longestDurationHandler);
			
			ConfigFileEntryHandler<Long> currentProcessStartTimeHandler = new ConfigFileEntryHandler<Long>(PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE,
                    CURRENT_PROCESS_STARTTIME, ParallelLauncher.runningLauncherFirstProcessStartTime) {
				
				@Override
				public boolean readEntry(String entryLine) {
					if(entryLine.startsWith(key)){
							try{
								Long newRunningLauncherFirstProcessStartTime
									= Long.parseLong(entryLine.substring(entryLine.indexOf(CONFIG_FILE_KEY_VALUE_SEPARATOR) 
											+ CONFIG_FILE_KEY_VALUE_SEPARATOR.length()).trim());
								if(!newRunningLauncherFirstProcessStartTime.equals(ParallelLauncher.runningLauncherFirstProcessStartTime)){
									ParallelLauncher.runningLauncherFirstProcessStartTime = newRunningLauncherFirstProcessStartTime;
									System.out.println(PREFIX + "Updated start time of first-started process to " + 
											ParallelLauncher.runningLauncherFirstProcessStartTime);
								}
							} catch (NumberFormatException e){
								System.err.println("Failed to read start time of last process from config file (Value: " 
										+ entryLine.substring(entryLine.indexOf(CONFIG_FILE_KEY_VALUE_SEPARATOR) 
										+ CONFIG_FILE_KEY_VALUE_SEPARATOR.length()).trim() + ").");
							}
						}
					return false;
				}
	
				@Override
				public String constructEntry() {
					return new StringBuilder(key).append(CONFIG_FILE_KEY_VALUE_SEPARATOR).
							append(ParallelLauncher.runningLauncherFirstProcessStartTime).toString();
				}
			};
			addConfigEntryHandler(currentProcessStartTimeHandler);
		}
		
		if(debug) {
			System.out.println(PREFIX + "Registered configuration entry handlers:" + System.getProperty("line.separator") + configEntryHandler);
		}
	}
	
	///// CONFIG FILE MANIPULATION
	
  	/**
  	 * Lock to constrain access to config file to single thread.
  	 */
  	private static Object configFileLock = new Object(); 
	
  	/**
  	 * Refreshes the runtime configuration from config files and writes 
  	 * config files if settings not yet saved.
  	 * Note: Does not perform launcher-specific checks but blindly
  	 * updates settings from configuration files. Locks configuration 
  	 * files during read and write.
  	 */
	protected static void refreshRuntimeConfigFromConfigFiles() {
		for(String configFile: configEntryHandler.keySet()) {
			refreshRuntimeConfigFromConfigFiles(configFile);
		}
	}
	
	/**
	 * Refreshes runtime configuration from specific configuration file and writes
	 * settings if not yet saved to file. 
	 * Note: Does not perform launcher-specific checks but blindly
  	 * updates settings from configuration files. Locks configuration 
  	 * files during read and write.
	 * @param configFile Filename of config file to check
	 */
	protected static void refreshRuntimeConfigFromConfigFiles(String configFile) {
		
		ArrayList<ConfigFileEntryHandler> configEntries = configEntryHandler.get(configFile);
		if(configEntries == null || configEntries.isEmpty()) {
			if(debug) {
				System.out.println(PREFIX + "Attempt to update configuration from file " + configFile + " aborted since no entry handler specified.");
			}
			return;
		}
			
		synchronized(configFileLock) {
  			File confFile = new File(configFile);
  			boolean containsProcessConfiguration = true;
  			
  			if(confFile.exists()) {
  				
  				//READING FROM CONFIG
  				
  				List<String> content = null;
  				try {
					content = FileUtils.readLines(confFile);
				} catch (IOException e) {
					System.err.println(PREFIX + "Problems reading config file " + e.getMessage());
				}
  				if(content != null) {
  					//check per handler
					for(int k = 0; k < configEntries.size(); k++) {
						if(debug) {
							System.out.println(PREFIX + "Reading from ConfigFileHandler " + configEntries.get(k));
						}
						boolean foundContent = false;
	  					//check per line of content
	  					for(int i = 0; i < content.size(); i++){
  							//do handler-specific processing
  							if(configEntries.get(k).readEntry(content.get(i))) {
  								//if found somewhere in the config file
  								foundContent = true;
  								if(debug) {
  	  								System.out.println(PREFIX + "Found entry in config file for " + configEntries.get(k));
  	  							}
  							}
  						}
	  					if(!foundContent) {
	  						containsProcessConfiguration = false;
	  						if(debug) {
  								System.out.println(PREFIX + "No entry in config file for " + configEntries.get(k));
  							}
	  					}
  					}
  				}
  			} else {
  				//if no file, then no config
  				containsProcessConfiguration = false;
  			}
  				
			//WRITING TO CONFIG
			
			//Write config file content if non-found related to config file
			if(ParallelLauncher.configFileCheckForLauncherConfiguration && !containsProcessConfiguration) {	
				//if config file exists, get all content and append process-related stuff
  				List<String> contents = new ArrayList<String>();
  				if(confFile.exists()) {
  					try {
  						//if exists, get previous content (knowing that it does not contain process information)
						contents = FileUtils.readLines(confFile);
					} catch (IOException e) {
						System.err.println(PREFIX + "Problems reading config file " + e.getMessage());
					}
  				}
  				
  				//now just add missing contents
  				for(int k = 0; k < configEntries.size(); k++) {
					//do handler-specific processing
					String entry = configEntries.get(k).constructEntry();
					if(entry != null && !entry.isEmpty()) {
						//actually write or update entry
						System.out.println(PREFIX + "Writing configuration entry for " + configEntries.get(k).getKey() + 
								" (Value: " + configEntries.get(k).getValue() + ") to config file '" + configFile + "'.");
						contents = updateEntry(contents, configEntries.get(k).getKey(), entry);
					}
				}
  				
  				//and write config file
  				try {
					FileUtils.writeLines(confFile, contents);
					if(debug) {
						System.out.println(PREFIX + "Wrote runtime configuration to " + confFile.getName());
					}
				} catch (IOException e) {
					System.err.println(PREFIX + "Error when attempting to write config file " + e.getMessage());
				}
  			}
  		} //end of thread sync
	}
	
	/**
	 * Updates the runtime configuration value for a given field in a given configuration file.
	 * @param confFile Configuration file
	 * @param key Key of field to be updated
	 * @param value Value
	 */
	protected static void updateRuntimeConfiguration(String confFile, String key, String value) {
		synchronized(configFileLock){
	  		File configFile = new File(confFile);
	  		String updatedEntry = new StringBuilder(key)
	  			.append(CONFIG_FILE_KEY_VALUE_SEPARATOR)
	  			.append(value).toString();
			if(configFile.exists()){
				try {		
					//if existing file, check for content and replace duration-related info
					List<String> content = FileUtils.readLines(configFile);
					content = updateEntry(content, key, updatedEntry);
					//rewrite file but leave other entries untouched
					FileUtils.writeLines(configFile, content);
				} catch (IOException e) {
					System.err.println(PREFIX + "Error when attempting to write config file: " + e.getMessage());
				}
			} else {
				List<String> newContent = new ArrayList<String>();
				newContent.add(updatedEntry);
				try {
					FileUtils.writeLines(configFile, newContent);
				} catch (IOException e) {
					System.err.println(PREFIX + "Error when attempting to write config file: " + e.getMessage());
				}
			}
  		}
	}
	
	/**
	 * Updates entry in given list of String by looking line with given key and replacing that line.
	 * Adds entryLine if not in content list.
	 * @param content
	 * @param key
	 * @param entryLine
	 * @return List with updated or added entry
	 */
	private static List<String> updateEntry(List<String> content, String key, String entryLine) {
		boolean containedEntry = false;
		for(int i = 0; i < content.size(); i++){
			if(content.get(i).equals(entryLine)) {
				containedEntry = true;
				break;
			}
			//Replace line if different values in entry line or non-existent
			if(!containedEntry && content.get(i).startsWith(key)) {
				content.remove(i);
				content.add(i, entryLine);
				containedEntry = true;
				if(debug) {
					System.out.println(PREFIX + "Updated config file entry for " + key);
				}
				break;
			}
		}
		if(!containedEntry){
			content.add(entryLine);
			if(debug) {
				System.out.println(PREFIX + "Added config file entry for " + key);
			}
		}
		return content;
	}

	// Separate handler to read Machine ID
	
	private static ConfigFileEntryHandler<Integer> machineIdEntryHandler = new ConfigFileEntryHandler<Integer>(META_LAUNCHER_MACHINE_ID_FILE, MACHINE_ID, 
			MetaLauncher.currentMachineID) {
		
		@Override
		public boolean readEntry(String entryLine) {
			if(entryLine.startsWith(key)){
				try{
					Integer newMachineId
						= Integer.parseInt(entryLine.substring(entryLine.indexOf(CONFIG_FILE_KEY_VALUE_SEPARATOR) 
								+ CONFIG_FILE_KEY_VALUE_SEPARATOR.length()).trim());
					if(!newMachineId.equals(MetaLauncher.currentMachineID)){
						MetaLauncher.currentMachineID = newMachineId;
						System.out.println("MetaLauncher: Updated Machine ID to " + 
								MetaLauncher.currentMachineID);
					}
				} catch (NumberFormatException e){
					System.err.println("Failed to read Machine ID from config file (Value: " 
							+ entryLine.substring(entryLine.indexOf(CONFIG_FILE_KEY_VALUE_SEPARATOR) 
							+ CONFIG_FILE_KEY_VALUE_SEPARATOR.length()).trim() + ").");
				}
				return true;
			}
			return false;
		}

		@Override
		public String constructEntry() {
			return new StringBuilder(key).append(CONFIG_FILE_KEY_VALUE_SEPARATOR).
					append(MetaLauncher.currentMachineID).toString();
		}
	};
	
	/**
	 * Initialises this instance's machine ID from config file {@link #META_LAUNCHER_MACHINE_ID_FILE}, or 
	 * creates new config file with default value and uses this.
	 * @return Returns the machine ID
	 */
	public static int initializeMachineIdFromConfigFile() {
		if (!configEntryHandler.containsKey(machineIdEntryHandler.getConfigFile())) {
			ArrayList<ConfigFileEntryHandler> entries = new ArrayList<ConfigFileEntryHandler>();
			entries.add(machineIdEntryHandler);
			
			configEntryHandler.put(machineIdEntryHandler.getConfigFile(), entries);
		}
		refreshRuntimeConfigFromConfigFiles(machineIdEntryHandler.getConfigFile());
		return MetaLauncher.currentMachineID;
	}
	
}
