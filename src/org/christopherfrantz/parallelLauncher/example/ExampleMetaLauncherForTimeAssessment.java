package org.christopherfrantz.parallelLauncher.example;

import org.christopherfrantz.parallelLauncher.MetaLauncher;
import org.christopherfrantz.parallelLauncher.util.listeners.MetaLauncherListener;

/**
 * This MetaLauncher implementation just runs simple long-running processes. Useful to assess the runtime prediction accuracy.
 *
 * @author Christopher Frantz
 *
 */
public class ExampleMetaLauncherForTimeAssessment extends MetaLauncher {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		addLaunchersToBeLaunched(ExampleLauncherWithWaitingExecutables.class, 20);

		
		maxNumberOfQueuedOrRunningLaunchersAtOneTime = 3;
		
		//createOneJarFileForAllLaunchers = true;
		
		start();
		registerMetaLauncherListener(new MetaLauncherListener() {
			
			@Override
			public void executionOfAllLaunchersFinished() {
				System.out.println("Only now will be executed whatever comes afterwards");
			}
			
		});
		
	}

}
