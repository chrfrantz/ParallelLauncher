package org.christopherfrantz.parallelLauncher.util.parameterHandling;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.commons.io.FileUtils;

/**
 * Generates a Parameter file consumed by ParameterExtractor to populate simulation instances.
 * This is a helper function to be used within the simulation instance implementation to extract
 * parameter information to allow for sensitivity analysis. To facilitate this, the ParameterExtractor
 * ensures the return of unique parameter combinations.
 *
 * @author Christopher Frantz
 *
 */
public class ParameterFileGenerator {

	private static final String PREFIX = "Parameter File Generator: ";
	public static final String PARAMETER_FILE_HEADER_START = "###";
	public static final String PARAMETER_FILE_HEADER_SEPARATOR = "|";
	
	/**
	 * Separator used to separate individual values in a given parameter permutation.
	 */
	public static final String PERMUTATION_VALUE_SEPARATOR = " ";
	/**
	 * Separator used instead of whitespace to ensure treatment of targeted permutations as 
	 * combined parameter when passed to instance.
	 */
	public static final String TARGETED_PERMUTATION_DELIMITER = "~";

	/**
	 * Default outfile.
	 */
	protected static String parameterFile = ParameterExtractor.parameterFile;

	private static ArrayList<float[]> parameterRanges = new ArrayList<>();
	private static ArrayList<Integer> parameterRangeDimensions = new ArrayList<>();
	
	private static ArrayList<String> parameterNames = new ArrayList<>();
	private static ArrayList<String> parameterListNames = new ArrayList<>();
	
	private static ArrayList<String> headerEntries = new ArrayList<String>();
	
	/**
	 * Identifier for stringified arrays
	 */
	protected static final String ARRAY_PREFIX = "Array";
	
	/**
	 * Identifier prefix for stringified float arrays
	 */
	protected static final String ARRAY_TYPE_FLOAT = "Float";
	
	/**
	 * Identifier prefix for stringified integer arrays
	 */
	protected static final String ARRAY_TYPE_INTEGER = "Integer";
	
	/**
	 * Identifier prefix for stringified double arrays
	 */
	protected static final String ARRAY_TYPE_DOUBLE = "Double";
	
	/**
	 * Identifier prefix for stringified long arrays
	 */
	protected static final String ARRAY_TYPE_LONG = "Long";
	
	/**
	 * Identifier prefix for stringified short arrays
	 */
	protected static final String ARRAY_TYPE_SHORT = "Short";
	
	/**
	 * Identifier prefix for stringified string arrays
	 */
	protected static final String ARRAY_TYPE_STRING = "String";
	
	/**
	 * Opening bracket for stringified arrays
	 */
	protected static final String ARRAY_OPENING_BRACKET = "+";
	
	/**
	 * Closing bracket for stringified arrays
	 */
	protected static final String ARRAY_CLOSING_BRACKET = "-";
	
	/**
	 * Value delimiter for stringified arrays
	 */
	protected static final String ARRAY_VALUE_DELIMITER = "_";

	/**
	 * Specifies range of parameters that is automatically generated at runtime and 
	 * used to generate all parameter combinations. Begin and end of triplets are 
	 * considered inclusive during generation. Additional array contents specify 
	 * exclusions (i.e. values excluded from auto-generated parameter values). 
	 * Note: Automatically generated parameter values are considered before manually 
	 * added lists and maintain the order of entering. 
	 * For lists of given values use {@link #addParameterList(List)}.
	 * @param parameterName Name/Identifier of parameter in generated output
	 * @param parameterRange Triplet with syntax [<begin>, <end>, <step size>], or  [<begin>, <end>, <step size>, excludedValue1, ...]
	 */
	protected static void addParameterRange(String parameterName, float[] parameterRange) {
		if (parameterName == null || parameterName.isEmpty()) {
			throw new IllegalArgumentException(PREFIX + "Parameter name is null or empty.");
		}
		if (parameterRange.length < 3) {
			System.err.println(PREFIX
					+ "Invalid arguments for parameter triplet. Syntax: [<begin>, <end>, <steps>]");
			return;
		} else if (parameterRange.length > 3) {
			//Indicate exclusions
			HashSet<Float> excluded = new HashSet<>();
			if (parameterRange.length > 3) {
				for (int i = 3; i < parameterRange.length; i++) {
					excluded.add(parameterRange[i]);
				}
			}
			System.out.println(PREFIX + "Parameter range specification for parameter '" + parameterName + "' specifies exclusion of values " + excluded);
		}
		if (parameterRange[0] > parameterRange[1]) {
			System.err.println(PREFIX
					+ "Start parameter value greater than end: Start: "
					+ parameterRange[0] + ", end: " + parameterRange[1]);
			return;
		}
		if (parameterRange[2] > (parameterRange[1] - parameterRange[0])) {
			System.err.println(PREFIX + "Stepping " + parameterRange[2]
					+ " too big for parameter range '" + parameterRange[0]
					+ " to " + parameterRange[1] + "'");
			return;
		}
		parameterRanges.add(parameterRange);
		parameterNames.add(parameterName);
	}
	
