package org.christopherfrantz.parallelLauncher.util.wrappers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import org.apache.commons.io.FileUtils;
import org.christopherfrantz.parallelLauncher.MetaLauncher;
import org.christopherfrantz.parallelLauncher.ParallelLauncher;
import org.christopherfrantz.parallelLauncher.util.listeners.ProcessStatusListener;

/**
 * Creates explicit Java process that runs (wrapped) executable in order to afford high-level
 * monitoring and control of process execution. Fine-grained control of executable is performed
 * by WrapperExecutable that wraps executable and returns status information.
 *
 * @author Christopher Frantz
 *
 */
public class ProcessWrapper {

	private boolean debug = (ParallelLauncher.debug || MetaLauncher.debug);
	
	/**
	 * Wrapped process reference
	 */
	private Process process;

	/**
	 * Returns reference to wrapped process.
	 * @return
	 */
	public Process getProcess() {
		return process;
	}
	
	/**
	 * Human-readable name of class launched in process
	 */
	private String name;
	
	/**
	 * Returns a human-readable description of class launched in process.
	 * @return
	 */
	public String getName(){
		return name;
	}

	/**
	 * Indicator if process has finished
	 */
	private boolean isFinished = false;

	/**
	 * Indicates if wrapped process has finished its operation.
	 * @return
	 */
	public boolean isFinished() {
		return isFinished;
	}

	/**
	 * Process exit code.
	 */
	private int exitCode;

	/**
	 * Returns exit code the process return upon termination.
	 * @return
	 */
	public int getExitCode() {
		return exitCode;
	}

	/**
	 * Registered ProcessStatusListeners for the process wrapped in this 
	 * ProcessWrapper
	 */
	private ArrayList<ProcessStatusListener> listeners = new ArrayList<>();
	
	/**
	 * Registers ProcessStatusListener for wrapped process.
	 * @param listener
	 */
	public void registerListener(ProcessStatusListener listener){
		if(!listeners.contains(listener)){
			if(debug){
				System.out.println(ParallelLauncher.getCurrentTimeString(true) 
						+ ": ProcessWrapper: Registered listener for process running class " + name);
			}
			this.listeners.add(listener);
		}
	}
	
	/**
	 * Script file to be deleted upon process termination.
	 */
	private File scriptFileToBeDeleted = null;
	
	/**
	 * Registers a scriptfile which is going to be deleted once the process 
	 * terminates.
	 * @param scriptfile Script file reference
	 */
	public void registerScriptFileToBeDeletedAfterProcessTermination(File scriptfile){
		if(debug){
			System.out.println(ParallelLauncher.getCurrentTimeString(true) 
					+ ": ProcessWrapper for '" + name + "': Registered script file " 
					+ scriptfile.getName() + " for deletion.");
		}
		this.scriptFileToBeDeleted = scriptfile;
	}
	
	/**
	 * Notifies ProcessStatusListeners upon termination of process.
	 */
	private void notifyListeners(){
		if(debug){
			System.out.println(ParallelLauncher.getCurrentTimeString(true) 
					+ ": ProcessWrapper: Notifying listeners about termination of process running class " + name);
		}
		for(int i = 0; i < this.listeners.size(); i++){
			listeners.get(i).executeAfterProcessTermination(this);
		}
	}
	
	/**
	 * The ProcessWrapper wraps a process' operation for easy accessibility of 
	 * status.
	 * @param name Human-readable process name - used for output
	 * @param process Process reference
	 * @param instantiator Which launcher instantiated this ProcessWrapper. Could be MetaLauncher or ParallelLauncher.
	 */
	public ProcessWrapper(final String name, final Process process, final Class instantiator) {
		this.process = process;
		this.name = name;
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					exitCode = process.waitFor();
				} catch (InterruptedException e) {
					System.err.println(ParallelLauncher.getCurrentTimeString(true) 
							+ ": "
							+ instantiator.getSimpleName()
							+ ": Error when waiting for process to finish. Error: " + e.getMessage());
					//e.printStackTrace();
				}
				//System.out.println("Process " + name + " has finished. Will do all notification operations.");
				isFinished = true;
				//deleting created batch file
				if(scriptFileToBeDeleted != null){
					FileUtils.deleteQuietly(scriptFileToBeDeleted);
					if(debug){
						System.out.println(ParallelLauncher.getCurrentTimeString(true) + ": Script "
								+ scriptFileToBeDeleted.getName() + " should have been deleted by now.");
					}
				}
				if (exitCode != 0 || debug) {
					//print exit code if != 0 or debugging activated
					System.out.println(ParallelLauncher
							.getCurrentTimeString(true)
							+ ": "
							+ instantiator.getSimpleName()
							+ ": Process '"
							+ name
							+ "' ended with exit value " + getExitCode());
					//print stdout output if exit code != 0 and debugging activated
					if(
							(MetaLauncher.debug || ParallelLauncher.debug) 
							&& exitCode != 0) {
						System.out.println(ParallelLauncher
								.getCurrentTimeString(true)
								+ ": "
								+ instantiator.getSimpleName()
								+ ": '"
								+ name
								+ "' standard output: "
								+ System.getProperty("line.separator")
								+ convertStreamToString(process
										.getInputStream()));
					}
					//print stderr output if exit code != 0
					if(exitCode != 0){
						System.out.println(ParallelLauncher
								.getCurrentTimeString(true)
								+ ": "
								+ instantiator.getSimpleName()
								+ ": '"
								+ name
								+ "' error output: "
								+ System.getProperty("line.separator")
								+ convertStreamToString(process
										.getErrorStream()));
					}
				}
				//notify all listeners regarding termination once all other activity is done
				notifyListeners();
			}

		}).start();
	}

	/**
	 * Converts InputStreams (e.g. from stdout and stderr) to 
	 * Strings for the purpose of printing.
	 * @param stream Stream to be converted
	 * @return String representation of stream content
	 */
	private static String convertStreamToString(InputStream stream) {

		StringBuilder outString = new StringBuilder();
		int chr;
		try {
			while ((chr = stream.read()) != -1) {
				outString.append(String.valueOf((char) chr));
			}
			return outString.toString();
		} catch (IOException e) {
			System.err.println(ParallelLauncher.getCurrentTimeString(true) 
					+ ": MetaLauncher: Error converting InputStream to String. Error: " + e.getMessage());
			//e.printStackTrace();
		}
		return null;
	}

}
