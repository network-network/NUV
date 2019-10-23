package edu.wisc.cs.arc.graphs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.ospf.OspfArea;
import org.batfish.datamodel.RouteFilterList;
import org.batfish.datamodel.BgpActivePeerConfig;
import org.batfish.datamodel.BgpProcess;
import org.batfish.datamodel.ospf.OspfProcess;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.StaticRoute;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.Vrf;
import org.batfish.datamodel.routing_policy.RoutingPolicy;

import edu.wisc.cs.arc.GeneratorException;
import edu.wisc.cs.arc.Logger;
import edu.wisc.cs.arc.graphs.Interface.InterfaceType;

/**
 * A device in a network.
 * @author Aaron Gember-Jacobson (agember@cs.wisc.edu)
 */
public class Device implements Serializable {
	private static final long serialVersionUID = -9130905482529614009L;

	/** Name */
	private String name;
	
	/** Interfaces on the device */
	private List<Interface> interfaces;
	
	/** Routing processes on the device */
	private List<Process> routingProcesses;
	
	/** Standard ACLs on the device */
	private Map<String, IpAccessList> acls;
	
	
	/** Route maps on the device */
	private Map<String, RoutingPolicy> routePolicies;
	
	/** Prefix lists on the device */
	private Map<String, RouteFilterList> routeFilterLists;
	
	/** Whether the device is external */
	private boolean external;
	
	/** Configuration for device */
	private Configuration configuration;
	
	/**
	 * Create a device.
	 * @param name name of the device
	 */
	public Device(String name) {
		this.name = name;
		this.interfaces = new ArrayList<Interface>();
		this.routingProcesses = new ArrayList<Process>();
		this.acls = null;
		this.routePolicies = null;
		this.routeFilterLists = null;
		this.external = true;
	}
	
	/**
	 * Create a device.
	 * @param name name of the device
	 * @param standardAcls standard ACLs on the device
	 * @param extendedAcls extended ACLs on the device
	 * @param routeMaps route maps on the device
	 * @param prefixLists prefix lists on the device
	 */
	public Device(String name, Map<String, IpAccessList> acls,
			Map<String, RoutingPolicy> routePolicies, 
			Map<String, RouteFilterList> routeFilterLists) {
		this(name);
		this.acls = acls;
		this.routePolicies = routePolicies;
		this.routeFilterLists = routeFilterLists;
		this.external = false;
	}
	