	/**
	 * Specifies range of parameters that is automatically generated at runtime and 
	 * used to generate all parameter combinations. Begin and end of triplets are 
	 * considered inclusive during generation. Additional array contents specify 
	 * exclusions (i.e. values excluded from auto-generated parameter values). 
	 * Note: Automatically generated parameter values are considered before manually 
	 * added lists and maintain the order of entering. 
	 * For lists of given values use {@link #addParameterList(List)}.
	 * @param parameterName Name/Identifier of parameter in generated output
	 * @param parameterRange Triplet with syntax [<begin>, <end>, <step size>], or  [<begin>, <end>, <step size>, excludedValue1, ...]
	 */
	protected static void addParameterRange(String parameterName, int[] parameterRange) {
		if (parameterName == null || parameterName.isEmpty()) {
			throw new IllegalArgumentException(PREFIX + "Parameter name is null or empty.");
		}
		float[] array = new float[parameterRange.length];
		for(int i = 0; i < parameterRange.length; i++) {
			array[i] = parameterRange[i];
		}
		addParameterRange(parameterName, array);
	}
	
	/**
	 * Adds a list of parameters that are included in the generation of parameter permutations. 
	 * Parameter lists are considered as last columns in the permutation output. 
	 * However, the order of multiple entered lists is maintained. For specifying ranges
	 * of automatically generated values use {@link #addParameterRange(float[])}.
	 * @param parameterName Name/Identifier of parameter in generated output
	 * @param parameters List of parameter values
	 */
	protected static void addParameterList(String parameterName, List<String> parameters){
		if (parameterName == null || parameterName.isEmpty()) {
			throw new IllegalArgumentException(PREFIX + "Parameter name is null or empty.");
		}
		if (parameters == null || parameters.isEmpty()) {
			throw new IllegalArgumentException(PREFIX + "Parameter value list is null or empty.");
		}
		if(listsOfParameters == null){
			listsOfParameters = new ArrayList<>();
		}
		listsOfParameters.add(0, parameters);
		parameterListNames.add(parameterName);
	}
	
