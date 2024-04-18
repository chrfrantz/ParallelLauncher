package org.christopherfrantz.parallelLauncher;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.christopherfrantz.parallelLauncher.util.*;
import org.christopherfrantz.parallelLauncher.util.listeners.DynamicLauncherInstanceCounter;
import org.christopherfrantz.parallelLauncher.util.listeners.MetaLauncherListener;
import org.christopherfrantz.parallelLauncher.util.listeners.ProcessStatusListener;
import org.christopherfrantz.parallelLauncher.util.wrappers.ProcessWrapper;

/**
 * The MetaLauncher allows the automated queueing of multiple ParallelLaunchers, 
 * e.g. in order to schedule simulation runs for given number of times. 
 * MetaLauncher assures that ParallelLaunchers are correctly identified as running
 * (via OS-specific features, such as WMI on Windows, or ps on Linux) before
 * starting subsequent instances. Each ParallelLauncher starts an own
 * output window in order to monitor its execution. 
 * Please refer to the 'examples' sub-package for examples of use.
 * 
 * @author Christopher Frantz
 *
 */
public class MetaLauncher extends Launcher {
	
	public static final String PREFIX = "MetaLauncher: ";

	/**
	 * ParallelLauncher specialisations to be launched by MetaLauncher, along with number of invocations specified as DynamicLauncherInstanceCounter
	 * to allow changes at runtime.
	 */
	private static LinkedHashMap<Class<? extends ParallelLauncher>, DynamicLauncherInstanceCounter> launchersToBeLaunched = new LinkedHashMap<>();
	
	/**
	 * Adds a ParallelLauncher class for launching along with the number of instances that are to be launched, specified as {@link DynamicLauncherInstanceCounter} 
	 * in order to permit changes to that number at runtime.
	 * @param launcher Launcher class to be launched
	 * @param instanceCounter {@link DynamicLauncherInstanceCounter} specifying number of launcher instances to be queued
	 */
	public static void addLaunchersToBeLaunched(final Class<? extends ParallelLauncher> launcher, final DynamicLauncherInstanceCounter instanceCounter) {
		if(launcher == null || instanceCounter == null) {
			System.out.println(PREFIX + "Attempted to add null values for launcher class. Registration aborted.");
			return;
		}
		launchersToBeLaunched.put(launcher, instanceCounter);
	}
	
	/**
	 * Adds a ParallelLauncher class for launching along with the static number of instances that are to be launched.<br>
	 * To allow dynamic change of number of instances during MetaLauncher runtime (e.g. changing parameter files), 
	 * use {@link #addLaunchersToBeLaunched(Class, DynamicLauncherInstanceCounter)} instead.
	 * @param launcher Launcher class to be launched
	 * @param numberOfInstances Number of instances to be queued
	 */
	public static void addLaunchersToBeLaunched(final Class<? extends ParallelLauncher> launcher, final int numberOfInstances) {
		launchersToBeLaunched.put(launcher, new DynamicLauncherInstanceCounter() {
			
			@Override
			public int totalNumberOfInstancesToBeLaunched() {
				return numberOfInstances;
			}

			@Override
			public int remainingNumberOfInstancesToBeLaunched() {
				return -1;
			}
			
		});
	}
	
	/**
	 * IPC file reference used for interaction with launched ParallelLauncher (to await proper startup before starting subsequent ParallelLauncher)
	 */
	private static File ipcFile = null;
	
	/**
	 * Default waiting time between different checks in ms
	 */
	protected static long defaultWaitingTime = 10000;
	
	/**
	 * Optionally extended waiting time for startup of processes (in ms).
	 */
	protected static long defaultStartupWaitingTime = defaultWaitingTime * 1;
	
	/**
	 * Indicates the maximum number of MetaLaunchers running (i.e. dispatching
	 * ParallelLaunchers) at the same time. Additional MetaLaunchers 
	 * will be queued (i.e. do not start scheduling ParallelLaunchers) 
	 * until a slot becomes available. Default: 1
	 */
	public static int maxNumberOfMetaLaunchersRunningAtOneTime = 1;
	
	/**
	 * Indicates the maximum number of ParallelLaunchers running/waiting at the same time.
	 * Reduces memory load and WMI stress. -1 deactivates any limitation, i.e. 
	 * all specified ParallelLaunchers will be queued at once. Default: 4
	 */
	public static int maxNumberOfQueuedOrRunningLaunchersAtOneTime = 4;
	
