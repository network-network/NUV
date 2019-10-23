package edu.wisc.cs.arc.graphs;

import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.IpProtocol;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.IpWildcardIpSpace;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.RouteFilterLine;
import org.batfish.datamodel.RouteFilterList;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.acl.MatchHeaderSpace;
import org.batfish.datamodel.routing_policy.RoutingPolicy;

import edu.wisc.cs.arc.configs.Config;

/**
 * Represents a set of network entities (hosts, processes, etc.). A policy 
 * group is defined in terms of IP prefix, port range, and transport protocol.
 * 
 * @author Raajay Viswanathan
 * @author Aaron Gember-Jacobson
 */
public class PolicyGroup implements Comparable<PolicyGroup>, Serializable {
	private static final long serialVersionUID = -1897130799118500967L;
	
	/** IP address range associated with the policy group */
	private Ip _startIp;
	private Ip _endIp;
    
    /** Port range associated with the policy group */
    private int _startPort;
    private int _endPort;
    
    /** Port range associated with the policy group */
    private EnumSet<IpProtocol> _protocols;
    
    /** Whether the entities represented by the group are inside the network */
    private boolean _internal;

	/** Whether the policy group name should be anonymized */
	private String _anonymous;

    /**
     * Create a policy group that covers the entire network.
     */
    public PolicyGroup()
    {
    	this._startIp = new Ip("0.0.0.0");
    	this._endIp = new Ip("255.255.255.255");
    	this._startPort = 1;
    	this._endPort = 65535;
        this._protocols = EnumSet.of(IpProtocol.TCP, IpProtocol.UDP);
        this._internal = false;
    }

    /**
     * Create a policy group that has the same address range, port range, and
     * transport protocol as another policy group.
     * @param other the policy group to clone
     */
    public PolicyGroup(PolicyGroup other) {
        this._startIp = other._startIp;
        this._endIp = other._endIp;
        this._startPort = other._startPort;
        this._endPort = other._endPort;
        this._protocols = other._protocols;
        this._internal = other._internal;
    }

    /**
     * Create a policy group with a specific address range, the entire port
     * range, and all transport protocols.
     * @param startIp the first IP in the address range for the policy group
     * @param endIp the last IP in the address range for the policy group
     */
    public PolicyGroup(Ip startIp, Ip endIp) {
        this();
        this._startIp = startIp;
        this._endIp = endIp;
    }
    
    /**
     * Create a policy group with a specific address range, the entire port
     * range, and all transport protocols.
     * @param prefix the address range for the policy group
     */
    public PolicyGroup(Prefix prefix) {
        this(prefix.getStartIp(), prefix.getEndIp());
    }

    /**
     * Create a policy group with a specific address range, a specific port
     * range and all transport protocols.
     * @param startIp the first IP in the address range for the policy group
     * @param endIp the last IP in the address range for the policy group
     * @param startPort the first port in the port range for the policy group
     * @param endPort the last port in the port range for the policy group
     */
    public PolicyGroup(Ip startIp, Ip endIp, int startPort, int endPort) {
        this(startIp, endIp);
        //this._startPort = startPort;
        //this._endPort = endPort;
    }
    
    /**
     * Create a policy group with a specific address range, a specific port
     * range and all transport protocols.
     * @param prefix the address range for the policy group
     * @param ports the port range for the policy group
     */
    public PolicyGroup(Prefix prefix, SubRange ports) {
        this(prefix.getStartIp(), prefix.getEndIp(), 
        		ports.getStart(), ports.getEnd());
    }
    
    /**
     * Create a policy group with a specific address range, port range, and 
     * transport protocol.
     * @param startIp the first IP in the address range for the policy group
     * @param endIp the last IP in the address range for the policy group
     * @param startPort the first port in the port range for the policy group
     * @param endPort the last port in the port range for the policy group
     * @param protocols transport protocols for the policy group
     */
    public PolicyGroup(Ip startIp, Ip endIp, int startPort, int endPort,
    		EnumSet<IpProtocol> protocols) {
        this(startIp, endIp, startPort, endPort);
       	//this._protocols = protocols;
    }
    
