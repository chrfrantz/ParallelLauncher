package org.christopherfrantz.parallelLauncher;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.apache.commons.io.FileUtils;
import org.christopherfrantz.parallelLauncher.util.processhandlers.ProcessReader;
import org.christopherfrantz.parallelLauncher.util.CombinedClassAndStatusListener;
import org.christopherfrantz.parallelLauncher.util.DataStructurePrettyPrinter;
import org.christopherfrantz.parallelLauncher.util.ProcessMonitorGui;
import org.christopherfrantz.parallelLauncher.util.listeners.ProcessStatusListener;
import org.christopherfrantz.parallelLauncher.util.wrappers.ProcessWrapper;
import org.christopherfrantz.parallelLauncher.util.wrappers.WrapperExecutable;


/**
 * Helper utility to allow the parallel run of Java classes in order to 
 * utilize multi-core processing for applications that cannot exploit 
 * multiprocessor capabilities but can be run independently.<BR>
 * It further allows the queueing of 'launchers', so launchers wait for
 * the completion of previous launchers before starting 'their classes'
 * in dedicated child processes.<BR>
 * In short: Launchers are run sequentially; all processes started 
 * by a given launcher run in parallel.<BR>
 * <BR>
 * Only requirement for classes is that they contain a main method.<BR>
 * Important: Ensure to wait for launchers to confirm that you can start
 * further launchers before doing so to avoid non-deterministic behaviour!<BR>
 * <BR>
 * Apart from the queueing functionality, the tool allows to<BR>
 * - control processor affinity (e.g. use not of all but reduced number of 
 * cores), <BR>
 * - prevent side-effects from changing source code by detecting and 
 * packing relevant source files into temporary JAR files which are 
 * automatically purged by subsequent launchers.<BR>
 * <BR>
 * Note: The utility relies on various OS-specific operations (especially 
 * for process management) and currently only operates under MS Windows and Linux.<BR>
 * <BR>
 * How to use it:<BR>
 * <BR>
 * - Create subclass with main method<BR>
 * - Optional, but required if system cannot detect it automatically: Assign that subclass to launcherClass field. (You are doing that, so the system knows what the launcher implementation is (and queue them if multiple are running).)<BR>
 * - Add executable classes to classesToBeLaunched collection from within subclass main method. (Those will be run in parallel.)<BR>
 * - Call super class start() method. (which calls the main method - can also be called directly)<BR>
 * - Run your created subclass. (All elements in classesToBeLaunched are run in parallel, multiple launchers (of the type you just created) are queued.)<BR>
 * <BR>
 * Example:<BR>
 * <BR>
 * public class UsageExample extends ParallelLauncher {<BR>
 *<BR>
 *	 public static void main(String[] args) {<BR>
 *		launcherClass = UsageExample.class;<BR>
 *		classesToBeLaunched.add(ClassContainingMainMethod.class);<BR>
 *		classesToBeLaunched.add(AnotherClassContainingMainMethod.class);<BR>
 *		start();<BR>
 *	 }<BR>
 *<BR>
 * }<BR>
 * <BR>
 * Avoid a mixed run with other launcher implementations (i.e. further subclasses 
 * (e.g. UsageExample2 and so on)) as they will all run in parallel (and not sequentially), 
 * and, depending on load, will make your system unusable unless you carefully assigned 
 * specific processor cores to launchers (e.g. two cores for each launcher on a quad
 * core machine).<BR>
 * See example package for examples of different launcher implementations showing features:<BR> 
 * - Launched classes with arguments<BR>
 * - Prompting for specification of JDK location<BR>
 * - Configuring maximum number of launched processes<BR>
 * - Registering user-defined listeners for process execution<BR>
 * - ...<BR>
 * Persistence-related features:<BR>
 * - Prompting used of JDK location if not on %PATH% and saving to file for reuse 
 * 		(Switch {@link #writeUserSelectedJdkBinDirectoryToFile})<BR>
 * - Changing of configuration for maximum number of active parallel launchers and
 * 		processes at runtime (Switch {@link #configFileCheckForLauncherConfiguration}).
 * 		File will be written it not existing and read after each waiting cycle to react to eventual 
 * 		modifications.<BR>
 * <BR>
 * Recommendation: Leave one processor unused, so your system still reacts 
 * to user input. This is especially of concern for applications that makes extensive 
 * use of processors.<BR>
 * <BR>
 *  
 * @author Christopher Frantz
 *
 */
public class ParallelLauncher extends Launcher {
	
	/**
	 * Prefix for uniform console output
	 */
	private static final String PREFIX = "ParallelLauncher: ";

	/**
	 * Classes that are actually going to be launched.
	 */
	private static ArrayList<CombinedClassAndStatusListener> classesToBeLaunched = new ArrayList<>();

	/**
	 * Returns the number of classes registered for launching.
	 * @return Number of classes registered for launching
	 */
	protected static int getNumberOfClassesToBeLaunched(){
		return classesToBeLaunched.size();
	}
	
	/**
	 * List containing listeners that are to be notified upon any process completion.
	 */
	private static ArrayList<ProcessStatusListener> globalListeners = new ArrayList<>();
	
	/**
	 * Calls all listeners registered for the given class and registers listeners with their 
	 * respective ProcessWrappers (which then notify those listeners once processes terminate).
	 * Considers both listeners registered for an individual class as well as global listeners.
	 * Should be called at the start of a process.
	 * @param wrapper Reference to process concerned (i.e. either started or terminated)
	 * @param classWhoseListenersAreToBeCalled Class whose listeners are called.
	 */
	private static void executeListeners(ProcessWrapper wrapper, Class classWhoseListenersAreToBeCalled){
		//count notifications in case of debugging
		int notificationCount = 0;
		//global listeners
		for(int i = 0; i < globalListeners.size(); i++){
				globalListeners.get(i).executeDuringProcessLaunch(wrapper);
			notificationCount++;
			//register with ProcessWrapper for call on termination
			wrapper.registerListener(globalListeners.get(i));
		}
		//individual listeners
		for(int i = 0; i < classesToBeLaunched.size(); i++){
			//take matching class from class/listener list
			if(classesToBeLaunched.get(i).clazz.equals(classWhoseListenersAreToBeCalled)){
				ProcessStatusListener listener = classesToBeLaunched.get(i).listener;
				if(listener != null){
					//call it if it is not null
					listener.executeDuringProcessLaunch(wrapper);
					notificationCount++;
					//register the listener with wrapper (to be called on termination)
					wrapper.registerListener(listener);
					/*
					 * remove class/listener combination from list to ensure the next one is
					 * called in case of specification of different listeners for multiple 
					 * instances of same type 
					 */
					classesToBeLaunched.remove(i);
					i--;
				}
				//ensure only one individual listener is called, thus break
				break;
			}
		}
		if(debug){
			System.out.println(PREFIX + "Notified " + notificationCount + " listeners for launched class " 
					+ classWhoseListenersAreToBeCalled.getSimpleName() + ".");
		}
	}
	
	/**
	 * Classes that are only tested when launching new processes (to ensure 
	 * we are not starting too many).
	 */
	private static ArrayList<Class> classesToBeTestedInRunningProcesses = new ArrayList<Class>();
	
	/**
	 * Registers a class to be launched as part of the ParallelLauncher instance.
	 * @param classToBeLaunched Class that should be launched
	 */
	protected static void addClassToBeLaunched(Class classToBeLaunched){
		addClassToBeLaunched(classToBeLaunched, false, null);
	}
	
	/**
	 * Registers a class to be launched as part of the ParallelLauncher instance.
	 * @param classToBeLaunched Class that should be launched
	 * @param checkOnly Indicates that class should only be checked for (e.g. when 
	 * 			determining running processes, but not be actually launched).
	 */
	protected static void addClassToBeLaunched(Class classToBeLaunched, boolean checkOnly){
		addClassToBeLaunched(classToBeLaunched, checkOnly, null);
	}
	
	/**
	 * Registers a class to be launched as part of the ParallelLauncher instance.
	 * @param classToBeLaunched Class that should be launched
	 * @param listener Listener to be notified once process execution complete
	 */
	protected static void addClassToBeLaunched(Class classToBeLaunched, ProcessStatusListener listener){
		addClassToBeLaunched(classToBeLaunched, false, listener);
	}
	
	/**
	 * Registers a class to be launched as part of the ParallelLauncher instance.
	 * @param classToBeLaunched Class that should be launched
	 * @param checkOnly Indicates that class should only be checked for (e.g. when 
	 * 			determining running processes, but not be actually launched.
	 * @param listener Listener to be notified once process execution complete
	 */
	protected static void addClassToBeLaunched(Class classToBeLaunched, boolean checkOnly, ProcessStatusListener listener){
		//add for testing in any case
		classesToBeTestedInRunningProcesses.add(classToBeLaunched);
		if(checkOnly){
			return;
		}
		//add only for launching if intended
		classesToBeLaunched.add(new CombinedClassAndStatusListener(classToBeLaunched, listener));
	}
	
	/**
	 * Registers a global ProcessStatusListener. Global ProcessStatusListeners are notified upon 
	 * any process completion.
	 * @param listener Listener to be notified upon any process completion
	 */
	protected static void setGlobalProcessStatusListener(ProcessStatusListener listener){
		if(!globalListeners.contains(listener)){
			globalListeners.add(listener);
		}
	}
	
	/**
	 * Holds arguments that are passed to launched class instances. 
	 */
	private volatile static String[] argumentsToBePassedToLaunchedClasses = null;
	
	
	/**
	 * Sets the arguments to be passed to launched classes upon instantiation. 
	 * Repeated calls to method leads to accumulation of arguments.
	 * Reset can be performed using {@link #resetArgumentsToBePassedToLaunchedClasses()}.
	 * @param args Arguments passed to each launched class instance.
	 */
	public static void setArgumentsToBePassedToLaunchedClasses(String... args){
		if(args != null && args.length > 0){
			int index = 0;
			// Check if already arguments specified
			if(argumentsToBePassedToLaunchedClasses != null 
					&& argumentsToBePassedToLaunchedClasses.length > 0){
				String[] tempArr = new String[argumentsToBePassedToLaunchedClasses.length + args.length];
				// Save old array content
				while(index < argumentsToBePassedToLaunchedClasses.length){
					tempArr[index] = argumentsToBePassedToLaunchedClasses[index];
					index++;
				}
				// Assign to original field
				argumentsToBePassedToLaunchedClasses = tempArr;
			} else {
				// no previous arguments - instantiate new array
				argumentsToBePassedToLaunchedClasses = new String[args.length];
			}
			// Save new arguments
			for(int i = 0; i < args.length; i++){
				if(args[i].contains(" ")){
					System.err.println(PREFIX + "The argument '" + args[i] + "' to be passed to launched classes contains whitespace character(s)!" 
							+ System.getProperty("line.separator") + "Note that this might lead to unintended behaviour (separation into different arguments).");
				}
				// Add to argument array
				argumentsToBePassedToLaunchedClasses[index + i] = args[i];
			}
		} else {
			System.err.println(PREFIX + "Passed invalid argument array (empty or null) for arguments to be passed to invoked processes. Arguments ignored.");
		}
	}
	
	/**
	 * Resets arguments (to be passed to launched classes), which have been 
	 * previously added using {@link #setArgumentsToBePassedToLaunchedClasses(String...)}. 
	 */
	public static void resetArgumentsToBePassedToLaunchedClasses(){
		argumentsToBePassedToLaunchedClasses = null;
	}
	
	/**
	 * Expands the specified arguments to String that can be appended as 
	 * part of the command line (i.e. spaces between arguments).
	 * @return
	 */
	private static String expandArgumentsIntoCmdLineString(){
		StringBuilder builder = new StringBuilder();
		if(argumentsToBePassedToLaunchedClasses != null){
			for(int i = 0; i < argumentsToBePassedToLaunchedClasses.length; i++){
				builder.append(argumentsToBePassedToLaunchedClasses[i]);
				if(i != (argumentsToBePassedToLaunchedClasses.length - 1)){
					builder.append(" ");
				}
			}
		}
		return builder.toString();
	}
	
	/**
	 * Holds user-specified queue checking frequency. Note that this field does not 
	 * necessarily contain the actual checking frequency. For that purpose use 
	 * processCheckFrequencyForOtherLaunchersRunningProcesses and launcherCheckFrequency 
	 * in main method.
	 */
	private static Integer queueCheckFrequency = null;
	
	/**
	 * Sets the frequency which ParallelLauncher instances use to check their 
	 * queue status. For short-running processes, it might be desirable to 
	 * modify the default value (3 minutes) to minimize waiting times. 
	 * Time can be specified using decimal digits (to represent fraction of minutes, 
	 * e.g. 3.5).
	 * @param frequencyInMinutes Checking frequency in minutes
	 */
	protected static void setQueueCheckingFrequency(Double frequencyInMinutes){
		if(frequencyInMinutes == null){
			System.err.println(PREFIX + "Specified null value for Queue Checking Frequency!");
			return;
		}
		queueCheckFrequency = new Float(frequencyInMinutes * 60000).intValue();
		System.out.println(PREFIX + "Launcher queue checking frequency set to " + frequencyInMinutes + " minute(s).");
	}
	
	/**
	 * Sets the frequency which ParallelLauncher instances use to check their 
	 * queue status. For short-running processes, it might be desirable to 
	 * modify the default value (3 minutes) to minimize waiting times. 
	 * @param frequencyInMinutes Checking frequency in minutes
	 */
	protected static void setQueueCheckingFrequency(Integer frequencyInMinutes){
		setQueueCheckingFrequency(new Double(frequencyInMinutes));
	}
	
	/**
	 * Frequency with which a launcher can check for its own running 
	 * processes before starting further ones as constrained by {@link #maxNumberOfRunningLaunchedProcesses}.
	 */
	private static int processCheckFrequencyForProcessesStartedByLauncher = 15000;
	
	/**
	 * Sets the check frequency the launcher uses to recheck whether it can start further 
	 * processes. Default value: 15 seconds. 
	 * Note: This does not affect the queue check frequency used for checking number of 
	 * queued launchers (set via {@link #setQueueCheckingFrequency(Double)}).
	 * @param frequencyInMinutes Frequency in minutes
	 */
	protected static void setProcessCheckFrequencyForProcessesStartedByLauncherItself(Double frequencyInMinutes){
		if(frequencyInMinutes == null){
			System.err.println(PREFIX + "Specified null value for Process Launch Checking Frequency!");
			return;
		}
		processCheckFrequencyForProcessesStartedByLauncher = new Float(frequencyInMinutes * 60000).intValue();
		System.out.println(PREFIX + "Process Launch checking frequency set to " + frequencyInMinutes + " minute(s).");
	}
	
