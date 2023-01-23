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

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		addParameterRange("Prob", new float[] { 0f, 1f, 0.1f });
		addParameterRange("Test", new float[] { 1f, 2f, 0.1f });
		addParameterRange("Jah", new float[] { 5, 7, 0.2f });
		ArrayList<String> seeds = new ArrayList<String>();
		seeds.add("345346");
		seeds.add("24642642");
		ArrayList<String> seeds2 = new ArrayList<String>();
		seeds2.add("3222");
		seeds2.add("222222642");
		
		addParameterList("Seeds1", seeds);
		addParameterList("Seeds2", seeds2);
		generateParameterFile("TestFile.txt");
	}

}
