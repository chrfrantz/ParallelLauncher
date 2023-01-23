package org.christopherfrantz.parallelLauncher.util.processhandlers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;

import org.christopherfrantz.parallelLauncher.util.DataStructurePrettyPrinter;

/**
 * Windows variant of process reader. Automatically instantiated when Windows OS is detected.
 *
 * @author Christopher Frantz
 *
 */
public class WindowsProcessReader extends ProcessReader {

	private static final String WMIC_COMMAND = "process where name='";
	private static final ArrayList<String> JAVA_CMDs = new ArrayList<String>();
	private static final ArrayList<String> ignoredOutput = new ArrayList<String>();

	
	static {
		//potential java-related entries found in task manager
		JAVA_CMDs.add("java.exe");
		JAVA_CMDs.add("javaw.exe");
		//irrelevant WMI output to be filtered
		ignoredOutput.add("wmic:root\\cli>");
		ignoredOutput.add("CreationDate");
	}
	
	/**
	 * Returns processes with a given name.
	 * @param processName Process name to be looked up.
	 * @return Process information for processes matching input name
	 */
	@Override
	public ArrayList<String> retrieveProcessesWithName(String processName){
		//System.out.println("Trying to retrieve process with name: " + processName);
		ArrayList<String> list = new ArrayList<String>();
		list.add(processName);
		return retrieveProcessesWithNames(list);
	}
	
	/**
	 * Returns processes with a given names.
	 * @param processNameList list of process names to be looked up.
	 * @return Process information for processes matching input name(s)
	 */
	@Override
	public ArrayList<String> retrieveProcessesWithNames(ArrayList<String> processNameList){
		return runWmiQueryForProcessNames(processNameList, null);
	}
	
	/**
	 * Returns process information for Java OS processes whose commands contain a given 
	 * string (e.g. class name or classpath element).
	 * Returns null if operating system is not Windows.
	 * @param commandContains Command, classpath element or other element to be looked up
	 * @return ArrayList containing information on process as String
	 */
	public ArrayList<String> retrieveProcessesWithCommandContaining(String commandContains){
		ArrayList<String> contained = new ArrayList<>();
		contained.add(commandContains);
		return retrieveProcessesWithCommandContaining(contained, null);
	}
	
	/**
	 * Returns process information for Java OS processes whose commands contain one of the given 
	 * strings (e.g. class name or classpath element).
	 * Returns null if operating system is not Windows.
	 * @param commandContainsEitherOf Commands, classpath element or other elements to be checked in each process
	 * @param exceptionEvenIfContains Exception from previous case if element of this parameter is contained in same result line
	 * @return ArrayList containing information on matching processes as String representation
	 */
	public ArrayList<String> retrieveProcessesWithCommandContaining(ArrayList<String> commandContainsEitherOf, ArrayList<String> exceptionEvenIfContains){
		return runWmiQueryForJavaCommands(commandContainsEitherOf, exceptionEvenIfContains, " get commandline, creationdate");
	}
	