	/**
	 * Create a device from a Cisco device configuration
	 * @param name name of the device
	 * @param config configuration for the device
	 * @param logger
	 */
	public Device(String name, Configuration config, Logger logger) {
		this(name, config.getIpAccessLists(), 
				 config.getRoutingPolicies(), 
				config.getRouteFilterLists());
		this.configuration = config;

//		System.out.println("create device @@@@@@@@@@@@@@ "+name);
		// Extract interfaces
		for (org.batfish.datamodel.Interface genericIface : 
				config.getInterfaces().values()) {
//			System.out.println("why interface ------- "+genericIface.getName());
			Interface iface = new Interface(this, genericIface);
			
			// Ignore certain interface types
			if (iface.getType() == InterfaceType.BRIDGE
			        || iface.getType() == InterfaceType.ATM) {
			    continue;
			}
			
			this.addInterface(iface);
		}
		
		// Set up sub interfaces
		for (Interface iface : this.getInterfaces()) {
			if (iface.getChannelGroup() != null) {
				Interface portChannel = this.getInterface(
						Interface.NAME_PREFIX_PORT_CHANNEL 
						+ iface.getChannelGroup());
				if (null == portChannel) {
					throw new GeneratorException("No port channel " 
							+ iface.getChannelGroup() + " for interface "
							+ iface.getDevice() + ":" + iface.getName());
				}
				else {
					portChannel.addSubInterface(iface);
				}
			}
			if (iface.getType() != InterfaceType.VLAN 
					&& iface.getAccessVlan() != null) {
				Interface vlan = this.getInterface(Interface.NAME_PREFIX_VLAN
						+ iface.getAccessVlan());
				if (null == vlan) {
					logger.debug("No vlan " + iface.getAccessVlan() 
							+ " for interface " + iface.getDevice() + ":" 
							+ iface.getName());
				}
				else {
					vlan.addSubInterface(iface);
				}
			}
            else if (iface.getType() != InterfaceType.VLAN 
                    && iface.getAllowedVlans() != null) {
                for (Interface tmpiface : this.getInterfaces()) {
                    if (tmpiface.getType() != InterfaceType.VLAN
                            || null == tmpiface.getAccessVlan()) {
                        continue;
                    }
                    boolean allowed = false;
                    for (SubRange range : iface.getAllowedVlans()) {
                        if (tmpiface.getAccessVlan() >= range.getStart()
                                && tmpiface.getAccessVlan() <= range.getEnd()) {
                            allowed = true;
                            break;
                        }
                    }
                    if (allowed) {
                        tmpiface.addSubInterface(iface);
                    }
                }
            }
		}
		
		// Extract routing processes from every VRF
		for (Vrf vrf : config.getVrfs().values()) {
		    // Extract BGP process
			if (vrf.getBgpProcess() != null){
				BgpProcess bgpProcess = vrf.getBgpProcess();
				// Determine interfaces participating in the process
				Collection<Interface> bgpInterfaces = new ArrayList<Interface>();
				for (BgpActivePeerConfig neighbor : bgpProcess.getActiveNeighbors().values()){
					// Check if each interface can be used to reach peer
				    Ip remotePeer = neighbor.getPeerAddress();
				    if (null == remotePeer) {
				    	continue; // FIXME
				    }
					for (Interface localInterface : this.getInterfaces()) {
						if (localInterface.hasPrefix() 
							&& localInterface.getPrefix().containsIp(remotePeer)){
							bgpInterfaces.add(localInterface);
							break;
						}
					}
				}
				this.addRoutingProcess(new Process(this, vrf, bgpProcess, 
						bgpInterfaces));
				
				/*logger.debug("BGP " + bgpProcess.getPid() + " peer groups:");
				for (BgpPeerGroup peerGroup : bgpProcess.getAllPeerGroups()) {
				    logger.debug("\t" + peerGroup.getName() 
				        + " RemoteAS=" + peerGroup.getRemoteAS()
				        + " InboundRouteMap=" + peerGroup.getInboundRouteMap());
				}
				
				logger.debug("BGP " + bgpProcess.getPid() + " IP peer groups:");
				for (IpBgpPeerGroup peerGroup : bgpProcess.getIpPeerGroups().values()) {
	                logger.debug("\t" + peerGroup.getName() 
	                    + " RemoteAS=" + peerGroup.getRemoteAS()
	                    + " InboundRouteMap=" + peerGroup.getInboundRouteMap()
	                    + " Template=" + peerGroup.getGroupName());
	            }*/
			}
		
    		// Extract OSPF process
    		if (vrf.getOspfProcess()!=null) {
    			OspfProcess ospfProcess = vrf.getOspfProcess();
    			// Determine interfaces participating in the process
    			Collection<Interface> ospfInterfaces = new ArrayList<Interface>();
    			// Consider every prefix that is advertised by the OSPF process
    			for (OspfArea area : ospfProcess.getAreas().values()) {
    			    for (String areaIfaceName : area.getInterfaces()) {
                        Interface areaIface = this.getInterface(areaIfaceName);
    			        // FIXME: Only consider non-black-listed, non-passive ifaces
    			        /*if (!areaIface.getBlacklisted()
    			                && !areaIface.getOspfPassive()
    			                &&  */
                        //add by lyh, because of abstraction configs
                        if(areaIface!=null) {
                        if (areaIface.getAddress() != null) {
    			            Interface iface = this.getInterface(
    			                    areaIface.getName());
    			            if (null == iface) {
    			                logger.warn(this.getName() 
    			                        + " does not have the interface " 
                                        + areaIface
                                        + " specified in the OSPF area "
                                        + area.toString());
    			            } else if (iface.getType() 
    			                    != InterfaceType.LOOPBACK) {
    			                ospfInterfaces.add(iface);
    			            }
    			        }
    			    }
    			    }
    			}
    			
    			this.addRoutingProcess(new Process(this, vrf, ospfProcess, 
    					ospfInterfaces));
    		}
    		
    		// Extract static routes
    		if (vrf.getStaticRoutes()!=null){
    			Set<StaticRoute> staticRoutes = vrf.getStaticRoutes();
    			for (StaticRoute staticRoute:staticRoutes){
    				List<Interface> staticInterfaces = new ArrayList<Interface>();
    				if (staticRoute.getNextHopInterface() != null
    						&& !staticRoute.getNextHopInterface().toLowerCase().startsWith("null")) {
    					Interface iface = this.getInterface(
    							staticRoute.getNextHopInterface());
    					if (null == iface) {
    						logger.warn(this.getName() + " does not have the interface " 
    								+ staticRoute.getNextHopInterface() 
    								+ " specified as the next hop for the static route "
    								+ staticRoute.toString());
    					}
    					else {
    						staticInterfaces.add(iface);
    					}
    				}
    				else if (staticRoute.getNextHopIp() != null) {
    					for (Interface iface : this.getInterfaces()) {
    						if (iface.hasPrefix() && iface.getPrefix()
                                    .containsIp(staticRoute.getNextHopIp())) {
    							staticInterfaces.add(iface);
    							break;
    						}
    					}
    				}
    				
    				this.addRoutingProcess(new Process(this, vrf, staticRoute, 
    						staticInterfaces));
    			}
    		}
    	}
	}
	
