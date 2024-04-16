package org.christopherfrantz.parallelLauncher.example;

import org.christopherfrantz.parallelLauncher.ParallelLauncher;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Launcher exemplifying specification of known process for inquiring OS processes.
 * This is useful to detect potentially unreliable reporting of running executables
 * (processes) by the OS - to guard against underreporting of actually running instances.
 *
 * @author Christopher Frantz
 *
 */
public class ExampleLauncherSpecifyingKnownRunningProcess extends ParallelLauncher {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		/*
		 * Configuring known running process name is 
		 * useful, because the WMI is unreliable when 
		 * exposed to many queries. Knowing a third-party 
		 * process that should appear in any result of 
		 * all running processes can improve the robustness 
		 * of ParallelLauncher, even if WMI is overused.
		 */
		checkForKnownProcessAsWmiFailureBackupCheck = true;
		knownRunningProcessNames = new ArrayList<>(Arrays.asList("explorer.exe"));
		
		addClassToBeLaunched(IndependentExecutable1.class);
		
		start(args);
	}

}
