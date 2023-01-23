package org.christopherfrantz.parallelLauncher.util.listeners;

/**
 * Interface for MetaLauncherListener called when all launchers are finished.
 *
 * @author Christopher Frantz
 *
 */
public interface MetaLauncherListener {

	/**
	 * Is called once all scheduled ParallelLaunchers have
	 * finished(!) their execution. It is invoked as the final 
	 * task in a MetaLauncher.
	 */
	public void executionOfAllLaunchersFinished();
	
}