	/**
	 * Adds one or more elements as parameters for generating combinations. 
	 * Helper method to enter elements directly without prior generation of list.
	 * Supports the submission of arrays, which are stringified in a proprietary format 
	 * understood by ParameterExtractor (Format: ArrayFloat+0.3_0.3_0.3- for float).
	 * See {@link #addParameterList(String, List)} for complete behaviour specification.
	 * @param parameterName Name/Identifier of parameter in generated output
	 * @param parameters Array of parameter values
	 */
	protected static void addParameterList(String parameterName, Object ... parameters){
		if (parameterName == null || parameterName.isEmpty()) {
			throw new IllegalArgumentException(PREFIX + "Parameter name is null or empty.");
		}
		if (parameters == null || parameters.length == 0) {
			throw new IllegalArgumentException(PREFIX + "Parameter value array is null or empty.");
		}
		// Convert input to String
		String[] stringParams = new String[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			
			// Special case: arrays are stringified. Supported types: float, integer, double, short, long, string
			if (parameters[i] != null && parameters[i].getClass().isArray()) {
				// Deal with arrays and decompose into "ArrayFloat+0.3_0.3_0.3-" format (example for float)
				StringBuilder arrString = new StringBuilder(ARRAY_PREFIX);
				switch (parameters[i].getClass().getComponentType().getName()) {
					case "float":
						arrString.append(ARRAY_TYPE_FLOAT).append(ARRAY_OPENING_BRACKET);
						for (int j = 0; j < ((float[])parameters[i]).length; j++) {
							arrString.append(String.valueOf(((float[])parameters[i])[j]));
							if (j < ((float[])parameters[i]).length - 1) {
								arrString.append(ARRAY_VALUE_DELIMITER);
							}
						}
						stringParams[i] = arrString.append(ARRAY_CLOSING_BRACKET).toString();
						break;
					case "int":
						arrString.append(ARRAY_TYPE_INTEGER).append(ARRAY_OPENING_BRACKET);
						for (int j = 0; j < ((int[])parameters[i]).length; j++) {
							arrString.append(String.valueOf(((int[])parameters[i])[j]));
							if (j < ((int[])parameters[i]).length - 1) {
								arrString.append(ARRAY_VALUE_DELIMITER);
							}
						}
						stringParams[i] = arrString.append(ARRAY_CLOSING_BRACKET).toString();
						break;
					case "double":
						arrString.append(ARRAY_TYPE_DOUBLE).append(ARRAY_OPENING_BRACKET);
						for (int j = 0; j < ((double[])parameters[i]).length; j++) {
							arrString.append(String.valueOf(((double[])parameters[i])[j]));
							if (j < ((double[])parameters[i]).length - 1) {
								arrString.append(ARRAY_VALUE_DELIMITER);
							}
						}
						stringParams[i] = arrString.append(ARRAY_CLOSING_BRACKET).toString();
						break;
					case "long":
						arrString.append(ARRAY_TYPE_LONG).append(ARRAY_OPENING_BRACKET);
						for (int j = 0; j < ((long[])parameters[i]).length; j++) {
							arrString.append(String.valueOf(((long[])parameters[i])[j]));
							if (j < ((long[])parameters[i]).length - 1) {
								arrString.append(ARRAY_VALUE_DELIMITER);
							}
						}
						stringParams[i] = arrString.append(ARRAY_CLOSING_BRACKET).toString();
						break;
					case "short":
						arrString.append(ARRAY_TYPE_SHORT).append(ARRAY_OPENING_BRACKET);
						for (int j = 0; j < ((short[])parameters[i]).length; j++) {
							arrString.append(String.valueOf(((short[])parameters[i])[j]));
							if (j < ((short[])parameters[i]).length - 1) {
								arrString.append(ARRAY_VALUE_DELIMITER);
							}
						}
						stringParams[i] = arrString.append(ARRAY_CLOSING_BRACKET).toString();
						break;
					case "String":
						arrString.append(ARRAY_TYPE_STRING).append(ARRAY_OPENING_BRACKET);
						for (int j = 0; j < ((String[])parameters[i]).length; j++) {
							arrString.append(String.valueOf(((String[])parameters[i])[j]));
							if (j < ((String[])parameters[i]).length - 1) {
								arrString.append(ARRAY_VALUE_DELIMITER);
							}
						}
						stringParams[i] = arrString.append(ARRAY_CLOSING_BRACKET).toString();
						break;
					default: throw new RuntimeException("Array of type " + 
						parameters[i].getClass().getComponentType().getName() + " is not supported by Parameter File Generator.");
				}
			} else {
				// Deal with regular values
				stringParams[i] = String.valueOf(parameters[i]);
			}
		}
		// Create list
		ArrayList<String> list = new ArrayList<>();
		for(int i = 0; i < stringParams.length; i++) {
			list.add(stringParams[i]);
		}
		addParameterList(parameterName, list);
	}
	
	/**
	 * Adds a parameter with single value to include in generating parameter permutations.
	 * @param parameterName Name/Identifier of parameter in generated output
	 * @param parameter Parameter value
	 */
	protected static void addParameter(String parameterName, Object parameter){
		addParameterList(parameterName, parameter);
	}

