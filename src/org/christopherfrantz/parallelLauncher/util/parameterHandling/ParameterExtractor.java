package org.christopherfrantz.parallelLauncher.util.parameterHandling;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.StringTokenizer;

import org.apache.commons.io.FileUtils;

/**
 * The ParameterExtractor facilitates the extraction of parameter tuples for
 * each request to ensure unique parameterization for simulations, e.g., to perform
 * sensitivity analyses. Corresponding input files are generated via ParameterFileGenerator.
 *
 * @author Christopher Frantz
 *
 */
public class ParameterExtractor {

	/**
	 * Prefix used for console messages from ParameterExtractor
	 */
	public static final String PREFIX = "Parameter Extractor: ";
	
	/**
	 * Parameter file holding parameters used as inputs
	 */
	public static String parameterFile = "Parameters.txt";
	
	/**
	 * File holding used parameters for tracking progress. If not 
	 * specified by user, it will be auto-generated based on {@link #parameterFile}.
	 */
	public static String usedParameterFile = null;
	
	/**
	 * Returns file to be used for used parameters. Will be automatically 
	 * generated based on {@link #parameterFile} if not explicitly specified by user.
	 * @param overrideParameterFile Parameter filename overriding class field. Will be 
	 * used to generate used parameter filename dynamically (not {@link #parameterFile}), 
	 * unless explicitly specified in {@link #usedParameterFile}.
	 * @return
	 */
	public static String usedParameterFile(String overrideParameterFile){
		return usedParameterFile == null ? "used" + 
					(overrideParameterFile != null && !overrideParameterFile.isEmpty() ? 
							overrideParameterFile : parameterFile) : 
					usedParameterFile;
	}
	