    /**
     * Create a policy group with a specific address range, port range, and 
     * transport protocol.
     * @param startIp the first IP in the address range for the policy group
     * @param endIp the last IP in the address range for the policy group
     * @param ports the port range for the policy group
     * @param protocol transport protocolsfor the policy group
     */
    public PolicyGroup(Ip startIp, Ip endIp, SubRange ports,
            IpProtocol protocol) {
        this(startIp, endIp, ports.getStart(), ports.getEnd());
        //this._protocols = protocols;
    }
    
    /**
     * Create a policy group with a specific address range, port range, and 
     * transport protocol.
     * @param prefix the address range for the policy group
     * @param ports the port range for the policy group
     * @param protocols transport protocols for the policy group
     */
    public PolicyGroup(Prefix prefix, SubRange ports, 
    		EnumSet<IpProtocol> protocols) {
        this(prefix.getStartIp(), prefix.getEndIp(), 
        		ports.getStart(), ports.getEnd(), protocols);
    }
    
    /**
     * Create a policy group with a specific address range, port range, and 
     * transport protocol.
     * @param prefix the address range for the policy group
     * @param ports the port range for the policy group
     * @param protocol transport protocol for the policy group
     */
    public PolicyGroup(Prefix prefix, SubRange ports, IpProtocol protocol) {
        this(prefix, ports, EnumSet.of(protocol));
    }
    
    /**
     * Create a policy group with a specific address range, the entire port
     * range, and a specific transport protocol.
     * @param startIp the first IP in the address range for the policy group
     * @param endIp the last IP in the address range for the policy group
     * @param protocol transport protocol for the policy group
     */
    public PolicyGroup(Ip startIp, Ip endIp, IpProtocol protocol) {
        this(startIp, endIp);
        //this._protocols = EnumSet.of(protocol);
    }
    
    /**
     * Create a policy group with a specific address range, the entire port
     * range, and a specific transport protocol.
     * @param prefix the address range for the policy group
     * @param protocol transport protocol for the policy group
     */
    public PolicyGroup(Prefix prefix, IpProtocol protocol) {
        this(prefix.getStartIp(), prefix.getEndIp(), protocol);
    }

