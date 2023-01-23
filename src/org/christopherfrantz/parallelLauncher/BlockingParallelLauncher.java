package org.christopherfrantz.parallelLauncher;

/**
 * This launcher is a special ParallelLauncher type. Once started it runs 
 * infinitely and prevents any other waiting launcher instances from starting.
 * It is suitable to setup a large number of launchers without being interrupted
 * by starting launchers (especially if they cause high CPU load).<BR>
 * Another purpose is to prevent waiting ParallelLaunchers from starting without 
 * destroying them (e.g. system maintenance of an otherwise unusable system). 
 * In this case the running launcher finishes its job, but no further will 
 * start until the BlockingParallelLauncher is stopped.
 * <BR><BR>
 * Note: This launcher does not stop automatically but requires you to stop it!
 * 
 * @author Christopher Frantz
 *
 */
public class BlockingParallelLauncher extends ParallelLauncher {


	public static void main(String[] args) {
		launcherClass = BlockingParallelLauncher.class;
		ParallelLauncher.main(args);
	}

}