	/**
	 * Returns list of all parameter names in parameter file (and the number of value variations of respective parameter).
	 * @param parameterFile
	 * @return
	 * @throws ParameterExtractorException
	 */
	public static ArrayList<String> getAllParameterNames(String parameterFile) throws ParameterExtractorException {
		File paramFile = new File(parameterFile);
		ArrayList<String> parameterNames = new ArrayList<>();
		if(paramFile.exists()){
			String firstLine;
			try {
				firstLine = FileUtils.lineIterator(paramFile).next();
				if(firstLine != null) {
					firstLine = firstLine.trim();
					if(firstLine.startsWith(ParameterFileGenerator.PARAMETER_FILE_HEADER_START)) {
						// Iterate through headers, but swallow header symbols at the beginning ...
						StringTokenizer tokenizer = new StringTokenizer(firstLine.substring(firstLine.indexOf(ParameterFileGenerator.PARAMETER_FILE_HEADER_START) + 
								ParameterFileGenerator.PARAMETER_FILE_HEADER_START.length(), firstLine.length()), ParameterFileGenerator.PARAMETER_FILE_HEADER_SEPARATOR);
						while(tokenizer.hasMoreTokens()) {
							String tok = tokenizer.nextToken();
							if (tok.contains(ParameterFileGenerator.PARAMETER_FILE_HEADER_START)) {
								// ... and at the end
								parameterNames.add(tok.substring(0, tok.indexOf(ParameterFileGenerator.PARAMETER_FILE_HEADER_START)).trim());
							} else {
								parameterNames.add(tok.trim());
							}
						}
					} else {
						throw new ParameterExtractorException("Parameter file '" + parameterFile + "' does not contain header line.");
					}
				} else {
					throw new ParameterExtractorException("Parameter file '" + parameterFile + "' does not contain content.");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return parameterNames;
		}
		throw new ParameterExtractorException("Parameter file '" + parameterFile + "' could not be found.");
	}
	
	/**
	 * Returns the index for a given parameter name for the parameter arrays contained in parameter file.
	 * Returns -1 if parameter name could not found. Throws exception if parameter file or header could not be found.
	 * @param parameterFile Parameter file to inspect
	 * @param parameterName Parameter of interest (for which the index is to be returned)
	 * @return Index of instance array indicating the parameter value
	 * @throws ParameterExtractorException 
	 */
	public static int getArrayIndexForParameterArrayByParameterName(String parameterFile, String parameterName) throws ParameterExtractorException {
		File paramFile = new File(parameterFile);
		if(paramFile.exists()){
			try {
				String firstLine = FileUtils.lineIterator(paramFile).next();
				if(firstLine != null) {
					firstLine = firstLine.trim();
					if(firstLine.startsWith(ParameterFileGenerator.PARAMETER_FILE_HEADER_START)) {
						StringTokenizer tokenizer = new StringTokenizer(firstLine, ParameterFileGenerator.PARAMETER_FILE_HEADER_SEPARATOR);
						int index = 0;
						while(tokenizer.hasMoreTokens()) {
							String token = tokenizer.nextToken().trim();
							
							if (token == null || token.isEmpty() || !token.contains(parameterName)) {
								// should not really be empty ever, but guarding against format manipulations
								index++;
								continue;
							}
							
							// Extract string without trailing parentheses containing number of unique values for parameter " (1)"
							token = token.substring(0, token.lastIndexOf("(")).trim();
							
							if (token.startsWith(ParameterFileGenerator.PARAMETER_FILE_HEADER_START)) {
								// Chop off leading file header if found in token ("### ")
								token = token.substring(ParameterFileGenerator.PARAMETER_FILE_HEADER_START.length()).trim();
							}
							if (token.endsWith(ParameterFileGenerator.PARAMETER_FILE_HEADER_START)) {
								// Chop off trailing file header if found in token (" ###") -- should practically be dealt with by removal of parentheses - but guarding against format change
								token = token.substring(0, token.length() - ParameterFileGenerator.PARAMETER_FILE_HEADER_START.length()).trim();
							}
							
							if(token.equals(parameterName)) {
								return index;
							}
							// Increase index to point to next parameter
							index++;
						}
					} else {
						throw new ParameterExtractorException("Parameter file '" + parameterFile + "' does not contain header line.");
					}
				} else {
					throw new ParameterExtractorException("Parameter file '" + parameterFile + "' does not contain content.");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		throw new ParameterExtractorException("Parameter file '" + parameterFile + 
				"' does not exist or does not contain parameter name header '" + parameterName + "'.");
	}
	
	/**
	 * Returns parameter value from parameter array (parameter set of individual simulation instance) by parameter name.
	 * @param parameterFile Parameter file used to identify parameter names.
	 * @param parameterName Parameter of concern
	 * @param parameterArray Simulation instance parameter set passed to simulation instance and to be extracted
	 * @return Value for parameter as String
	 * @throws ParameterExtractorException
	 */
	public static String getParameterValueFromParameterArrayByParameterName(String parameterFile, String parameterName, String[] parameterArray) throws ParameterExtractorException {
		return parameterArray[getArrayIndexForParameterArrayByParameterName(parameterFile, parameterName)];
	}
	
	/**
	 * Extracts parameter for given file and name from given parameter value set. Handles typecasting
	 * for selected parameter types (Integer, Float, Double, Long, String). A special case are combined
	 * parameters (targeted permutations produced by ParameterFileGenerator). Those are returned as 
	 * (ordered) ArrayList<String>. 
	 * If stringified arrays are passed, they should be passed in the format "ArrayFloat+0.3_0.3_0.3-" 
	 * (Example for float). Types Float, Integer, Double, Short, Long and String are supported. The type
	 * should be indicated as the type parameter (e.g. Float[].class for float).
	 * The format produced by ParameterFileGenerator is compatible.
	 * If type is unspecified, this method treats it as String.
	 * @param parameterFile Parameter file containing all specifications.
	 * @param parameterName Parameter name (whose array position is to be matched based on parameter file)
	 * @param parameterArray Parameter set the value is to be extracted from
	 * @param type Parameter data type (class)
	 * @return Extracted parameter value as specified type
	 * @throws ParameterExtractorException
	 */
	public static <T> T getParameterValueFromParameterArrayByParameterName(String parameterFile, String parameterName, String[] parameterArray, Class<T> type) throws ParameterExtractorException {
		
		if (parameterArray == null || parameterArray.length == 0) {
			throw new ParameterExtractorException(PREFIX + "Parameter array is null or empty!");
		}
		
		String value = null;		
		try {
			value = parameterArray[getArrayIndexForParameterArrayByParameterName(parameterFile, parameterName)];
		} catch(ParameterExtractorException e) {
			throw new ParameterExtractorException(e.getMessage());
		}
		
		// Dealing with stringified arrays
		if((type == Float[].class || 
				type == Integer[].class || 
				type == Double[].class || 
				type == Short[].class || 
				type == Long[].class ||
				type == String[].class) && new String(value).startsWith(ParameterFileGenerator.ARRAY_PREFIX)) {
			// Raw string
			String arrString = new String(value);
			// Decomposition of array type (+0.3_0.3_0.3-)
			String arrType = arrString.substring(0, arrString.indexOf(ParameterFileGenerator.ARRAY_OPENING_BRACKET));
			StringTokenizer tokenizer = new StringTokenizer(arrString.substring(arrString.indexOf(ParameterFileGenerator.ARRAY_OPENING_BRACKET) + 1, 
					arrString.length()-1), ParameterFileGenerator.ARRAY_VALUE_DELIMITER);
			switch (arrType) {
				case ParameterFileGenerator.ARRAY_PREFIX + ParameterFileGenerator.ARRAY_TYPE_FLOAT:
					ArrayList<Float> valuesFloat = new ArrayList<>();
					// Tokenize but ignore opening and closing bracket
					while (tokenizer.hasMoreTokens()) {
						valuesFloat.add(Float.valueOf(tokenizer.nextToken()));
					}
					return (T) valuesFloat.toArray(new Float[valuesFloat.size()]);
				case ParameterFileGenerator.ARRAY_PREFIX + ParameterFileGenerator.ARRAY_TYPE_INTEGER:
					ArrayList<Integer> valuesInt = new ArrayList<>();
					// Tokenize but ignore opening and closing bracket
					while (tokenizer.hasMoreTokens()) {
						valuesInt.add(Integer.valueOf(tokenizer.nextToken()));
					}
					return (T) valuesInt.toArray(new Integer[valuesInt.size()]);
				case ParameterFileGenerator.ARRAY_PREFIX + ParameterFileGenerator.ARRAY_TYPE_DOUBLE:
					ArrayList<Double> valuesDouble = new ArrayList<>();
					// Tokenize but ignore opening and closing bracket
					while (tokenizer.hasMoreTokens()) {
						valuesDouble.add(Double.valueOf(tokenizer.nextToken()));
					}
					return (T) valuesDouble.toArray(new Double[valuesDouble.size()]);
				case ParameterFileGenerator.ARRAY_PREFIX + ParameterFileGenerator.ARRAY_TYPE_LONG:
					ArrayList<Long> valuesLong = new ArrayList<>();
					// Tokenize but ignore opening and closing bracket
					while (tokenizer.hasMoreTokens()) {
						valuesLong.add(Long.valueOf(tokenizer.nextToken()));
					}
					return (T) valuesLong.toArray(new Long[valuesLong.size()]);
				case ParameterFileGenerator.ARRAY_PREFIX + ParameterFileGenerator.ARRAY_TYPE_SHORT:
					ArrayList<Short> valuesShort = new ArrayList<>();
					// Tokenize but ignore opening and closing bracket
					while (tokenizer.hasMoreTokens()) {
						valuesShort.add(Short.valueOf(tokenizer.nextToken()));
					}
					return (T) valuesShort.toArray(new Short[valuesShort.size()]);
				case ParameterFileGenerator.ARRAY_PREFIX + ParameterFileGenerator.ARRAY_TYPE_STRING:
					ArrayList<String> valuesString = new ArrayList<>();
					// Tokenize but ignore opening and closing bracket
					while (tokenizer.hasMoreTokens()) {
						valuesString.add(String.valueOf(tokenizer.nextToken()));
					}
					return (T) valuesString.toArray(new String[valuesString.size()]);
				default: throw new ParameterExtractorException("Could not identified stringified array of type " + arrType + " (Values: " + arrString + ")");
			}
		}
		
		// if type unspecified assume String
		if(type == null || type.getName().equals("java.lang.String")) {
			return (T) new String(value);
		}
		
		// if contains ~ return a List of the permutation components
		if(value.contains(ParameterFileGenerator.TARGETED_PERMUTATION_DELIMITER)) {
			type = (Class<T>) ArrayList.class;
			System.out.println(PREFIX + "Detected combined parameter '" + value + "'. Will produce list of individual values.");
			ArrayList<String> list = new ArrayList<String>();
			StringTokenizer st = new StringTokenizer(value, ParameterFileGenerator.TARGETED_PERMUTATION_DELIMITER);
			while(st.hasMoreTokens()){
				list.add(st.nextToken());
			}
			return (T) list;
		}
		
		// Deal with primitive types
		switch(type.getName()) {
			case "java.lang.Integer":
				//Do a bit of a detour for integer, since we converted all int[] to float[] internally
				return (T) new Integer(new Float(Float.parseFloat(value)).intValue());
			case "java.lang.Float":
				return (T) new Float(Float.parseFloat(value));
			case "java.lang.Double":
				return (T) new Double(Double.parseDouble(value));
			case "java.lang.Long":
				return (T) new Long(Long.parseLong(value));
			case "java.lang.Boolean":
				return (T) new Boolean(Boolean.parseBoolean(value));
			default:
				throw new ParameterExtractorException("Attempted to extract parameter for unsupported type " + type.getName());	
		}
	}
	
	/**
	 * Extracts parameter array from parameter file (specified in {@link #parameterFile}).
	 * Returns each value once and stores picked values in {@link #usedParameterFile}.
	 * @return Parameter array
	 */
	public static String[] getParameterArrayFromParameterFile(){
		return getParameterArrayFromParameterFile(parameterFile);
	}
	
	/**
	 * Extracts parameter array from given parameter file (looks in user.dir 
	 * if no path specified as part of filename).
	 * Returns each value once and stores picked values in {@link #usedParameterFile} 
	 * if explicitly specified, or file auto-generated based on input filename.
	 * Note: This method should be used by ParallelLauncher instance, not by the 
	 * simulation instance for the extraction of parameters! For that purpose refer 
	 * to {@link #getArrayIndexForParameterArrayByParameterName(String, String)}.
	 * @return Parameter array
	 */
	public static String[] getParameterArrayFromParameterFile(String parameterFile){
		try {
			File paramFile = new File(parameterFile);
			if(paramFile.exists()){
				System.out.println(PREFIX + "Using parameter file '" + parameterFile + "'.");
				ArrayList<String> allParameters = new ArrayList<String>(FileUtils.readLines(paramFile));
				File usedParameters = new File(usedParameterFile(parameterFile));
				if(!usedParameters.exists()){
					if(!usedParameters.createNewFile()){
						System.err.println(PREFIX + "Error when trying to create file '" + usedParameters + "'.");
					}
				}
				HashSet<String> usedParams = new HashSet<String>(FileUtils.readLines(usedParameters));
				for(int i = 0; i < allParameters.size(); i++){
					//trim
					String sanitizedParameter = allParameters.get(i).trim(); 
					//ensure not to use empty lines and no headers
					if(!sanitizedParameter.startsWith(ParameterFileGenerator.PARAMETER_FILE_HEADER_START)
							&& !sanitizedParameter.isEmpty()/* && sanitizedNumberInput.length() > 0*/){
						if(!usedParams.contains(sanitizedParameter)){
							//if not yet used, add to usedSeeds file and add line break
							FileUtils.write(usedParameters, sanitizedParameter + System.getProperty("line.separator"), true);
							System.out.println(PREFIX + "Retrieved parameters '" + sanitizedParameter + "'.");
							StringTokenizer tokenizer = new StringTokenizer(sanitizedParameter, " ");
							String[] paramArray = new String[tokenizer.countTokens()];
							int ct = 0;
							while(tokenizer.hasMoreTokens()){
								String value = tokenizer.nextToken();
								// if contains ~, replace it with whitespace before storing
								/*if(value.contains(ParameterFileGenerator.TARGETED_PERMUTATION_DELIMITER)) {
									value = value.replaceAll(ParameterFileGenerator.TARGETED_PERMUTATION_DELIMITER, 
											ParameterFileGenerator.PERMUTATION_VALUE_SEPARATOR);
								}*/
								paramArray[ct] = value;
								ct++;
							}
							//then return it for use
							return paramArray;
						}
					}
				}
			} else {
				System.err.println(PREFIX + "No parameter file '" + parameterFile + "' found.");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Counts the number of valid parameter array entries in parameter file 
	 * {@link #parameterFile}. Does not modify file or pick parameter sets 
	 * for use.
	 * @return Number of valid parameter array entries
	 */
	public static int countNumberOfValidParameterArrays(){
		return countNumberOfValidParameterArrays(parameterFile, false);
	}
	
	/**
	 * Counts the number of valid parameter array entries in parameter file 
	 * specified as parameter. Does not modify file or pick parameter sets 
	 * for use.
	 * @param parameterFile Parameter file to be checked
	 * @return Number of valid parameter array entries
	 */
	public static int countNumberOfValidParameterArrays(String parameterFile){
		return countNumberOfValidParameterArrays(parameterFile, false);
	}
	
	/**
	 * Counts the number of valid remaining parameter array entries in parameter file 
	 * {@link #parameterFile}. It looks for the used parameter file and reduces the 
	 * its entries by the number of entries in the {@link #usedParameterFile} (generated 
	 * via {@link #usedParameterFile(String)}, using {@link #parameterFile} as input).
	 * Does not modify file or pick parameter sets for use.
	 * @return Number of valid parameter array entries
	 */
	public static int countNumberOfValidRemainingParameterArrays(){
		return countNumberOfValidParameterArrays(parameterFile, true);
	}
	
	/**
	 * Counts the number of valid remaining parameter array entries in parameter file 
	 * specified as parameter. It looks for the used parameter file and reduces the 
	 * its entries by the number of entries in the {@link #usedParameterFile} (generated 
	 * via {@link #usedParameterFile(String)}, using parameterFile argument as input).
	 * Does not modify file or pick parameter sets for use.
	 * @return Number of valid parameter array entries
	 */
	public static int countNumberOfValidRemainingParameterArrays(String parameterFile){
		return countNumberOfValidParameterArrays(parameterFile, true);
	}
	
	/**
	 * Counts the number of valid parameter array entries in given parameter file 
	 * (looks in user.dir if no path specified as part of filename).
	 * Allows the consideration of intervals that have not been used yet (via 
	 * {@link #getParameterArrayFromParameterFile()} variants). 
	 * Does not modify file or pick parameter sets for use.
	 * @param parameterFile Name of parameter file
	 * @param remaining Only returns number of array intervals not yet used (i.e. 
	 * in ('used parameters' file).
	 * @return Number of valid parameter array entries
	 */
	private static int countNumberOfValidParameterArrays(String parameterFile, boolean remaining){
		int count = 0;
		int retries = 0;
		try {
			File paramFile = new File(parameterFile);
			if(paramFile.exists()){
				ArrayList<String> allParameters = new ArrayList<String>(FileUtils.readLines(paramFile));
				HashSet<String> usedParams = new HashSet<String>();
				if(remaining){
					//checks for used parameters
					File usedParameters = new File(usedParameterFile(parameterFile));
					if(!usedParameters.exists()){
						if(!usedParameters.createNewFile()){
							System.err.println(PREFIX + "Error when trying to create file '" + usedParameters + "'.");
						}
					}
					//fill used parameters
					usedParams.addAll(FileUtils.readLines(usedParameters));
				}
				for(int i = 0; i < allParameters.size(); i++){
					//trim
					String sanitizedParameter = allParameters.get(i).trim(); 
					//ensure not to use empty lines and no headers
					if(!sanitizedParameter.startsWith(ParameterFileGenerator.PARAMETER_FILE_HEADER_START)
							&& !sanitizedParameter.isEmpty()){
						if(!remaining || (remaining && !usedParams.contains(sanitizedParameter))){
							count++;
						}
					}
				}
			} else {
				System.err.println(PREFIX + "No parameter file '" + parameterFile + " found.");
			}
		} catch (IOException e) {
			retries++;
			System.err.print(PREFIX + "Error reading file " + parameterFile + ". ");
			if(retries < 3) {
				/*
				 * Retry various times before returning counted values. 
				 */
				System.err.println("Retrying ...");
				return countNumberOfValidParameterArrays(parameterFile, remaining);
			} else {
				System.err.println("Error: ");
				e.printStackTrace();
			}
		}
		return count;
	}
	
	/**
	 * Extracts parameter array from parameter file (specified in {@link #parameterFile}).
	 * Returns each value once and stores picked values in {@link #usedParameterFile}.
	 * Throws a ParameterExhaustedException once all parameters from file used, or a 
	 * a more general ParameterExtractorException in case of other problems.
	 * @return Parameter array
	 * @throws ParameterExtractorException 
	 */
	public static String[] getParameterArrayFromParameterFileAndThrowExceptionOnceAllUsed() throws ParameterExtractorException{
		return getParameterArrayFromParameterFileAndThrowExceptionOnceAllUsed(parameterFile);
	}
	
	/**
	 * Extracts parameter array from parameter file specified as argument (looks in user.dir 
	 * folder if no path specified as part of filename).
	 * Returns each value once and stores picked values in {@link #usedParameterFile}.
	 * Throws a ParameterExhaustedException once all parameters from file used, or a 
	 * a more general ParameterExtractorException in case of other problems.
	 * @param parameterFile Parameter file to be used
	 * @return
	 * @throws ParameterExtractorException 
	 */
	public static String[] getParameterArrayFromParameterFileAndThrowExceptionOnceAllUsed(String parameterFile) throws ParameterExtractorException{
		String[] params = getParameterArrayFromParameterFile(parameterFile);
		if(params == null){
			throw new ParameterExtractorException(PREFIX + "Something went wrong when retrieving parameters from file.");
		}
		if(params.length == 0){
			throw new ParametersExhaustedException(PREFIX, parameterFile);
		}
		return params;
	}

}