	/**
	 * Looks up Java processes (process names java.exe and javaw.exe) that contain a specified command(s)/Strings (e.g. in classpath).
	 * Valuable information: http://www.winhelponline.com/blog/list-running-processes-and-their-creation-times/
	 * @param commandContainsEitherOf Strings to be found in classpath of respective java process.
	 * @param exceptionEvenIfContains Exception from previous case if element of this parameter is contained in same result line
	 * @param querySuffix Eventual WMI query suffix
	 * @return List containing WMI information output of processes matching input parameters.
	 */
	private synchronized ArrayList<String> runWmiQueryForJavaCommands(ArrayList<String> commandContainsEitherOf, ArrayList<String> exceptionEvenIfContains, String querySuffix){
		if(!runsOnWindows()){
			return null;
		}
		ArrayList<String> processes = new ArrayList<>();
		String line;
		for(int i = 0; i < JAVA_CMDs.size(); i++){
			try {
			    Process proc = Runtime.getRuntime().exec("wmic.exe");
			    BufferedReader input = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			    OutputStreamWriter oStream = new OutputStreamWriter(proc.getOutputStream());
			    //differentiate whether query suffix has been provided
			    if(querySuffix != null && !querySuffix.isEmpty()){
			    	oStream.write(WMIC_COMMAND + JAVA_CMDs.get(i) + "' " + querySuffix);
			    } else {
			    	oStream.write(WMIC_COMMAND + JAVA_CMDs.get(i) + "'");
			    }
			    oStream.flush();
			    oStream.close();
			    while ((line = input.readLine()) != null) {
			    	if(commandContainsEitherOf != null && !commandContainsEitherOf.isEmpty()){
				    	for(int j = 0; j < commandContainsEitherOf.size(); j++){
					    	if(line.contains(commandContainsEitherOf.get(j))){
					    		boolean add = true;
					    		if(exceptionEvenIfContains != null){
					    			if(debug){
					    				System.out.println("Testing for exceptions: " + exceptionEvenIfContains);
					    			}
						    		for(int k = 0; k < exceptionEvenIfContains.size(); k++){
						    			//filter unwanted entries
						    			if(line.contains(exceptionEvenIfContains.get(k))){
						    				add = false;
						    			}
						    		}
					    		}
					    		if(add){
						    		if(debug){
						    			System.out.println("Added line to results as it matched '" + commandContainsEitherOf.get(j) + "': " + line);	
						    		}	
						    		processes.add(line.trim());
						    		break;
					    		} else {
					    			if(debug){
					    				System.out.println("Line filtered by conflicting statement in " 
					    						+ exceptionEvenIfContains + ", despite matching of " + commandContainsEitherOf.get(j) + ": " + line);	
					    			}
					    		}
					    	}
				    	}
			    	} else {
			    		//if no specific contents requested, return all output but filter it with things that are irrelevant (check ignoreOutput list for details)
			    		if(!line.isEmpty() && !ignoredOutput.contains(line)){
			    			boolean add = true;
				    		if(exceptionEvenIfContains != null){
					    		for(int k = 0; k < exceptionEvenIfContains.size(); k++){
					    			//filter unwanted entries
					    			if(line.contains(exceptionEvenIfContains.get(k))){
					    				add = false;
					    			}
					    		}
				    		}
				    		if(add){
				    			if(debug){
				    				System.out.println("Added line to results (no filtering): " + line);
				    			}
				    			processes.add(line.trim());
				    		} else {
				    			if(debug){
				    				System.out.println("Line filtered by conflicting statement in " 
				    						+ exceptionEvenIfContains + ": " + line);	
				    			}
				    		}
			    		}
			    	}
			    	//System.out.println("Line: " + line);
			    }
			    input.close();
			} catch (IOException ioe) {
			    ioe.printStackTrace();
			}
		}
		if(debug){
			System.out.println("About to return discovered processes: " + DataStructurePrettyPrinter.decomposeRecursively(processes, null));
		}
		return processes;
	}
	
	/**
	 * Runs WMI query for arbitrary process names (not necessarily Java processes) and 
	 * returns the process names found.
	 * @param processNames Process names for which running processes should be looked up.
	 * @param querySuffix Eventual suffix of query
	 * @return ArrayList of WMI information output on running processes with name(s) specified in processNames
	 */
	private synchronized static ArrayList<String> runWmiQueryForProcessNames(ArrayList<String> processNames, String querySuffix){
		if(!runsOnWindows()){
			System.err.println("Running WMI query on non-Windows systems doesn't make sense. Query aborted.");
			return null;
		}
		ArrayList<String> processes = new ArrayList<>();
		if(processNames == null || processNames.isEmpty()){
			System.err.println("Passed null or empty list when checking for running process via WMI.");
			return processes;
		}
		String line;
		for(int i = 0; i < processNames.size(); i++){
			try {
			    Process proc = Runtime.getRuntime().exec("wmic.exe");
			    BufferedReader input = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			    OutputStreamWriter oStream = new OutputStreamWriter(proc.getOutputStream());
			    //differentiate whether query suffix has been provided
			    if(querySuffix != null && !querySuffix.isEmpty()){
			    	oStream.write(WMIC_COMMAND + processNames.get(i) + "' " + querySuffix);
			    } else {
			    	//System.out.println("Query: " + WMIC_COMMAND + processNames.get(i) + "'");
			    	oStream.write(WMIC_COMMAND + processNames.get(i) + "'");
			    }
			    oStream.flush();
			    oStream.close();
			    while ((line = input.readLine()) != null) {
		    		//if no specific contents requested, return all output but filter it with things that are irrelevant (check ignoreOutput list for details)
		    		if(!line.isEmpty() && !ignoredOutput.contains(line)){
		    			processes.add(line.trim());
		    		}
			    	//System.out.println("Line: " + line);
			    }
			    input.close();
			} catch (IOException ioe) {
			    ioe.printStackTrace();
			}
		}
		return processes;
	}
	