	/**
	 * Get the name of the device.
	 * @return name of the device
	 */
	public String getName() {
		return this.name;
	}
	
	/**
	 * Add an interface to the device.
	 * @param iface interface to add
	 */
	public void addInterface(Interface iface) {
		this.interfaces.add(iface);
	}
	
	/**
	 * Get the interfaces on the device.
	 * @return interfaces on the device
	 */
	public Collection<Interface> getInterfaces() {
		return this.interfaces;
	}
	
	/**
	 * Get a specific interface on the device.
	 * @param name name of the interface
	 * @return interfaces on the device with the given name; null if none exists
	 */
	public Interface getInterface(String name) {
		for (Interface iface : this.interfaces) {
			if (iface.getName().equals(name)) {
				return iface;
			}
		}
		return null;
	}

	public Interface getInterface(Ip ip) {
		for (Interface iface : this.interfaces) {
			if (iface.getAddress().equals(ip)) {
				return iface;
			}
		}
		return null;
	}
	
	/**
	 * Add a routing process to the device.
	 * @param process routing process to add
	 */
	public void addRoutingProcess(Process process) {
		this.routingProcesses.add(process);
	}
	
	/**
	 * Get the routing processes running on the device.
	 * @return routing processes running on the device
	 */
	public List<Process> getRoutingProcesses() {
		return this.routingProcesses;
	}
	
	/**
	 * add by lyh
	 */
	public Map<String, RoutingPolicy> getRoutePolicies() {
		return this.routePolicies;
	}
	
	public Set<String> getRoutePoliNames() {
	    return this.routePolicies.keySet();
	}
	/**
	 * Remove static routing process that do not apply to any policy group.
	 * @param policyGroups
	 */
	public void pruneStaticProcesses(List<PolicyGroup> policyGroups) {
		List<Process> toRemove = new ArrayList<Process>();
		for (Process process : this.routingProcesses) {
			if (!process.isStaticProcess()) {
				continue;
			}
			Prefix staticPrefix = process.getStaticRouteConfig().getNetwork();
			boolean appliesToPolicyGroup = false;
			for (PolicyGroup policyGroup : policyGroups) {
				if (policyGroup.intersects(staticPrefix)) {
					appliesToPolicyGroup = true;
					break;
				}
			}
			if (!appliesToPolicyGroup) {
				toRemove.add(process);
			}
		}
		this.routingProcesses.removeAll(toRemove);
	}
	
	/**
	 * Get the names of the standard ACLs on the device.
	 * @return names of the standard ACLs
	 */
	public Set<String> getAclNames() {
	    return this.acls.keySet();
	}
	
	/**
	 * Get a specific standard ACL on the device.
	 * @param name name of the ACL
	 * @return the standard ACL with the given name; null if none exists
	 */
	public IpAccessList getAcl(String name) {
		if (this.acls == null) {
			return null;
		}
		return this.acls.get(name);
	}
	
	
	/**
	 * Get a specific route map on the device.
	 * @param name name of the route map
	 * @return the route map with the given name; null if none exists
	 */
	public RoutingPolicy getRoutingPolicy(String name) {
		if (this.routePolicies== null) {
			return null;
		}
		return this.routePolicies.get(name);
	}
	
	/**
	 * Get a specific prefix list on the device.
	 * @param name name of the prefix list
	 * @return the prefix list with the given name; null if none exists
	 */
	public RouteFilterList getRouteFilterList(String name) {
		if (this.routeFilterLists == null) {
			return null;
		}
		return this.routeFilterLists.get(name);
	}
	
	/**
	 * Get the device's configuration.
	 * @return device's configuration
	 */
	public Configuration getConfiguration() {
	    return this.configuration;
	}
	
	/**
	 * Determine where the device is external.
	 * @return true if the device is external, otherwise false
	 */
	public boolean isExternal() {
		return this.external;
	}
	
	/**
	 * Determine whether a subnet is connected to this device.
	 */
	public boolean isSubnetConnected(Prefix prefix) {
		for (Interface iface : this.interfaces) {
			if (iface.hasPrefix()) {
				Prefix ifacePrefix = iface.getPrefix();
				if (ifacePrefix.containsPrefix(prefix)
						|| ifacePrefix.equals(prefix)) {
					return true;
				}
			}
		}
		return false;
	}
	
	@Override
	public String toString() {
		return this.getName();
	}
	
    @Override
    public boolean equals(Object other) {
    	if (this == other) {
    		return true;
    	}
    	if (!(other instanceof Device)) {
    		return false;
    	}
		Device otherDevice = (Device)other;
		return this.name.equals(otherDevice.name);
	}
}
