package org.christopherfrantz.parallelLauncher.example;

import org.christopherfrantz.parallelLauncher.ParallelLauncher;

/**
 * This is an example how the ParallelLauncher should be used.
 * You can run this class multiple times in a new JVM instance 
 * (e.g. in Eclipse -> Run As ... -> Java Application) as soon 
 * as the previous launcher instance tells you that it is safe 
 * to do so).<BR>
 * More information can be found in the ParallelLauncher javadoc.<BR>
 * <BR>
 * @author Christopher Frantz
 *
 */
public class ExampleLauncher extends ParallelLauncher {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		/*
		 * Indicate that this class will be launcher class.
		 * Should work automatically, but if system cannot infer it automatically, 
		 * this is the way to do it:
		 */
		launcherClass = ExampleLauncher.class;
		/*
		 * Define some classes to be launched. 
		 * They need to have main methods, otherwise they are 
		 * ignored by the launcher.
		 */
		addClassToBeLaunched(IndependentExecutable1.class);
		addClassToBeLaunched(IndependentExecutable2.class);
		
		//can also include multiple instances of same class: 
		addClassToBeLaunched(IndependentExecutable2.class);
		
		//can also just test if some classes are running without launching them afterwards
		//check only
		addClassToBeLaunched(IndependentExecutable1.class, true);
		//check and run (same as addClassToBeLaunched( ... ))
		addClassToBeLaunched(IndependentExecutable1.class, false);
		
		//class without any main method (should not be executed)
		addClassToBeLaunched(InvalidExecutable.class);
		
		//debug = true;
		setQueueCheckingFrequency(0.5);
		
		//start processes and Monitor GUI minimized
		startBatchProcessesMinimized = true;
		startInputOutputMonitorMinimized = true;
		
		//start the launcher
		start(args);
	}

}
