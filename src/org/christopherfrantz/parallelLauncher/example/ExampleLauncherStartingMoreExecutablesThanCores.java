package org.christopherfrantz.parallelLauncher.example;

import org.christopherfrantz.parallelLauncher.ParallelLauncher;

/**
 * Example launcher running more executables than cores to test queueing facilities.
 *
 * @author Christopher Frantz
 *
 */
public class ExampleLauncherStartingMoreExecutablesThanCores extends ParallelLauncher {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		/*
		 * Starting more than cores available - should produce warning in console before starting
		 */
		for(int i = 0; i < Runtime.getRuntime().availableProcessors(); i++){
			addClassToBeLaunched(IndependentExecutable1.class);
		}
		//and start an additional one
		addClassToBeLaunched(IndependentExecutable2.class);
		
		//use all processors
		runOnLimitedProcessors = false;
		
		//allow more processors to be launched in parallel than cores available
		maxNumberOfRunningLaunchedProcesses = Runtime.getRuntime().availableProcessors() + 1;
		
		start(args);
	}

}