	/**
	 * Generates permutations of all given parameters.
	 * Source: http://stackoverflow.com/questions/17192796/generate-all-combinations-from-multiple-lists
	 * @param lists
	 * @param result
	 * @param depth
	 * @param current
	 */
	private static void generatePermutations(List<List<String>> lists,
			List<String> result, int depth, String current) {
		if (depth == lists.size()) {
			result.add(current);
			return;
		}

		for (int i = 0; i < lists.get(depth).size(); ++i) {
			generatePermutations(lists, result, depth + 1, 
					lists.get(depth).get(i) + 
						(current.isEmpty() ? "" : PERMUTATION_VALUE_SEPARATOR + current));
		}
	}
	
	/**
	 * Returns a list of all permutations of input values whose sum equals target
	 * @param lists List of values to be used for permutations
	 * @param target Target value
	 * @return
	 */
	private static List<String> generateTargetedPermutations(List<List<String>> lists, Float target) {
		List<String> result = new ArrayList<>();
		generatePermutations(lists, result, 0, "");
		
		List<String> filteredResults = new ArrayList<>();
		StringTokenizer tk = null; 
		for (String entry: result) {
			tk = new StringTokenizer(entry, PERMUTATION_VALUE_SEPARATOR);
			Float sum = 0f;
			while (tk.hasMoreTokens()) {
				sum += Float.parseFloat(tk.nextToken());
			}
			// values meet target
			if (sum.floatValue() == target.floatValue()) {
				// Replace whitespace with ~ before saving
				filteredResults.add(entry.replaceAll(PERMUTATION_VALUE_SEPARATOR, TARGETED_PERMUTATION_DELIMITER));
			}
		}
		return filteredResults;
	}
	
	/**
	 * Generates a new outfile with generated permutations. Write to file 
	 * {@link #parameterFile}. Use {@link #generateParameterFile(String)} 
	 * to specify custom filename.
	 */
	protected static void generateParameterFile(){
		generateParameterFile(null, false);
	}
	
	/**
	 * Generates (or appends to) an outfile with generated permutations. Write to file 
	 * {@link #parameterFile}. Use {@link #generateParameterFile(String)} 
	 * to specify custom filename.
	 * @param append Indicates whether generated parameter combinations are appended to existing parameter file(s).
	 */
	protected static void generateParameterFile(boolean append){
		generateParameterFile(null, append);
	}
	
	/**
	 * Generates a new outfile with generated permutations. Use {@link #generateParameterFile()} 
	 * to write to default outfile.
	 * @param parameterFilename Outfile name
	 */
	protected static void generateParameterFile(String parameterFilename) {
		generateParameterFile(parameterFilename, 1, false);
	}
	
	/**
	 * Generates (or appends to) an outfile with generated permutations. Use {@link #generateParameterFile()} 
	 * to write to default outfile.
	 * @param parameterFilename Outfile name
	 * @param append Indicates if new content should be appended to existing parameter file
	 */
	protected static void generateParameterFile(String parameterFilename, boolean append) {
		generateParameterFile(parameterFilename, 1, append);
	}
	
