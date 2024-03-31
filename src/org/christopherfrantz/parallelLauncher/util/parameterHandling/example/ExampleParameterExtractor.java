package org.christopherfrantz.parallelLauncher.util.parameterHandling.example;

import org.christopherfrantz.parallelLauncher.util.parameterHandling.ParameterExtractor;
import org.christopherfrantz.parallelLauncher.util.parameterHandling.ParameterExtractorException;

import java.util.ArrayList;

public class ExampleParameterExtractor {

    public static final String PARAMETER_FILE = ExampleParameterFileGenerator.PARAMETER_FILE;

    /**
     * This main method is an example of how to conceivably extract content from a set of parameters
     * passed via the main method arguments. This would require invocation from a ParallelLauncher instance
     * (or otherwise). However, the functionality within the method showcases the different ways in which
     * parameters can be accessed.
     * @param args
     */
    public static void IndirectlyInvokedmain(String[] args){

        try {
            // Returning all parameter names from file
            ArrayList<String> params = ParameterExtractor.getAllParameterNames(ExampleParameterExtractor.PARAMETER_FILE);
            System.out.println("Parameters in file: " + params);
        } catch (ParameterExtractorException e) {
            throw new RuntimeException(e);
        }

        try {
            // Extract specific parameter value from input parameters
            String params = ParameterExtractor.getParameterValueFromParameterArrayByParameterName(ExampleParameterExtractor.PARAMETER_FILE, "Number", args);
            System.out.println("Parameters in file: " + params);
        } catch (ParameterExtractorException e) {
            throw new RuntimeException(e);
        }

        try {
            // Extract fixed configurations (e.g., parameter distributions in the form of parameter arrays) from input parameters
            // Supports other forms of objects
            Float[] params = ParameterExtractor.getParameterValueFromParameterArrayByParameterName(ExampleParameterExtractor.PARAMETER_FILE, "SVO-Distribution", args, Float[].class);
            System.out.println("Parameters in file: " + params);
        } catch (ParameterExtractorException e) {
            throw new RuntimeException(e);
        }

    }

}