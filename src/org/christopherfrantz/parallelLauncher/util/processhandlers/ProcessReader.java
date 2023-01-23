package org.christopherfrantz.parallelLauncher.util.processhandlers;

import java.util.ArrayList;
import java.util.List;

/**
 * Facilitates OS-independent retrieval of unique identifiers for launched instances for
 * further tracking. Requires OS-specific implementation and return of unique identifier
 * for further assessment of runtime status of an individual instance.
 *
 * @author Christopher Frantz
 *
 */
public abstract class ProcessReader {
	
	public static boolean debug = false;
	
	/**
	 * Returns list of processes that run java executables.
	 * @param classesRunInProcesses
	 * @return
	 */
	public abstract List<String> retrieveProcessesRunningJavaClasses(ArrayList<Class> classesRunInProcesses);
	
	/**
	 * Returns list of processes whose names contain a given name string.
	 * @param processNameString
	 * @return
	 */
	public abstract List<String> retrieveProcessesWithName(String processNameString);
	
	/**
	 * Returns list of processes whose name contains any of the given names.
	 * @param processNameList
	 * @return
	 */
	public abstract List<String> retrieveProcessesWithNames(ArrayList<String> processNameList);
	
	/**
	 * Returns the creation times for processes of a given class. Returns empty list if not found.
	 * @param classToLookup
	 * @param exceptionClass
	 * @return
	 */
	public abstract List<String> getCreationTimesOfRunningInstances(String classToLookup, String exceptionClass);
	
	/**
	 * Returns the creation times for processes of a given class. Returns empty list if not found.
	 * @param classToLookup
	 * @param exceptionClass
	 * @return
	 */
	public List<String> getCreationTimesOfRunningInstances(Class classToLookup, Class exceptionClass){
		if(debug){
			System.out.println("Class to lookup: " + (classToLookup == null ? "null" : classToLookup.getCanonicalName()));
			System.out.println("Exception class: " + (exceptionClass == null ? "null" : exceptionClass.getCanonicalName()));
		}
		return getCreationTimesOfRunningInstances(classToLookup == null ? null : classToLookup.getCanonicalName(), 
				exceptionClass != null ? exceptionClass.getCanonicalName() : null);
	}
	
	/**
	 * Checks if this application is run on a Windows operating system.
	 * Returns true if so, and false if not.
	 * @return
	 */
	public static boolean runsOnWindows(){
		if(!System.getProperty("os.name").startsWith("Windows")){
			return false;
		}
		return true;
	}
	
	/**
	 * Indicates whether the system runs on Linux.
	 * Returns true if so, and false if not.
	 * @return
	 */
	public static boolean runsOnLinux() {
		if(!System.getProperty("os.name").contains("Linux")){
			return false;
		}
		return true;
	}
	
	/**
	 * Checks if this application is run on a Windows operating system, 
	 * with version greater than Vista.
	 * Returns true if so, and false if not non-Windows or older Windows 
	 * system (e.g. Windows XP, Windows Server 2003/2008 and older). 
	 * @return Boolean indicating if Windows OS of version Vista or higher
	 */
	public static boolean runsWindowsVistaAndHigher(){
		String osName = System.getProperty("os.name");
		if(osName.startsWith("Windows 95")
				|| osName.startsWith("Windows 98")
				|| osName.startsWith("Windows Me")
				|| osName.startsWith("Windows NT")
				|| osName.startsWith("Windows 2000")
				|| osName.startsWith("Windows XP")
				|| osName.startsWith("Windows 2003")
				|| osName.startsWith("Windows 2008")){
			//hope we captured all old candidates
			return false;
		}
		//double-check that it is Windows OS
		if(osName.startsWith("Windows")){
			/*
			 * assume that all newer Windows versions (as of now Vista, 7, 8 and 10)
			 * should be captured by this
			 */
			return true;
		}
		//if not Windows, return false
		return false;
	}

}
