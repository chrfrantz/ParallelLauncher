package org.christopherfrantz.parallelLauncher.example;

import org.christopherfrantz.parallelLauncher.util.listeners.DynamicLauncherInstanceCounter;
import org.christopherfrantz.parallelLauncher.MetaLauncher;
import org.christopherfrantz.parallelLauncher.util.listeners.MetaLauncherListener;

/**
 * Examples use of MetaLauncher that manages multiple ParallelLaunchers that themselves
 * manage executables. Useful to maximize efficiency of processor use with diverse ParallelLauncher
 * types (or varying handling, such as passing parameters externally).
 *
 * @author Christopher Frantz
 *
 */
public class ExampleMetaLauncher extends MetaLauncher {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		addLaunchersToBeLaunched(ExampleLauncher.class, 2);
		addLaunchersToBeLaunched(ExampleLauncherPassingArguments.class, new DynamicLauncherInstanceCounter() {
			
			@Override
			public int totalNumberOfInstancesToBeLaunched() {
				return 6;
			}

			@Override
			public int remainingNumberOfInstancesToBeLaunched() {
				return -1;
			}
		});
		maxNumberOfQueuedOrRunningLaunchersAtOneTime = 3;
		//debug = true;
		createOneJarFileForAllLaunchers = true;
		start();
		registerMetaLauncherListener(new MetaLauncherListener() {
			
			@Override
			public void executionOfAllLaunchersFinished() {
				System.out.println("Only now will be executed whatever comes afterwards");
			}
			
		});
		
	}

}
