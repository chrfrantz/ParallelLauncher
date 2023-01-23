package org.christopherfrantz.parallelLauncher.util;

/**
 * Classifies return state from OS based on validity, and produces human-readable output.
 *
 * @author Christopher Frantz
 *
 */
public class LauncherWaitStateResolver {

	/**
	 * Resolves a given ParallelLauncher queue check response into 
	 * human-readable information. Useful for console output.
	 * @param code Code returned upon queue check in ParallelLauncher
	 * @return
	 */
	public static String resolveLauncherWaitState(int code){
		switch(code){
		case -2:
			return "BlockingLauncher running - preventing any launcher start";
		case -1:
			return "Invalid WMI response";
		}
		return "Number " + (code + 1) + " in launcher queue";
	}
	
}