	/**
	 * File name for log file that keeps track of temporary created JAR files.
	 */
	public static final String CLEANUP_LOG_FILE = "ParallelLauncher_CleanUpLog";
	
	/**
	 * File name for file holding previously specified JDK bin path.
	 * If deleted, the user will be prompted to specify it again if necessary.
	 */
	public static final String JDK_BIN_PATH_FILE = "ParallelLauncher_JdkBinPath";
	
	/**
	 * Subfolder for temporary JAR files for individual launchers
	 */
	public static String tempJarSubfolder = "jars";
	
	/**
	 * Ending for jar files
	 */
	private static final String JAR_FILE_ENDING = ".jar";
	
	/**
	 * Ending for launch script files
	 */
	private static final String LAUNCH_SCRIPT_FILE_ENDING = (ProcessReader.runsOnWindows() ? ".bat" : (ProcessReader.runsOnLinux() ? ".sh" : ""));
	
	/**
	 * It set to true, user-selected JDK bin folders will be written to 
	 * file {@link #JDK_BIN_PATH_FILE} in order to reuse them for future launches.
	 */
	protected static boolean writeUserSelectedJdkBinDirectoryToFile = true;
	
	/** 
	 * It switched on, temporary JAR files are generated for each folder on the 
	 * launchers classpath to allow queueing of multiple launchers from an automatically
	 * compiling IDE such as Eclipse or Netbeans.
	 * Should generally be set to true unless you want to minimize writing to disk.
	 * However, it set to false, only one launcher should be queued, else launchers
	 * may not run what you expected them to run!
	 * (Recommended: true)
	 */
	protected static boolean createTemporaryJarFilesForQueueing = true;
	
	/**
	 * If switched on, the ParallelLauncher uses a temporary classpath 
	 * variable instead of concatenating the classpath to the command line.
	 * This is particularly useful for long classpaths as it avoids the 
	 * 'input line too long' error on Windows systems.
	 * (Recommended: true)
	 */
	protected static boolean createTemporaryClasspathVariable = true;
	
	/**
	 * Temporary map to keep information on generated jar file names for 
	 * later clean up logging once process has been started
	 */
	private static LinkedHashMap<String, String> jarNameMapper = new LinkedHashMap<String, String>();
	
	/**
     * Constrain the number of cores the application uses. 
     * It is activated by default, and if no affinity mask is specified 
     * in {@link #processorsToRunOnAffinityMask}, ParallelLauncher will 
     * run on all but Core 0 in order to keep the system responsive.
     */
    protected static boolean runOnLimitedProcessors = true;
    
    /**
     * Processor affinity tool required for Windows OS below Windows Vista.
     */
    private static final String processorAffinityTool = "psexec.exe";
    
    /**
     * Comma-separated list of cores to be used by ParallelLauncher 
     * starting from 0 for first processor (e.g. 0,1,2 for first three processors).
     * Will be used if {@value #runOnLimitedProcessors} is set to true and 
     * running on an Windows operating system that does not have processor 
     * affinity support. In this case the tool {@value #processorAffinityTool} will 
     * be required in the project directory.
     * Is automatically set by {@link #setAffinityMaskForCores(boolean, int...)} method 
     * variants.
     */
    private static String processorsToRunOnForWindowsOsWithoutAffinitySupport = null;
    
    /**
     * Windows Vista and higher Processor Affinity Mask to restrict number of cores (hexadecimal information) 
     * used by ParallelLauncher if {@value #runOnLimitedProcessors} is set to true.
     * To configure it for your system, create a binary string each bit representing one core, 
     * from highest to lowest. (Example: 4 core machine, launcher should only use 3 core (all but core 0): 1110)
     * Then convert this binary string to hexadecimal value and assign to this field.
     * The direct use if discouraged in mixed environments with older Windows versions. 
     * Generally, and in those case in particular, the simplified assignment via 
     * {@link #setAffinityMaskForCores(boolean, int...)} method variants is recommended as those 
     * generate the affinity mask automatically and do so both for Windows affinity support as well legacy tool support.
     */  
  	protected static String processorsToRunOnAffinityMask = null;
  	
  	/**
  	 * Sets an affinity mask that specifies the run on all cores
  	 * but Core 0 (in order to keep system responsive). 
  	 * Does so both for Windows affinity support as well as 
  	 * legacy tool support via {@link #processorAffinityTool}.
  	 */
  	private static void setAffinityMaskForAllButCore0(){
  		setAffinityMaskForCores(false, 0);
  	}
  	
  	/**
  	 * Calculates and assigns affinity mask for processor cores.
  	 * Enter the desired core IDs as individual parameters (e.g. 0, 1). 
  	 * Note that core IDs are zero-based. Invalid Core IDs (e.g. negative, duplicate
  	 * values or exceeding available cores) are ignored.
  	 * It also sets the affinity for Windows OS that do not support affinity directly (such as Windows XP), 
  	 * and use the {@link #processorAffinityTool} instead.
  	 * Note: This method is the recommended way of de/selecting cores. 
  	 * Though discouraged in mixed Windows environments, direct assignment of an affinity mask can be 
  	 * achieved using the field {@link #processorsToRunOnAffinityMask}.
  	 * The method variant {@link #setAffinityMaskForCores(boolean, int...)} allows 
  	 * the specification of excluded cores as opposed to included ones.
  	 * @param coresToBeUsed individual integers indicating core IDs to be used by launcher
  	 */
  	protected static void setAffinityMaskForCores(int... coresToBeUsed){
  		setAffinityMaskForCores(true, coresToBeUsed);
  	}
  	
  	/**
  	 * Calculates and assigns affinity mask for processor cores.
  	 * Enter the core IDs to be in/excluded as individual parameters (e.g. 0, 1). 
  	 * Note that core IDs are zero-based. Invalid Core IDs (e.g. negative, duplicate
  	 * values or exceeding available cores) are ignored. 
  	 * The inclusion or exclusion is specified via useSpecifiedCoresElseDontUseSpecifiedCores parameter.
  	 * It also sets the affinity for Windows OS that do not support affinity directly (such as Windows XP), 
  	 * and use the {@link #processorAffinityTool} instead.
  	 * Note: This method is the recommended way of de/selecting cores. 
  	 * Though discouraged in mixed Windows environments, direct assignment of an affinity mask can be 
  	 * achieved using the field {@link #processorsToRunOnAffinityMask}.
  	 * @param useSpecifiedCoresElseDontUseSpecifiedCores If true, uses only specified cores. If false, uses 
  	 * 		all but specified cores.
  	 * @param coresToBeUsed individual integers indicating core IDs used or ignored by launcher
  	 */
  	protected static void setAffinityMaskForCores(boolean useSpecifiedCoresElseDontUseSpecifiedCores, int... coresToBeUsed){
  		int processors = Runtime.getRuntime().availableProcessors();
		StringBuilder bits = new StringBuilder();
		for(int i = 0; i < processors; i++){
			boolean appended = false;
			for(int j = 0; j < coresToBeUsed.length; j++){
				if(i == processors - 1 - coresToBeUsed[j]){
					if(useSpecifiedCoresElseDontUseSpecifiedCores){
						//use core
						bits.append("1");
					} else {
						//ignore core
						bits.append("0");
					}
					appended = true;
					break;
				}
			}
			if(!appended){
				if(useSpecifiedCoresElseDontUseSpecifiedCores){
					//ignore non-specified ones
					bits.append("0");
				} else {
					//use non-specified ones
					bits.append("1");
				}
			}
		}
		if(debug){
			System.out.println("ParallelLauncher: Assigned CPU affinity mask " + Long.toHexString(Long.parseLong(bits.toString(), 2)));
		}
		//assign affinity mask
		processorsToRunOnAffinityMask = Long.toHexString(Long.parseLong(bits.toString(), 2));
		
		//assign legacy affinity
		
		//filter invalid entries
		ArrayList<Integer> uniqueEntries = new ArrayList<>();
		if(!useSpecifiedCoresElseDontUseSpecifiedCores){
			//fill with all processor cores if cores to be excluded
			for(int i = 0; i < processors; i++){
				uniqueEntries.add(i);
			}
		}
		for(int i = 0; i < coresToBeUsed.length; i++){
			//if cores are to be included
			if(useSpecifiedCoresElseDontUseSpecifiedCores
				//must not exceed number of processors
				&& coresToBeUsed[i] <= (processors - 1)
			    //must be >= zero (not negative)
				&& coresToBeUsed[i] >= 0
				//must not be duplicate
				&& !uniqueEntries.contains(coresToBeUsed[i])){
					//add cores
					uniqueEntries.add(coresToBeUsed[i]);
			} else if(!useSpecifiedCoresElseDontUseSpecifiedCores){
				//cores are to be excluded
				uniqueEntries.remove(new Integer(coresToBeUsed[i]));
			}
		}
		//derive String version of parameters
		StringBuilder validLegacyEntries = new StringBuilder();
		for(int i = 0; i < uniqueEntries.size(); i++){
			if(validLegacyEntries.length() > 0){
				validLegacyEntries.append(",");
			}
			validLegacyEntries.append(uniqueEntries.get(i));
		}
		//assign final parameter for legacy affinity tool
		processorsToRunOnForWindowsOsWithoutAffinitySupport = validLegacyEntries.toString();
		if(debug){
			System.out.println(PREFIX + "Assigned legacy CPU affinity " + processorsToRunOnForWindowsOsWithoutAffinitySupport);
		}
  	}
	
  	/**
  	 * Specifies the number of ParallelLaunchers that can launch their 
  	 * classes in parallel (e.g. on machines that have more cores than
  	 * classes specified in launchers). (Default: 1)
  	 * Note: When increasing the number of active parallel launchers,
  	 * remember to configure {@link #maxNumberOfRunningLaunchedProcesses} 
  	 * in order to avoid an uncontrolled starting of processes.
  	 */
  	protected static Integer maxNumberOfActiveParallelLaunchers = 1;
  	
  	/**
  	 * Deactivates loading of configuration properties from config file if 
  	 * ParallelLauncher instance is started directly, i.e. not via MetaLauncher. Default: true
  	 * Overrides {@link #configFileCheckForLauncherConfiguration}.
  	 */
  	protected static boolean ignoreConfigFileIfStartingIndividualParallelLauncher = true;
  	
  	/**
  	 * Switch indicating that this instance has been started without MetaLauncher.
  	 * Is used in conjunction with {@link #ignoreConfigFileIfStartingIndividualParallelLauncher} 
  	 * to determine whether config files should be considered.
  	 */
  	private static boolean launcherStartedWithoutMetaLauncher = false;
  	
  	/**
  	 * Activates reading of configuration for maximum number of active 
  	 * parallel launchers and processes from config file. (Default: true)<BR>
  	 * Only works if {@link #ignoreConfigFileIfStartingIndividualParallelLauncher} 
  	 * is deactivated (false) if starting launcher individually (i.e. not via MetaLauncher). 
  	 * Otherwise, no config file check (or any other activity, such as deletion via
  	 * {@link #resetLauncherAndProcessConfigurationFilesAtStartup}) will occur.
  	 */
  	protected static boolean configFileCheckForLauncherConfiguration = true;
  	
  	/**
  	 * Activates continuous optimisation of queue check timing. If queueing multiple 
  	 * launchers of approximately same duration, individual launchers adapt check for
  	 * their queue position dynamically in order to minimize wait times by too long 
  	 * delays between checks.
  	 * Note: Dynamic adaptation only makes sense for multiple launchers whose longest-
  	 * running classes have a similar execution duration.
  	 */
  	protected static boolean adaptQueueCheckTimingDynamically = false;
  	
  	protected static boolean adaptiveQueueCheckActivatedAndDataAvailable(){
  		return adaptQueueCheckTimingDynamically 
  				&& lastLaunchersLongestProcessDuration != null
  				&& runningLauncherFirstProcessStartTime != null;
  	}
  	
  	/**
  	 * Tolerance added in adaptive queue time checks to account for 
  	 * variance in process launches
  	 */
  	private static float adaptiveCheckTolerance = 1.02f;
  	
  	/**
  	 * Duration of last launcher's longest-running process.
  	 */
  	protected static Long lastLaunchersLongestProcessDuration = null;
  	
  	/**
  	 * Start time of first launched class of currently running ParallelLauncher instance.
  	 */
  	protected static Long runningLauncherFirstProcessStartTime = null;
  	
  	/**
  	 * If set to true, it deletes existing launcher information configuration files at 
  	 * startup in order to rewrite it with newly specified values. 
  	 * If set to false and {@link #configFileCheckForLauncherConfiguration} 
  	 * is activated, the launcher instances will always pick up the configuration 
  	 * provided in the config file independent of its own configuration. 
  	 * (Default: false)
  	 * Is ignored if {@link #configFileCheckForLauncherConfiguration} is 
  	 * deactivated, or if instance has been started without MetaLauncher and 
  	 * {@link #ignoreConfigFileIfStartingIndividualParallelLauncher} is activated.
  	 */
  	protected static boolean resetLauncherAndProcessConfigurationFilesAtStartup = false;
  	
