package org.christopherfrantz.parallelLauncher.util.listeners;

import org.christopherfrantz.parallelLauncher.util.wrappers.ProcessWrapper;

/**
 * The ProcessStatusListener can be implemented to have custom 
 * listeners that are invoked upon launch or termination of a 
 * process in order to provide flexible manipulation capabilities 
 * for processes run by ParallelLauncher.
 * 
 * ProcessStatusListener can be registered for specific launched 
 * classes as well as globally, i.e. for all classes launched in 
 * a particular ParallelLauncher. Use addClassToBeLaunched() method 
 * variants as well as setGlobalProcessStatusListener() in 
 * ParallelLauncher to register a listener. 
 * 
 * @author Christopher Frantz
 *
 */
public interface ProcessStatusListener {

	/**
	 * Is called immediately after launching a class and allows 
	 * manipulation of the process object (e.g. redirection of 
	 * input, output or error streams).
	 * The ProcessWrapper object gives access to the process object 
	 * as well as further information relevant for ParallelLauncher 
	 * (e.g. name of class executed in process).
	 * @param wrapper ProcessWrapper containing launched process object
	 */
	void executeDuringProcessLaunch(ProcessWrapper wrapper);
	
	/**
	 * Is called immediately after process termination in order to 
	 * monitor the execution or invoke application-specific post-processes. 
	 * The ProcessWrapper object exposes the process object itself 
	 * as well further information such name of executed class but 
	 * also exit code and process status.
	 * @param wrapper ProcessWrapper containing terminated process object
	 */
	void executeAfterProcessTermination(ProcessWrapper wrapper);

}
