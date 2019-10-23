package edu.wisc.cs.arc.policies;

/**
 * Runtime exception thrown during policy manipulation.
 * @author Aaron Gember-Jacobson (agemberjacobson@colgate.edu)
 */
public class PolicyException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	public PolicyException(String msg) {
		super(msg);
	}
	
	public PolicyException(String msg, Exception ex) {
		super(msg, ex);
	}
}
