package org.christopherfrantz.parallelLauncher.example;

import org.christopherfrantz.parallelLauncher.ParallelLauncher;

/**
 * This launcher exemplifies the passing of arguments to launched instances
 * (used for passing parameter values, for instance).
 *
 * @author Christopher Frantz
 *
 */
public class ExampleLauncherPassingArguments extends ParallelLauncher {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		/*
		 * Specification of launcherClass (i.e. this implementation) 
		 * is optional but recommended to ensure smooth operation.
		 */
		launcherClass = ExampleLauncherPassingArguments.class;
		
		addClassToBeLaunched(IndependentExecutable1.class);
		addClassToBeLaunched(IndependentExecutable2.class);
		
		String[] argArray = new String[2];
		/*
		 * Define arguments. Be mindful to avoid white spaces (which split arguments).
		 * Launcher will warn you on start if you do so...
		 */
		argArray[0] = "FirstArg";
		argArray[1] = "SecondArg";
		/*
		 * Set arguments that are to be passed to launched classes.
		 */
		setArgumentsToBePassedToLaunchedClasses(argArray);
		
		//debug = true;
		setQueueCheckingFrequency(0.5);
		start(args);
	}

}
