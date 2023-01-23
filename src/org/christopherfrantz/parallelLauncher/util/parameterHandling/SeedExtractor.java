package org.christopherfrantz.parallelLauncher.util.parameterHandling;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;

import org.apache.commons.io.FileUtils;

/**
 * All methods in the seeds extractor help with the (semi-)automated 
 * specification of simulation seeds. Ideally you can read seeds 
 * from a file and set those as parameters for classes launched by 
 * different ParallelLauncher instances, while ensuring that they are 
 * only used once. Use ParallelLauncher.setArgumentToBePassedToLaunchedClasses(seed) 
 * in order to set the respective seed in organicFarming.launchers.
 * 
 * @author Christopher Frantz
 *
 */
public class SeedExtractor {
	
	public static final String PREFIX = "Seed Extractor: ";
	
	/**
	 * Files holding the seeds to be used for launcher instances
	 */
	public static String seedsFile = "Seeds.txt";
	
	/**
	 * File holding all seeds that have been used for organicFarming.launchers in
	 * order to avoid repeated use.
	 */
	public static String usedSeedsFile = "usedSeeds.txt";
	
	/**
	 * Extracts Long representation of seed from given String representation.
	 * @param seed in String representation
	 * @return Long representation of seed
	 */
	public static Long extractSeedFromParameters(String seed){
		if(seed != null){
			System.out.println(PREFIX + "Extracted seed: " + seed);
			try{
				//remove all letters from input
				return Long.valueOf(seed.replaceAll("[^\\d.]", ""));
			} catch (NumberFormatException e){
				System.err.println(PREFIX + "Could not extract seed from given parameter set.");
			}
		} else {
			System.err.println(PREFIX + "Could not extract seed from parameters. Passed argument: " + seed);
		}
		return null;
	}
	
	/**
	 * Extracts Long seed representation from first element of given String array.
	 * @param seedArray String array containing seeds
	 * @return Long representation of first array element
	 */
	public static Long extractSeedFromParameters(String[] seedArray){
		if(seedArray.length != 0){
			System.out.println(PREFIX + "Extracted seed: " + seedArray[0]);
			try{
				//remove all letters from input
				return Long.valueOf(seedArray[0].replaceAll("[^\\d.]", ""));
			} catch (NumberFormatException e){
				System.err.println(PREFIX + "Could not extract seed from given parameter set.");
			}
		} else {
			System.err.println(PREFIX + "Could not extract seed from parameters. Number of parameters: " + seedArray.length);
		}
		return null;
	}
	
	/**
	 * Gets simulation seed from file {@value #seedsFile} in project directory and 
	 * returns the first unused seed. Used seeds are stored in {@value #usedSeedsFile} 
	 * to prevent multiple reuse of seeds. Returns null if all seeds have been used 
	 * (i.e. all seeds from {@value #seedsFile} are also in {@value #usedSeedsFile}) 
	 * or if {@link #seedsFile} cannot be found.
	 * @return Simulation seed or null if exhausted or {@link #seedsFile} not found
	 */
	public static String getSimulationSeed(){
		try {
			ArrayList<String> allSeeds = new ArrayList<String>(FileUtils.readLines(new File(seedsFile)));
			File usedSeedsFileRef = new File(usedSeedsFile);
			if(!usedSeedsFileRef.exists()){
				if(!usedSeedsFileRef.createNewFile()){
					System.err.println(PREFIX + "Error when trying to create file '" + usedSeedsFile + "'.");
				}
			}
			HashSet<String> usedSeeds = new HashSet<String>(FileUtils.readLines(usedSeedsFileRef));
			for(int i = 0; i < allSeeds.size(); i++){
				//remove all letters and whitespaces
				String sanitizedNumberInput = allSeeds.get(i).replaceAll("[^\\d.]", "").trim(); 
				//ensure not to use empty lines
				if(!sanitizedNumberInput.isEmpty()/* && sanitizedNumberInput.length() > 0*/){
					if(!usedSeeds.contains(sanitizedNumberInput)){
						//if not yet used, add to usedSeeds file and add line break
						FileUtils.write(usedSeedsFileRef, sanitizedNumberInput + System.getProperty("line.separator"), true);
						System.out.println(PREFIX + "Retrieved seed '" + sanitizedNumberInput + "'.");
						//then return it for use
						return sanitizedNumberInput;
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Returns all seeds in {@link #seedsFile} without (!) marking them as 
	 * used seeds (see {@link #getSimulationSeed()}). Removes empty and null values.
	 * @return ArrayList of seeds
	 */
	public static ArrayList<String> getSimulationSeeds(){
		return getSimulationSeeds(seedsFile);
	}
	
	/**
	 * Returns the first given number of seeds from {@link #seedsFile} without (!)
	 * marking them as used. Removes empty and null values. Guarantees the number of 
	 * requested seeds or throws ParametersExhaustedException.
	 * @param numberOfSeeds Number of expected seeds
	 * @return ArrayList of seeds
	 */
	public static ArrayList<String> getSimulationSeeds(int numberOfSeeds) throws ParametersExhaustedException {
		ArrayList<String> seeds = getSimulationSeeds();
		if(seeds == null || seeds.isEmpty()){
			throw new ParametersExhaustedException(PREFIX, "Seed file " + seedsFile + " not found or exhausted.");
		}
		if(seeds.size() < numberOfSeeds){
			throw new ParametersExhaustedException(PREFIX, "Seed file " + seedsFile + " does not contain " + numberOfSeeds + " seeds.");
		}
		return new ArrayList<String>(seeds.subList(0, numberOfSeeds));
	}
	
	/**
	 * Returns all seeds from a given file. Removes empty and null values.
	 * @param seedsFile Seeds file in project directory.
	 * @return ArrayList of seeds
	 */
	public static ArrayList<String> getSimulationSeeds(String seedsFile){
		File seedFile = new File(seedsFile);
		ArrayList<String> loadedSeeds = null;
		if(seedFile.exists()){
			try {
				loadedSeeds = new ArrayList<>(FileUtils.readLines(seedFile));
				//Remove null values
				loadedSeeds.removeAll(Collections.singleton(null));
				for(int i = 0; i < loadedSeeds.size(); i++){
					String seed = loadedSeeds.get(i).replaceAll("[^\\d.]", "");
					if(seed.isEmpty()){
						loadedSeeds.remove(i);
						i--;
					} else {
						loadedSeeds.set(i, seed);
					}
				}
				//Remove potential duplicates and return as ArrayList
				return new ArrayList<String>(new LinkedHashSet<String>(loadedSeeds));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		throw new RuntimeException(PREFIX + "Seeds file '" + seedsFile + "' could not be found.");
	}
	
	/**
	 * Retrieves seed from file (see {@link #getSimulationSeed()} but 
	 * also checks if returned seed is null and throws ParametersExhaustedException 
	 * if no further seed in order to prevent execution.
	 * @return individual seed value
	 * @throws ParametersExhaustedException 
	 */
	public static String getSimulationSeedWithExceptionOnceAllSeedsUsed() throws ParametersExhaustedException{
		String seed = getSimulationSeed();
		if(seed == null){
			throw new ParametersExhaustedException(PREFIX, "All seeds executed!");
		}
		return seed;
	}

}