	/**
	 * Make the policy group name anonymous when it is output.
	 */
	public void makeAnonymous() {
		if (this._anonymous != null) {
			return;
		}
		String raw = this.toString();
		try {
			MessageDigest messageDigest = 
					MessageDigest.getInstance("MD5");
			messageDigest.update(raw.getBytes());
			this._anonymous = new BigInteger(1, 
					messageDigest.digest()).toString(16).substring(0,8);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

    /**
     * Get this policy group's starting IP.
     * @return the first IP in the address range for this policy group
     */
    public Ip getStartIp() {
        return this._startIp;
    }
    
    /**
     * Get this policy group's ending IP.
     * @return the last IP in the address range for this policy group
     */
    public Ip getEndIp() {
        return this._endIp;
    }
    
    /**
     * Get this policy's IP range as a prefix.
     * @return the prefix for this policy group
     */
    public Prefix getPrefix() {
        long numIps = this._endIp.asLong() - this._startIp.asLong() + 1;
        int prefixLength = 32 - (int) (Math.log(numIps)/Math.log(2));
        return new Prefix(this._startIp, prefixLength);
    }

    /**
     * Get this policy group's starting port.
     * @return the first port in the port range for this policy group
     */
    public int getStartPort() {
        return this._startPort;
    }
    
    /**
     * Get this policy group's ending port.
     * @return the last port in the port range for this policy group
     */
    public int getEndPort() {
        return this._endPort;
    }
    
    /**
     * Get this policy group's transport protocols.
     * @return policy group's transport protocols
     */
    public EnumSet<IpProtocol> getProtocols() {
        return this._protocols;
    }
    
    /**
     * Indicate whether the policy group refers to entities inside the network.
     * @param internal whether the policy group refers to internal entities
     */
    public void setInternal(boolean internal) {
    	this._internal = internal;
    }
    
    /**
     * Determine if the policy group refers to entities inside the network.
     * @return true if the entities are internal, otherwise false
     */
    public boolean isInternal() {
    	return this._internal;
    }

    /**
     * Check if another policy group falls inside this policy group.
     * @param other policy group to compare to
     * @return true if another policy group's address range, port range, and
     * 			transport protocols are within this policy group's address
     * 			range, port range, and transport protocols; otherwise false
     */
    public boolean contains(PolicyGroup other) {
    	// Check if protocols is a subset (or the same)
    	if (!this._protocols.containsAll(other._protocols)) {
    		return false;
    	}
    	
    	// Check if port range is a subset (or the same)
    	if (other._startPort < this._startPort 
    			|| other._endPort > this._endPort) {
    		return false;
    	}
    	
    	// Check if prefix is inside
    	if (other._startIp.asLong() < this._startIp.asLong()
    		|| other._endIp.asLong() > this._endIp.asLong()) {
    		return false;
    	}
    	
    	return true;
    }
    
    /**
     * Check if an IP address falls inside (or equals) this policy group's 
     * address range.
     * @param ip IP address to check
     * @return true if the IP address falls within the policy group's address
     * 			range; otherwise false 
     */
    public boolean contains(Ip ip) {
    	return (this._startIp.asLong() <= ip.asLong() 
    			&& this._endIp.asLong() >= ip.asLong());
    }
    
    /**
     * Check if an IP prefix falls inside this policy groups's address range.
     * @param prefix prefix to check
     * @return true if the entire prefix falls within the policy group's address
     * 			range; otherwise false 
     */
    public boolean contains(Prefix prefix) {
    	return (this._startIp.asLong() <= prefix.getStartIp().asLong()
    			&& this._endIp.asLong() >= prefix.getEndIp().asLong());
    }
    
    /**
     * Check if this policy group's address range is within (or equals) an IP 
     * prefix.
     * @param prefix prefix to check
     * @return true if the entire policy group's address range falls within the 
     * 			prefix; otherwise false 
     */
    public boolean within(Prefix prefix) {
    	return this.within(prefix.getStartIp(), prefix.getEndIp());
    }
    
    /**
     * Check if this policy group's address range is within (or equals) a range
     * of IP addresses
     * @param startIp start of the range to check
     * @param endIp end of the range to check
     * @return true if the entire policy group's address range falls within the 
     *          prefix; otherwise false 
     */
    public boolean within(Ip startIp, Ip endIp) {
        return (this._startIp.asLong() >= startIp.asLong()
                && this._endIp.asLong() <= endIp.asLong());
    }

    
    /**
     * Check this policy group overlaps with another policy group.
     * @param other policy group to compare to
     * @return true if another policy group's prefix, port range, and transport
     * 			protocols overlap with this policy group's prefix, port range,
     * 			and transport protocols; otherwise false
     */
    public boolean intersects(PolicyGroup other) {
    	// Check if protocols do not overlap
    	boolean protocolsOverlap = false;
    	for (IpProtocol protocol : this._protocols) {
    		if (other._protocols.contains(protocol)) {
    			protocolsOverlap = true;
    			break;
    		}
    	}
    	if (!protocolsOverlap) {
    		return false;
    	}
    	
    	// Check if port range is entirely before or after
    	if (this._endPort < other._startPort
    			|| this._startPort > other._endPort) {
    		return false;
    	}
    	
    	// Check if prefix is entirely before or after
    	if (this._endIp.asLong() < other._startIp.asLong()
    		|| this._startIp.asLong() > other._endIp.asLong()) {
    		return false;
    	}
    	
    	return true;
    }
    
    /**
     * Check if this policy group's address range overlaps an IP prefix.
     * @param prefix prefix to check
     * @return true if the policy group's address range intersects the prefix; 
     * 			otherwise false 
     */
    public boolean intersects(Prefix prefix) {
    	// Check if prefix is entirely before or after
    	return (!(this._startIp.asLong() > prefix.getEndIp().asLong()
    			|| this._endIp.asLong() < prefix.getStartIp().asLong()));
    }
    
    /**
     * Get the prefix, port range, and transport protocol(s) for this policy
     * group.
     */
    @Override
    public String toString() {
		if (this._anonymous != null) {
			return this._anonymous;
		}
		return this._startIp.toString() + "-" + this._endIp;// + " " + 
        		//this._startPort + "-" + this._endPort + " " + this._protocols;
    }
    
    @Override
    public boolean equals(Object other) {
    	if (this == other) {
    		return true;
    	}
    	if (!(other instanceof PolicyGroup)) {
    		return false;
    	}
		PolicyGroup otherGroup = (PolicyGroup)other;
		
		// Check if protocol is the same
    	if (!this._protocols.equals(otherGroup._protocols)) {
    		return false;
    	}
    	
    	// Check if port range is the same
    	if (this._startPort != otherGroup._startPort
    			|| this._endPort != otherGroup._endPort) {
    		return false;
    	}
    	
    	// Check if prefix is the same
    	if (this._startIp.asLong() != otherGroup._startIp.asLong()
    		|| this._endIp.asLong()	!= otherGroup._endIp.asLong()) {
    		return false;
    	}
    	
    	return true;
    }

    /**
     * Parse the interested policy groups form a group of configurations.
     * @param configs
     * @return a set of policy groups
     */
    public static List<PolicyGroup> extract(Map<String, Config> configs) {
    	List<PolicyGroup> allGroups = new ArrayList<PolicyGroup>();
    	
    	// Process each device configuration
		for (Entry<String, Config> entry : configs.entrySet()) {
				List<PolicyGroup> deviceGroups = PolicyGroup.extract(
						entry.getValue().getGenericConfiguration());
				for (PolicyGroup group : deviceGroups) {
					if (!allGroups.contains(group)) {
						allGroups.add(group);
					}
					else {
						if (group.isInternal()) {
							allGroups.remove(group);
							allGroups.add(group);
						}
					}
				
			}
		
		}
		
		Collections.sort(allGroups);
		return allGroups;
    }

   
    //add by lyh
    
	public static Map<Integer, Map<String, String>> getPlolicyIface(PolicyGroup dst, PolicyGroup src, Map<String, Config> configs2, DeviceGraph etg) {
		Map<Integer, Map<String, String>> ifacemap = new LinkedHashMap<Integer, Map<String, String>>();
		 Map<String, String> tmap = new LinkedHashMap<String, String>();
		Boolean dflag = false, iflag = false;
		for (Entry<String, Config> entry : configs2.entrySet()) {
			
			for (Interface iface : entry.getValue().getGenericConfiguration().getInterfaces().values()) {
				if (null == iface.getAddress()) {
					continue;
				}
				PolicyGroup group = new PolicyGroup(iface.getAddress().getPrefix());
				group.setInternal(true);
				if (group.intersects(dst)) {
					tmap.put(entry.getKey(), iface.getName());
					ifacemap.put(2, tmap);
					dflag = true;
					tmap.clear();
				}
				if (group.intersects(src)) {
					tmap.put(entry.getKey(), iface.getName());
					ifacemap.put(1, tmap);
					iflag = true;
					tmap.clear();
				}
				if (dflag && iflag)
					break;
			}
			if (dflag && iflag)
				break;
		}
		return ifacemap;
	}
	
    /**
     * Extract policy group information from a Cisco device configuration.
     * @param config configuration for the device
     */
    private static List<PolicyGroup> extract(Configuration config) {
    	List<PolicyGroup> groups = new ArrayList<PolicyGroup>();
    	
    	Set<String> usedAcls = new LinkedHashSet<String>();
    	
    	// Extract policy groups based on interface addresses
    	for (Interface iface : config.getInterfaces().values()) {
    		if (null == iface.getAddress()) {
    			continue;
    		}
    		
    		if (iface.getIncomingFilter() != null) {
    			usedAcls.add(iface.getIncomingFilterName());
    		}
    		if (iface.getOutgoingFilter() != null) {
    			usedAcls.add(iface.getOutgoingFilterName());
    		}
    		 
    		PolicyGroup group = new PolicyGroup(iface.getAddress().getPrefix());
    		group.setInternal(true);
    		groups.add(group);
    	}
    	
    	// Extract policy groups from standard ACLs
    	for (IpAccessList acl: config.getIpAccessLists().values()) {
    		if (!usedAcls.contains(acl.getName())) {
    			continue;
    		}
    		for (IpAccessListLine line : acl.getLines()) {
                MatchHeaderSpace match = 
                        (MatchHeaderSpace)line.getMatchCondition();
                HeaderSpace headerSpace = match.getHeaderspace();
    		    IpWildcard srcWildcard = ((IpWildcardIpSpace)
                        headerSpace.getSrcIps()).getIpWildcard();
    		    Ip startIp = srcWildcard.getIp();
    	        Ip endIp = srcWildcard.getIp().getWildcardEndIp(
    	                srcWildcard.getWildcard());
    			if (headerSpace.getSrcPorts().size()==0){
    				groups.add(new PolicyGroup(startIp, endIp, IpProtocol.IP));
    			} else {
    				for (SubRange portRange : headerSpace.getSrcPorts()){
    					groups.add(new PolicyGroup(startIp, endIp, portRange,
        						IpProtocol.IP)); //FIXME add proper protocol
    				}
    			}
        		
    		    IpWildcard dstWildcard = ((IpWildcardIpSpace)
                        headerSpace.getDstIps()).getIpWildcard();
                startIp = dstWildcard.getIp();
                endIp = dstWildcard.getIp().getWildcardEndIp(
                        dstWildcard.getWildcard());
    			if (headerSpace.getDstPorts().size()==0){
    				groups.add(new PolicyGroup(startIp, endIp, IpProtocol.IP));
    			} else {
    				for (SubRange portRange : headerSpace.getDstPorts()){
    					groups.add(new PolicyGroup(startIp, endIp, portRange,
        						IpProtocol.IP)); //FIXME add proper protocol
    				}
    			}
       		}
    	}
    	
    	return groups;
    }
    
    public static Set<PolicyGroup> getMerged(Set<PolicyGroup> policyGroups) {
    	Set<PolicyGroup> merged = new LinkedHashSet<PolicyGroup>();
    	
    	// Process all policy groups to obtain coalesced groups
    	List<PolicyGroup> toProcess = new ArrayList<PolicyGroup>(policyGroups);
    	Collections.sort(toProcess);
    	
    	while (toProcess.size() > 0) {
    		PolicyGroup groupA = toProcess.get(0);
    		
    		// Find all neighboring groups
    		for (int b = 1; b < toProcess.size(); b++) {
    			if (groupA.attemptMerge(toProcess.get(b))) {
    				toProcess.remove(b);
    				b--;
    			}
    		}
    		
    		merged.add(groupA);
    		toProcess.remove(0);
    	}
    	
    	return merged;
    }
    
    /**
     * Attempts to merge another policy group with this policy group.
     * @param other policy group to merge
     * @return true if a merge was possible; otherwise false
     */
    public boolean attemptMerge(PolicyGroup other) {
    	// If the ports and the IP ranges are the same, then merge
    	if (this._startPort == other._startPort
    			&& this._endPort == other._endPort
    			&& this._startIp.asLong() == other._startIp.asLong()
    			&& this._endIp.asLong() == other._endIp.asLong()) {
    		this._protocols.addAll(other._protocols);
    		return true;
    	}
    	
    	// If the ports and protocols are the same and the IP ranges are
    	// neighbors, then merge
    	if ((this._startPort == other._startPort
    			&& this._endPort == other._endPort
    			&& this._protocols.equals(other._protocols))
    			&& (this._startIp.asLong() == other._endIp.asLong() + 1
    				|| other._startIp.asLong() == this._endIp.asLong() + 1)) {
    		if (other._startIp.asLong() < this._startIp.asLong()) {
    			this._startIp = other._startIp;
    		}
    		if (other._endIp.asLong() > this._endIp.asLong()) {
    			this._endIp = other._endIp;
    		}
    		return true;
    	}
    	
    	// If the IP ranges and protocols are the same and the port ranges are 
    	// neighbors, then merge
    	if ((this._startIp.asLong() == other._startIp.asLong()
    			&& this._endIp.asLong() == other._endIp.asLong()
    			&& this._protocols.equals(other._protocols))
    			&& (this._startPort == other._endPort + 1
    				|| other._startPort == this._endPort + 1)) {
    		if (other._startPort < this._startPort) {
    			this._startPort = other._startPort;
    		}
    		if (other._endPort > this._endPort) {
    			this._endPort = other._endPort;
    		}
    		return true;
    	}
    	
    	return false;
    }
    
    public static List<PolicyGroup> getNonOverlapping(
    		List<PolicyGroup> policyGroups)
    {
    	List<PolicyGroup> nonOverlapping = new ArrayList<PolicyGroup>();
    	
    	// Process all policy groups to obtain non-overlapping groups
    	List<PolicyGroup> toProcess = new ArrayList<PolicyGroup>();
    	toProcess.addAll(policyGroups);
    	Collections.sort(toProcess);
    	
    	while (toProcess.size() > 0) {
    		// Start with the first group in the list
    		PolicyGroup groupA = toProcess.get(0);
    		
    		// Find an overlapping group
    		PolicyGroup groupB = null;
    		for (int b = 1; b < toProcess.size(); b++) {
    			if (groupA.equals(toProcess.get(b))) {
    				if(toProcess.remove(b).isInternal()) {
    				    groupA.setInternal(true);
    				}
    				b--;
    			}
    			if (groupA.intersects(toProcess.get(b))) {
    				groupB = toProcess.get(b);
    				break;
    			}
    		}
    		
    		// If there is no overlapping group, then we have successfully
    		// eliminated all overlap for groupA, and we move groupA to the 
    		// non-overlapping list
    		if (groupB == null) {
    			toProcess.remove(groupA);
    			nonOverlapping.add(groupA);
    			continue;
    		}
    		
    		// Otherwise, we remove both groups from the overlapping list,
    		// compute smaller non-overlapping groups, and add the smaller
    		// groups to the overlapping list
    		toProcess.remove(groupA);
    		toProcess.remove(groupB);
    		toProcess.addAll(groupA.getNonOverlapping(groupB));
    	}

        return nonOverlapping;
    }
    
    public Set<PolicyGroup> getNonOverlapping(PolicyGroup other) {
    	Set<PolicyGroup> nonOverlapping = new LinkedHashSet<PolicyGroup>();
    	
    	if (this.equals(other)) {
    		nonOverlapping.add(this);
    		return nonOverlapping;
    	}
    	
    	// Compute non-overlapping protocols
    	Set<EnumSet<IpProtocol>> protocols = new LinkedHashSet<EnumSet<IpProtocol>>();
    	EnumSet<IpProtocol> thisUniqueProtocols = this._protocols.clone();
    	thisUniqueProtocols.removeAll(other._protocols);
    	if (thisUniqueProtocols.size() > 0) {
    		protocols.add(thisUniqueProtocols);
    	}
    	EnumSet<IpProtocol> otherUniqueProtocols = other._protocols.clone();
    	otherUniqueProtocols.removeAll(this._protocols);
    	if (otherUniqueProtocols.size() > 0) {
    		protocols.add(otherUniqueProtocols);
    	}
    	EnumSet<IpProtocol> overlapProtocols = this._protocols.clone();
    	overlapProtocols.removeAll(thisUniqueProtocols);
    	if (overlapProtocols.size() > 0) {
    		protocols.add(overlapProtocols);
    	}

    	// Compute non-overlapping port ranges
    	Set<Integer> portBoundaries = new LinkedHashSet<Integer>();
    	portBoundaries.add(this._startPort);
    	portBoundaries.add(this._endPort+1);
    	portBoundaries.add(other._startPort);
    	portBoundaries.add(other._endPort+1);
    	
    	List<Integer> sortedPortBoundaries = 
    			new ArrayList<Integer>(portBoundaries);
    	Collections.sort(sortedPortBoundaries);
    	
    	// Compute non-overlapping address ranges
    	Set<Long> addressBoundaries = new LinkedHashSet<Long>();
    	addressBoundaries.add(this._startIp.asLong());
    	addressBoundaries.add(this._endIp.asLong()+1);
    	addressBoundaries.add(other._startIp.asLong());
    	addressBoundaries.add(other._endIp.asLong()+1);
    	
    	List<Long> sortedAddressBoundaries = 
    			new ArrayList<Long>(addressBoundaries);
    	Collections.sort(sortedAddressBoundaries);
    	
    	for (int p = 1; p < sortedPortBoundaries.size(); p++) {
    		int startPort = sortedPortBoundaries.get(p - 1);
    		int endPort = sortedPortBoundaries.get(p) - 1;
    		for (int a = 1; a < sortedAddressBoundaries.size(); a++) {
    			Ip startIp = new Ip(sortedAddressBoundaries.get(a - 1));
    			Ip endIp = new Ip(sortedAddressBoundaries.get(a) - 1);
    			for (EnumSet<IpProtocol> protocolSet : protocols) {
    				PolicyGroup group = new PolicyGroup(startIp, endIp, 
	    					startPort, endPort, protocolSet);
    				
    				// Determine if group is internal
    				if (this.contains(other)) {
    					if (other.contains(group)) {
    						group.setInternal(other.isInternal());
    					}
    					else {
    						group.setInternal(this.isInternal());
    					}
    				}
    				else if (other.contains(this)) {
    					if (this.contains(group)) {
    						group.setInternal(this.isInternal());
    					}
    					else {
    						group.setInternal(other.isInternal());
    					}
    				}
    				else {
    					if (this.contains(group) && !other.contains(group)) {
    						group.setInternal(this.isInternal());
    					}
    					else {
    						group.setInternal(other.isInternal());
    					}
    				}
    				
	    			nonOverlapping.add(group);
    			}
    		}
    	}
    	return nonOverlapping;
    }
    
    @Override
    public int hashCode() {
    	return (int)(this._startIp.asLong()) + (int)(this._endIp.asLong())
    			+ this._startPort + this._endPort + this._protocols.hashCode();
    }

	@Override
	public int compareTo(PolicyGroup other) {
		long startIpDiff = this._startIp.asLong() - other._startIp.asLong();
		if (startIpDiff != 0) {
			return (startIpDiff < 0 ? -1 : 1);
		}
		
		long endIpDiff = this._endIp.asLong() - other._endIp.asLong();
		if (endIpDiff != 0) {
			return (endIpDiff < 0 ? -1 : 1);
		}
		
		int startPortDiff = this._startPort - other._startPort;
		if (startPortDiff != 0) {
			return startPortDiff;
		}
		
		int endPortDiff = this._endPort - other._endPort;
		if (endPortDiff != 0) {
			return endPortDiff;
		}
		
		return 0;
	}
	
    /**
     * Checks if the policy group is blocked by a route filter list.
     * @param routeFilterList route filter list to check
     * @return true if the flow is blocked the route filter list; otherwise 
     *      false
     */
    public boolean isBlocked(RouteFilterList routeFilterList) {
    	for (RouteFilterLine line : routeFilterList.getLines()) {
    		// Check if the policy group is covered by the current line
    		if (this.within(line.getIpWildcard().toPrefix())) {
    			return (line.getAction() == LineAction.REJECT);
    		}
    	}
    	
    	// FIXME?
    	return true;
    }
    
  
    
    /**
     * Checks if the flow is blocked by an ACL.
     * @param acl access control list to check
     * @return true if the flow is blocked the ACL; otherwise false
     */
    public boolean isBlocked(IpAccessList acl) {
    	for (IpAccessListLine line : acl.getLines()) {
            MatchHeaderSpace match = (MatchHeaderSpace)line.getMatchCondition();
            HeaderSpace headerSpace = match.getHeaderspace();
    		// Check if the policy group is covered by the current line
    		Prefix dstPrefix = ((IpWildcardIpSpace)headerSpace.getDstIps())
                    .getIpWildcard().toPrefix();
			if (this.within(dstPrefix)) {
    			return (line.getAction() == LineAction.REJECT);
    		}//FIXME also iterate over source IPs ?? 
    		
    	
    	}
    	
    	// "By default, there is an implicit deny all clause at the end of every
    	// ACL." 
    	// [http://cisco.com/c/en/us/support/docs/ip/access-lists/26448-ACLsamples.html]
    	return true;
    }
	
	/**
     * Checks if the policy group is denied by a routing policy.
     * @param routingPolicy routing policy to check
     * @return true if the policy group is denied the routing policy; otherwise 
     *      false
     */
    public boolean isBlocked(RoutingPolicy routingPolicy, Device device){
        // FIXME
        return false;
   /* 	 ArrayList<Integer> sequenceNumbers = 
    			new ArrayList<Integer>(routeMap.getClauses().keySet());
    	Collections.sort(sequenceNumbers);
    	for (Integer sequenceNumber : sequenceNumbers) {
    		RouteMapClause clause = routeMap.getClauses().get(sequenceNumber);
    		boolean denied = (clause.getAction() == LineAction.REJECT);
    		
    		// "if no matching statements, all prefixes are matched implicitly."
    		// [https://learningnetwork.cisco.com/message/254239#254239]
    		if (0 == clause.getMatchList().size()) {
    			return denied;
    		}
    		
    		// See if destination is matched
    		for (RouteMapMatchLine line : clause.getMatchList()) {
    			switch (line.getType()) {
    			case IP_PREFIX_LIST:
    				RouteMapMatchIpPrefixListLine ipPrefixLine = 
    						(RouteMapMatchIpPrefixListLine)line;
    				for (String prefixListName : ipPrefixLine.getListNames()) {
    					PrefixList prefixList = 
    							device.getPrefixList(prefixListName);
    					if (null == prefixList) {
    						throw new GeneratorException(
    								"Device " + device.getName()
    								+ " did not have prefix list " 
    								+ prefixListName 
    								+ " referenced in route-map "
    								+ routeMap.getMapName());
    					}
    					if(!this.isBlocked(prefixList)) {
    						return denied;
    					}
    				}
    				break;
    			case IP_ACCESS_LIST:
    				RouteMapMatchIpAccessListLine ipAclLine =
    						(RouteMapMatchIpAccessListLine)line;
    				for (String aclName : ipAclLine.getListNames()) {
    					StandardAccessList standardAcl = 
    							device.getStandardAcl(aclName);
    					ExtendedAccessList extendedAcl = 
    							device.getExtendedAcl(aclName);
    					if (null == standardAcl && null == extendedAcl) {
    						throw new GeneratorException(
    								"Device " + device.getName()
    								+ " did not have ACL " + aclName 
    								+ " referenced in route-map "
    								+ routeMap.getMapName());
    					}
    					if (standardAcl != null
    							&& !this.isBlocked(standardAcl)) {
    						return denied;
    					}
    					if (extendedAcl != null 
    							&& !this.isBlocked(extendedAcl)) {
    						return denied;
    					}
    				}
    				break;
    			default:
    				throw new GeneratorException(
    						"Unhandled route-map match line type "
    						+ line.getType());
    			}
    		}
    	}
    	
    	// "Each ACL ends with an implicit deny statement, by design convention;
    	// there is no similar convention for route maps. If the end of a route 
    	// map is reached during matching attempts, the result depends on the 
    	// specific application of the route map. Fortunately, route maps that 
    	// are applied to redistribution behave the same way as ACLs: if the 
    	// route does not match any clause in a route map then the route 
    	// redistribution is denied, as if the route map contained deny 
    	// statement at the end."
    	// [http://www.cisco.com/c/en/us/td/docs/security/asa/asa84/configuration85/guide/asa_cfg_cli_85/route_maps.html#wp1121542]
    	return true;*/
    }
}
