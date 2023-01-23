package org.christopherfrantz.parallelLauncher.example;

import org.christopherfrantz.parallelLauncher.ParallelLauncher;

/**
 * Exemplifies the overriding of a globally generated shared classpath for all instances,
 * and instead passing it via the parameters. Associated risk is the limited permissible
 * length of parameters, leading to incomplete or invalid classpath information for the
 * executable. The practice of passing individual classpaths is hence discouraged.
 *
 * @author Christopher Frantz
 *
 */
public class ExampleLauncherUsingClasspathParameterVersion extends ParallelLauncher {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		/*
		 * Per default, each process is launched 
		 * from an environment with a local %CLASSPATH%.
		 * If switched off, the classpath is passed 
		 * as part of the command line (as parameter).
		 * However, this limits the length of the 
		 * classpath. Thus, leaving it on is 
		 * recommended.
		 * 
		 * Here we intentionally switch to the 
		 * command line version, just for testing.
		 */
		createTemporaryClasspathVariable = false;

		/*
		 * To see the effects, generated batch files 
		 * should not be deleted, so you can have a 
		 * look at the different versions.
		 */
		deleteBatchFilesAfterStart = false;
		
		addClassToBeLaunched(IndependentExecutable1.class);
		
		start(args);
		
	}

}
