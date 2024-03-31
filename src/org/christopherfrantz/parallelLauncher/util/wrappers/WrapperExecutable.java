package org.christopherfrantz.parallelLauncher.util.wrappers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.security.Permission;
import org.apache.commons.io.FileUtils;
import org.christopherfrantz.parallelLauncher.util.MultiOutputStream;

/**
 * Wraps an executable upon instantiation in order to control standard input and output streams,
 * as well as status codes for error reporting.
 *
 * @author Christopher Frantz
 *
 */
public class WrapperExecutable {

	/**
	 * Constant indicating that both stderr and stdout should be redirected 
	 * to file (along with console output).
	 */
	public static final String REDIRECT_BOTH = "REDIRECT_BOTH";
	
	/**
	 * Constant indicating that only stderr should be redirected 
	 * to file (along with console output).
	 */
	public static final String REDIRECT_STDERR = "REDIRECT_STDERR";

	/**
	 * Constant indicating that neither stderr nor stdout should be redirected 
	 * to file, but only printed on console.
	 */
	public static final String REDIRECT_NONE = "REDIRECT_NONE";
	
	/**
	 * Expected number of parameters from invoking ParallelLauncher.
	 * Will not change unless ParallelLauncher implementation is modified.
	 */
	private static final int numberOfExpectedParameters = 4;
	
	/**
	 * Field holding identifier for launched class file as specified by ParallelLauncher
	 */
	private static String identifier = null;
	
	/**
	 * Name for outfile containing redirected stdout/stderr content.
	 */
	private static String redirectionOutFilename = null;
	
	/**
	 * Field holding name of class to be launched
	 */
	private static String classNameOfClassToBeLaunched = null;
	