	/**
	 * Generates (or appends to) an outfile with generated permutations. Use {@link #generateParameterFile()} 
	 * to write to default outfile. Allows the distribution of generated combinations across
	 * multiple files. If numberOfFiles > 1, each filename will have a zero-based index suffix (e.g. _0, _1, etc.).
	 * @param parameterFilename Outfile name
	 * @param numberOfFiles Number of parameter files the generated combinations are distributed
	 * 		across
	 * @param append If existing, appends data to existing files.
	 */
	protected static void generateParameterFile(String parameterFilename, int numberOfFiles, boolean append) {
		if (parameterFilename != null) {
			parameterFile = parameterFilename;
		}
		//prevent eventual nastiness by fixing value of 1
		if(numberOfFiles < 1) {
			numberOfFiles = 1;
		}
		
		//prepare header
		headerEntries.clear();
		
		//parameter names for generated data
		for(int i = 0; i < parameterNames.size(); i++){
			headerEntries.add(parameterNames.get(i));
		}
		//parameter names for given lists
		for(int i = 0; i < parameterListNames.size(); i++){
			headerEntries.add(parameterListNames.get(i));
		}
		
		//Generate sweeps
		List<String> sweeps = generateSweeps();
		
		//Compile file header with augmented parameter headers (now includes counts)
		StringBuffer header = new StringBuffer(PARAMETER_FILE_HEADER_START).append(" ");
		for(int i = 0; i < headerEntries.size(); i++){
			header.append(headerEntries.get(i));
			if(i != headerEntries.size() - 1){
				header.append(PARAMETER_FILE_HEADER_SEPARATOR);
			}
		}
		header.append(" ").append(PARAMETER_FILE_HEADER_START);
		
		if(sweeps != null && !sweeps.isEmpty()){
			float rawEntriesPerFile = sweeps.size() / (float)numberOfFiles;
			int entriesPerFile = new Double(Math.ceil(rawEntriesPerFile)).intValue();
			// determine last parameter file size if dealing with odd number of entries
			int lastFileSize = entriesPerFile - Math.abs(sweeps.size() - numberOfFiles * entriesPerFile);
			for(int i = 0; i < numberOfFiles; i++) {
				List<String> actualSweeps = sweeps.subList(i * entriesPerFile, 
						i * entriesPerFile + (i == (numberOfFiles - 1) ? lastFileSize : entriesPerFile));
				try {
					ArrayList<String> content = new ArrayList<>();
					content.add(header.toString());
					content.addAll(actualSweeps);
					//Determine parameter filename based on number of required files
					String specificParameterFile = numberOfFiles == 1 ? parameterFile : 
						(parameterFile.substring(0, parameterFile.lastIndexOf(".")) + "_" + i +
								parameterFile.substring(parameterFile.lastIndexOf("."), parameterFile.length())); 
					FileUtils.writeLines(new File(specificParameterFile), content, append);
					System.out.println(PREFIX + "Generated " + (content.size() - 1) +
							" parameter combinations and " + (append ? "appended" : "wrote") + 
							" them to file '" + specificParameterFile + "'.");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} else {
			System.out.println(PREFIX + "Did not generate any parameter combinations.");
		}
	}
	
	/**
	 * Convenience method to generate the parameter filename from original (complete, i.e. including extension) 
	 * base name and index. Useful if multiple parameter files have been generated (e.g. for distribution across
	 * multiple machines). If index is set to -1, the original filename will be returned.
	 * @param parameterFilename
	 * @param index
	 * @return
	 */
	public static String getParameterFilenameForIndex(final String parameterFilename, final int index) {
		if (index < -1) {
			throw new RuntimeException("Parameter index " + index + " is invalid.");
		} else if (index == -1) {
			System.out.println("Assuming a single parameter file '" + parameterFilename + "'.");
			return parameterFilename;
		}
		// In all other cases, assume multiple parameter files
		return parameterFilename.substring(0, parameterFilename.lastIndexOf(".")) + "_" + index +
				parameterFilename.substring(parameterFilename.lastIndexOf("."), parameterFilename.length());
	}
	
	/**
	 * List of lists holding the (expanded) raw parameter values which are to be combined.
	 */
	private static List<List<String>> listsOfParameters = null;
	
	/**
	 * Expands parameter value ranges and integrates them with 
	 * list before calculating all combinations. Returns a 
	 * list of all combinations. 
	 * Use {@link #generateSweeps(Float)} to create a list of combinations 
	 * filtered by target value.
	 * Use {@link #generateParameterFile()} method variants to write to file.
	 * @return
	 */
	protected static List<String> generateSweeps() {
		return generateSweeps(null, false);
	}
	
	/**
	 * Expands parameter value ranges and integrates them with 
	 * list before calculating all permutations. Returns a 
	 * list of all permutations that are equal to a given target 
	 * value. Note that each permutation is concatenated using 
	 * {@link #TARGETED_PERMUTATION_DELIMITER} to ensure treatment as
	 * single parameter.
	 * 
	 * This method is designed to prepared targeted permutations, then clearing
	 * the generator for the actual parameter generation using the results of the
	 * preceding run as a parameter input.
	 * 
	 * Example:
	 * -> Preparing targeted sweep
	 * addParameterRange("s1", new float[]{0.1f, 1f, 0.1f});
	 * addParameterRange("s1", new float[]{0.1f, 1f, 0.1f});
	 * -> Here the actual parameter specification starts
	 * addParameterList("Combined parameter", generateSweeps(1.0f, true));
	 * ....
	 * generateParameterFile()
	 * 
	 * Use {@link #generateSweeps()} to create all permutations without filtering.
	 * Use {@link #generateParameterFile()} method variants to write to file.
	 * @param target Target value. Set to null if all permutations should be returned.
	 * @param clear Clears the parameter generator instance for reuse
	 * @return
	 */
	protected static List<String> generateSweeps(Float target, boolean clear) {
		calculateParameterRangeDims();
		//list for prepared individual lists
		if(listsOfParameters == null){
			listsOfParameters = new ArrayList<List<String>>();
		}
		
		//go over dimensions
		for(int i = parameterRangeDimensions.size() - 1; i >= 0; i--){
			
			List<String> valueList = new ArrayList<String>();
			//now do each parameter range dimension
			for(int j = 0; j <= parameterRangeDimensions.get(i); j++){
				String value = calculateValue(i, j);
				if (value != null) {
					valueList.add(value);
				}
			}
			listsOfParameters.add(valueList);
		}

		//Augment header entries with number of values per dimension
		for(int j = 0; j < headerEntries.size(); j++){
			headerEntries.set(j, headerEntries.get(j) + " (" + listsOfParameters.get(listsOfParameters.size() - j - 1).size() + ")");
		}

		List<String> results = new ArrayList<String>();
		if	(target == null) {
			// no filtering
			generatePermutations(listsOfParameters, results, 0, "");
		} else {
			// filtering
			results = generateTargetedPermutations(listsOfParameters, target);
		}
		listsOfParameters = null;
		//clear results if nothing meaningful produced
		if(results.size() == 1 && results.contains("")){
			results.clear();
		}
		
		if (clear) {
			clearAllParameterEntries();
		}
		
		return results;
	}
	
	/**
	 * Clears all parameter settings to permit reuse of the generator.
	 */
	public static void clearAllParameterEntries() {
		if (headerEntries != null) {
			headerEntries.clear();
		}
		if (listsOfParameters != null) {
			listsOfParameters.clear();
		}
		if (parameterListNames != null) {
			parameterListNames.clear();
		}
		if (parameterNames != null) {
			parameterNames.clear();
		}
		if (parameterRangeDimensions != null) {
			parameterRangeDimensions.clear();
		}
		if (parameterRanges != null) {
			parameterRanges.clear();
		}
	}
	
	private static String calculateValue(int parameterIndex, int increment){
		BigDecimal startValue = new BigDecimal(String.valueOf(parameterRanges.get(parameterIndex)[0]));
		BigDecimal step = new BigDecimal(String.valueOf(parameterRanges.get(parameterIndex)[2]));
		BigDecimal targetValue = startValue.add(new BigDecimal(increment).multiply(step));
		//Check for excluded values - if parameter range array is greater than 3
		if (parameterRanges.get(parameterIndex).length > 3) {
			for (int i = 3; i < parameterRanges.get(parameterIndex).length; i++) {
				if (targetValue.compareTo(new BigDecimal(parameterRanges.get(parameterIndex)[i])) == 0) {
					//exclude value from calculation
					return null;
				}
			}
		}
		return targetValue.toPlainString();
	}
	
	protected static void calculateParameterRangeDims(){
		for(int i = 0; i < parameterRanges.size(); i++){
			//System.out.println("Checking Range " + parameterRanges.get(i));
			//BigDecimal startValue = new BigDecimal(String.valueOf(parameterRanges.get(0)[0]));
			//BigDecimal endValue = new BigDecimal(String.valueOf(parameterRanges.get(0)[1]));
			BigDecimal subtraction = new BigDecimal(String.valueOf(parameterRanges.get(i)[1])).subtract(new BigDecimal(String.valueOf(parameterRanges.get(i)[0]))); 
			BigDecimal step = new BigDecimal(String.valueOf(parameterRanges.get(i)[2]));
			if(subtraction.doubleValue() == 0.0 && step.doubleValue() == 0.0){
				//if start and end value the same and step zero, interpret as one dimension, one value
				parameterRangeDimensions.add(0);
			} else {
				//calculate proper ranges
				//BigDecimal remainder = subtraction.remainder(step);
				int dim = subtraction.divide(step).intValue();
				//System.out.println("Subtraction: " + subtraction + ", step: " + step + ", Dims: " + dim);
				parameterRangeDimensions.add(dim);
			}
		}
		//System.out.println("Dimensions: " + parameterRangeDimensions);
	}

}
