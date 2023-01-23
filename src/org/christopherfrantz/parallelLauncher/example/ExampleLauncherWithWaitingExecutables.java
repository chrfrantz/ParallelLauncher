package org.christopherfrantz.parallelLauncher.example;

import org.christopherfrantz.parallelLauncher.ParallelLauncher;

/**
 * Exemplifies launcher with long-running single executable.
 *
 * @author Christopher Frantz
 *
 */
public class ExampleLauncherWithWaitingExecutables extends ParallelLauncher {

	public static void main(String[] args) {
		
		// Start single executable running for 40 seconds
		addClassToBeLaunched(IndependentExecutable1.class);
		
		// Start processes and Monitor GUI minimized
		startBatchProcessesMinimized = true;
		startInputOutputMonitorMinimized = true;
		
		start(args);
	}
	
}