	/**
	 * Returns String representation of OS processes that run either one of the provided Java classes.
	 * Returns null if not running on Windows.
	 * @param classesRunInProcesses ArrayList of classes to be checked in processes
	 * @return String representation for each process running any provided input class
	 */
	@Override
	public ArrayList<String> retrieveProcessesRunningJavaClasses(ArrayList<Class> classesRunInProcesses){
		if(!runsOnWindows()){
			return null;
		}
		ArrayList<String> classesAsStrings = new ArrayList<String>();
		for(int j = 0; j < classesRunInProcesses.size(); j++){
			classesAsStrings.add(classesRunInProcesses.get(j).getCanonicalName());
		}
		//use hashset, so duplicates are removed automatically
		HashSet<String> processes = new HashSet<>();
		for(int i = 0; i < classesRunInProcesses.size(); i++){
			processes.addAll(retrieveProcessesWithCommandContaining(classesAsStrings.get(i)));
		}
		if(debug){
			System.out.println("Aggregated into " + DataStructurePrettyPrinter.decomposeRecursively(processes, null));
		}
		return new ArrayList<String>(processes);
	}
	
	/**
	 * Returns String representation of OS processes that run the provided Java class.
	 * Returns null if not running on Windows.
	 * @param classRunInProcesses Classes to be checked in processes
	 * @return String representation for each process running the provided input class
	 */
	public ArrayList<String> retrieveProcessesRunningJavaClass(Class classRunInProcesses){
		ArrayList<Class> list = new ArrayList<>();
		list.add(classRunInProcesses);
		return retrieveProcessesRunningJavaClasses(list);
	}
	
	/**
	 * Returns the creation time of Java processes of interest. Internally it relies 
	 * on two lookups, first including java processes, after that a second iteration 
	 * matching on date of processes. This avoids dealing with the otherwise nasty 
	 * WMI output.
	 * @param classNameToLookup Class name or other identifier that appears in the command line of a Java process of interest
	 * @param exceptionClass If occurring together with classNameToLookup, entry is ignored
	 * @return ArrayList of String timestamps (produced by WMI) of the start time of processes matching the request
	 */
	@Override
	public ArrayList<String> getCreationTimesOfRunningInstances(String classNameToLookup, String exceptionClass){
		ArrayList<String> input = new ArrayList<String>();
		if(classNameToLookup != null){
			input.add(classNameToLookup);
		}
		ArrayList<String> exception = new ArrayList<String>();
		if(exceptionClass != null){
			exception.add(exceptionClass);
		}
		ArrayList<String> processesAndTime = runWmiQueryForJavaCommands(input.isEmpty() ? null : input, exception.isEmpty() ? null : exception, "get commandline, creationdate");
		ArrayList<String> onlyTimes = runWmiQueryForJavaCommands(null, null, "get creationdate");
		ArrayList<String> validTimes = new ArrayList<String>();
		for(int i = 0; i < onlyTimes.size(); i++){
			for(int j = 0; j < processesAndTime.size(); j++){
				if(processesAndTime.get(j).contains(onlyTimes.get(i))){
					validTimes.add(onlyTimes.get(i));
				}
			}
		}
		return validTimes;
	}

}
