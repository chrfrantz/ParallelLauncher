package org.christopherfrantz.parallelLauncher.example;

import org.christopherfrantz.parallelLauncher.ParallelLauncher;

/**
 * Determining affinity mask for current machine. Helpful for machine-specific configuration of launcher.
 *
 * @author Christopher Frantz
 *
 */
public class ProcessorAffinityStringTest extends ParallelLauncher {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		debug = true;
		setAffinityMaskForCores(true, 0);

	}

}
