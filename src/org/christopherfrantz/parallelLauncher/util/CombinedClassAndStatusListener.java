package org.christopherfrantz.parallelLauncher.util;

import org.christopherfrantz.parallelLauncher.util.listeners.ProcessStatusListener;

/**
 * Data structure to bundle executed classes and their respective listeners. 
 * Using this associative structure, different instances of the same type 
 * (i.e. executable class) can be associated with different listeners 
 * (e.g. first Class1 instance calls Listener1, another Class1 instance calls Listener2)
 * 
 * Used internally by ParallelLauncher
 * 
 * @author cfrantz
 *
 */
public final class CombinedClassAndStatusListener {

	public final Class clazz;
	public final ProcessStatusListener listener;

	public CombinedClassAndStatusListener(Class clazz, ProcessStatusListener listener) {
		this.clazz = clazz;
		this.listener = listener;
	}
}
