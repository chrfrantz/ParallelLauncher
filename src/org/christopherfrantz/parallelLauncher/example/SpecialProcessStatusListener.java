package org.christopherfrantz.parallelLauncher.example;

import org.christopherfrantz.parallelLauncher.util.listeners.ProcessStatusListener;
import org.christopherfrantz.parallelLauncher.util.wrappers.ProcessWrapper;

/**
 * This SpecialProcessStatusListener is an example of how ProcessStatusListeners 
 * can be used for arbitrary operations (here merely printing to console).
 * It is used in conjunction with the ExampleLauncherWithListeners ParallelLauncher 
 * implementation in the examples package.
 * 
 * @author Christopher Frantz
 *
 */
public class SpecialProcessStatusListener implements ProcessStatusListener {
	
	@Override
	public void executeDuringProcessLaunch(ProcessWrapper wrapper) {
		System.out.println("Listener output: Process '" + wrapper.getName() + "' has started.");
	}
	
	@Override
	public void executeAfterProcessTermination(ProcessWrapper wrapper) {
		System.out.println("Listener output: Process '" + wrapper.getName() + "' has terminated with exit code " + wrapper.getExitCode());
	}
	
};
