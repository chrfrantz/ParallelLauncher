package org.christopherfrantz.parallelLauncher.example;

import org.christopherfrantz.parallelLauncher.ParallelLauncher;

/**
 * This example launcher starts more processes than allowed at one time. 
 * The launcher will manage the number of processes run at one time 
 * based on the specified constraints and starts further ones 
 * successively once the previous have finished to stay below the limit.
 * 
 * @author Christopher Frantz
 *
 */
public class ExampleLauncherManagingMoreExecutablesThanAllowedAtOneTime extends ParallelLauncher {

	public static void main(String[] args){
		
		/*
		 * Starting more than cores available
		 */
		for(int i = 0; i < 3; i++){
			addClassToBeLaunched(IndependentExecutable1.class);
		}
		//and start an additional one
		addClassToBeLaunched(IndependentExecutable2ReturningNonZeroExitCode.class);
		
		runOnLimitedProcessors = true;
		//only start limited number of processes - adjust if machine has only few cores to see effect.
		maxNumberOfRunningLaunchedProcesses = 2;
		
		//test stdout and stderr redirection
		redirectStdOutAndStdErrForLaunchedProcesses = true;
				
		start(args);
		
	}
	
}
