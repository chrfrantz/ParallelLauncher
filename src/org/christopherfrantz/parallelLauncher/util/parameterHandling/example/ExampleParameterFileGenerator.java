package org.christopherfrantz.parallelLauncher.util.parameterHandling.example;

import java.util.ArrayList;

import org.christopherfrantz.parallelLauncher.util.parameterHandling.ParameterFileGenerator;

/**
 * Exemplifies generation of parameter file for sensitivity analysis based
 * on various inputs, including values and ranges (for automatic stepping).
 * Output is to be consumed with ParameterExtractor.
 *
 * @author Christopher Frantz
 *
 */
public class ExampleParameterFileGenerator extends ParameterFileGenerator {

	public static final String PARAMETER_FILE = "TestFile.txt";

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		// As generic a parameter as possible - used for static parameters
		addParameter("Number", 25);

		// Show use of general ranges - basic functionality of lower, upper boundary and step size
		addParameterRange("Prob", new float[] { 0f, 1f, 0.1f });
		addParameterRange("Test", new float[] { 1f, 2f, 0.1f });
		addParameterRange("Jah", new float[] { 5, 7, 0.2f });

		// Show use of parameter lists - good for fixed systematically changed configurations (e.g., distributions)
		Float[] svoDistribution1 = new Float[3];
		svoDistribution1[0] = 0.33f;
		svoDistribution1[1] = 0.50f;
		svoDistribution1[2] = 0.167f;
		Float[] svoDistribution2 = new Float[3];
		svoDistribution2[0] = 0.50f;
		svoDistribution2[1] = 0.33f;
		svoDistribution2[2] = 0.167f;

		addParameterList("SVO-Distribution", svoDistribution1, svoDistribution2);

		// Generic use of lists for other purposes
		ArrayList<String> seeds = new ArrayList<>();
		seeds.add("3222");
		seeds.add("222222642");

		addParameterList("Seeds", seeds);
		generateParameterFile(PARAMETER_FILE);
	}

}
