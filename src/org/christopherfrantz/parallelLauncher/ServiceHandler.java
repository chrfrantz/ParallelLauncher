package org.christopherfrantz.parallelLauncher;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.christopherfrantz.parallelLauncher.util.listeners.ProcessStatusListener;
import org.christopherfrantz.parallelLauncher.util.wrappers.ProcessWrapper;

/**
 * Special ProcessStatusListener implementation used to provide 
 * service functionality for ParallelLauncher such as clean-up 
 * of temporary files or adaptive queue check timing.
 * Will be automatically launched with each ParallelLauncher.
 * 
 * @author Christopher Frantz
 *
 */
public final class ServiceHandler implements ProcessStatusListener {

	/**
	 * Start time of first process
	 */
	private long startTime = -1;
	/**
	 * Termination time of last process
	 */
	private long stopTime = -1;
	/**
	 * Counter for processes at start
	 */
	private int startCounter = 0;
	/**
	 * Counter for terminated processes
	 */
	private int endCounter = 0;
	/**
	 * Stores eventually deviating exit code from launched class to pass up as result
	 */
	private int deviatingExitCode = 0;
	
	@Override
	public void executeDuringProcessLaunch(ProcessWrapper wrapper) {
		if(startTime == -1){
			//save only timing of first launched process
			startTime = System.currentTimeMillis();
			if(ParallelLauncher.adaptQueueCheckTimingDynamically){
				//save start time of first process to file
				ParallelLauncher.updateStartTimeConfiguration(startTime);
				if(ParallelLauncher.debug){
					System.out.println("QueueTimingOptimizer: Wrote start time of this process to runtime config. Start time: " + startTime);
				}
			}
		}
		startCounter++;
	}

	@Override
	public void executeAfterProcessTermination(ProcessWrapper wrapper) {
		stopTime = System.currentTimeMillis();
		endCounter++;
		if(wrapper.getExitCode() != 0){
			deviatingExitCode = wrapper.getExitCode();
		}
		System.out.println(ParallelLauncher.getCurrentTimeString(true) + ": Process '" + wrapper.getName() + "' finished (Return code: " + wrapper.getExitCode() + ").");
		//if same number of processes started as ended
		if(startCounter == endCounter 
				/* AND no outstanding process starts 
					(as launcher may be waiting for some of its processes to finish
					 before starting the remaining ones) */
				&& startCounter == ParallelLauncher.getNumberOfClassesToBeLaunched()){
			if(ParallelLauncher.adaptQueueCheckTimingDynamically){
				//if all processes have ended, write execution duration to file
				writeExecutionDurationToRuntimeConfig();
			}
			//In any case, attempt to clean up old JAR files.
			ParallelLauncher.cleanUpTemporaryJarFiles();
			
			//Print runtime
			Date date = new Date(System.currentTimeMillis() - startTime);
			DateFormat formatter = new SimpleDateFormat("HH:mm:ss");
			String dateFormatted = formatter.format(date);
			System.out.println("ParallelLauncher Runtime: " + dateFormatted);
			
			//Then wait a bit, ... -- not elegant!
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			/*
			 * ... before shutting down process and return eventual non-standard
			 * exit code from any launched process 
			 */
			System.exit(deviatingExitCode);
		}
	}
	
	/**
	 * Returns the number of running processes
	 * @return
	 */
	public int getNumberOfRunningProcesses(){
		return startCounter - endCounter;
	}
	
	/**
	 * Writes total launcher execution time (i.e. time from first started class to 
	 * last terminated one) into runtime configuration file used for adaptive 
	 * queue checks.
	 */
	private void writeExecutionDurationToRuntimeConfig(){
		ParallelLauncher.updateQueueCheckTimingConfiguration(stopTime - startTime);
		if(ParallelLauncher.debug){
			System.out.println("QueueTimingOptimizer: Wrote duration of last process to runtime config. Duration: " + (stopTime - startTime));
		}
	}

}