	public static void main(String[] args){
		// expect at least three parameters, but there may be more that are passed on to the executable
		if(args.length < numberOfExpectedParameters){
			throw new RuntimeException("Attempted to run executable from ParallelLauncher failed: " +
					"Insufficient number of parameters in WrapperExecutable.");
		}
		// expect first parameter to be redirection information
		// expect second parameter to redirection outfile - will be passed along as null if redirection deactivated
		redirectionOutFilename = args[1];
		
		// perform redirection
		redirect(args[0], redirectionOutFilename);
		
		// third parameter: identifier for launched class file
		identifier = args[2];
		
		// prepare eventual parameters for main method
		final String[] parametersForMainMethod = new String[args.length - numberOfExpectedParameters];
		if(args.length > numberOfExpectedParameters){
			int paramCt = 0;
			for(int i = numberOfExpectedParameters; i < args.length; i++){
				parametersForMainMethod[paramCt] = args[i];
				paramCt++;
			}
		}
		// register shutdown hook to handle exit code of class to be invoked
		addExitCodeCapturingShutdownHook();
		
		// save name of class to be launched
		classNameOfClassToBeLaunched = args[3];
		
		// expect fourth parameter to be executable class
		try {
			Class classToBeRun = Class.forName(classNameOfClassToBeLaunched);
			//System.out.println("Invoking class " + classToBeRun.getCanonicalName() + " with " + parametersForMainMethod.length + " parameters.");
			// invoke passed executables main method
			classToBeRun.getMethod("main", String[].class).invoke(null, (Object)parametersForMainMethod);
		} catch (Exception e) { // Should capture everything, including RuntimeException -
			// Launch-related specific exceptions: (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			if(e.getClass().equals(InvocationTargetException.class)){
				System.err.println("Exception in launched executable '"+ classNameOfClassToBeLaunched + "':");
				e.getCause().printStackTrace();
				saveMessageToErrorFile(e.getCause().getMessage());
			} else {
				e.printStackTrace();
				saveMessageToErrorFile(e.getMessage());
			}
		}
	}
	
	/**
	 * Sets up redirection according to redirection type and routes output to specified file 
	 * in addition to console output. Creates file if not already existing.
	 * @param type redirection type as specified in constants in {@link WrapperExecutable}.
	 * @param filename
	 */
	private static void redirect(String type, String filename){
		if(type.equals(REDIRECT_NONE)){
			//do nothing
			return;
		} else {
			boolean redirect = false;
			File stdOutFile = new File(filename);
			if(!stdOutFile.exists()){
				try {
					if(!stdOutFile.createNewFile()){
						String message = "Unable to create new file " + stdOutFile.getAbsolutePath() + ", cannot redirect StdOut and StdErr.";
						saveMessageToErrorFile(message);
						System.err.println(message);
					} else {
						redirect = true;
					}
				} catch (IOException e) {
					String message = "Exception when creating StdOut/StdErr redirection file. Error: " + e.getMessage();
					saveMessageToErrorFile(message);
					System.err.println(message);
				}
			} else {
				redirect = true;
			}
			if(redirect){
				FileOutputStream outWriter;
				try {
					outWriter = new FileOutputStream(filename);
					
					// Combined OutputStream instance (file and console) for error
					MultiOutputStream multiErr = new MultiOutputStream(System.err, outWriter);
					// Redirect stderr in any case
					System.setErr(new PrintStream(multiErr));
					
					// Redirect stdout only if requested
					if(type.equals(REDIRECT_BOTH)){
						MultiOutputStream multiOut = new MultiOutputStream(System.out, outWriter);
						System.setOut(new PrintStream(multiOut));
					}
					
				} catch (FileNotFoundException e) {
					String message = "Error writing to StdOut/StdErr output " + stdOutFile.getName() + ", Error: " + e.getMessage();
					saveMessageToErrorFile(message);
					System.err.println(message);
				}
			}
		}
	}
	
	/**
	 * Adds a shutdown hook to capture exit codes raised via System.exit()
	 * prior to shutting down VM.
	 * Inspired by http://stackoverflow.com/questions/1486679/determine-exit-status-within-the-java-shutdown-hook-thread
	 */
	private static void addExitCodeCapturingShutdownHook(){
		System.setSecurityManager(new ExitMonitorSecurityManager());
		Runtime.getRuntime().addShutdownHook(new Thread(new ExitCodeCapturingShutdownHook()));
	}
	
	/**
	 * Modified security manager to access exit code prior to VM shutdown.
	 * 
	 * @author Christopher Frantz
	 *
	 */
	private static class ExitMonitorSecurityManager extends SecurityManager {

    	@Override
    	public void checkPermission(Permission perm) {
    		//System.out.println(perm.getName());
    		//System.out.println(perm.getActions());
    	}

    	@Override
    	public void checkPermission(Permission perm, Object context) {
    		//System.out.println(perm.getName());
    		//System.out.println(perm.getActions());
    	}

    	@Override
    	public void checkExit(int status) {
    		//System.out.println("Setting exit value via security manager...");
    		ExitCodeCapturingShutdownHook.EXIT_STATUS = status;
    	}
    }
	
	/**
	 * Shutdown hook handling exit code prior to actually shutting down VM.
	 * @author Christopher Frantz
	 *
	 */
	private static class ExitCodeCapturingShutdownHook implements Runnable {

    	public static Integer EXIT_STATUS;

    	public void run() {
    		System.out.println("Exit code is " + EXIT_STATUS);
    		if(EXIT_STATUS != 0){
    			saveMessageToErrorFile("Exit code: " + EXIT_STATUS);
    		}
    	}
    	
    }
	
	/**
	 * Writes a given message to an error file constructed from process identifier and class name.
	 * @param message Message to be saved
	 */
	private static void saveMessageToErrorFile(String message){
		String errorFilename = null;
		if(message != null && !message.isEmpty()){
			try {
				errorFilename = identifier + "_" 
						+ (redirectionOutFilename != null && !redirectionOutFilename.equals("null") ? 
								//use redirection outfilename as a basis for construction if not null
								redirectionOutFilename.substring(0, redirectionOutFilename.lastIndexOf("_")) : 
								Class.forName(classNameOfClassToBeLaunched).getSimpleName())
						+ "_Error";
				File file = new File(errorFilename);
				FileUtils.write(file, message, true);
			} catch (ClassNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
}
