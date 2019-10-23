package edu.wisc.cs.arc.graphs;

import java.io.Serializable;

import org.batfish.datamodel.HeaderSpace;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.IpWildcardIpSpace;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.acl.MatchHeaderSpace;
import org.batfish.representation.cisco.PrefixList;
import org.batfish.representation.cisco.PrefixListLine;

/**
 * Represents a pair of communicating policy groups.
 *
 * @author Aaron Gember-Jacobson
 */
public class Flow implements Serializable {
	private static final long serialVersionUID = 4251002186681527261L;

	/** Entities sending traffic */
	private PolicyGroup source;

	/** Entities receiving traffic */
	private PolicyGroup destination;

	/**
	 * Creates a flow between two policy groups.
	 * @param source entities sending traffic
	 * @param destination entities receiving traffic
	 */
	public Flow(PolicyGroup source, PolicyGroup destination) {
		this.source = source;
		this.destination = destination;
	}

	/**
	 * Get the entities sending traffic.
	 * @return entities sending traffic
	 */
	public PolicyGroup getSource() {
		return this.source;
	}

	/**
	 * Get the entities receiving traffic.
	 * @return entities receiving traffic
	 */
	public PolicyGroup getDestination() {
		return this.destination;
	}

	@Override
	public String toString() {
		String src =
				(this.source != null ? this.source.toString() : "*");
		String dst =
				(this.destination != null ? this.destination.toString() : "*");
		return src + " -> " + dst;
	}

    @Override
    public boolean equals(Object other) {
    	if (this == other) {
    		return true;
    	}
    	if (!(other instanceof Flow)) {
    		return false;
    	}
		Flow otherFlow = (Flow)other;
		if (((this.source == null || otherFlow.source == null)
				&& this.source != otherFlow.source)
				|| (this.source != null
					&& !this.source.equals(otherFlow.source))) {
			return false;
		}
		if (((this.destination == null || otherFlow.destination == null)
				&& this.destination != otherFlow.destination)
				|| (this.destination != null
					&& !this.destination.equals(otherFlow.destination))) {
			return false;
		}
		return true;
	}

    @Override
    public int hashCode() {
    	return this.toString().hashCode();
    }

 

    /**
     * Checks if the flow is blocked by an ACL.
     * @param acl access control list to check
     * @return true if the flow is blocked the ACL; otherwise false
     */
    public boolean isBlocked(IpAccessList acl) {
    	// "Extended ACLs (registered customers only) control traffic by
    	// comparing the source and destination addresses of the IP packets to
    	// the addresses configured in the ACL."
    	// [http://cisco.com/c/en/us/support/docs/ip/access-lists/26448-ACLsamples.html]
    	for (IpAccessListLine line : acl.getLines()) {
            MatchHeaderSpace match = (MatchHeaderSpace)line.getMatchCondition();
            HeaderSpace headerSpace = match.getHeaderspace();
    		// Check if the source and destination are covered by the current
    		// line in the ACL
    	    IpWildcard srcWildcard = 
                ((IpWildcardIpSpace)headerSpace.getSrcIps()).getIpWildcard();
            Ip srcStartIp = srcWildcard.getIp();
            Ip srcEndIp = srcWildcard.getIp().getWildcardEndIp(
                    srcWildcard.getWildcard());
            IpWildcard dstWildcard = 
                ((IpWildcardIpSpace)headerSpace.getDstIps()).getIpWildcard();
            Ip dstStartIp = dstWildcard.getIp();
            Ip dstEndIp = dstWildcard.getIp().getWildcardEndIp(
                    dstWildcard.getWildcard());
    		if (this.source != null && this.source.within(srcStartIp, srcEndIp)
    				&& this.destination != null
    				&& this.destination.within(dstStartIp, dstEndIp)) {
    			// FIXME: Also check ports
    			return (line.getAction() == LineAction.REJECT);
    		}
    	}

    	// "By default, there is an implicit deny all clause at the end of every
    	// ACL."
    	// [http://cisco.com/c/en/us/support/docs/ip/access-lists/26448-ACLsamples.html]
    	return true;
    }

    /**
     * Checks if the flow is blocked by a prefix list.
     * @param prefixList prefix list to check
     * @return true if the flow is blocked the prefix list; otherwise false
     */
    public boolean isBlocked(PrefixList prefixList) {
    	for (PrefixListLine line : prefixList.getLines()) {
    		// Check if the destination is covered by the current line in the
    		// prefix list
    		if (this.destination != null
    				&& this.destination.within(line.getPrefix())) {
    			return (line.getAction() == LineAction.REJECT);
    		}
    	}

    	// FIXME?
    	return true;
    }
}
