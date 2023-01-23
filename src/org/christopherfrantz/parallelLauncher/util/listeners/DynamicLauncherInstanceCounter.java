package org.christopherfrantz.parallelLauncher.util.listeners;

/**
 * Interface used in MetaLauncher to determine number of instances to be started dynamically, 
 * i.e. at launcher runtime (e.g. based on Parameter file entries).
 * 
 * @author Christopher Frantz
 *
 */
public interface DynamicLauncherInstanceCounter {

	/**
	 * Returns the number of remaining instances to be 
	 * run at a given point in time. Value may change between calls.
	 * Note: A return value of -1 indicates that this value should 
	 * be ignored and launcher should determine remaining 
	 * instances based on own monitoring.
	 * @return
	 */
	public int remainingNumberOfInstancesToBeLaunched();
	
	/**
	 * Returns the total number of instances to be run at a given point in time.
	 * Value may change between calls.
	 * @return
	 */
	public int totalNumberOfInstancesToBeLaunched();
	
}
