package edu.wisc.cs.arc.policies;

import java.io.Serializable;
import java.util.List;

import edu.wisc.cs.arc.graphs.Flow;
import edu.wisc.cs.arc.graphs.ProcessVertex;
import edu.wisc.cs.arc.repair.VirtualDirectedEdge;

/**
 * Represents a high-level policy a network should satisfy: e.g., traffic from 
 * subnet A to subnet B is always blocked.
 * @author Aaron Gember-Jacobson (agemberjacobson@colgate.edu)
 */
public class Policy implements Serializable {
	private static final long serialVersionUID = 3501913304962787386L;

	/** Types of policies */
	public enum PolicyType {
		ALWAYS_BLOCKED,
		ALWAYS_REACHABLE,
		ALWAYS_WAYPOINT,
		ALWAYS_ISOLATED,
		PRIMARY_PATH
	}
	
	/** Type of policy */
	private PolicyType type;
	
	/** Traffic class to which the policy applies */
	private Flow appliesTo;
	
	/** An additional, optional, type-specific parameter */
	private Object parameter;
	
	/**
	 * Creates a policy of a specific type for a specific traffic class.
	 * @param type the type of policy
	 * @param appliesTo the traffic class to which the policy applies
	 */
	public Policy(PolicyType type, Flow appliesTo) {
		this(type, appliesTo, null);
	}
	
	/**
	 * Creates a policy of a specific type for a specific traffic class with 
	 * a particular parameter.
	 * @param type the type of policy
	 * @param appliesTo the traffic class to which the policy applies
	 * @param paramter a type-specific parameter
	 */
	public Policy(PolicyType type, Flow appliesTo, Object paramter) {
		this.type = type;
		this.appliesTo = appliesTo;
		this.parameter = paramter;
	}
	
	/**
	 * Gets the type of the policy.
	 * @return the type the policy
	 */
	public PolicyType getType() {
		return this.type;
	}
	
	/**
	 * Gets the traffic class to which the policy applies.
	 * @return the traffic class to which the policy applies
	 */
	public Flow getTrafficClass() {
		return this.appliesTo;
	}
	
	/**
	 * Gets the optional parameter for the policy, if one exists.
	 * @return the optional parameter for the policy
	 */
	public Object getParameter() {
		return this.parameter;
	}
	
	public String toString() {
		String result = this.appliesTo.toString() + " ";
		switch (this.type) {
		case ALWAYS_BLOCKED:
			result += "Blocked";
			break;
		case ALWAYS_REACHABLE: 
			int k = (Integer)this.getParameter();
			result += "Reachable (< " + k + ")";
			break;	
		case ALWAYS_WAYPOINT:
			result += "Waypoint";
			break;
		case ALWAYS_ISOLATED:
			result += "Isolated";
			break;
		case PRIMARY_PATH:
		    List<VirtualDirectedEdge<ProcessVertex>> primaryPath =
		        (List<VirtualDirectedEdge<ProcessVertex>>)this.getParameter();
		    result += "Primary path (";
		    VirtualDirectedEdge<ProcessVertex> firstEdge = primaryPath.get(0);
		    result += firstEdge.getSource().toString();
		    for (VirtualDirectedEdge<ProcessVertex> edge : primaryPath) {
		        result += "->" + edge.getDestination();
		    }
		    result += ")";
		}
		return result;
	}
}
