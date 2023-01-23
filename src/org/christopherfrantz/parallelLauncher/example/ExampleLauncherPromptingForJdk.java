package org.christopherfrantz.parallelLauncher.example;

import org.christopherfrantz.parallelLauncher.ParallelLauncher;

/**
 * Examples for launcher that explicitly prompts for JDK to be used to compile
 * and run executed code. Useful if specific JDKs should be used for compilation/execution.
 *
 * @author Christopher Frantz
 *
 */
public class ExampleLauncherPromptingForJdk extends ParallelLauncher {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		addClassToBeLaunched(IndependentExecutable1.class);
		
		/*
		 * Generally the system looks for a JDK on %PATH%. 
		 * However, if it could not find JDK on %PATH% or 
		 * an invalid path is specified, it will prompt
		 * the user to point to the JDK's location.
		 */
		
		/* Specify invalid JDK path, so system should prompt you.
		 * Valid JDK paths will be saved in config file in project 
		 * folder for future reuse. 
		 * If this file exists, the system will not prompt you but 
		 * reuse that path. Delete the file 'LauncherJdkBinPath' 
		 * in order to make the system prompt.
		 */
		jdkBinPath = "X:";
		
		start(args);
	}

}