	/**
	 * Waiting time (in milliseconds) between checks on running processes 
	 * data structure in order to determine whether further ParallelLaunchers can
	 * be started. Only used if 
	 * {@link #maxNumberOfQueuedOrRunningLaunchersAtOneTime} != -1. Default: 120000
	 */
	protected static long queueCheckFrequencyMetaLauncher = 120000;
	
	/**
	 * Sets the frequency which MetaLauncher instances use to check their 
	 * queue status. For short-running MetaLaunchers, it might be desirable to 
	 * modify the default value ({@value #queueCheckFrequencyMetaLauncher} minutes) to
	 * minimize waiting times. 
	 * Time can be specified using decimal digits (to represent fraction of minutes, 
	 * e.g. 3.5).
	 * @param frequencyInMinutes Checking frequency in minutes
	 */
	protected static void setQueueCheckingFrequency(Double frequencyInMinutes){
		if(frequencyInMinutes == null){
			System.err.println(PREFIX + "Specified null value for Queue Checking Frequency!");
			return;
		}
		queueCheckFrequencyMetaLauncher = new Float(frequencyInMinutes * 60000).longValue();
		System.out.println(PREFIX + "Launcher queue checking frequency set to " + frequencyInMinutes + " minute(s).");
	}
	
	/**
	 * Sets the frequency which MetaLauncher instances use to check their 
	 * queue status. For short-running processes, it might be desirable to 
	 * modify the default value ({@value #queueCheckFrequencyMetaLauncher} minutes) to
	 * minimize waiting times. 
	 * @param frequencyInMinutes Checking frequency in minutes
	 */
	protected static void setQueueCheckingFrequency(Integer frequencyInMinutes){
		setQueueCheckingFrequency(new Double(frequencyInMinutes));
	}
	
	/**
	 * Returns the queue checking frequency in milliseconds.
	 * @return
	 */
	protected static Long getQueueCheckingFrequency() {
		return queueCheckFrequencyMetaLauncher;
	}
	
	/**
	 * Default Machine ID for local machine.
	 */
	public static int defaultMachineID = 0;
	
	/**
	 * Current machine ID (defaults to {@link #defaultMachineID}) if not explicitly 
	 * set via code or config files.
	 */
	public static int currentMachineID = defaultMachineID;
	
	/**
	 * Special user input that triggers launch of all remaining ParallelLaunchers overriding
	 * the threshold specified in {@link #maxNumberOfQueuedOrRunningLaunchersAtOneTime}.
	 */
	private static final String inputToTriggerLaunchOfAllProcesses = "all";
	
	/**
	 * References to started processes in order to keep track how many processes are running.
	 */
	private static HashSet<Process> runningProcesses = new HashSet<>();
	
	/**
	 * Timestamp at which the last terminated process (not the 'final' process!) finished.
	 * Used to calculate stats.
	 */
	private static long terminationTimeOfLastProcess = 0l;
	
