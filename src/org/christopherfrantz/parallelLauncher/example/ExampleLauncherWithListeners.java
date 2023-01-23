package org.christopherfrantz.parallelLauncher.example;

import org.christopherfrantz.parallelLauncher.ParallelLauncher;

/**
 * Exemplifies the use of listeners attached to specific executables.
 * Captured events include launch and termination of process.
 *
 * @author Christopher Frantz
 *
 */
public class ExampleLauncherWithListeners extends ParallelLauncher {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//add individual listener for class
		addClassToBeLaunched(IndependentExecutable1.class, new SpecialProcessStatusListener());
		//add same and another class without listener
		addClassToBeLaunched(IndependentExecutable1.class);
		addClassToBeLaunched(IndependentExecutable2.class);
		/*
		 * Add global listener, so we should get one console notification for every process, 
		 * and twice for the first process who has an additional individual listener instance.
		 * Note: If global and individual listener are the same instance, it is only called once.
		 */
		setGlobalProcessStatusListener(new SpecialProcessStatusListener());
		
		start(args);
	}

}