  	/**
  	 * Update configuration information from Runtime config file {@link #PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE}. 
  	 * Checks on maximum number of parallel launchers and processes from
  	 * configuration file as well as duration and start times of previous and 
  	 * launchers. Updates the following fields:<br>
  	 * - in ParallelLauncher: 
  	 * {@link #maxNumberOfActiveParallelLaunchers} 
  	 * {@link #maxNumberOfRunningLaunchedProcesses} 
  	 * {@link #lastLaunchersLongestProcessDuration} 
  	 * {@link #runningLauncherFirstProcessStartTime}<br>
  	 * - in MetaLauncher: 
  	 * maxNumberOfQueuedOrRunningLaunchersAtOneTime.
  	 * 
  	 * Note: It will only be executed if launcher instance has been started via 
  	 * MetaLauncher OR 
  	 * (not been started via MetaLauncher AND {@link #ignoreConfigFileIfStartingIndividualParallelLauncher} is false).
  	 * @return
  	 */
  	private static void updateLauncherAndProcessConfiguration(){
  		if((configFileCheckForLauncherConfiguration
  				|| adaptQueueCheckTimingDynamically)
  			//if started individually
			&& (!ignoreConfigFileIfStartingIndividualParallelLauncher
					&& launcherStartedWithoutMetaLauncher)
			//if started from MetaLauncher
			|| (!launcherStartedWithoutMetaLauncher)
			){
  			//Read and write actual configuration files
  			if(debug) {
  				System.out.println(PREFIX + "Reading/Updating config file " + PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE);
  			}
  			refreshRuntimeConfigFromConfigFiles(Launcher.PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE);
  		} else {
  			if(debug) {
  				System.out.println(PREFIX + "NOT reading config file " + PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE);
  				System.out.println("Possible reason: launcher started without meta launcher: " + launcherStartedWithoutMetaLauncher);
  				System.out.println("Ignoring config file if individual launcher: " + ignoreConfigFileIfStartingIndividualParallelLauncher);
  				System.out.println("Config file check: " + configFileCheckForLauncherConfiguration);
  			}
  		}
  		if(ignoreConfigFileIfStartingIndividualParallelLauncher
  				&& launcherStartedWithoutMetaLauncher && !ignoreConfigInfoMessagePrinted){
  			System.out.println(PREFIX + "Note that this launcher does not update the launcher config files." + System.getProperty("line.separator")
  					+ "Set ParallelLauncher.ignoreConfigFileIfStartingIndividualParallelLauncher to false in order to utilise config files if not starting launcher via MetaLauncher.");
  			ignoreConfigInfoMessagePrinted = true;
  		}
  		if(maxNumberOfActiveParallelLaunchers > 1 && maxNumberOfRunningLaunchedProcesses == -1){
  			System.err.println(PREFIX + "Warning: Multiple launchers can start in parallel (" + maxNumberOfActiveParallelLaunchers + "),"
  					+ System.getProperty("line.separator") + "but number of processes is not constrained. Can lead to uncontrolled starting of queued processes.");
  		}
  		//do nothing if configFileCheck... is deactivated
  	}
  	
  	/**
  	 * Switch to mark whether the information about the deactivated use of configuration files has been printed.
  	 */
  	private static boolean ignoreConfigInfoMessagePrinted = false;
  	
  	/**
  	 * Update information about duration of class launched by last-run launcher 
  	 * into configuration file {@link #PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE}.
  	 * @param durationOfLastProcess Duration of last process duration in milliseconds
  	 */
  	protected static void updateQueueCheckTimingConfiguration(long durationOfLastProcess){
  		updateRuntimeConfiguration(PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE, LAST_PROCESS_DURATION, String.valueOf(durationOfLastProcess));
  	}
  	
  	/**
  	 * Update information about the start time of the last class launched by the currently
  	 * launcher running launcher into configuration file {@link #PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE}.
  	 * @param startTimeOfCurrentProcess Start time of last launched class
  	 */
  	protected static void updateStartTimeConfiguration(long startTimeOfCurrentProcess){
  		updateRuntimeConfiguration(PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE, CURRENT_PROCESS_STARTTIME, String.valueOf(startTimeOfCurrentProcess));
  	}
  	
  	/**
  	 * Specifies the maximum number of processes concurrently running. 
  	 * If set to -1, all specified classes of a launcher will run, 
  	 * irrespective of available cores.
  	 */
  	protected static Integer maxNumberOfRunningLaunchedProcesses = -1;
  	
  	/**
	 * Activates check for other known process name to ensure that WMI is actually working when submitting queries 
	 * (important before starting new set of child processes). 
	 * Should be true (!) unless good reason not to (e.g. no known process to check) as WMI information is unreliable.
	 * Remember to specify 'knownRunningProcessName'.
	 */
	protected static boolean checkForKnownProcessAsWmiFailureBackupCheck = true;
	
	/**
	 * Process name(s) (executable) of continuously running process(es) when running ParallelLauncher.
	 * Those are used to ensure that the OS process querying (e.g., Windows Management Instrumentation (WMI)) works
	 * properly. Used in by {@link #checkForKnownProcessAsWmiFailureBackupCheck}.
	 * Good values could be the IDE you are using (e.g. "eclipse.exe") or generic OS services (e.g., "explorer.exe"
	 * on Windows, "gnome-shell" on Linux).
	 * Also supports partial names (e.g., "intellij"). Note that services should be OS-dependent. Check the
	 * corresponding task manager for processes.
	 * The evaluation requires the presence of at least one of the listed processes to continue execution.
	 */
	protected static ArrayList<String> knownRunningProcessNames = ProcessReader.runsOnWindows() ?
			new ArrayList<>(Arrays.asList("explorer.exe")):
			(ProcessReader.runsOnLinux() ?
					new ArrayList<>(Arrays.asList("gnome-shell", "equinox.launcher", "intellij")) :
					new ArrayList<>());
  	
	/**
	 * Indicates if batch files for creation of JARs are deleted after 
	 * start. Default: true
	 */
	protected static boolean deleteBatchFilesCreatingJarsAfterStart = true;
	
	/**
	 * Indicates if batch file for launching the processes are 
	 * deleted after execution of processes. Default: true
	 */
	protected static boolean deleteBatchFilesAfterStart = true;
	
	/**
	 * Keeps batch file window open after running it (for debugging). Effective in 
	 * combination with runJavaClassInBatchFile to show output from Java in console.
	 * See also {@link #logBatchFileExecution}.
	 */
	protected static boolean keepWindowOpen = false;
	
	/**
	 * Starts batch file windows minimized.
	 */
	protected static boolean startBatchProcessesMinimized = false;
	
    /**
     * Runs the launched Java class inside the batch file instead of opening a separate window.
     * Should be run in combination with {@link #keepWindowOpen}, so the window stays open after execution.
     */
    protected static boolean runJavaClassInBatchFile = false;
    
    /**
     * MetaLauncher IPC filename. Passed to ParallelLauncher as first argument if launched from 
     * MetaLauncher. File is used for IPC between ParallelLauncher and MetaLauncher during startup 
     * to assure successful startup before continuing launch of further instances.
     */
    private static String metaLauncherIpcFile = null;
    
    /**
     * Launch iteration for ParallelLauncher implementation. Passed along as second argument from 
     * MetaLauncher. Useful in output in order to distinguish different ParallelLauncher instances 
     * if multiple of same type are launched.
     */
    private static String launchedLauncherIteration = null;
	
    /**
     * Filename of unified JAR file for this ParallelLauncher. Passed along as third argument from 
     * MetaLauncher. If file exists, ParallelLauncher will use that instead of compiling an own one. 
     * If not found, ParallelLauncher will generate it.
     */
    private static String unifiedJarFilename = null;
    
    /**
     * If set to true, ParallelLauncher launches a separate GUI frame showing output and 
     * capturing input instead of showing it on the regular console (Setting: false). 
     * If ParallelLauncher is started from MetaLauncher, a separate Monitor GUI is always 
     * launched. Default: false
     */
    protected static boolean launchInputOutputMonitor = false;
    
    /**
     * If set to true, launches Monitor GUI instances minimized.
     */
    protected static boolean startInputOutputMonitorMinimized = false;
    
    /**
     * If set to true, the ParallelLauncher uses the arguments that are to be passed to 
     * launched classes (set via {@link #setArgumentsToBePassedToLaunchedClasses(String[])}) 
     * as identifier for the Process Monitor GUI title (along with launcher simple class name), 
     * in addition to iteration counter passed down from MetaLauncher.
     * Default: true
     */
    protected static boolean useArgumentsToBePassedToInstancesAsMonitorGuiIdentifier = true;
    