	/**
	 * ProcessStatusListener used to keep track of actively running process instances.
	 */
	private static ProcessStatusListener processStatusListener = new ProcessStatusListener() {
		
		@Override
		public void executeDuringProcessLaunch(ProcessWrapper wrapper) {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void executeAfterProcessTermination(ProcessWrapper wrapper) {
			if(runningProcesses.contains(wrapper.getProcess())){
				runningProcesses.remove(wrapper.getProcess());
				//Memorize time of last finished process (up to now) - for more accurate prediction
				terminationTimeOfLastProcess = System.currentTimeMillis();
				if(debug){
					System.out.println(PREFIX + "Removed process '" + wrapper.getName() + "' from list of running processes.");
				}
			}
			// Check for deletion of unified JAR file if activated
			if(launchingFinished && createOneJarFileForAllLaunchers && runningProcesses.isEmpty()){
				// If launching has finished and all processes have finished, then delete JAR file
				File subfolder = new File(System.getProperty("user.dir") + FOLDER_SEPARATOR + ParallelLauncher.tempJarSubfolder);
				if (debug) {
					System.out.println(PREFIX + "Subfolder " + subfolder + " to be deleted.");
				}
				// Search for all files starting with this prefix (and ending with .jar) and delete them
				ArrayList<File> unifiedJars = 
						new ArrayList<File>(FileUtils.listFiles(subfolder, FileFilterUtils.prefixFileFilter(unifiedJarFile + "_"), null));
				if(!unifiedJars.isEmpty()){
					System.out.println(ParallelLauncher.getCurrentTimeString(true) + 
							": " + PREFIX + "Deleting unified JAR(s) after termination of all ParallelLaunchers: " + unifiedJars.toString());
					for(int i = 0; i < unifiedJars.size(); i++){
						if(FileUtils.deleteQuietly(unifiedJars.get(i))){
							if(debug){
								System.out.println(ParallelLauncher.getCurrentTimeString(true) + 
										": " + PREFIX + "Deleted file '" + unifiedJars.toString() + "'.");
							}
						} else {
							System.err.println(ParallelLauncher.getCurrentTimeString(true) + 
									": " + PREFIX + "Deletion of file '" + unifiedJars.toString() + "' failed.");
						}
					}
				} else {
					System.err.println(ParallelLauncher.getCurrentTimeString(true) + 
							": " + PREFIX + "Could not find unified JAR(s) to be deleted.");
				}
			}
			// Notify all listeners once all processes have terminated
			if(launchingFinished && runningProcesses.isEmpty()){
				notifyListeners();
				
				// Print runtime
				printDuration();
			}
		}
	};
	
	/**
	 * Instructs ParallelLaunchers launched by this MetaLauncher to use one JAR file 
	 * instead of compiling their own ones. Advantage: Even ParallelLaunchers that have 
	 * not been queued immediately but with delay (based on {@link #maxNumberOfQueuedOrRunningLaunchersAtOneTime}) 
	 * will still use the source base of the time of the first JAR generation.
	 * However, if scheduled ParallelLaunchers should use the code base at the time 
	 * of their instantiation (i.e. potentially changed since starting the 
	 * MetaLauncher), each should generate its own JAR (i.e. {@link #createOneJarFileForAllLaunchers} 
	 * should be set to false). Default: true
	 */
	public static boolean createOneJarFileForAllLaunchers = false;
	
	/**
	 * Unified JAR filename generated during MetaLauncher start and passed to all ParallelLauncher
	 * instances if {@link #createOneJarFileForAllLaunchers} is activated.
	 */
	private static String unifiedJarFile = null;
	
	/**
	 * Indicates if MetaLauncher has finished all scheduled launches of ParallelLauncher instances 
	 * and effectively just awaits their termination.
	 */
	private static boolean launchingFinished = false;
	
	/**
	 * Collection of all registered MetaLauncherListeners
	 */
	private static ArrayList<MetaLauncherListener> listeners = new ArrayList<>();
	
	/**
	 * Registers a listener for this MetaLauncher that is called upon termination of 
	 * all scheduled ParallelLaunchers.
	 * @param listener
	 */
	public static void registerMetaLauncherListener(MetaLauncherListener listener){
		if(!listeners.contains(listener)){
			listeners.add(listener);
		}
	}
	
	/**
	 * Notifies all registered MetaLauncherListeners about finished execution 
	 * of all scheduled ParallelLaunchers.
	 */
	private static void notifyListeners(){
		for(int i = 0; i < listeners.size(); i++){
			listeners.get(i).executionOfAllLaunchersFinished();
		}
	}
	
	/**
	 * If activated, checks for other running MetaLauncher instances and 
	 * awaits their termination before starting. Per default it checks 
	 * only for MetaLauncher instances of the same type (i.e. class name).
	 */
	public static boolean queueMetaLauncherInstances = true;
	
	/**
	 * Contains MetaLauncher implementations to be considered when queueing 
	 * MetaLaunchers in order (if {@link #queueMetaLauncherInstances} is activated).
	 */
	public static ArrayList<Class> metaLauncherInstancesToConsiderWhenQueueing = new ArrayList<>();
	
	/**
	 * Main entry point for MetaLauncher. The arguments are not used as part of the MetaLauncher.
	 * @param args
	 */
	public static void main(String[] args) {
		
		detectLauncherClass(MetaLauncher.class);
		
		loadConfigEntryHandler();
		
		if(launchersToBeLaunched.isEmpty()){
			System.out.println(PREFIX + "Nothing to be launched. Exiting ...");
			System.err.println("You cannot start MetaLauncher directly, "
				+ "but need to implement a subclass to schedule different ParallelLaunchers." + System.getProperty("line.separator")
				+ "See the MetaLauncher examples in the sub-package 'examples'.");
			return;
		}
		
		final long metaLauncherId = System.nanoTime();
		// File used for IPC with this MetaLauncher
		final String ipcFileName = "MetaLauncher_IPC_" + metaLauncherId;
		if(debug){
			System.out.println(ParallelLauncher.getCurrentTimeString(true) + ": MetaLauncher IPC file: " + ipcFileName);
		}
		ipcFile = new File(ipcFileName);
		// Delete file if existing
		deleteIpcFile();
		
		String separator = System.getProperty("file.separator");
		String classpath = System.getProperty("java.class.path");
		String path = System.getProperty("java.home")
				+ separator + "bin" + separator + "java";
		
		// Generate name for unified JAR file if activated
		if(createOneJarFileForAllLaunchers){
			unifiedJarFile = String.valueOf(System.currentTimeMillis());
				System.out.println(ParallelLauncher.getCurrentTimeString(true) + ": "+ PREFIX + "Using unified JAR file '" + unifiedJarFile + "' for all ParallelLaunchers.");
			// JARify classpath to avoid side effects if sources are manipulated after initial launch
			classpath = ParallelLauncher.createJARifiedClasspath(classpath, unifiedJarFile, false);
			if(debug){
				System.out.println(PREFIX + "Generated classpath: " + classpath);
			}
		}
		
		// Read configuration settings from config file - relevant for initial start parameter
		refreshRuntimeConfigFromConfigFiles(META_LAUNCHER_RUNTIME_CONFIG_FILE);
		
		// Check for position in queue before starting if activated
		if(queueMetaLauncherInstances){
			int turn = myTurnInRunning(metaLauncherInstancesToConsiderWhenQueueing);
			while(turn > maxNumberOfMetaLaunchersRunningAtOneTime - 1){
				System.out.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "Will perform recheck in " + queueCheckFrequencyMetaLauncher + " ms.");
				awaitUserInput(queueCheckFrequencyMetaLauncher, "perform queue position check");
				turn = myTurnInRunning(metaLauncherInstancesToConsiderWhenQueueing);
			}
		}
		
		// Maintain a counter of actually launched instances by this MetaLauncher for the purpose of calculating stats.
		int launchedLauncherInstances = 0;
		
		for(final Entry<Class<? extends ParallelLauncher>, DynamicLauncherInstanceCounter> entry: launchersToBeLaunched.entrySet()){
			if(entry.getValue().totalNumberOfInstancesToBeLaunched() <= 0){
				System.err.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "Invalid number of launches (" + entry.getValue().totalNumberOfInstancesToBeLaunched() + ") for launcher '" + entry.getKey().getSimpleName() + "', moving to next one...");
			} else {
				boolean dynamicLauncher = entry.getValue().remainingNumberOfInstancesToBeLaunched() != -1;
				int start = !dynamicLauncher ? 0 : (entry.getValue().totalNumberOfInstancesToBeLaunched() - entry.getValue().remainingNumberOfInstancesToBeLaunched());  
				// Switch to allow overriding the maximum permissible number of queued launcher - used when release all using console command 'all'
				boolean overrideMaxQueuedLauncher = false;
				// Start number of ParallelLaunchers - but consider dynamic changes at runtime
				for(int i = start; i < entry.getValue().totalNumberOfInstancesToBeLaunched(); i++){
					System.out.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "Launch number " + (i + 1) 
							+ " (out of " + entry.getValue().totalNumberOfInstancesToBeLaunched() + ") for ParallelLauncher '" 
							+ (entry.getKey().getSimpleName() != null ? entry.getKey().getSimpleName() : entry.getKey().getName())  + "'.");
					ProcessBuilder processBuilder = 
						(createOneJarFileForAllLaunchers ? 
							new ProcessBuilder(path, "-cp", 
								classpath, 
								// Launched launcher fully-qualified (FQ) class name
								entry.getKey().getName(), 
								// IPC filename reference for interaction
								ipcFileName, 
								// Iteration for that launcher type as reference (mostly for debug purposes)
								String.valueOf(i + 1), 
								// Name for unified JAR file (respective prefix if multiple JAR files)
								unifiedJarFile) :	
							new ProcessBuilder(path, "-cp", 
								classpath, 
								// Launched launcher fully-qualified (FQ) class name
								entry.getKey().getName(), 
								// IPC filename reference for interaction
								ipcFileName, 
								// Iteration for that launcher type as reference (mostly for debug purposes)
								String.valueOf(i + 1)));
					//processBuilder.redirectErrorStream(true);
					if(debug){
						System.out.println(PREFIX + "About to start ParallelLauncher with command: " + processBuilder.command());
					}
					// Generate name for process
					String name = entry.getKey().getSimpleName() + " " + (i + 1);
					ProcessWrapper process = null;
					try {
						process = new ProcessWrapper(name, processBuilder.start(), MetaLauncher.class);
						
						// Wait for valid status code
						awaitLaunchersSuccessfulStart(name, process);
						
						// Delete IPC file, so a new launcher instance can create a new one
						deleteIpcFile();
						
						if(process != null && !process.isFinished()){
							System.out.println(ParallelLauncher.getCurrentTimeString(true) + ": MetaLauncher: Launcher '" + name + "' successfully launched. Observe status in its Monitor GUI; closing the GUI ends the process.");
							// Register with data structure that maintains reference to running ParallelLaunchers
							runningProcesses.add(process.getProcess());
							process.registerListener(processStatusListener);
							// Consider this launch successful (and maintain stats-related information)
							launchedLauncherInstances++;
						} else if(process.isFinished()){
							System.err.println(ParallelLauncher.getCurrentTimeString(true) + ": MetaLauncher: Note: Launcher '" + name + "' has already finished execution (just after launching).");
						}
					} catch (IOException e) {
						System.err.println("MetaLauncher: Instantiation of launcher '" + name + "' failed.");
						e.printStackTrace();
					}
					
					if(!overrideMaxQueuedLauncher) {
						// Read settings from config files
						refreshRuntimeConfigFromConfigFiles(META_LAUNCHER_RUNTIME_CONFIG_FILE);
					}
					
					// Adjust number of remaining instances to be launched
					if(dynamicLauncher) {
						i = entry.getValue().totalNumberOfInstancesToBeLaunched() 
								- entry.getValue().remainingNumberOfInstancesToBeLaunched();
						// Override automatic increment (used for non-dynamic launcher specifications)
						i--;
					}
									
					// Check if not too many ParallelLaunchers running
					if(maxNumberOfQueuedOrRunningLaunchersAtOneTime > -1 && 
							// and if further are to be started - if not, no point of blocking here
							i < (entry.getValue().totalNumberOfInstancesToBeLaunched() - 1)){
						if(maxNumberOfQueuedOrRunningLaunchersAtOneTime == 0){
							System.err.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "Value zero for max. number of running ParallelLaunchers is invalid. Deactivated functionality.");
							maxNumberOfQueuedOrRunningLaunchersAtOneTime = -1;
						}
						// Wait for number of active ParallelLaunchers to drop, or user intervention
						// Switch to release further ParallelLaunchers
						boolean release = false;
						// Check if less than max processes running
						release = runningProcesses.size() < maxNumberOfQueuedOrRunningLaunchersAtOneTime;
						// If not, do repeated checks
						while(!release){
							System.out.println(ParallelLauncher.getCurrentTimeString(true) 
									+ ": " + PREFIX + "Currently " + runningProcesses.size() + " active ParallelLaunchers. "
									+ "Waiting for those to drop below " + maxNumberOfQueuedOrRunningLaunchersAtOneTime
									+ " before starting further ones. Recheck in "
									+ queueCheckFrequencyMetaLauncher + " ms.");
							
							// Shows CLI and waits for specified time or until user presses key. Assigns true to release additional
							// launcher(s) if corresponding user action has been performed.
							switch (showCLI(queueCheckFrequencyMetaLauncher, entry.getValue(), dynamicLauncher, launchedLauncherInstances, "")) {
								case 0:
									// Don't release any launcher
									release = false;
									break;
								case 1:
									// Release one launcher
									release = true;
									break;
								case -1:
									// Release all ParallelLaunchers
									release = true;
									overrideMaxQueuedLauncher = true;
									break;
								default:
									// Don't release any launcher
									release = false;
									break;
							}
							
							if(!overrideMaxQueuedLauncher) {
								// Update settings from config files
								refreshRuntimeConfigFromConfigFiles(META_LAUNCHER_RUNTIME_CONFIG_FILE);
							}
							// Check if no user-caused release, check for condition-based release
							if(!release){
								if(maxNumberOfQueuedOrRunningLaunchersAtOneTime > -1 
									// Keep rechecking as long as too many running processes/ParallelLaunchers
									&& runningProcesses.size() >= maxNumberOfQueuedOrRunningLaunchersAtOneTime) {
										// do nothing
								} else {
									release = true;
								}
							}
						}
					}
				}
			}
		}
		// MetaLauncher is done; if unified JARs are activated, it can be deleted once the final process has terminated
		launchingFinished = true;
		String msg = ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "All specified ParallelLaunchers have been started.";
		
		// Show CLI while processes are running
		while (!runningProcesses.isEmpty()) {
			showCLI(queueCheckFrequencyMetaLauncher, null, true, launchedLauncherInstances, msg);
		}
		// No explicit termination, else we lose information about process termination from ProcessWrapper
	}
	
	/**
	 * Shows the CLI for MetaLauncher interaction.
	 * @param waitTime Waiting time until returning control.
	 * @param instanceCounter Instance counter reference
	 * @param dynamicLauncher Indicator whether the launcher is dynamic (i.e. adapts to changing number of ParallelLaunchers over time)
	 * @param launchedLauncherInstances Counter of already launched instances
	 * @param msg Message to be printed before showing user selection
	 * @return Indicates the number of instances to be release (-1 = all remaining instances)
	 */
	private static int showCLI(long waitTime, DynamicLauncherInstanceCounter instanceCounter, boolean dynamicLauncher, int launchedLauncherInstances, String msg) {
		
		// Print some message
		System.out.println(msg);
		
		// Allow user selection
		int result = awaitUserInput(waitTime, "recheck", 
				"one", "release one additional launcher", 
				inputToTriggerLaunchOfAllProcesses, "release all remaining ParallelLaunchers",
				"debug", "toggle debug mode", 
				"time", "show the elapsed time since launcher start",
				"stats", "show runtime per process and estimated remaining runtime",
				"help", "see a explanation of parameter file entries");
		
		switch (result) {
			case 0:
				// Timeout
				return 0;
			case 1:
				// Refresh
				return 0;
			case 2:
				// Release one
				return 1;
			case 3:
				// Set max number of queued ParallelLaunchers to -1 --> infinite and release
				maxNumberOfQueuedOrRunningLaunchersAtOneTime = -1;
				System.out.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "Bypassing maximum number of permissible ParallelLaunchers and launch all remaining ones.");
				return -1;
			case 4:
				// Toggle debug
				toggleDebugMode();
				return 0;
			case 5:
				// Show elapsed time
				System.out.println(PREFIX + "Elapsed time since launcher start: " + getElapsedRuntime().getSimpleRepresentation());
				return 0;
			case 6:
				// Show stats
				int launched = launchedLauncherInstances + 1;
				int toBeLaunched = 0;
				if (instanceCounter != null) {
					if (dynamicLauncher) {
						toBeLaunched = instanceCounter.remainingNumberOfInstancesToBeLaunched();
					} else {
						toBeLaunched = instanceCounter.totalNumberOfInstancesToBeLaunched() - launched;
					}
				}
				// Calculate average runtime
				long runtimePerProcess = 0l;
				if(terminationTimeOfLastProcess != 0l) {
					// Reduce launched instances by one, because only finished instances should be considered in stats calculation
					runtimePerProcess = new Float((terminationTimeOfLastProcess - startTime) / 
							(float)(launchedLauncherInstances - 1)).longValue();
					System.out.println(PREFIX + "Mean runtime per process: " + generateDuration(runtimePerProcess).getSimpleRepresentation());
				}
				// Predict remaining runtime based on runtime across all terminated processes run by this launcher instance.
				long predictedRuntime = runtimePerProcess * toBeLaunched;
				System.out.println(PREFIX + "Elapsed runtime: " + getElapsedRuntime().getSimpleRepresentation() + 
						System.getProperty("line.separator") + (terminationTimeOfLastProcess == 0l ? 
								"Not enough information yet to perform runtime prediction." :
									"Estimated remaining runtime (for " + toBeLaunched + " processes): " +
									generateDuration(predictedRuntime).getExplicitRepresentation() + 
									System.getProperty("line.separator") + 
									"Predicted shutdown time: " + calculateFutureDate(predictedRuntime, DateFormat.FULL)));
				return 0;
			case 7:
				// Show parameter file entries along with current values
				System.out.println("Parameter '" + MAX_RUNNING_METALAUNCHERS_KEY + 
						"' specifies the maximum number of running metalauncher (not launcher!) instances." + 
						System.getProperty("line.separator") +
						"  In practice values > 1 are only necessary if completely different simulation scenarios are to be scheduled by multiple manually-started MetaLaunchers." + 
						System.getProperty("line.separator") +
						"  Example: Two different simulation scenarios with very few tested instances each." + 
						System.getProperty("line.separator") +
						"  Current value: " + maxNumberOfMetaLaunchersRunningAtOneTime);
				System.out.println("Parameter '" + MAX_QUEUED_LAUNCHERS_BY_METALAUNCHER_KEY + 
						"' specifies the maximum number of queued or running ParallelLaunchers (spawned by one or more MetaLaunchers) at any given time." +
						System.getProperty("line.separator") +
						"  In practice this is the commonly used parameter to control load. " +
						System.getProperty("line.separator") +
						"  Current value: " + maxNumberOfQueuedOrRunningLaunchersAtOneTime);
				return 0;
			default:
				// Don't do anything
				System.out.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "Invalid user input '" + result + "' ignored.");
				return 0;
		}
	}
	
	/**
	 * Start time of this ParallelLauncher instance (in WMI).
	 */
	private static String myStartTime = null;
	
	/**
	 * Name of the launcher class itself (the one the user implements).
	 */
	protected static Class launcherClass = null;
	
	/**
	 * Attempts to infer class name of user implemented MetaLauncher for queue checking
	 */
	private static void inferLauncherClassName(){
		/**
		 * Checking if launcher class is specified. If not use own class as launcher.
		 */
		if(launcherClass == null){
			boolean successfulInference = false;
			// Try to infer launcherClass name
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
				throw new RuntimeException(PREFIX + "Please assign your launcher subclass (extends MetaLauncher) to the launcherClass field of MetaLauncher.");
			}
		}
	}
	
	/**
	 * Checks WMI instrumentation if this MetaLauncher should start.
	 * Return codes:
	 * -1 if invalid response from WMI (Windows) or ps (Linux) or unable to determine 
	 * position in queue
	 * -2 if BlockingParallelLauncher is running (to prevent immediate 
	 * start of MetaLauncher and launcher instances)
	 * 0 if first element in queue (should initiate start of ParallelLaunchers),
	 * else number of MetaLaunchers in queue before this launcher.
	 * @return
	 */
	private static Integer myTurnInRunning(ArrayList<Class> launcherClassesToConsider){
		// Get all creation times of this process and other specified processes
		ArrayList<String> output = new ArrayList<String>();
		if(launcherClass == null){
			// Try to infer - throws RuntimeException if failing
			inferLauncherClassName();
		}
		if(!launcherClassesToConsider.contains(launcherClass)){
			launcherClassesToConsider.add(launcherClass);
		}
		for(int i = 0; i < launcherClassesToConsider.size(); i++){
			output.addAll(processReader.getCreationTimesOfRunningInstances(launcherClassesToConsider.get(i), null));
		}
		if(debug){
			System.out.println(PREFIX + "My start time: " + myStartTime + ", Got as return from OS process manager: " + output);
		}
		if(output.isEmpty()){
			System.err.println(PREFIX + "No process of my kind (" + launcherClass.getSimpleName() + ") seems to be started - Impossible. WMI is probably overused. Will wait and make another query later.");
			if(myStartTime == null){
				System.err.println(PREFIX + "Important: DO NOT start new launcher instance yet as OS process manager did not deliver reliable information on this instance." + System.getProperty("line.separator") 
						+ "If you start a new instance now, both instances (this and the new one) are likely to infer wrong starting times.");
			}
			return -1;
		}
		// Order times ascending
		Collections.sort(output);
		// Assume that my start is the most recent one and memorize that
		if(myStartTime == null){
			// Once set it is only used for comparison
			myStartTime = output.get(output.size() - 1);
			System.out.println(PREFIX + "Assume my creation time as " + myStartTime);
			// If one launcher running and creation of temporary JAR files deactivated, warn user about confounded setups
			/*if(output.size() == 1 && !createTemporaryJarFilesForQueueing){
				System.err.println("=== Starting further launcher instances or working on source files should NOT be done" + System.getProperty("line.separator")
						+ "if you are running ParallelLaunchers from an IDE that compiles source files automatically as it will affect all queued ParallelLaunchers." + System.getProperty("line.separator")
						+ "Activate the generation of temporary JAR files if you want to queue more ParallelLaunchers or work on source files during launcher runs. ===");
			} else {*/
			System.out.println(PREFIX + "=== SUCCESS! You can now safely start further MetaLauncher instances. ===");
			//}
		}
		// Check for blocking launcher
		List<String> blockerRunning = processReader.getCreationTimesOfRunningInstances(BlockingParallelLauncher.class, null);
		if(!blockerRunning.isEmpty()){
			System.err.println(PREFIX + "Blocking Launcher is running. Will prevent me and any other (Meta)Launcher from starting (good for setup of large number of (Meta)Launchers).");
			return -2;
		}
		if(!output.contains(myStartTime)){
			throw new RuntimeException(PREFIX + "Something went wrong - I assumed the wrong start time as mine.... processes started too close after one another...");
		}
		if(output.get(0).equals(myStartTime)){
			System.out.println(PREFIX + "First of " + output.size() + " queued MetaLauncher(s) - my turn next.");
			// my turn, others are queued
			return 0;
		}
		for(int i = 0; i < output.size(); i++){
			if(output.get(i).equals(myStartTime)){
				System.out.println(PREFIX + "Queued behind " + i + " MetaLauncher(s) (in total " + output.size() + " queued ParallelLaunchers).");
				return i;
			}
		}
		/*
		 * some invalid response as I should otherwise have been able to provide the number
		 * of ParallelLaunchers queued before me.
		 */
		return -1;
	}
	
	/**
	 * Awaits start of process by check on its proper WMI registration, 
	 * mediated via IPC file.
	 * @param name Human-readable name of launcher for representation in messages
	 * @param process Process reference
	 */
	private static void awaitLaunchersSuccessfulStart(String name, ProcessWrapper process){
		boolean testAgain = true;
		if(process == null){
			System.err.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "Passed null reference for launched process '" + name + "'.");
			return;
		}
		while(testAgain){
			// Check if perhaps already terminated
			if(process.isFinished()){
				return;
			}
			// Wait until file is existing (or premature termination of process)
			while(!basicFileChecks(true)){
				if(process.isFinished()){
					return;
				}
				System.out.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "Waiting for starting of process '" + name + "', recheck in " + defaultStartupWaitingTime + " ms.");
				try {
					Thread.sleep(defaultStartupWaitingTime);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			// File exists, now check for valid entries
			List<String> content = null;
			try {
				content = FileUtils.readLines(ipcFile);
			} catch (IOException e) {
				System.err.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "Error when reading IPC file: " + e.getMessage());
				//e.printStackTrace();
			}
			if(content == null){
				System.err.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "No IPC information for process '" + name + "' yet. Will retry in " + defaultWaitingTime + " ms.");
				try {
					Thread.sleep(defaultWaitingTime);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} else {
				if(content.size() > 1){
					System.err.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "Too many entries in IPC file. Will only interpret first line.");
				}
				
				String firstLine;
				if(!content.isEmpty() && !(firstLine = content.get(0)).isEmpty()){
					try{
						int status = Integer.parseInt(firstLine);
						if(debug){
							System.out.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "Process '" + name + "' started with status " + status + " (" + LauncherWaitStateResolver.resolveLauncherWaitState(status) + ").");
						}
						if(status != -1){
							testAgain = false;
							// should have been started properly at this stage
						} else {
							System.out.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "Process '" + name + "' did not receive valid WMI response, need to wait and recheck before starting further ParallelLaunchers.");
							try {
								Thread.sleep(defaultWaitingTime);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					} catch (NumberFormatException e){
						System.err.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "Error extracting launcher status from value " + firstLine);
					}
				} else {
					System.err.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "No process status information for process '" + name + "' in IPC file. Will wait....");
					try {
						Thread.sleep(defaultWaitingTime);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	/**
	 * Convenience method for starting MetaLauncher from meta launcher specialisation.
	 * Calls MetaLauncher's main method.
	 */
	public static void start(){
		MetaLauncher.main(new String[0]);
	}
	
	/**
	 * Convenience method for starting MetaLauncher from meta launcher specialisation.
	 * Calls MetaLauncher's main method.
	 * @param args 
	 */
	public static void start(String[] args){
		MetaLauncher.main(args);
	}
	
	/**
	 * Deletes IPC file (if existing)
	 */
	private static void deleteIpcFile(){
		basicFileChecks(false);
		while(ipcFile.exists() && !ipcFile.delete()){
			System.err.println(ParallelLauncher.getCurrentTimeString(true) + ": " + PREFIX + "Tried to delete IPC file " + ipcFile.getName() + ", but failed. Will try again in a few seconds.");
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Performs basic check on not-null of ipcFile and 
	 * optionally checks for its existence in the file system.
	 * @param existence Boolean indicating if one should check for file in file system
	 * @return True if no check for existence or if check for existence successful. False if 
	 * check for existence activated, but file not existing.
	 */
	private static boolean basicFileChecks(boolean existence){
		if(ipcFile == null){
			throw new RuntimeException(PREFIX + "IPC file is null!");
		}
		if(existence){
			return ipcFile.exists();
		}
		// return true if no existence check
		return true;
	}
	
}
