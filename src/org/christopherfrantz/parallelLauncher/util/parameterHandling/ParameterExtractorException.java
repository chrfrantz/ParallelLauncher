package org.christopherfrantz.parallelLauncher.util.parameterHandling;

/**
 * Exception issued upon failure to read parameter from file.
 *
 * @author Christopher Frantz
 *
 */
public class ParameterExtractorException extends Exception{

	public ParameterExtractorException(String message){
		super(message);
	}
	
	public static final String EXTRACTOR_ERROR = "Error during parameter extraction.";
	
}