	/**
	 * No parameters necessary, but classesToBeLaunched should contain executable classes.
	 * Example for implementation: Create class with main method, extending this class. Fill classesToBeLaunched 
	 * with self-contained executable classes, then run created extension.
	 * @param args Unique IPC filename of the MetaLauncher calling this launcher (optional). To 
	 * ensure it is operational, use the {@link #start(String[])} method (with parameters) 
	 * to initiate launching of processes as it passes the MetaLauncher IPC filename along.
	 */
	public static void main(String[] args) {
		
		detectLauncherClass(ParallelLauncher.class);
		
		loadConfigEntryHandler();
		
		if(args.length == 2 || args.length == 3){
			// Meta launcher IPC filename
			metaLauncherIpcFile = args[0];
			// Launch iteration (if multiple launchers of this type are launched) - used for MonitorGui
			launchedLauncherIteration = args[1];
			if(debug){
				System.out.println(PREFIX + "Specified MetaLauncher with IPC file " 
						+ metaLauncherIpcFile + "; Instance " + launcherClass.getSimpleName() + " " + launchedLauncherIteration);
			}
			if(args.length == 3){
				//MetaLauncher passes unified JAR file for all ParallelLauncher instances
				unifiedJarFilename = args[2];
				System.out.println(PREFIX + "MetaLauncher requested use of unified JAR file '" + unifiedJarFilename + "' instead of compiling individually.");
			}
		} else {
			if(args.length != 0){
				System.err.println(PREFIX + "Invalid number of arguments passed to ParallelLauncher. Ignored those, continuing launch ...");
			}
			// Remember that this instance has been started without MetaLauncher - important if ignoring config file is activated
			launcherStartedWithoutMetaLauncher = true;
		}
		// Launch MonitorGui if either requested or started via MetaLauncher - given that a graphics environment is available
		if((args.length == 2 || args.length == 3 || launchInputOutputMonitor) && !GraphicsEnvironment.isHeadless()){
			new ProcessMonitorGui(launcherClass.getSimpleName()
					// Append counter to Process Monitor Gui title if launched from MetaLauncher
					+ (launchedLauncherIteration != null ? " " + launchedLauncherIteration : "")
					// Append passed arguments to Process Monitor Gui title (if activated and not null)
					+ ((useArgumentsToBePassedToInstancesAsMonitorGuiIdentifier  
							&& argumentsToBePassedToLaunchedClasses != null 
							&& argumentsToBePassedToLaunchedClasses.length > 0) ? 
						(" (" + expandArgumentsIntoCmdLineString() + ")") : ""), 
					startInputOutputMonitorMinimized
				);
		} else {
			if(GraphicsEnvironment.isHeadless()){
				System.err.println(PREFIX + "Runtime environment is headless. No Process Monitor GUI started.");
			}
		}
		
		
		// Check for valid number of cores to be run in parallel
		if(maxNumberOfActiveParallelLaunchers < 1){
			throw new RuntimeException(PREFIX + "Maximum number for ParallelLaunchers to be active in parallel is invalid: " + maxNumberOfActiveParallelLaunchers);
		}
		
		/*
		 * Register global service handler. 
		 * It is a listener implementation that monitors the processes 
		 * lifetimes and enables adaptive queue timing optimisation (if activated)
		 * and cleanup operation after process execution. 
		 * It is also used to limit the number of running processes if 
		 * those are constrained via #maxNumberOfRunningProcesses. 
		 */
		ServiceHandler serviceHandler = new ServiceHandler();
		setGlobalProcessStatusListener(serviceHandler);
		
		/*
		 * Delays the startup of subsequent processes to ensure initializing batch files has been executed completely to avoid
		 * premature deletion. Recommended! This is particularly an issue for more memory-intensive applications or 
		 * applications that require a long time for initialization for any other reason.
		 */
		final boolean delay = true;
		/*
		 * Frequency of checking whether child processes of previous launcher are running.
		 */
		final int processCheckFrequencyForOtherLaunchersRunningProcesses = (queueCheckFrequency == null ? (60000 * 3) : queueCheckFrequency);
		/*
		 * Frequency of checking status of preceding launcher in queue - 
		 * should occur significantly less frequent as quite some WMI interaction involved.
		 */
		final int launcherCheckFrequency = (queueCheckFrequency == null ? (60000 * 4) : queueCheckFrequency);
		
		/*
		 * Checks if all dependencies are available.
		 */
		checkForDependencies();
		
		/*
		 * Checks for processor affinity support
		 */
		// Check for existence of psexec.exe if limited processors are activated (including simple checks on processor specification
		if(runOnLimitedProcessors){
			if(ProcessReader.runsOnWindows() && Runtime.getRuntime().availableProcessors() > 1){
				if(!ProcessReader.runsWindowsVistaAndHigher()){
	                if(validCoreSpecificationForPsExec()){
						if(!new File(processorAffinityTool).exists()){
							throw new RuntimeException(PREFIX + "Cannot configure processor affinity as the tool '" + processorAffinityTool + "' could not be found "  
									+ System.getProperty("line.separator") + "in user/program directory " + "(" + System.getProperty("user.dir") + ")."
									+ System.getProperty("line.separator") + "It is part of the Windows Sysinternals suite and should be available under " 
									+ System.getProperty("line.separator") + "http://technet.microsoft.com/en-us/sysinternals/bb897553");
						} else {
							System.out.println(PREFIX + "Using processor affinity support for Windows OS without native command line processor affinity support (" + System.getProperty("os.name") + ").");
						}
	                } else {
	                    System.err.println(PREFIX + "Invalid processor specification '" + processorsToRunOnForWindowsOsWithoutAffinitySupport + "' for processor affinity tool. Affinity configuration ignored.");
	                    runOnLimitedProcessors = false;
	                }
				} else {
					System.out.println(PREFIX + "Using native processor affinity support of " + System.getProperty("os.name") + ".");
					if(processorsToRunOnAffinityMask == null){
						System.out.println(PREFIX + "Running on all cores other than Core 0");
						setAffinityMaskForAllButCore0();
					}
				}
			} else if (ProcessReader.runsOnLinux()){
				// Deactivate process affinity support on Linux
				System.out.println(PREFIX + "Processor affinity support deactivated on Linux.");
				// TODO: Introduce affinity for Linux
				runOnLimitedProcessors = false;
			} else {
				// Deactivate process affinity support if only one processor
				System.out.println(PREFIX + "Processor affinity support deactivated as only one available core.");
				runOnLimitedProcessors = false;
			}
		}
		
				
		String classpath = System.getProperty("java.class.path");
		// Opens self-closing console if not running on Windows Vista/7
		final boolean openConsoleWindowIfNotUsingWindowsVistaAndHigher = false;
		// Opens console window (non-self-closing on Windows 7 (Vista?)) on all systems
		final boolean openConsoleWindow = false;
		
		/*
		 * Basic validation of classes to be launched in parallel
		 */
		
		// Check if there is anything to launch in the first place
		if(classesToBeLaunched.isEmpty() && !launcherClass.equals(BlockingParallelLauncher.class)){
			System.out.println(PREFIX + "Nothing to be launched. Exiting ...");
			if(launcherClass.equals(ParallelLauncher.class)){
				System.err.println(PREFIX + "You cannot start ParallelLauncher directly, "
						+ "but need to implement a subclass to schedule classes to be launched." + System.getProperty("line.separator")
						+ "See the examples in the sub-package 'examples' or consult the javadocs of ParallelLauncher.");
			}
			return;
		}
		
		// Check if classes to be run have main methods....
		for(int i = 0; i < classesToBeLaunched.size(); i++){
			try {
				classesToBeLaunched.get(i).clazz.getMethod("main", String[].class);
			} catch (NoSuchMethodException e) {
				System.err.println(PREFIX + "Class " + classesToBeLaunched.get(i).clazz.getCanonicalName() + " is not executable. It will be removed from the list of executed classes.");
				classesToBeLaunched.remove(i);
				i--;
			} catch (SecurityException e) {
				e.printStackTrace();
			}
		}
		
		// Delete config file if requested (unless ignored for individual launcher anyway), and thus use in-launcher configuration
		if(!(launcherStartedWithoutMetaLauncher && ignoreConfigFileIfStartingIndividualParallelLauncher) 
				&& configFileCheckForLauncherConfiguration && resetLauncherAndProcessConfigurationFilesAtStartup){
			FileUtils.deleteQuietly(new File(PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE));
		}
		
		// Check if processor-selection makes sense
		if(maxNumberOfRunningLaunchedProcesses > Runtime.getRuntime().availableProcessors()
				&& classesToBeLaunched.size() > Runtime.getRuntime().availableProcessors()){
			System.err.println(PREFIX + "Your system is configured to launch more processes " 
				+ System.getProperty("line.separator") + "(To be launched: " + classesToBeLaunched.size() 
				+ ", maximum permissible: " + maxNumberOfRunningLaunchedProcesses + ") than it has cores ("
				+ Runtime.getRuntime().availableProcessors() + ")."
				+ System.getProperty("line.separator")+ "This is not a problem, but it might lead to sub-optimal performance. Specifically watch your memory consumption.");
		}
		
		// Print current configuration to console - passing information for actual launcher check frequency, and status of child processes of previous launcher
		printCurrentConfiguration(launcherCheckFrequency, processCheckFrequencyForOtherLaunchersRunningProcesses);
		
		/*
		 * Creation of temporary JARs
		 */
		// Check if the launcher is supposed to build JAR files from class files to prevent side effects.
		if(createTemporaryJarFilesForQueueing && !launcherClass.equals(BlockingParallelLauncher.class)){
			if(createTemporaryClasspathVariable){
				// local-variable version (set CLASSPATH=%CLASSPATH%;newJarFile.jar)
				classpath = createJARifiedClasspath(classpath, unifiedJarFilename, true);
			} else {
				// parameter line version (-classpath <classpath>)
				classpath = createJARifiedClasspath(classpath, unifiedJarFilename, false);
			}
		}
	
		// Build command (incl. classpath) but without launched class specification - note the different quotation marks to capture space issues
		// see http://stackoverflow.com/questions/12891383/correct-quoting-for-cmd-exe-for-multiple-arguments for details on cmd /C syntax
		// CF: 20131026 - Deactivated differentiated quoting as single-quoting seems best...
		boolean simpleQuotes = true;//(jdkBinPath == null);
		// Use doublequotes if explicit JDK path (which potentially has white spaces in it) is required
		String javaExeCommand = (simpleQuotes ? "java" : "\"\"" + buildJdkBinDirectoryString() + "java\"\"");
		final String command = javaExeCommand 
				// Is classpath passed as parameter instead of local variable?
				+ (!createTemporaryClasspathVariable ? " -classpath " + (simpleQuotes ? "\"": "\"\"") + classpath + (simpleQuotes ? "\"": "\"\"") : "") 
				+ " ";
		
		/*
		 * All preparation done. Now the queue checks start.
		 */
		
		/*
		 * 1st Step - waiting for other preceding launchers. If more than one launcher allowed in parallel
		 * 		      and queue position permits inclusion, progress to process check. 
		 */
		int launchersQueuedBeforeMe = myTurnInRunning();
		// Check whether the process been properly registered in WMI (i.e. return value != -1)? If so, status will not be rewritten later.
		boolean validOSProcessExecutionFeedback = launchersQueuedBeforeMe != -1;
		// Write to IPC file if existing
		writeStatusToMetaLauncherIpcFile(launchersQueuedBeforeMe);
		
		if(debug){
			System.out.println(PREFIX + "Max. number of allowed active launchers: " + maxNumberOfActiveParallelLaunchers + ", number in queue: " + launchersQueuedBeforeMe);
		}
		
		// Update eventual configuration before initial check
		updateLauncherAndProcessConfiguration();
		
		// -1 indicates WMI problem, -2 indicates running blocking launcher
		while(launchersQueuedBeforeMe == -1 || launchersQueuedBeforeMe == -2 || launchersQueuedBeforeMe > maxNumberOfActiveParallelLaunchers - 1){
			try {
				/*
				 * Let queued launchers wait with linear increment in sleep time for queue position
				 * (use Math.abs() as value for launchers can be -1).
				 * also consider potentially parallel running launchers (which reduce runtime considerably),
				 * so divide number of preceding queued launchers by max number of active launchers
				 */
				int actualSleepTime = Math.abs(new Float(launcherCheckFrequency * 
						(launchersQueuedBeforeMe / (float)maxNumberOfActiveParallelLaunchers)).intValue());
				if(adaptiveQueueCheckActivatedAndDataAvailable()){
					if(launchersQueuedBeforeMe > 0){
						// Currently running launcher's project remaining runtime
						long runningLaunchersRemainingRuntime = lastLaunchersLongestProcessDuration - (System.currentTimeMillis() - runningLauncherFirstProcessStartTime);
						// only use remaining runtime information if greater than 0, else it may be old launcher start time, so ignore it
						actualSleepTime = Math.round(((runningLaunchersRemainingRuntime > 0 ? runningLaunchersRemainingRuntime : 0)
								+ launchersQueuedBeforeMe * lastLaunchersLongestProcessDuration)
								// add some tolerance
								* adaptiveCheckTolerance);
						if(debug){
							System.out.println(PREFIX + "Adapted queue check delay to " + actualSleepTime + " ms.");
						}
					}
				}
				if(launcherClass.equals(BlockingParallelLauncher.class)){
					System.err.println("BLOCKING LAUNCHER: Blocking all other launchers from starting. Stop me in order to let others proceed.");
					// Sleep forever
					Thread.sleep(Long.MAX_VALUE);
				} else {
					System.out.println(getCurrentTimeString(true) 
							+ ": Waiting for other launcher(s) to start. Next check in " 
							+ actualSleepTime/1000 + " seconds.");
					// Check if user presses enter to initiate recheck
					int res = awaitUserInput(actualSleepTime, "to perform recheck", "debug", "toggle debug mode and perform recheck");
					// Toggle debug mode if requested
					if(res == 2){
						toggleDebugMode();
					}
				}
			} catch (InterruptedException e) {
				// if thread sleep makes problems
				e.printStackTrace();
			}
			// Check for eventual configuration updates
			updateLauncherAndProcessConfiguration();
			// Check again after waiting
			launchersQueuedBeforeMe = myTurnInRunning();
			/*
			 * Check for WMI registration (if not happened before) -
			 * Once VALID WMI status has been received (i.e. not -1), write to IPC file - and never again after that
			 */
			if(!validOSProcessExecutionFeedback){
				// Test again and update status
				validOSProcessExecutionFeedback = launchersQueuedBeforeMe != -1;
				// Write current state to IPC file
				writeStatusToMetaLauncherIpcFile(launchersQueuedBeforeMe);
			}
		}
		if(debug && maxNumberOfActiveParallelLaunchers > 1){
			System.out.println(PREFIX + "Multiple launchers allowed. Currently queued before me: "
					+ launchersQueuedBeforeMe + ", max. allowed: " + maxNumberOfActiveParallelLaunchers);
		}
		
		/*
		 * 2nd Step - Wait for child processes of previous launcher to finish before starting own ones,
		 * 			  or check if enough slots for further child processes before starting second launcher
		 */
		if(debug){
			StringBuffer configOutput = new StringBuffer();
			configOutput.append(PREFIX + "Max. number of active launchers: ")
				.append(maxNumberOfActiveParallelLaunchers)
				.append(System.getProperty("line.separator"));
			configOutput.append(PREFIX + "Max. number of active processes: ")
				.append(maxNumberOfRunningLaunchedProcesses)
				.append(System.getProperty("line.separator"));
			System.out.println(configOutput);
		}
		List<String> runningProcessesRunningJavaClasses = processReader.retrieveProcessesRunningJavaClasses(classesToBeTestedInRunningProcesses);
		if(debug){
			System.out.println(PREFIX + "Number of currently running processes: " + runningProcessesRunningJavaClasses.size());
		}
		
		if (knownRunningProcessNames == null) {
			throw new RuntimeException(PREFIX + "Cannot test for running reference process, since OS could not be detected.");
		}
		
		while(	/*
					Checks for number of running processes (if multiple launchers allowed);
					also checking for known process name (such as eclipse.exe) as running process if check is activated, 
					just to be sure WMI (if running Windows) is doing its job (implying that the actual process needs to be running!)
				*/
				(
					// If more than one launcher allowed,
						(maxNumberOfActiveParallelLaunchers > 1 && maxNumberOfRunningLaunchedProcesses != -1
						// check if enough 'space' for parallel processes before launching
						&& (runningProcessesRunningJavaClasses.size() + classesToBeLaunched.size()) > maxNumberOfRunningLaunchedProcesses)
					// or, if only one launcher allowed,
					|| (maxNumberOfActiveParallelLaunchers == 1
						// ensure that no further running classes before starting
						&& !runningProcessesRunningJavaClasses.isEmpty())
				)
				// and check if known process is found (if check is activated) - SHOULD be found if activated
				|| (checkForKnownProcessAsWmiFailureBackupCheck ? 
						processReader.retrieveProcessesWithNames(knownRunningProcessNames).isEmpty() : false)
		){
			String reason = null;
			// Determine reason for wait
			if((maxNumberOfActiveParallelLaunchers > 1 && maxNumberOfRunningLaunchedProcesses != -1
					// Check if enough 'space' for parallel processes before launching
					&& (runningProcessesRunningJavaClasses.size() + classesToBeLaunched.size()) > maxNumberOfRunningLaunchedProcesses)){
				reason = "Reason for wait: Not enough slots to launch " + classesToBeLaunched.size() 
						+ " classes. Max number of allowed processes: " + maxNumberOfRunningLaunchedProcesses;
			} else if((maxNumberOfActiveParallelLaunchers == 1
					// Ensure that no further running classes before starting
					&& !runningProcessesRunningJavaClasses.isEmpty())){
				reason = "Reason for wait: Processes of previous launcher still seem to be running (" 
					+ runningProcessesRunningJavaClasses.size() + " processes)";
			} else if((checkForKnownProcessAsWmiFailureBackupCheck ? 
					processReader.retrieveProcessesWithNames(knownRunningProcessNames).isEmpty() : false)){
				reason = "Reason for wait: Check for backup process '" + knownRunningProcessNames
						+ "' failed. OS-specific process retrieval not functioning properly?";
			}
				
			if(debug){
				System.out.println(PREFIX + "Need to wait for processes to finish...");
				if(classesToBeLaunched.size() != classesToBeTestedInRunningProcesses.size()){
					System.out.println("Tested for " 
							+ classesToBeTestedInRunningProcesses.size() 
							+ ", but will only launch " + classesToBeLaunched.size());
				}
				System.out.println(PREFIX + "Max. number of active launchers: " + maxNumberOfActiveParallelLaunchers);
				System.out.println(PREFIX + "Number of running processes: " 
						+ runningProcessesRunningJavaClasses.size() 
						+ ", max. allowed: " + maxNumberOfRunningLaunchedProcesses);
				if(debug){
					if(!runningProcessesRunningJavaClasses.isEmpty()){
						System.out.println(PREFIX + "Found processes: " + DataStructurePrettyPrinter.decomposeRecursively(runningProcessesRunningJavaClasses, null));
					}
				}
			}
			// Introduce delay when queued depending on number of allowed parallel launchers to avoid overuse
			int progressiveCheckDelay = (maxNumberOfActiveParallelLaunchers + 1) * processCheckFrequencyForOtherLaunchersRunningProcesses;
			//System.out.println("Number of classes: " + runningProcessesRunningJavaClasses.size() + ": " + DataStructurePrettyPrinter.decomposeRecursively(runningProcessesRunningJavaClasses, null));
			
			if(adaptiveQueueCheckActivatedAndDataAvailable()){
				// currently running launcher's project remaining runtime
				long runningLaunchersRemainingRuntime = lastLaunchersLongestProcessDuration - (System.currentTimeMillis() - runningLauncherFirstProcessStartTime);
				// only use remaining runtime information if greater than 0, else it may be old launcher start time, so ignore it
				if(runningLaunchersRemainingRuntime > 0){
					progressiveCheckDelay = Math.round(runningLaunchersRemainingRuntime * adaptiveCheckTolerance);
					if(debug){
						System.out.println(PREFIX + "Adapted queue check delay to " + progressiveCheckDelay + " ms.");
					}
				}
			}
			
			System.out.println(getCurrentTimeString(true) + ": Waiting before performing recheck. Next check in " + (progressiveCheckDelay / 1000) + " seconds. (" + reason + ")");
			// Pressing enter will abort timeout and recheck immediately
			int res = awaitUserInput(progressiveCheckDelay, "recheck immediately", "debug", "toggle debug mode and recheck immediately");
			if(res == 2){
				toggleDebugMode();
			}
			
			// Check if launcher and process configuration has been updated
			updateLauncherAndProcessConfiguration();
			// Retrieve all running Java classes to recheck conditions for launching
			runningProcessesRunningJavaClasses = processReader.retrieveProcessesRunningJavaClasses(classesToBeTestedInRunningProcesses);
			if(debug){
				System.out.println(PREFIX + "Number of currently running processes: " + runningProcessesRunningJavaClasses.size());
			}
		}
		if(debug && maxNumberOfActiveParallelLaunchers > 1){
			System.out.println(PREFIX + "Enough slots to start my own " + classesToBeLaunched.size() 
					+ " processes in addition to " + runningProcessesRunningJavaClasses.size() 
					+ " running processes (max. allowed: " + maxNumberOfRunningLaunchedProcesses + ").");
			System.out.println(PREFIX + "Running processes: " + DataStructurePrettyPrinter.decomposeRecursively(runningProcessesRunningJavaClasses, null));
		}
		
		// Remove old temporary JAR files of previous launchers if existing
		cleanUpTemporaryJarFiles();
				
		
		/*
		 * Preparing processor affinity-related command line prefixes
		 * 
		 * Information on processor affinity:
		 * Windows 7:
		 * http://stackoverflow.com/questions/7759948/set-affinity-with-start-affinity-command-on-windows-7
		 * Windows XP:
		 * http://stackoverflow.com/questions/827754/how-to-set-processor-affinity-from-batch-file-for-windows-xp?rq=1
		 */
		final String processorAffinityPrefix = processorAffinityTool + " -a " + processorsToRunOnForWindowsOsWithoutAffinitySupport + " ";// + " /c ";
		//start in background window with given affinity
		final String processorAffinityPrefixWindowsVistaAndHigher = "cmd /C start " +
																		//optionally start processes in minimized window
																		(startBatchProcessesMinimized ? "/MIN " : "") + 
																		"/WAIT /AFFINITY " + processorsToRunOnAffinityMask + " ";
		
		/*
		 * Starting parallel processes
		 */
		
		/*
		 * Prepare fixed list of classes to be actually launched. Does not use classesToBeLaunched 
		 * directly as this is modified (i.e. entries deleted) once listeners are called in order 
		 * to maintain the association between classes and their individual listeners 
		 * (Recall that ParallelLauncher can start multiple instance of the same class, but with 
		 * respective different listeners.)
		 */
		final ArrayList<Class> listOfClassesActuallyLaunched = new ArrayList<>();
		
		for(int i = 0; i < classesToBeLaunched.size(); i++){
			listOfClassesActuallyLaunched.add(classesToBeLaunched.get(i).clazz);
		}
		
		// Counter for launched classes
		int launchCt = 0;
		
		for(Class classToBeLaunched: listOfClassesActuallyLaunched){
			System.out.println(getCurrentTimeString(true) + ": Attempting to start instance '" + classToBeLaunched.getSimpleName() + "'.");
			Process launchedClassProcess = null;
			// Wrapper for launched process
			ProcessWrapper wrapper = null;
			
			// Generate OS-dependent launch script
			File scriptFile = runLaunchScriptGeneration(classToBeLaunched, classpath, javaExeCommand, openConsoleWindow, openConsoleWindowIfNotUsingWindowsVistaAndHigher, processorAffinityPrefixWindowsVistaAndHigher, processorAffinityPrefix);
			
			
			// Wait for batch file to be created (in case of delayed execution)
			while(!scriptFile.exists()){
				try {
					System.out.println(PREFIX + "Waiting for batch file '" + scriptFile.getName() + "' to be created ...");
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			// Run script file
			try {
				String startCommand;
				if (ProcessReader.runsOnLinux()) {
					// Use -hold to keep window open after finishing run (debug)
					if (keepWindowOpen) {
						startCommand = "xterm -hold -e bash " + scriptFile.getAbsolutePath() + " &";
					} else {
						startCommand = "xterm -e bash " + scriptFile.getAbsolutePath() + " &";
					}
				} else if (ProcessReader.runsOnWindows()) {
					startCommand = /*(runOnLimitedProcessors ? 
							//prepares affinity for Windows Vista and higher
							(WmicReader.runsWindowsVistaAndHigher() ? (validAffinityMaskSpecification() ? processorAffinityPrefixWindowsVistaAndHigher : "") : 
							//prepares prefix for psexec (Windows XP and others)
							(validCoreSpecificationForPsExec() ? processorAffinityPrefix : "")) : "") 
						//appends actual script file
						+*/ 
						scriptFile.getAbsolutePath();
				} else {
					throw new RuntimeException(PREFIX + "Attempting to run ParallelLauncher on unknown OS.");
				}
				
				if(logBatchFileExecution){
					startCommand += " > " + scriptFile.getName().substring(0, scriptFile.getName().indexOf(LAUNCH_SCRIPT_FILE_ENDING));
				}
				if(debug){
					System.out.println(getCurrentTimeString(true) + ": Running command: " + startCommand);
				}
				ProcessBuilder pb = new ProcessBuilder(tokenizeCommandStringToArrayList(startCommand));
				launchedClassProcess = pb.start();
				// Execute all registered listeners upon start (and register listeners for process termination)
				wrapper = new ProcessWrapper(classToBeLaunched.getSimpleName(), launchedClassProcess, ParallelLauncher.class); 
				executeListeners(wrapper, classToBeLaunched);
				
				if(debug){
					System.out.println(getCurrentTimeString(true) + ": Started in process " + launchedClassProcess.toString());
				}
				if(delay){
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				try{
					// that call should throw exception - then the process is running.
					int exitVal = launchedClassProcess.exitValue();
					System.out.println(getCurrentTimeString(true) + ": Execution of batch file spawning process '" + classToBeLaunched.getSimpleName() + "' has finished. Exit code: " + exitVal);
					if(exitVal != 0){
						throw new RuntimeException(PREFIX + "Unexpected response for Process " + classToBeLaunched.getSimpleName() + ": " + printProcessOutput(launchedClassProcess, scriptFile.getAbsolutePath(), true));
					}
				} catch(IllegalThreadStateException ex){
					// no output necessary, ProcessWrappers and respective listeners should take care of that.
				}
				if(deleteBatchFilesAfterStart){
					// registering launch batch file for deletion
					wrapper.registerScriptFileToBeDeletedAfterProcessTermination(scriptFile);
				}
			} catch (IOException e) {
				// Some drama occurred. Most likely space issues or device failure
				e.printStackTrace();
				System.err.println(getCurrentTimeString(true) + ": Problems launching process. Check for available harddrive space!");
			}
			
			// Increase launch counter
			launchCt++;
			
			// Check for number of permissible running processes before starting further ones within this launcher (if there are further to be launched)
			if(maxNumberOfRunningLaunchedProcesses != -1){
				while((serviceHandler.getNumberOfRunningProcesses() >= maxNumberOfRunningLaunchedProcesses) 
						&& (listOfClassesActuallyLaunched.size() - launchCt) > 0){
					StringBuilder builder = new StringBuilder(getCurrentTimeString(true)).append(": Waiting for at least ") 
								.append(((serviceHandler.getNumberOfRunningProcesses() - maxNumberOfRunningLaunchedProcesses) + 1))
								.append(" process(es) to finish before starting further ones (Number of currently running processes: ")
								.append(serviceHandler.getNumberOfRunningProcesses()) 
								.append(", Number of processes still to be run by this launcher: ")
								.append((listOfClassesActuallyLaunched.size() - launchCt)).append(").")
								.append(System.getProperty("line.separator"))
								.append("Next check in ").append((processCheckFrequencyForProcessesStartedByLauncher / 1000))
								.append(" seconds.");
					System.out.println(builder);
					int res = awaitUserInput(processCheckFrequencyForProcessesStartedByLauncher, "recheck immediately", "debug", "toggle debug mode and recheck");
					if(res == 2){
						toggleDebugMode();
					}
					//Note: Alternative to waiting: register listener with service handler to be notified once process finishes
				}
			}
		}
		
		/*
		 * Clean up-related activities once all processes have been started
		 */
		// Eventually write temporary Jar files into delete log once started, so they can be safely deleted by the next launcher
		if(!jarNameMapper.isEmpty()){
			try {
				FileUtils.writeLines(new File(CLEANUP_LOG_FILE), jarNameMapper.values(), true);
			} catch (IOException e) {
				System.err.println(PREFIX + "Error when writing to clean up log file.");
				e.printStackTrace();
			}
		}
		// Final output indicating that launcher is done - all necessary operations (spawning of child processes, writing log information) has finished
		System.out.println(getCurrentTimeString(true) + ": All parallel processes have been spawned. For information on their runtime status, please check their respective consoles.");
		
		/*
		 * Don't explicitly terminate here or else we lose output from asynchronously terminating threads
		 * Let the ServiceHandler take care of that.
		 */
	}
	
	/**
	 * Indicates whether stdout AND stderr output of launched processes should be 
	 * redirected to file.
	 */
	public static boolean redirectStdOutAndStdErrForLaunchedProcesses = false;
	
	/**
	 * Switch to indicate if stderr output of launched processes should be 
	 * redirected to file.
	 */
	public static boolean redirectStdErrForLaunchedProcesses = false;
	
	/**
	 * Subfolder (relative to user directory) in which stdout and stderr redirection 
	 * files should be stored.
	 */
	public static String subfolderForStdOutAndStdErrRedirections = null;
	
	/**
	 * Start time of this ParallelLauncher instance (in WMI).
	 */
	private static String myStartTime = null;
	
	/**
	 * If activated, the execution of batch files is logged for debugging purposes
	 */
	protected static boolean logBatchFileExecution = false;
	
    /**
     * Checks for valid specification of cores for psexec tool. 
     * Automatically generates parameters if not specified yet.
     * @return 
     */
    private static boolean validCoreSpecificationForPsExec(){
        if(runOnLimitedProcessors && processorsToRunOnForWindowsOsWithoutAffinitySupport == null){
        	System.out.println(PREFIX + "Running on all cores other than Core 0");
        	setAffinityMaskForAllButCore0();
        }
    	//very simple check on valid specification of cores - TODO to be extended to check for format 1,2,3 etc.
        return (processorsToRunOnForWindowsOsWithoutAffinitySupport != null && !processorsToRunOnForWindowsOsWithoutAffinitySupport.isEmpty());
    }
    
    /**
     * Checks for valid affinity mask specification (for Windows 7 and higher)
     */
    private static boolean validAffinityMaskSpecification(){
        //very simple check on not-null and being filled - TODO to be extended for better semantic checks
        return (processorsToRunOnAffinityMask != null && !processorsToRunOnAffinityMask.isEmpty());
    }
    
    /**
     * Prints the current configuration to console.
     * @param integers Custom values relevant for configuration output, determined at runtime
     * Currently: 
     * integers[0] - actual queue check delay for launchers
     * integers[1] - Check delay for processes started by previous launcher
     */
    private static void printCurrentConfiguration(Integer... integers){
    	String bar = "-------------------------------------------------------------------";
    	StringBuffer configOutput = new StringBuffer();
    	configOutput.append("ParallelLauncher Configuration for launcher '").append(launcherClass.getSimpleName()).append("':").append(System.getProperty("line.separator"));
    	configOutput.append(bar).append(System.getProperty("line.separator"));
    	configOutput.append("Max. Number of Active Launchers: ").append(maxNumberOfActiveParallelLaunchers).append(System.getProperty("line.separator"));
    	configOutput.append("Max. Number of Running Processes: ").append(maxNumberOfRunningLaunchedProcesses).append(System.getProperty("line.separator"));
    	if(metaLauncherIpcFile != null){
    		//started via MetaLauncher
    		configOutput.append("MetaLauncher ID: ").append(metaLauncherIpcFile);
    		if(configFileCheckForLauncherConfiguration) {
    			configOutput.append(", using config file '").append(PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE)
    				.append("' for runtime configuration.").append(System.getProperty("line.separator"));
    			configOutput.append("Reset of config file during start: ").append(resetLauncherAndProcessConfigurationFilesAtStartup);
    		} else {
    			configOutput.append(", deactivated use of config file for runtime configuration.");
    		}
    	} else {
    		//started as individual launcher
    		configOutput.append("Started as individual ParallelLauncher");
    		if(configFileCheckForLauncherConfiguration 
    				&& !ignoreConfigFileIfStartingIndividualParallelLauncher){
    			configOutput.append(", using config file '").append(PARALLEL_LAUNCHER_RUNTIME_CONFIG_FILE)
					.append("' for runtime configuration.").append(System.getProperty("line.separator"));
    			configOutput.append("Reset of config file during start: ").append(resetLauncherAndProcessConfigurationFilesAtStartup);
    		} else if(configFileCheckForLauncherConfiguration 
    				&& ignoreConfigFileIfStartingIndividualParallelLauncher){
    			configOutput.append(", ignoring config file for runtime configuration");
    		} else {
    			//config file check must be deactivated anyway
    			configOutput.append(", deactivated use of config file for runtime configuration.");
    		}
    		configOutput.append(System.getProperty("line.separator"));
    		configOutput.append("Start of Monitor GUI: ").append(launchInputOutputMonitor);
    	}
    	configOutput.append(System.getProperty("line.separator"));
    	//Debug-related
    	configOutput.append("Debug mode ").append(debug ? "activated" : "deactivated").append(System.getProperty("line.separator"));
    	configOutput.append("Logging output from process-launching batch files: ").append(logBatchFileExecution).append(System.getProperty("line.separator"));
    	configOutput.append("Deletion of batch files used for JAR generation ").append(deleteBatchFilesCreatingJarsAfterStart ? "activated" : "deactivated")
    		.append(System.getProperty("line.separator"));
    	configOutput.append("Deletion of batch files starting processes ").append(deleteBatchFilesAfterStart ? "activated" : "deactivated")
    		.append(System.getProperty("line.separator"));
    	//JDK and compilation stuff
    	configOutput.append("JDK path: ").append(jdkBinPath == null ? "<to be determined>" : jdkBinPath).append(System.getProperty("line.separator"));
    	configOutput.append("Using unified JAR file for all launched processes: ").append(unifiedJarFilename == null ? "<to be generated>" : unifiedJarFilename)
    		.append(" in subfolder '").append(tempJarSubfolder).append("'").append(System.getProperty("line.separator"));
    	//Processor affinity stuff
    	if(!runOnLimitedProcessors){
    		configOutput.append("ParallelLauncher uses all CPU cores.");
    	} else {
    		configOutput.append("CPU core affinity settings: ")
    			.append(processorsToRunOnAffinityMask == null ? 
    					processorsToRunOnForWindowsOsWithoutAffinitySupport : 
    						new StringBuilder(processorsToRunOnAffinityMask)
    							.append(" (Cores: ")
    							.append(processorsToRunOnForWindowsOsWithoutAffinitySupport)
    							.append(")"));
    	}
    	configOutput.append(System.getProperty("line.separator"));
    	// Simulation-specific stuff
    	configOutput.append("Arguments passed to launched processes (Count: ")
    		.append(argumentsToBePassedToLaunchedClasses == null ? 0 : argumentsToBePassedToLaunchedClasses.length)
    		.append("): ").append(expandArgumentsIntoCmdLineString())
    		.append(System.getProperty("line.separator"));
    	// Queue checking and WMI stuff
    	configOutput.append("Process queue check delay (progression base value): ").append(integers[0]).append(System.getProperty("line.separator"));
    	configOutput.append("Process termination check delay for processes launched by previous launcher: ").append(integers[1]).append(System.getProperty("line.separator"));
    	configOutput.append("Process termination check delay for processes launched by same launcher: ").append(processCheckFrequencyForProcessesStartedByLauncher)
    		.append(System.getProperty("line.separator"));
    	if(checkForKnownProcessAsWmiFailureBackupCheck){
    		configOutput.append("Check for reference process activated (to detect OS process manager failure): ").append(checkForKnownProcessAsWmiFailureBackupCheck);
    		configOutput.append(", Reference process: ").append(knownRunningProcessNames);
    	} else {
    		configOutput.append("Check for reference process (to detect OS process manager failure) deactivated.");
    	}
    	configOutput.append(System.getProperty("line.separator"));
    	if(redirectStdOutAndStdErrForLaunchedProcesses){
    		configOutput.append("Redirection of stdout and stderr to file activated").append(System.getProperty("line.separator"));
    	} else if(redirectStdErrForLaunchedProcesses){
    		configOutput.append("Redirection of stderr to file activated").append(System.getProperty("line.separator"));
    	}
    	if(startBatchProcessesMinimized){
    		configOutput.append("Starting processes in minimized window.");
    		configOutput.append(System.getProperty("line.separator"));
    	}
    	if(startInputOutputMonitorMinimized){
    		configOutput.append("Starting MonitorGUIs as minimized.");
    		configOutput.append(System.getProperty("line.separator"));
    	}
    	//configOutput.append("ParallelLauncher instance start time: ").append(myStartTime).append(System.getProperty("line.separator"));
    	configOutput.append(bar);
    	System.out.println(configOutput);
    }
        
	/**
	 * Checks WMI instrumentation if this launcher should start.
	 * Return codes:
	 * -1 if invalid response from WMI or unable to determine 
	 * position in queue
	 * -2 if BlockingParallelLauncher is running (to prevent immediate 
	 * start of launcher instances)
	 * 0 if first element in queue (should initiate start of executables), 
	 * else number of launchers in queue before this launcher.
	 * @return
	 */
	private static Integer myTurnInRunning(){
		if(launcherClass.equals(BlockingParallelLauncher.class)){
			return -2;
		}
		//get all creation times of this process
		List<String> output = processReader.getCreationTimesOfRunningInstances(launcherClass, WrapperExecutable.class);
		if(debug){
			System.out.println(PREFIX + "My start time: " + myStartTime + ", Got as return from OS process manager: " + output);
		}
		if(output.isEmpty()){
			System.err.println(PREFIX + "No process of my kind (" + launcherClass.getSimpleName() + ") seems to be started - Impossible. WMI or Linux CLI is probably overused. Will wait and make another query later.");
			if(myStartTime == null){
				System.err.println(PREFIX + "Important: DO NOT start new launcher instance yet as OS process manager did not deliver reliable information on this instance." + System.getProperty("line.separator") 
						+ "If you start a new instance now, both instances (this and the new one) are likely to infer wrong starting times.");
			}
			return -1;
		}
		//order times ascending
		Collections.sort(output);
		//assume that my start is the most recent one (last one in sorted collection) and memorize that
		if(myStartTime == null){
			//once set it is only used for comparison
			myStartTime = output.get(output.size() - 1);
			System.out.println(PREFIX + "Assume my creation time as " + myStartTime);
			//if one launcher running and creation of temporary JAR files deactivated, warn user about confounded setups
			if(output.size() == 1 && !createTemporaryJarFilesForQueueing){
				System.err.println(PREFIX + "=== Starting further launcher instances or working on source files should NOT be done" + System.getProperty("line.separator")
						+ "if you are running launchers from an IDE that compiles source files automatically as it will affect all queued launchers." + System.getProperty("line.separator")
						+ "Activate the generation of temporary JAR files if you want to queue more launchers or work on source files during launcher runs. ===");
			} else {
				System.out.println(PREFIX + "=== SUCCESS! You can now safely start further launcher instances. ===");
			}
		}
		//check for blocking launcher
		List<String> blockerRunning = processReader.getCreationTimesOfRunningInstances(BlockingParallelLauncher.class, null);
		if(!blockerRunning.isEmpty()){
			System.err.println(PREFIX + "Blocking Launcher is running. Will prevent me and any other launcher from starting (good for setup of large number of launchers).");
			return -2;
		}
		if(!output.contains(myStartTime)){
			System.err.println(PREFIX + "Something went wrong - WMI does not report my own instance. Will recheck ....");
			//did I memorize wrong start time, or does WMI make trouble?
			return -1;
		}
		if(output.get(0).equals(myStartTime)){
			System.out.println(PREFIX + "First of " + output.size() + " queued launcher(s) - my turn next.");
			//my turn, others are queued
			return 0;
		}
		for(int i = 0; i < output.size(); i++){
			if(output.get(i).equals(myStartTime)){
				System.out.println(PREFIX + "Need to wait for " + i + " queued launcher(s) (in total " + output.size() + " queued launchers).");
				return i;
			}
		}
		/*
		 * some invalid response as I should otherwise have been able to provide the number
		 * of launchers queued before me.
		 */
		return -1;
	}

	/**
	 * Absolute path to jar tool (without specifying jar tool itself) used to
	 * build jars from class files. If null, launcher assumes it to be on classpath.
	 */
	protected static String jdkBinPath = null;
	
	/**
	 * Indicates that system shows GUI that allows to specify JDK folder. 
	 * Only works if GUI is available.
	 */
	protected static boolean showFolderChooserUiIfJdkNotFound = true;
	
	/**
	 * Maintain chooser instance reference in case it is used multiple times (remembers directory).
	 */
	private static JFileChooser jdkBinChooser = null;
	
	/**
	 * Indicates if JDK path was manually chosen by user (and should thus be saved in file for 
	 * later reuse).
	 */
	private static boolean jdkPathChosenByUser = false;
	
	/**
	 * Shows GUI to pick correct JDK directory. Will only show up if system 
	 * is not in headless operation mode.
	 * Assigns picked directory to jdkBinPath field without further validation.
	 * Run {@link #testForCorrectJdkPath(boolean)} to check if jar executable can be found 
	 * on that path.
	 * @return
	 */
	private static boolean showGuiToPickJdkFolder(){
		jdkPathChosenByUser = false;
		if(!GraphicsEnvironment.isHeadless()){
			if(jdkBinChooser == null){
				jdkBinChooser = new JFileChooser(System.getProperty("user.dir"));
				jdkBinChooser.setDialogTitle("Could not find JDK folder! Choose JDK directory or its bin directory.");
	            jdkBinChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
	            jdkBinChooser.setMultiSelectionEnabled(false);
			} else {
				JOptionPane.showMessageDialog(jdkBinChooser, "Could not find JDK in directory \"" 
						+ jdkBinChooser.getSelectedFile().getAbsolutePath() + "\""
						+ System.getProperty("line.separator") 
						+ "Please specify a JDK directory or its bin folder or "
						+ System.getProperty("line.separator") 
						+ "click cancel (in directory chooser dialog) to abort selection.");
			}
            
            int result = jdkBinChooser.showOpenDialog(null);
            if(result == JFileChooser.CANCEL_OPTION){
            	return false;
            } else if(result == JFileChooser.ERROR_OPTION){
            	System.err.println(PREFIX + "An error occurred when showing directory picking GUI.");
            	return false;
            } else if(result == JFileChooser.APPROVE_OPTION){
            	if(debug){
            		System.out.println(jdkBinChooser.getSelectedFile());
            	}
            	jdkBinPath = jdkBinChooser.getSelectedFile().getAbsolutePath();
            	jdkPathChosenByUser = true;
            	return true;
            }
            return false;
		} else {
			System.out.println(PREFIX + "Cannot show directory picker GUI as system operates headless.");
			return false;
		}
	}
	
	/**
	 * Runs OS-specific generation of launch scripts.
	 * @param classToBeLaunched Class to be instantiated
	 * @param classpath Generated classpath
	 * @param javaCommand Java command prefix
	 * @param openSeparateConsoleWindow Indicates whether launched executable should be run in separate terminal/console.
	 * @param openConsoleWindowIfNotUsingWindowsVistaAndHigher Indicates whether external console should be used for older Windows version
	 * @param processorAffinityPrefixWindowsVistaAndHigher Native processor affinity prefix
	 * @param processorAffinityPrefix Processor affinity prefix for use with external tools
	 * @return Launch script file reference
	 */
	private static File runLaunchScriptGeneration(Class classToBeLaunched, String classpath, String javaCommand, boolean openSeparateConsoleWindow, boolean openConsoleWindowIfNotUsingWindowsVistaAndHigher, String processorAffinityPrefixWindowsVistaAndHigher, String processorAffinityPrefix) {
		if (ProcessReader.runsOnWindows()) {
			return runLaunchScriptWindows(classToBeLaunched, classpath, javaCommand, openSeparateConsoleWindow, openConsoleWindowIfNotUsingWindowsVistaAndHigher, processorAffinityPrefixWindowsVistaAndHigher, processorAffinityPrefix);
		} else if (ProcessReader.runsOnLinux()) {
			return runLaunchScriptLinux(classToBeLaunched, classpath, javaCommand, openSeparateConsoleWindow, processorAffinityPrefix);
		}
		throw new RuntimeException(PREFIX + "Could not detect supported OS for launch script generation. Reported OS: " + System.getProperty("os.name"));
	}
	
	/**
	 * Method to generate launch script on Linux. File reference of generated file is returned.
	 * @param classToBeLaunched Class to be launched
	 * @param classpath Classpath to be added for execution
	 * @param javaCommand Java command prefix to be complemented with arguments, etc.
	 * @param openSeparateConsoleWindow Indicates whether initiated instance should be launched in separate terminal
	 * @param processorAffinityPrefix Processor prefix - ignored under Linux (for now)
	 * @return Reference to generated launch script
	 */
	private static File runLaunchScriptLinux(Class classToBeLaunched, String classpath, String javaCommand, boolean openSeparateConsoleWindow, String processorAffinityPrefix) {
		// Generate unique bash file name for launch
		File scriptFile = new File(System.nanoTime() + LAUNCH_SCRIPT_FILE_ENDING);
		
		//generate stdout/stderr redirection outfile - or leave it as null if no redirection activated
		String redirectOutFilename = null;
		if(redirectStdErrForLaunchedProcesses || redirectStdOutAndStdErrForLaunchedProcesses){
			SimpleDateFormat simpleFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
			redirectOutFilename = (subfolderForStdOutAndStdErrRedirections != null ? subfolderForStdOutAndStdErrRedirections + "/" : "") 
					+ simpleFormat.format(getCurrentTime()) + "_" + classToBeLaunched.getSimpleName() + "_Console";
		}
		
		// Generate final command to be executed in order to instantiate class
		String cmd =
			// Prepare batch file (Batch file necessary because of long classpath)
			// Save classpath variable
			(createTemporaryClasspathVariable ? classpath : "")
			
			//process affinity stuff TODO: Not supported yet
			//+ (runOnLimitedProcessors ? processorAffinityPrefix : defaultStartCmd)
			
			// Actual Java command containing eventual jdkBinPath and java command inclusive classpath and launched class
			+ javaCommand + 
				" " +
				"-cp " + (jdkBinPath == null ? ("./bin" + CLASSPATH_SEPARATOR + "\"${CLASSPATH}\"") : "") +
				" " +
				WrapperExecutable.class.getCanonicalName()
			// Add redirection parameters (redirect type, outfile, identifier, executable class name)
				// Type of redirection (as specified by constants in WrapperExecutable
			+ " " + (redirectStdOutAndStdErrForLaunchedProcesses ? WrapperExecutable.REDIRECT_BOTH : (redirectStdErrForLaunchedProcesses ? WrapperExecutable.REDIRECT_STDERR : WrapperExecutable.REDIRECT_NONE))
				// Redirection outfile name
			+ " " + redirectOutFilename
				// Unique identifier for launched process (based on batch file name)
			+ " " + scriptFile.getName().substring(0, scriptFile.getName().indexOf(LAUNCH_SCRIPT_FILE_ENDING))
				// FQDN of class to be launched
			+ " " + classToBeLaunched.getCanonicalName()
		    // Append eventual arguments - future checks: arguments with space in between - not sure what happens
			+ (argumentsToBePassedToLaunchedClasses != null && argumentsToBePassedToLaunchedClasses.length != 0 ? " " + expandArgumentsIntoCmdLineString() : "")	
			;
			
		try {
			FileUtils.write(scriptFile, "#!/bin/bash" + System.getProperty("line.separator")
											//add actual java command
											+ cmd 
											+ System.getProperty("line.separator") 
											//return error level, so we can catch it
											//+ "echo Command terminated with error code %errorlevel%" + System.getProperty("line.separator") 
											+ (keepWindowOpen ? "" : "exit $?")
											+ System.getProperty("line.separator"));
			if(debug){
				System.out.println(getCurrentTimeString(true) + ": Wrote script file " + scriptFile.getAbsolutePath());
			}
			
			return scriptFile;
			
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return null;
	}
	
	private static File runLaunchScriptWindows(Class classToBeLaunched, String classpath, String javaCommand, boolean openSeparateConsoleWindow, boolean openConsoleWindowIfNotUsingWindowsVistaAndHigher, String processorAffinityPrefixWindowsVistaAndHigher, String processorAffinityPrefix) {
		// Generate unique batch file name for launch
		File scriptFile = new File(System.nanoTime() + LAUNCH_SCRIPT_FILE_ENDING);
		// Default start command used if no affinity support applies
		String defaultStartCmd = "start /WAIT ";
		
		// Generate stdout/stderr redirection outfile - or leave it as null if no redirection activated
		String redirectOutFilename = null;
		if(redirectStdErrForLaunchedProcesses || redirectStdOutAndStdErrForLaunchedProcesses){
			SimpleDateFormat simpleFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
			redirectOutFilename = (subfolderForStdOutAndStdErrRedirections != null ? subfolderForStdOutAndStdErrRedirections + "/" : "") 
					+ simpleFormat.format(getCurrentTime()) + "_" + classToBeLaunched.getSimpleName() + "_Console";
		}
		
		// Generate final command to be executed in order to instantiate class
		String cmd =
			// Prepare batch file (Batch file necessary because of long classpath)
			// local variable version required definition of local environment (as opposed to vs. parameter version)
			(createTemporaryClasspathVariable ? "setlocal" + System.getProperty("line.separator") + classpath : "")
			// is Java run in batch file directly, or launching a separate command line
			+ (runJavaClassInBatchFile ? "" : 
				(openSeparateConsoleWindow || (openConsoleWindowIfNotUsingWindowsVistaAndHigher && !ProcessReader.runsWindowsVistaAndHigher()) ? "cmd /c " : ""))//"start /WAIT "))
			// Process affinity aspects
			+ (runOnLimitedProcessors ? 
					// Prepares affinity for Windows Vista and higher
					(ProcessReader.runsWindowsVistaAndHigher() ? (validAffinityMaskSpecification() ? processorAffinityPrefixWindowsVistaAndHigher : defaultStartCmd) : 
					// Prepares prefix for psexec (Windows XP and others)
					(validCoreSpecificationForPsExec() ? processorAffinityPrefix : defaultStartCmd)) : defaultStartCmd)
			// Actual Java command containing eventual jdkBinPath and java command inclusive classpath and launched class
			+ javaCommand + " " + WrapperExecutable.class.getCanonicalName()
			// Add redirection parameters (redirect type, outfile, identifier, executable class name)
				// Type of redirection (as specified by constants in WrapperExecutable
			+ " " + (redirectStdOutAndStdErrForLaunchedProcesses ? WrapperExecutable.REDIRECT_BOTH : (redirectStdErrForLaunchedProcesses ? WrapperExecutable.REDIRECT_STDERR : WrapperExecutable.REDIRECT_NONE))
				// Redirection outfile name
			+ " " + redirectOutFilename
				// Unique identifier for launched process (based on batch file name)
			+ " " + scriptFile.getName().substring(0, scriptFile.getName().indexOf(LAUNCH_SCRIPT_FILE_ENDING))
				// FQDN of class to be launched
			+ " " + classToBeLaunched.getCanonicalName()
		    // Append eventual arguments - future checks: arguments with space in between - not sure what happens
			+ (argumentsToBePassedToLaunchedClasses != null && argumentsToBePassedToLaunchedClasses.length != 0 ? " " + expandArgumentsIntoCmdLineString() : "")	
			
			//in case of activated logging, save jar launch output for particular class into accordingly 
			//generated out file (both stdout and stderr (Command appendix 2>&1)) - Note: Only output from batch file, not executable!
			/* DOES NOT WORK+ (logBatchFileExecution ? " ^> " + scriptFile.getName().substring(0, scriptFile.getName().indexOf(BAT_FILE_ENDING)) 
													+ "_" + classToBeLaunched.getSimpleName() + " 2>&1 " : "") */
			
			//if local variable version, close statement here
			//+ (createTemporaryClasspathVariable ? System.getProperty("line.separator") + "endlocal" : "")
			//final semicolon alone to allow flexible choice of cmd elements
			;
			
		try {
			FileUtils.write(scriptFile, "@echo off" + System.getProperty("line.separator")
											// Add actual java command
											+ cmd 
											+ System.getProperty("line.separator") 
											// Return error level, so we can catch it
											//+ "echo Command terminated with error code %errorlevel%" + System.getProperty("line.separator") 
											+ (keepWindowOpen ? "" : "exit %errorlevel%")
											+ System.getProperty("line.separator"));
			if(debug){
				System.out.println(getCurrentTimeString(true) + ": Wrote script file " + scriptFile.getAbsolutePath());
			}
			
			return scriptFile;
			
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return null;
	}
	
	
	
	/**
	 * This method builds a Jar file with a given (absolute) name recursively from 
	 * a given (absolute) directory. Note: All passed parameters should be absolute paths.
	 * @param directory Absolute path of directory to be put into Jar file (recursively)
	 * @param targetFilename Absolute path of target file
	 * @return Target file name as given by the user. Throws exception of generation of 
	 * Jar file or any other related operation failed.
	 */
	private static String buildJarFromDirectory(String directory, String targetFilename){
		// Source for command: http://viralpatel.net/blogs/create-jar-file-in-java-eclipse/
		
		if(debug){
			System.out.println(PREFIX + "Trying to build temporary JAR for " + directory + " using target filename: " + targetFilename);
			System.out.println(PREFIX + "Command: " + buildJdkBinDirectoryString() + "jar cf " + targetFilename + " " + directory);
		}
		// Test running jar executable before preparing batch file - easier to see if it will work
		// Debug to show error stream from testing execution of jar executable
		boolean jdkPathCorrect = testForCorrectJdkPath(false);
		if(debug && !jdkPathCorrect){
			System.err.println(PREFIX + "JDK not found (jar/jar.exe, java/java.exe). Prompting user to pick folder.");
		}
		while(!jdkPathCorrect && showFolderChooserUiIfJdkNotFound && showGuiToPickJdkFolder()){
			if(debug){
				System.err.println(PREFIX + "JDK not found (jar/jar.exe, java/java.exe). Prompting user to pick folder.");
			}
			// Test as long as user is not pressing Cancel or the chooser fails for any other reason
			jdkPathCorrect = testForCorrectJdkPath(false);
		}
		
		if(!jdkPathCorrect){
			testForCorrectJdkPath(true);
		} else {
			if(writeUserSelectedJdkBinDirectoryToFile && jdkPathCorrect && jdkPathChosenByUser){
				// Write it to file to remember - overwrite existing file if present
				ArrayList<String> entry = new ArrayList<>();
				entry.add(buildJdkBinDirectoryString());
				try {
					FileUtils.writeLines(new File(JDK_BIN_PATH_FILE), entry, false);
					if(debug){
						System.out.println(PREFIX + "Wrote user-selected JDK bin path to file " + JDK_BIN_PATH_FILE);
					}
				} catch (IOException e) {
					System.err.println(PREFIX + "Problems when writing selected JDK bin path to file " + new File(JDK_BIN_PATH_FILE).getAbsolutePath());
					//e.printStackTrace();
				}
			}
		}
		
		// Create batch file with nano timestamp
		File scriptFile = new File(System.nanoTime() + LAUNCH_SCRIPT_FILE_ENDING);
		
		ProcessBuilder pb = null;
		if (ProcessReader.runsOnWindows()) {
			try {
				FileUtils.write(scriptFile, "@echo off" + System.getProperty("line.separator") 
						+ "cd \"" + directory + "\"" + System.getProperty("line.separator") 
						+ "\"" + buildJdkBinDirectoryString()
	                                        + "jar\" cf " + targetFilename + " *" + System.getProperty("line.separator")
						+ "exit" + System.getProperty("line.separator"));
				if(debug){
					System.out.println(PREFIX + "Wrote script file " + scriptFile.getAbsolutePath());
				}
			} catch (IOException e1) {
				System.err.println(PREFIX + "Writing of batch file for JAR generation failed.");
				e1.printStackTrace();
			}
			pb = new ProcessBuilder(scriptFile.getAbsolutePath());
		} else if(ProcessReader.runsOnLinux()) {
			try {
				FileUtils.write(scriptFile, "#!/bin/bash" + System.getProperty("line.separator") 
						+ (directory != null && !directory.isEmpty() ? "cd \"" + directory + "\"" + System.getProperty("line.separator") : "") 
						+ "\"" + buildJdkBinDirectoryString()
	                                        + "jar\" cf " + targetFilename + " *" + System.getProperty("line.separator")
						+ "exit $?" + System.getProperty("line.separator"));
				if(debug){
					System.out.println(PREFIX + "Wrote script file " + scriptFile.getAbsolutePath());
				}
			} catch (IOException e1) {
				System.err.println(PREFIX + "Writing of script file " + targetFilename + " for JAR generation failed!");
				e1.printStackTrace();
			}
			pb = new ProcessBuilder(new String[]{"bash", scriptFile.getAbsolutePath()});
		} else {
			throw new RuntimeException(PREFIX + "JAR generation failed: Could not detect supported operating system. Reported OS: " + System.getProperty("os.name"));
		}

		// pb cannot be null at this stage
		
		// Run batch file to generate JAR
		Process proc = null;
		try {
			System.out.println(PREFIX + "Generating temporary JAR file " + targetFilename);
			proc = pb.start();
		} catch (IOException e) {
			System.err.println(PREFIX + "Generating JAR file " + targetFilename + " failed.");
			e.printStackTrace();
		}
		try {
			if(debug){
				System.out.println(PREFIX + "Waiting for Jar building process to finish...");
			}
			if(proc != null){
				proc.waitFor();
				if(debug){
					printProcessOutput(proc, scriptFile.getAbsolutePath(), false);
				}
			} else {
				throw new RuntimeException(PREFIX + "Batch file execution for generation of temporary JAR file failed - process is null");
			}
		} catch (InterruptedException e) {
			System.err.println(PREFIX + "Exception when waiting for JAR generation process to finish.");
			e.printStackTrace();
		}
		// Delete batch file after JAR generation
		if(deleteBatchFilesCreatingJarsAfterStart){
			FileUtils.deleteQuietly(scriptFile);
		}
		if(debug){
			if(!scriptFile.exists()){
				System.out.println(PREFIX + "Batch file " + scriptFile.getName() + " successfully deleted.");
			} else {
				System.err.println(PREFIX + "Deletion of JAR generation batch file " + scriptFile.getName() + " failed.");
			}
		}
		if(new File(targetFilename).exists()){
			if(debug){
				System.out.println(PREFIX + "JAR file " + targetFilename + " successfully generated.");
			}
			return targetFilename;
		} else {
			throw new RuntimeException("Packaging of class files from directory into JAR file " + targetFilename + " failed." + System.getProperty("line.separator")
                                + "Check if java bin directory is on PATH (Windows: %PATH%; Linux: $PATH) or specify it via jdkBinPath field in you ParallelLauncher subclass.");
		}
	}
	
	/**
	 * Checks if the specified JDK path (jdkBinPath) is correct by 
	 * trying to execute jar executable. Returns true if correct path found 
	 * (or JAR executable on %PATH%), false if not found.
	 * @param showError If set to yes, method will throw RuntimeException 
	 * 		if JDK path not found.
	 * @return boolean indicating whether JDK path was found or not
	 */
	private static boolean testForCorrectJdkPath(boolean showError){
		final boolean testDebug = false;
		
		// Read JDK path from file if existent
		File jdkBinFile = new File(JDK_BIN_PATH_FILE);
		List<String> entries = null;
		if(writeUserSelectedJdkBinDirectoryToFile && jdkBinFile.exists()){
			if(debug){
				System.out.println(PREFIX + "Found JDK specification file " + jdkBinFile.getAbsolutePath());
			}
			try {
				entries = FileUtils.readLines(jdkBinFile);
			} catch (IOException e) {
				System.err.println(PREFIX + "Problems reading JDK specification file " + jdkBinFile.getAbsolutePath());
				//e.printStackTrace();
			}
		}
		
		String jarTestCommand = buildJdkBinDirectoryString();
		ArrayList<String> testCommands = new ArrayList<String>();
		testCommands.add(jarTestCommand);

		// Second version of command with appended bin directory (if jdkBinPath is not null)
		if(jdkBinPath != null){
			// used for assignment if check successful
			String rawFormatOfBinFolder = jdkBinPath + FOLDER_SEPARATOR + "bin";
			// used for testing
			jarTestCommand = rawFormatOfBinFolder + FOLDER_SEPARATOR;
			testCommands.add(jarTestCommand);
		}
		
		// If JDK bin specification file was found, test entries as well
		if(entries != null && !entries.isEmpty()){
			testCommands.addAll(entries);
		}
		
		for(int i = 0; i < testCommands.size(); i++){
			jarTestCommand = testCommands.get(i) + JAR_EXECUTABLE;
			if(debug){
				System.out.println(PREFIX + "Testing for JDK bin directory: " + jarTestCommand);
			}
			try {
				ProcessBuilder pb = new ProcessBuilder(jarTestCommand);
				Process proc = pb.start();
				proc.waitFor();
				if(testDebug){
					printProcessOutput(proc, "jar", false);
				}
				// If we get to here, something must have been found
				if(!testCommands.get(i).isEmpty()){
					// must either be on %PATH% or one of the user-specified ones from file
					jdkBinPath = testCommands.get(i);
				}
				if(jdkBinPath == null){
					System.out.println("Found JDK on PATH");
				} else {
					System.out.println("Found JDK under " + jdkBinPath);
				}
				return true;
			} catch (IOException e) {
				if(showError){
					// Show only if requested - Application will stop after showing that!
					throw new RuntimeException(PREFIX + "Could not execute jar compilation." + System.getProperty("line.separator") 
						+ "Check JDK path: " +
						(jdkBinPath == null ? JAR_EXECUTABLE + " could not be found on PATH." 
								+ System.getProperty("line.separator") + "You have to assign the JDK path (using the field jdkBinPath) in your launcher implementation" 
								: "Current path does not seem to point to JDK or its \\bin directory (Specified path: \"" + buildJdkBinDirectoryString() + "\")") + "!"
						+ System.getProperty("line.separator") + "Detailed error: " + e.getMessage());
				}
				if (testDebug) {
					e.printStackTrace();
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return false;
	}
	
	/**
	 * Prints error stream of given process along with error code.
	 * @param proc Process whose stderr is to be printed to console (and returned as String)
	 * @param testedFile Name of tested file to append to message
	 * @param returnOnly If set to true, returns String without printing it to console
	 * @return String version of stderr output and error code
	 */
	private static String printProcessOutput(Process proc, String testedFile, boolean returnOnly){
		InputStreamReader reader = new InputStreamReader(proc.getErrorStream());
		StringBuffer buffer = new StringBuffer();
		int nextChar;
		try {
			while((nextChar = reader.read()) != -1){
				buffer.append((char)nextChar);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String outcome = PREFIX + "Execution of " + testedFile + System.getProperty("line.separator")
				//+ "Exit code: " + proc.exitValue() + System.getProperty("line.separator") 
				+ "Error stream: " + buffer.toString();
		if(!returnOnly){
			System.out.println(outcome);
		}
		return outcome;
	}

	/**
	* Builds String representation of directory for JAR tool based on field jarToolDirectory.
	* Returns empty string if not specified.
	* @return
	*/
	private static String buildJdkBinDirectoryString(){
		if(jdkBinPath == null){
			return "";
		} else {
			if(jdkBinPath.endsWith(FOLDER_SEPARATOR)){
				return jdkBinPath;
			}
			return jdkBinPath + FOLDER_SEPARATOR;
		}
	}
	
	/**
	 * JARifies a given classpath, i.e. all class files on classpath are packed into JAR file. 
	 * This JAR file then replaces the reference to the original class file directory on the 
	 * classpath. The returned classpath only returns JAR file entries.
	 * @param classpath Classpath to be JARified
	 * @param unifiedJarFilename User-defined JAR file prefix. If null, method will auto-generate filename 
	 * based on time
	 * @param createLocalClasspathVariableInsteadOfParameter If set to true, method generates command lines 
	 * that declare a local classpath variable in a batch as opposed to provided a classpath parameter used 
	 * on command line. Local classpath variable is less likely (max. length 8k) than parameter (2k) to 
	 * hit length boundary when executed.
	 * @return JARified classpath either as parameter or local variable command line
	 */
	public static String createJARifiedClasspath(String classpath, String unifiedJarFilename, boolean createLocalClasspathVariableInsteadOfParameter){
		// Solution for creation of local classpath inspired by http://unserializableone.blogspot.co.nz/2007/10/solution-to-classpath-too-long-aka.html
		
		// Copy non-jar files temp directory, memorize directory, adapt classpath entry
		StringTokenizer tok = new StringTokenizer(classpath, CLASSPATH_SEPARATOR);
		// Counter in case multiple JAR files need to be created for the same launcher (i.e. multiple folders with class files on classpath)
		int dirCounter = 0;
		
		// Buffer for rebuilding modified classpath
		StringBuffer newClassPath = new StringBuffer();
		String tempJarFileName = null; 
		while(tok.hasMoreElements()){
			String token = tok.nextToken();
			boolean processDirectoryAsJarFile = false;
			if(!token.endsWith(JAR_FILE_ENDING)){
				// Check if containing class files
				File check = new File(token);
				if(check.isDirectory()){
					String[] ext = new String[1];
					ext[0] = "class";
					if(!FileUtils.listFiles(check, ext, true).isEmpty()){
						// only process if containing .class files
						processDirectoryAsJarFile = true;
					}
				}
			}
			if(processDirectoryAsJarFile){
				// Treat as directory and pack into jar
				if(tempJarFileName == null){
					// Prepare subfolder
					File subfolder = new File(System.getProperty("user.dir") + FOLDER_SEPARATOR + tempJarSubfolder);
					if(!subfolder.exists()){
						if(!subfolder.mkdirs()){
							throw new RuntimeException(PREFIX + "Error creating subdirectory " + subfolder.getAbsolutePath());
						}
					}
					tempJarFileName = subfolder + FOLDER_SEPARATOR 
							+ (unifiedJarFilename != null ? unifiedJarFilename : String.valueOf(System.nanoTime()));
				}
				String dynJarName = tempJarFileName + "_" + dirCounter + JAR_FILE_ENDING;
				// Increase counter in case of multiple Jars
				dirCounter++;
				if(unifiedJarFilename == null){
					// if not given filename by MetaLauncher, save mapping from old classpath entry to newly generated jar - for later addition to deletion log as well as for debugging
					jarNameMapper.put(token, dynJarName);
				}
				
				// Check if classpath is empty
				boolean firstElementOnClassPath = newClassPath.length() == 0;
				
				// Eventually generate JAR file if necessary
				if(unifiedJarFilename == null || !new File(dynJarName).exists()){
					if(unifiedJarFilename != null){
						//if(debug){
							System.out.println(PREFIX + "Generating unified JAR file " + dynJarName);
						//}
					}
					// Generate JAR if no unified JAR specified or if specified file does not exist
					buildJarFromDirectory(token, dynJarName);
				} else {
					if(unifiedJarFilename != null){
						//if(debug){
							System.out.println(PREFIX + "Using existing unified JAR file " + dynJarName);
						//}
					}
				}
				
				if(createLocalClasspathVariableInsteadOfParameter){
					// LOCAL VARIABLE VERSION
					if (ProcessReader.runsOnLinux()) {
						newClassPath.append("export CLASSPATH=");
					} else {
						newClassPath.append("set CLASSPATH=");
					}
					newClassPath.append(dynJarName);
					if(!firstElementOnClassPath){
						// Already existing classpath entries, so we need to append %CLASSPATH%
						if (ProcessReader.runsOnLinux()) {
							newClassPath.append(CLASSPATH_SEPARATOR + "${CLASSPATH}");
						} else {
							// Windows
							newClassPath.append(CLASSPATH_SEPARATOR + "%CLASSPATH%");
						}
					}
				} else {
					// PARAMETER VERSION
					newClassPath.append(dynJarName);
				}
			} else {
				boolean firstOnClassPath = newClassPath.length() == 0;
				if(createLocalClasspathVariableInsteadOfParameter){
					// LOCAL VARIABLE VERSION
					if (ProcessReader.runsOnLinux()) {
						newClassPath.append("export CLASSPATH="); //TODO: Check for quotation marks CLASSPATH=\""
					} else {
						newClassPath.append("set CLASSPATH=");
					}
					if (ProcessReader.runsOnLinux()) {
						newClassPath.append("\"").append(token).append("\"");
					} else {
						newClassPath.append(token);
					}
					if(!firstOnClassPath){
						// Already existing classpath entries, so we need to append %CLASSPATH%
						if (ProcessReader.runsOnLinux()) {
							// Linux
							newClassPath.append(CLASSPATH_SEPARATOR + "${CLASSPATH}");
						} else {
							// Windows
							newClassPath.append(CLASSPATH_SEPARATOR + "%CLASSPATH%");
						}
					}
				} else {
					// PARAMETER VERSION
					newClassPath.append(token);
				}
			}
			if(tok.hasMoreElements() && !createLocalClasspathVariableInsteadOfParameter){
				// only append if not last element
				newClassPath.append(CLASSPATH_SEPARATOR);
			}
			
			// Add linebreak if working with variables instead of command parameter
			if(createLocalClasspathVariableInsteadOfParameter){
				newClassPath.append(System.getProperty("line.separator"));
			}
		}
		
		if(debug){
			System.out.println(PREFIX + "Modified classpath: " + newClassPath);
		}
		// Overwrite old classpath with newly generated one - but ensure that it is not empty just to be safe
		if(newClassPath.length() > 0){
			classpath = newClassPath.toString();
		}
		return classpath;
	}
	
	/**
	 * Cleans up JAR files produced for previous launcher runs. 
	 * If successfully started, launchers register temporary JAR files in a log
	 * file (Constant CLEANUP_LOG_FILE). This method scans the log file and tries 
	 * to delete all entries and removes them from the log file. 
	 * Deletes the entire log file if all JAR files have been deleted.
	 * Logged files that cannot be found are considered deleted manually and purged 
	 * from the log.
	 */
	protected static void cleanUpTemporaryJarFiles(){
		File cleanupLog = new File(CLEANUP_LOG_FILE);
		if(cleanupLog.exists()){
			System.out.println(PREFIX + "Attempting to clean up temporary files from previous launcher runs...");
			Scanner scanner = null;
			try {
				scanner = new Scanner(cleanupLog);
			} catch (FileNotFoundException e) {
				System.err.println(PREFIX + "Cleanup log file could not be found. Should never happen unless several launchers are started in parallel - Don't do that!" + System.getProperty("line.separator")
						+ "Wait for launchers to tell you when it is safe to start the next one!");
				e.printStackTrace();
			}
			if(scanner == null){
				System.err.println(PREFIX + "Instantiation of text scanner for log file failed. Cleanup aborted.");
				return;
			}
			StringBuffer remainingFiles = new StringBuffer();
			while(scanner.hasNext()){
				String fileToDelete = scanner.next();
				File fileDel = new File(fileToDelete);
				if(fileDel.exists()){
					if(fileDel.delete()){
						System.out.println(PREFIX + "Deleted temporary file " + fileDel.getName());
					} else {
						System.err.println(PREFIX + "Could not delete temporary file " + fileDel.getAbsolutePath() 
								/*
								 * If multiple launchers allowed, notify user that deletion attempt will be done
								 * by next launcher as jar of previous launcher probably still running. 
								 */
								+ (maxNumberOfActiveParallelLaunchers > 1 ? 
										"." + System.getProperty("line.separator") 
										+ "Previous launcher is possibly still running. Deletion attempt with be repeated by next launcher." : "."));
						// Keep file reference for later deletion
						remainingFiles.append(fileToDelete).append(System.getProperty("line.separator"));
					}
				} else {
					System.out.println(PREFIX + "Logged temporary file could not be found - probably manually deleted (File: " + fileDel.getAbsolutePath() + ").");
				}
			}
			scanner.close();
			
			if(remainingFiles.length() > 0){
				// If there are remaining files, overwrite original file
				try {
					FileUtils.write(cleanupLog, remainingFiles, false);
				} catch (IOException e) {
					System.err.println(PREFIX + "Error when writing log file for remaining (not successfully deleted) temporary files.");
					e.printStackTrace();
				}
			} else {
				// If no failed deletes, simply delete the entire log file
				if(cleanupLog.delete()){
					System.out.println(PREFIX + "Successfully cleared up old JAR files");
				} else {
					System.err.println(PREFIX + "Deleting of temporary files log failed. Probably accessed by other process.");
				}
			}
		}
	}

	/**
	 * Tokenizes a given command into an ArrayList of individual tokens - 
	 * which is accepted as input for ProcessBuilder.
	 * @param command Command to be tokenized
	 * @return ArrayList containing all command tokens
	 */
	private static ArrayList<String> tokenizeCommandStringToArrayList(String command){
		StringTokenizer tokenizer = new StringTokenizer(command, " ");
		ArrayList<String> commands = new ArrayList<>();
		while(tokenizer.hasMoreTokens()){
			commands.add(tokenizer.nextToken());
		}
		return commands;
	}
	
	/**
	 * Convenience method for starting ParallelLauncher from launcher specialisation.
	 * Calls ParallelLauncher's main method. 
	 * Should not be used as it does not allow to pass parameters to the launcher 
	 * instance itself, which is necessary if the MetaLauncher (launching multiple 
	 * launchers) is used. Use {@link #start(String[])} instead.
	 */
	@Deprecated
	public static void start(){
		ParallelLauncher.main(new String[0]);
	}
	
	/**
	 * Convenience method for starting ParallelLauncher from launcher specialisation.
	 * Calls ParallelLauncher's main method.
	 * @param args 
	 */
	public static void start(String[] args){
		ParallelLauncher.main(args);
	}
	
	/**
	 * Checks for the existence of Apache Commons-IO and throws RuntimeException if not found. 
	 * Important in case ParallelLauncher is precompiled and thus does not break during compilation.
	 */
	private static void checkForDependencies() {
        
		// Apache Commons IO
		try {
            Class.forName("org.apache.commons.io.FileUtils");
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("ParallelLauncher: Class not found exception - missing dependency: " + ex.getMessage()
            		+ System.getProperty("line.separator")
            		+ "Dependency for file system operations was not found!"
                    + " Check for existence of Apache Commons IO on the classpath."); // + System.getProperty("line.separator"));
                    /*+ "It should be located in a subfolder 'lib' "
                    + "(usually a jar file of the pattern 'commons-io-X.X.jar' where X.X stands for the version).");*/
        }
        
		// xterm (Linux)
        if (ProcessReader.runsOnLinux()) {
        	ProcessBuilder pb = new ProcessBuilder("xterm", "-version");
        	int exit = -1;
			try {
				exit = pb.start().waitFor();
			} catch (IOException | InterruptedException e) {
				throw new RuntimeException("ParallelLauncher: xterm is not installed or not on PATH variable. Install xterm in order to use ParallelLauncher on Linux. (System Error: " + e.getMessage() + ")");
			}
			if (exit != 0) {
				throw new RuntimeException("ParallelLauncher: xterm returned a non-zero exit code. Please check to ensure reliable function. Exit code: " + exit);
			}
        }
    }
	
	/**
	 * Writes status of launcher (position in queue, registration with WMI) 
	 * to IPC file of eventual MetaLauncher. If no MetaLauncher, the 
	 * writing process is aborted. Should only be written as long as there is 
	 * no valid WMI response received. After that the ParallelLauncher handles 
	 * its own state autonomously; MetaLauncher instances can start further 
	 * ParallelLaunchers - knowing that each has been started with proper WMI 
	 * registration.
	 * @param status Status of launcher (Return value from {@link #myTurnInRunning()})
	 */
	private static void writeStatusToMetaLauncherIpcFile(int status){
		if(metaLauncherIpcFile != null){
			File ipcFile = new File(metaLauncherIpcFile);
			List<String> data = new ArrayList<String>();
			data.add(String.valueOf(status));
			try {
				FileUtils.writeLines(ipcFile, data);
			} catch (IOException e) {
				System.err.println(PREFIX + "Error when writing status to IPC file " + metaLauncherIpcFile + ": " + e.getMessage());
				//e.printStackTrace();
			}
			if(debug){
				System.out.println(PREFIX + "Wrote status " + status + " into MetaLauncher IPC file " + metaLauncherIpcFile);
			}
		} else {
			if(debug){
				System.out.println(PREFIX + "No MetaLauncher running. Did not write status to IPC file.");
			}
		}
	}

    /** 
	 * Predefined date format for time output 
	 */
	private static final String DATE_FORMAT_NOW = "yyyy-MM-dd HH:mm:ss";
	
	/**
	 * Predefined time format for time output 
	 */
	private static final String TIME_FORMAT_NOW = "HH:mm:ss";
        
    /**
	 * Returns the current time as Date object.
	 * 
	 * @return Current time as Date object
	 */
	public static Date getCurrentTime(){
		Calendar cal = Calendar.getInstance();
		return cal.getTime();
	}
	
	/**
	 * Returns the formatted current date/time as String (for output/logging purposes)
	 * supported formats:
	 * "yyyy-MM-dd HH:mm:ss", "HH:mm:ss" (see constants {@link #DATE_FORMAT_NOW} and {@link #TIME_FORMAT_NOW}).
	 * @param includingDate Boolean indicating if date should be returned or only time 
	 * @return String representation of current time
	 */
	public static String getCurrentTimeString(boolean includingDate){
		 String dateFormat = "";
		 if(includingDate){
			 dateFormat = DATE_FORMAT_NOW;
		 } else {
			 dateFormat = TIME_FORMAT_NOW;
		 }
		 SimpleDateFormat simpleFormat = new SimpleDateFormat(dateFormat);
		 return simpleFormat.format(getCurrentTime());
	}

}
