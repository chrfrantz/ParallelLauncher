package org.christopherfrantz.parallelLauncher.util.processhandlers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.christopherfrantz.parallelLauncher.util.DataStructurePrettyPrinter;

/**
 * Linux variant of process reader. Automatically instantiated when Linux-style OS is detected.
 *
 * @author Christopher Frantz
 *
 */
public class LinuxProcessReader extends ProcessReader {

	@Override
	public List<String> retrieveProcessesRunningJavaClasses(
			ArrayList<Class> classesRunInProcesses) {
		if (!runsOnLinux()) {
			return null;
		}
		
		ArrayList<String> classesAsStrings = new ArrayList<String>();
		for(int j = 0; j < classesRunInProcesses.size(); j++){
			classesAsStrings.add(classesRunInProcesses.get(j).getCanonicalName());
		}
		//use hashset, so duplicates are removed automatically
		HashSet<String> processes = new HashSet<>();
		for(int i = 0; i < classesRunInProcesses.size(); i++){
			processes.addAll(retrieveProcessesWithName(classesAsStrings.get(i)));
		}
		if(debug){
			System.out.println("Aggregated into " + DataStructurePrettyPrinter.decomposeRecursively(processes, null));
		}
		return new ArrayList<String>(processes);
	}

	@Override
	public List<String> retrieveProcessesWithName(String processNameString) {
		ArrayList<String> list = new ArrayList<>();
		list.add(processNameString);
		return retrieveProcessesWithNames(list);
	}

	@Override
	public List<String> retrieveProcessesWithNames(
			ArrayList<String> processNameList) {
		if (!runsOnLinux()) {
			return null;
		}
		
		List<String> processes = new ArrayList<>();
		
		// Query for process name
		try {
			Process process = Runtime.getRuntime().exec("ps -aux");
			BufferedReader processReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

		    String line = null;
		    while ((line = processReader.readLine()) != null) {
		    	if (!line.isEmpty()) {
		    		// Iterate through process list and check whether name is contained in line of ps -aux output
		    		for (int i = 0; i < processNameList.size(); i++) {
		    			if (line.contains(processNameList.get(i))) {
		    				processes.add(line);
		    			}
		    		}
		    	}
		    }
		    processReader.close();
		    
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return processes;
	}

	@Override
	public List<String> getCreationTimesOfRunningInstances(String classToLookup,
			String exceptionClass) {
		ArrayList<String> processes = new ArrayList<>();
		if (!runsOnLinux()) {
			return null;
		}
		try {
			Process process = Runtime.getRuntime().exec("ps -aux");
			BufferedReader processReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

		    String line = null;
		    while ((line = processReader.readLine()) != null) {
		    	if (!line.isEmpty() && line.contains(classToLookup) && 
		    			((exceptionClass == null || exceptionClass.isEmpty()) ? true : !line.contains(exceptionClass))) {
		    		if (debug) {
		    			System.out.println("Response from ps -aux for " + classToLookup + " (Exception: " + exceptionClass + "): " + line);
		    		}
		    		
		    		// Detect process id
		    		String[] strArr = line.split("\\s+");
		    		
		    		//System.out.println("Process id: " + strArr[1]);

		    		// Get process creation timestamp
		    		Process processGetCreationTime = Runtime.getRuntime().exec("ls -la --time-style=full-iso /proc/" + strArr[1] + "/stat");
		    		BufferedReader processGetCreationTimeReader = new BufferedReader(new InputStreamReader(processGetCreationTime.getInputStream()));
				    
				    line = null;
				    while ((line = processGetCreationTimeReader.readLine()) != null) {
				    	System.out.println(line);
				    	strArr = line.split("\\s+");
				    	String creationTime = strArr[5] + strArr[6] + strArr[7];
				    	processes.add(creationTime);
				    	if (debug) {
				    		System.out.println("Creation Time: " + creationTime);
				    	}
				    }
				    processGetCreationTimeReader.close();
		    	}
		    }
		    processReader.close();
		    
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return processes;
	}

}
