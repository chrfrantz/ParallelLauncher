package org.christopherfrantz.parallelLauncher.util.parameterHandling;

/**
 * Exception thrown when all parameters have been read from a parameter file (indicates completion of runs).
 *
 * @author Christopher Frantz
 *
 */
public class ParametersExhaustedException extends ParameterExtractorException {

	public ParametersExhaustedException(String prefix, String parameterFile){
		super(prefix + PARAMETERS_EXHAUSTED + parameterFile);
	}
	
	public static final String PARAMETERS_EXHAUSTED = "All parameters have been exhausted. Parameter file: "; 
	
}
