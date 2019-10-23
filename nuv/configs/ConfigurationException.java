package edu.wisc.cs.arc.configs;

/**
 * Runtime exception thrown while working with configurations.
 * @author Aaron Gember-Jacobson (agemberjacobson@colgate.edu)
 */
public class ConfigurationException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	public ConfigurationException(String msg) {
		super(msg);
	}
	
	public ConfigurationException(String msg, Exception ex) {
		super(msg, ex);
	}
}
