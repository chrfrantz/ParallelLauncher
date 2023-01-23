package org.christopherfrantz.parallelLauncher.example;

import org.christopherfrantz.parallelLauncher.ParallelLauncher;

/**
 * Tests capturing lacking specification of executables.
 *
 * @author Christopher Frantz
 *
 */
public class ExampleLauncherWithoutLaunchedClasses extends ParallelLauncher {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//should exit immediately as nothing to run....
		start(args);
	}

}
