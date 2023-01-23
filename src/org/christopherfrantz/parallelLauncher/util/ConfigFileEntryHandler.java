package org.christopherfrantz.parallelLauncher.util;

/**
 * Abstract class to implement configuration file handlers for usage in launcher variants.
 *
 * @param <V> Value type for key-value structure of configuration file
 *
 * @author Christopher Frantz
 *
 */
public abstract class ConfigFileEntryHandler<V> {

	private final String configFile;
	protected final String key;
	protected V currentValue; 
	
	/**
	 * Instantiates a given config entry handler for a given configuration file, 
	 * key within that file and the current value as reference.
	 * @param configFile
	 * @param key
	 * @param currentValue
	 */
	public ConfigFileEntryHandler(String configFile, String key, V currentValue) {
		this.configFile = configFile;
		this.key = key;
		this.currentValue = currentValue;
	}
	
	/**
	 * Returns the configuration file this ConfigEntryHandler is associated with.
	 * @return
	 */
	public String getConfigFile() {
		return configFile;
	}
	
	/**
	 * Returns the key this ConfigEntryHandler is associated with.
	 * @return
	 */
	public String getKey() {
		return key;
	}
	
	/**
	 * Returns the value for the managed entry.
	 * @return
	 */
	public V getValue() {
		return currentValue;
	}
	
	/**
	 * Processes a config file line and performs update operation of Launcher settings 
	 * if config file line key matches the one specified in constructor.
	 * @param entryLine Entry line to be checked
	 * @return Boolean indicator signifying whether configuration line was applicable to this handler
	 */
	public abstract boolean readEntry(String entryLine);
	
	/**
	 * Constructs a config file entry with the current value for the handled key.
	 * @return
	 */
	public abstract String constructEntry();
	
	@Override
	public String toString() {
		return "ConfigFileHandler for config file '" + configFile + "', key '" + key + "'" + System.getProperty("line.separator");
	}
}
