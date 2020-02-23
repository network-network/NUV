package edu.wisc.cs.arc.configs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

//import org.batfish.datamodel.routing_policy.expr
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.common.BatfishException;
import org.batfish.datamodel.BgpActivePeerConfig;
import org.batfish.datamodel.BgpPeerConfig;
import org.batfish.datamodel.BgpRoute;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.RouteFilterLine;
import org.batfish.datamodel.RouteFilterList;
import org.batfish.datamodel.RoutingProtocol;
import org.batfish.datamodel.StaticRoute;
import org.batfish.datamodel.routing_policy.Environment;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.Environment.Direction;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;
import org.batfish.datamodel.routing_policy.expr.BooleanExprs;
import org.batfish.datamodel.routing_policy.expr.CallExpr;
import org.batfish.datamodel.routing_policy.expr.Conjunction;
import org.batfish.datamodel.routing_policy.expr.ConjunctionChain;
import org.batfish.datamodel.routing_policy.expr.DestinationNetwork;
import org.batfish.datamodel.routing_policy.expr.Disjunction;
import org.batfish.datamodel.routing_policy.expr.DisjunctionChain;
import org.batfish.datamodel.routing_policy.expr.ExplicitPrefixSet;
import org.batfish.datamodel.routing_policy.expr.MatchAsPath;
import org.batfish.datamodel.routing_policy.expr.MatchCommunitySet;
import org.batfish.datamodel.routing_policy.expr.MatchPrefix6Set;
import org.batfish.datamodel.routing_policy.expr.MatchPrefixSet;
import org.batfish.datamodel.routing_policy.expr.MatchProtocol;
import org.batfish.datamodel.routing_policy.expr.NamedPrefixSet;
import org.batfish.datamodel.routing_policy.expr.Not;
import org.batfish.datamodel.routing_policy.expr.PrefixExpr;
import org.batfish.datamodel.routing_policy.expr.PrefixSetExpr;
import org.batfish.datamodel.routing_policy.expr.WithEnvironmentExpr;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.Statements;
import org.batfish.datamodel.routing_policy.statement.Statements.StaticStatement;
import org.batfish.representation.cisco.BgpProcess;
import org.batfish.representation.cisco.BgpRedistributionPolicy;
import org.batfish.representation.cisco.ExtendedAccessList;
import org.batfish.representation.cisco.ExtendedAccessListLine;
import org.batfish.representation.cisco.IpBgpPeerGroup;
import org.batfish.representation.cisco.OspfProcess;
import org.batfish.representation.cisco.OspfRedistributionPolicy;
import org.batfish.representation.cisco.StandardAccessList;
import org.batfish.representation.cisco.StandardAccessListLine;
//import org.batfish.representation.cisco;
import org.batfish.symbolic.AstVisitor;
import org.batfish.symbolic.Protocol;
import org.batfish.symbolic.TransferParam;
import org.batfish.symbolic.TransferResult;
import org.batfish.symbolic.smt.SymbolicRoute;

import com.microsoft.z3.BoolExpr;

import edu.wisc.cs.arc.graphs.Device;
import edu.wisc.cs.arc.graphs.Interface;
import edu.wisc.cs.arc.graphs.Process;
//import edu.wisc.cs.arc.graphs.RouteMapClause;
import edu.wisc.cs.arc.repair.graph.ConfigModification;
import edu.wisc.cs.arc.repair.graph.ConfigModification.Action;
import edu.wisc.cs.arc.repair.graph.ConfigModification.Stanza;
import edu.wisc.cs.arc.repair.graph.ConfigModification.Substanza;



/**
 * Compares the configurations of two devices.
 * Compare more kinds of configuration blocks.
 * Add detailed comparation for configuration blocks.
 * Support the inference in NUV.
 * @author Yahui Li (li-yh15@mails.tsinghua.edu.cn)
 */


public class ConfigComparer {
    
    /** Devices to compare */
    private Device deviceA;
    private Device deviceB;
    
    /** Referenced ACLs */
    private List<String> refAcls;
    
    /**
     * Compare the configurations of two devices.
     * @param deviceA original device configuration
     * @param deviceB new device configuration
     */
    public ConfigComparer(Device deviceA, Device deviceB) {
        this.deviceA = deviceA;
        this.deviceB = deviceB;
    }
    
    /**
     * Get a textual representation of the differences between the devices'
     * configurations.
     * @return a textual representation of configuration differences
     */
    public String toString() {
        String result = "";
        List<ConfigModification> modifications = this.getDifferences();
        for (ConfigModification modification : modifications) {
            result += modification.toString() + "\n";
        }
        return result;
    }
    
	// change by lyh, add type
	/**
	 * Get a list of differences between the devices' configurations.
	 * 
	 * @return a list of configuration differences
	 */
	public Map<Integer, List<ConfigModification>> getDifferences2() {
		List<ConfigModification> modifications1 = new ArrayList<ConfigModification>();
		List<ConfigModification> modifications2 = new ArrayList<ConfigModification>();
		List<ConfigModification> modifications3 = new ArrayList<ConfigModification>();
		List<ConfigModification> modifications6 = new ArrayList<ConfigModification>();
		Map<Integer, List<ConfigModification>> alls = new LinkedHashMap<Integer, List<ConfigModification>>();

		this.refAcls = new ArrayList<String>();
		// int aclflag1=-1, allflag3=-1, rflag2=-1;
		if (!this.compareStandardAcls().isEmpty() || !this.compareExtendedAcls().isEmpty()) {
//			System.out.println("&&&&&&&&&&&&&&&&&&&  "+"!this.compareStandardAcls().isEmpty()");
			modifications1.addAll(this.compareStandardAcls());
//			for (ConfigModification m: this.compareStandardAcls()) {
//				System.out.println("show -------------------- "+m);
//			}
			modifications1.addAll(this.compareExtendedAcls());
		}

		//repaire by lyh for abs compare, don't compare interfaces;
//		if (!this.compareInterfaces().isEmpty() || !this.compareRoutingProcesses().isEmpty()) {
		if (!this.compareRoutingProcesses().isEmpty()) {
			// TODO: rewrite acl should not in this;
			modifications3.addAll(this.compareRoutingProcesses());
		}
		
		// TODO: Compare route maps
		if (!this.compareRoutemap().isEmpty()) {
			modifications2.addAll(this.compareRoutemap());
		}
		
		if (!this.compareStaticProcesses().isEmpty()) {
			modifications6.addAll(this.compareStaticProcesses());
		}
		alls.put(1, modifications1);
		alls.put(3, modifications3);
		alls.put(2, modifications2);
		alls.put(6, modifications6);
	
		return alls;
	}


	public List<ConfigModification> getDifferences() {
		List<ConfigModification> modifications = new ArrayList<ConfigModification>();
		this.refAcls = new ArrayList<String>();
		modifications.addAll(this.compareStandardAcls());
		modifications.addAll(this.compareExtendedAcls());
		modifications.addAll(this.compareInterfaces());
		modifications.addAll(this.compareRoutingProcesses());

		// TODO: Compare route maps
		return modifications;
	}

    /**
     * Compare the devices' interfaces.
     * @return a list of modifications
     */
    private List<ConfigModification> compareInterfaces() {
        List<ConfigModification> modifications = 
                new ArrayList<ConfigModification>();
        
        // Build map of interfaces for devices
        Map<String,Interface> ifacesA = new LinkedHashMap<String,Interface>();
        for (Interface ifaceA : deviceA.getInterfaces()) {
            ifacesA.put(ifaceA.getName(), ifaceA);
        }
        Map<String,Interface> ifacesB = new LinkedHashMap<String,Interface>();
        for (Interface ifaceB : deviceB.getInterfaces()) {
            ifacesB.put(ifaceB.getName(), ifaceB);
            Interface ifaceA = ifacesA.get(ifaceB.getName());
            // Determine if an interface was added
            if (null == ifaceA) {
                modifications.add(new ConfigModification(Action.ADD, 
                        Stanza.INTERFACE, ifaceB.getName()));
            } else {
                modifications.addAll(this.compareInterfaces(ifaceA, ifaceB));
            }
        }
        
        // Determine which interfaces were removed
        for (Interface ifaceA : deviceA.getInterfaces()) {
            if (!ifacesB.containsKey(ifaceA.getName())) {
                modifications.add(new ConfigModification(Action.REMOVE, 
                        Stanza.INTERFACE, ifaceA.getName()));
            }
        }
        
        return modifications;
    }
    
    /**
     * Compare an interface.
     * @param ifaceA interface on deviceA
     * @param ifaceB interface on deviceB
     * @return a list of modifications
     */
    private List<ConfigModification> compareInterfaces(Interface ifaceA,
            Interface ifaceB) {
        List<ConfigModification> modifications = 
                new ArrayList<ConfigModification>();
        
        // Compare prefix
        if (ifaceA.hasPrefix() && !ifaceB.hasPrefix()) {
            modifications.add(new ConfigModification(Action.REMOVE,
                    Stanza.INTERFACE, ifaceA.getName(), Substanza.PREFIX,
                    ifaceA.getPrefix().toString()));
        } else if (!ifaceA.hasPrefix() && ifaceB.hasPrefix()) {
            modifications.add(new ConfigModification(Action.ADD,
                    Stanza.INTERFACE, ifaceA.getName(), Substanza.PREFIX,
                    ifaceB.getPrefix().toString()));
        } else if (ifaceA.hasPrefix() && ifaceB.hasPrefix() 
                && !ifaceA.getPrefix().equals(ifaceB.getPrefix())) {
            modifications.add(new ConfigModification(Action.CHANGE,
                    Stanza.INTERFACE, ifaceA.getName(), Substanza.PREFIX,
                    ifaceA.getPrefix().toString() + " -> " 
                    + ifaceB.getPrefix().toString()));
        }
        
        // Compare active
        if (ifaceA.getActive() != ifaceB.getActive()) {
            modifications.add(new ConfigModification(Action.CHANGE,
                    Stanza.INTERFACE, ifaceA.getName(), Substanza.ACTIVE,
                    "" + ifaceA.getActive() + " -> " + ifaceB.getActive()));
        }
        
        // Compare OSPF cost
        if (ifaceA.getOspfCost() != null && ifaceB.getOspfCost() == null) {
            modifications.add(new ConfigModification(Action.REMOVE,
                    Stanza.INTERFACE, ifaceA.getName(), Substanza.OSPF_COST,
                    ifaceA.getOspfCost().toString()));
        } else if (ifaceA.getOspfCost() == null && ifaceB.getOspfCost() != null) {
            modifications.add(new ConfigModification(Action.ADD,
                    Stanza.INTERFACE, ifaceA.getName(), Substanza.OSPF_COST,
                    ifaceB.getOspfCost().toString()));
        } else if (ifaceA.getOspfCost() != null && ifaceB.getOspfCost() != null 
                && !ifaceA.getOspfCost().equals(ifaceB.getOspfCost())) {
            modifications.add(new ConfigModification(Action.CHANGE,
                    Stanza.INTERFACE, ifaceA.getName(), Substanza.OSPF_COST,
                    ifaceA.getOspfCost().toString() + "->" 
                    + ifaceB.getOspfCost().toString()));
        }
        // delete by lyh,bercause of acl TODO
        // Compare incoming filter
   /*      if (ifaceA.getIncomingFilter() != null 
                && ifaceB.getIncomingFilter() == null) {
            modifications.add(new ConfigModification(Action.REMOVE,
                    Stanza.INTERFACE, ifaceA.getName(), 
                    Substanza.INCOMING_FILTER, ifaceA.getIncomingFilter()));
        } else if (ifaceA.getIncomingFilter() == null 
                && ifaceB.getIncomingFilter() != null) {
            modifications.add(new ConfigModification(Action.ADD,
                    Stanza.INTERFACE, ifaceA.getName(), 
                    Substanza.INCOMING_FILTER, ifaceB.getIncomingFilter()));
        } else if (ifaceA.getIncomingFilter() != null 
                && ifaceB.getIncomingFilter() != null 
                && !ifaceA.getIncomingFilter().equals(
                        ifaceB.getIncomingFilter())) {
            modifications.add(new ConfigModification(Action.CHANGE,
                    Stanza.INTERFACE, ifaceA.getName(), 
                    Substanza.INCOMING_FILTER, ifaceA.getIncomingFilter()
                    + "->" + ifaceB.getIncomingFilter()));
        }
        
        // Compare outgoing filter
        if (ifaceA.getOutgoingFilter() != null 
                && ifaceB.getOutgoingFilter() == null) {
            modifications.add(new ConfigModification(Action.REMOVE,
                    Stanza.INTERFACE, ifaceA.getName(), 
                    Substanza.OUTGOING_FILTER, ifaceA.getOutgoingFilter()));
        } else if (ifaceA.getOutgoingFilter() == null 
                && ifaceB.getOutgoingFilter() != null) {
            modifications.add(new ConfigModification(Action.ADD,
                    Stanza.INTERFACE, ifaceA.getName(), 
                    Substanza.OUTGOING_FILTER, ifaceB.getOutgoingFilter()));
        } else if (ifaceA.getOutgoingFilter() != null 
                && ifaceB.getOutgoingFilter() != null 
                && !ifaceA.getOutgoingFilter().equals(
                        ifaceB.getOutgoingFilter())) {
            modifications.add(new ConfigModification(Action.CHANGE,
                    Stanza.INTERFACE, ifaceA.getName(), 
                    Substanza.OUTGOING_FILTER, ifaceA.getOutgoingFilter()
                    + "->" + ifaceB.getOutgoingFilter()));
        }
        
        // Store referenced filters
        if (ifaceA.getIncomingFilter() != null) {
        	this.refAcls.add(ifaceA.getIncomingFilter());
        }
        if (ifaceB.getIncomingFilter() != null) {
        	this.refAcls.add(ifaceB.getIncomingFilter());
        }
        if (ifaceA.getOutgoingFilter() != null) {
        	this.refAcls.add(ifaceA.getOutgoingFilter());
        }
        if (ifaceB.getOutgoingFilter() != null) {
        	this.refAcls.add(ifaceB.getOutgoingFilter());
        }
        */
        // TODO: Compare access VLAN
        
        // TODO: Compare allowed VLANs
        
        // TODO: Compare subInterfaces?
        
        // TODO: Compare channel group
        
        return modifications;
    }
    
    /**
     * Compare the devices' routing processes.// change by lyh, don't compare static route;
     * @return a list of modifications
     */
    private List<ConfigModification> compareRoutingProcesses() {
        List<ConfigModification> modifications = 
                new ArrayList<ConfigModification>();
        
        // Build map of processes for devices
        Map<String,Process> processesA = new LinkedHashMap<String,Process>();
        for (Process processA : deviceA.getRoutingProcesses()) {
            processesA.put(processA.getName(), processA);
        }
        Map<String,Process> processesB = new LinkedHashMap<String,Process>();
        for (Process processB : deviceB.getRoutingProcesses()) {
            processesB.put(processB.getName(), processB);
            Process processA = processesA.get(processB.getName());
            // Determine if a routing process was added
            if (null == processA) {
            	if (processB.getType() != edu.wisc.cs.arc.graphs.Process.ProcessType.STATIC) {
                modifications.add(new ConfigModification(Action.ADD, 
                        Stanza.ROUTER, processB.getName()));
            	}
            } else {
            	if (processA.getType() != edu.wisc.cs.arc.graphs.Process.ProcessType.STATIC) {
            		if (processB.getType() != edu.wisc.cs.arc.graphs.Process.ProcessType.STATIC) {
                modifications.addAll(
                        this.compareRoutingProcesses(processA, processB));
                }
                }
            }
        }
        
        // Determine which routing processes were removed
        for (Process processA : deviceA.getRoutingProcesses()) {
            if (!processesB.containsKey(processA.getName())) {
            	if (processA.getType() != edu.wisc.cs.arc.graphs.Process.ProcessType.STATIC) {
                modifications.add(new ConfigModification(Action.REMOVE, 
                        Stanza.ROUTER, processA.getName()));
                }
            }
        }
        
        return modifications;
    }
    
	private List<ConfigModification> compareStaticProcesses() {
		List<ConfigModification> modifications = new ArrayList<ConfigModification>();

		// Build map of processes for devices
		Map<String, Process> processesA = new LinkedHashMap<String, Process>();
		for (Process processA : deviceA.getRoutingProcesses()) {
			processesA.put(processA.getName(), processA);
		}
		Map<String, Process> processesB = new LinkedHashMap<String, Process>();
		for (Process processB : deviceB.getRoutingProcesses()) {
			processesB.put(processB.getName(), processB);
			Process processA = processesA.get(processB.getName());
			// Determine if a routing process was added
			if (null == processA) {
				if (processA.getType() == edu.wisc.cs.arc.graphs.Process.ProcessType.STATIC) {
					modifications.add(new ConfigModification(Action.ADD, Stanza.ROUTER, processB.getName()));
				} else {
					if (processA.getType() == edu.wisc.cs.arc.graphs.Process.ProcessType.STATIC) {
						if (processB.getType() == edu.wisc.cs.arc.graphs.Process.ProcessType.STATIC) {
							modifications.addAll(this.compareStaticProcesses1(processA, processB));
						}
					}
				}
			}
		}

		// Determine which routing processes were removed
		for (Process processA : deviceA.getRoutingProcesses()) {
			if (!processesB.containsKey(processA.getName())) {
				if (processA.getType() == edu.wisc.cs.arc.graphs.Process.ProcessType.STATIC) {
					modifications.add(new ConfigModification(Action.REMOVE, Stanza.ROUTER, processA.getName()));
				}
			}
		}

		return modifications;
	}
    
    
    private List<ConfigModification> compareStaticProcesses1(Process processA, Process processB) {
        List<ConfigModification> modifications = 
                new ArrayList<ConfigModification>();
        
        // Compare administrative distances
        if (processA.getAdministrativeDistance() 
                != processB.getAdministrativeDistance()) {
            modifications.add(new ConfigModification(Action.CHANGE,
                    Stanza.ROUTER, processA.getName(), 
                    Substanza.ADMINISTRATIVE_DISTANCE,
                    "" + processA.getAdministrativeDistance() + " -> " 
                    + processB.getAdministrativeDistance()));
        }
        
        // Compare interfaces
        Map<String,Interface> ifacesA = new LinkedHashMap<String,Interface>();
        for (Interface ifaceA : processA.getInterfaces()) {
            ifacesA.put(ifaceA.getName(), ifaceA);
        }
        Map<String,Interface> ifacesB = new LinkedHashMap<String,Interface>();
        for (Interface ifaceB : processB.getInterfaces()) {
            ifacesB.put(ifaceB.getName(), ifaceB);
            if (!ifacesA.containsKey(ifaceB.getName())) {
                modifications.add(new ConfigModification(Action.ADD, 
                        Stanza.ROUTER, processB.getName(),
                        Substanza.INTERFACE, ifaceB.getName()));
            }
        }
        for (Interface ifaceA : processA.getInterfaces()) {
            if (!ifacesB.containsKey(ifaceA.getName())) {
                modifications.add(new ConfigModification(Action.REMOVE, 
                        Stanza.ROUTER, processA.getName(),
                        Substanza.INTERFACE, ifaceA.getName()));
            }
        }
        // static route next hop instead of generic list of interfaces?
        
        // Router-type-specific comparisons
        switch(processA.getType()) {
        case STATIC:
        	 modifications.addAll(this.compareSpeStaticProcesses(processA, processB));
            break;
		default:
			break;
        }
        
        return modifications;
    }
    /**
     * Compare a routing processes.
     * @param processA routing process on deviceA
     * @param processB routing process on deviceB
     * @return a list of modifications
     */
    private List<ConfigModification> compareRoutingProcesses(Process processA,
            Process processB) {
        List<ConfigModification> modifications = 
                new ArrayList<ConfigModification>();
        
        // Compare administrative distances
        if (processA.getAdministrativeDistance() 
                != processB.getAdministrativeDistance()) {
            modifications.add(new ConfigModification(Action.CHANGE,
                    Stanza.ROUTER, processA.getName(), 
                    Substanza.ADMINISTRATIVE_DISTANCE,
                    "" + processA.getAdministrativeDistance() + " -> " 
                    + processB.getAdministrativeDistance()));
        }
        
        // Compare interfaces
        Map<String,Interface> ifacesA = new LinkedHashMap<String,Interface>();
        for (Interface ifaceA : processA.getInterfaces()) {
            ifacesA.put(ifaceA.getName(), ifaceA);
        }
        Map<String,Interface> ifacesB = new LinkedHashMap<String,Interface>();
        for (Interface ifaceB : processB.getInterfaces()) {
            ifacesB.put(ifaceB.getName(), ifaceB);
            if (!ifacesA.containsKey(ifaceB.getName())) {
                modifications.add(new ConfigModification(Action.ADD, 
                        Stanza.ROUTER, processB.getName(),
                        Substanza.INTERFACE, ifaceB.getName()));
            }
        }
        for (Interface ifaceA : processA.getInterfaces()) {
            if (!ifacesB.containsKey(ifaceA.getName())) {
                modifications.add(new ConfigModification(Action.REMOVE, 
                        Stanza.ROUTER, processA.getName(),
                        Substanza.INTERFACE, ifaceA.getName()));
            }
        }
        // FIXME: Look at BGP neighbors, OSPF networks and interfaces, and
        // static route next hop instead of generic list of interfaces?
        
        // Router-type-specific comparisons
        switch(processA.getType()) {
        case BGP:
            modifications.addAll(this.compareBgpProcesses(processA, processB));
            break;
        case OSPF:
            modifications.addAll(this.compareOspfProcesses(processA, processB));
            break;
        case STATIC:
            // TODO: Anything to compare?
        	 //modifications.addAll(this.compareStaticProcesses(processA, processB));
            break;
        }
        
        return modifications;
    }
    
    //add by lyh
    private List<ConfigModification> compareSpeStaticProcesses(Process processA,
            Process processB) {
        List<ConfigModification> modifications = 
                new ArrayList<ConfigModification>();
        StaticRoute staticConfigA= processA.getStaticRouteConfig();
        StaticRoute staticConfigB= processA.getStaticRouteConfig();
        staticConfigA.getNextHopInterface();
        if((staticConfigA.getNextHopIp()!=staticConfigB.getNextHopIp())||(!staticConfigA.getNextHopInterface().equals(staticConfigB.getNextHopInterface())))
        {
        	 modifications.add(new ConfigModification(Action.ADD, 
                     Stanza.ROUTER, processB.getName(),
                     Substanza.STATIC_ROUTE, staticConfigB.toString()));
        }
        
        return modifications;
    }
    
    
    /**
     * Compare BGP routing processes.
     * @param processA BGP process on deviceA
     * @param processB BGP process on deviceB
     * @return a list of modifications
     */
    private List<ConfigModification> compareBgpProcesses(Process processA,
            Process processB) {
        List<ConfigModification> modifications = 
                new ArrayList<ConfigModification>();
        
        // FIXME
        org.batfish.datamodel.BgpProcess bgpA = processA.getBgpConfig();
        org.batfish.datamodel.BgpProcess bgpB = processB.getBgpConfig();
        
        // Compare neighbors
        for (Prefix peerA : bgpA.getActiveNeighbors().keySet()) {
            if(!bgpB.getActiveNeighbors().containsKey(peerA)) {
                modifications.add(new ConfigModification(Action.REMOVE, 
                        Stanza.ROUTER, processA.getName(),
                        Substanza.BGP_NEIGHBOR, peerA.toString()));
            }
        }
        for (Prefix peerB: bgpB.getActiveNeighbors().keySet()) {
            if(!bgpA.getActiveNeighbors().containsKey(peerB)) {
                modifications.add(new ConfigModification(Action.ADD, 
                        Stanza.ROUTER, processB.getName(),
                        Substanza.BGP_NEIGHBOR, peerB.toString()));
            }
        }
       /*
        // Compare redistribution
        for (RoutingProtocol protocolA : 
                bgpA.getRedistributionPolicies().keySet()) {
            if (!bgpB.getRedistributionPolicies().containsKey(protocolA)) {
                modifications.add(new ConfigModification(Action.REMOVE, 
                        Stanza.ROUTER, processA.getName(),
                        Substanza.BGP_REDISTRIBUTION, protocolA.toString()));
            } else {
                BgpRedistributionPolicy policyA = 
                        bgpA.getRedistributionPolicies().get(protocolA);
                BgpRedistributionPolicy policyB = 
                        bgpB.getRedistributionPolicies().get(protocolA);
                Integer metricA = policyA.getMetric();
                Integer metricB = policyB.getMetric();
                if (((null == metricA || null == metricB) && metricA != metricB)
                		|| (null != metricA && null != metricB 
                			&& !metricA.equals(metricB))) {
                    modifications.add(new ConfigModification(Action.CHANGE, 
                            Stanza.ROUTER, processA.getName(),
                            Substanza.BGP_REDISTRIBUTION, 
                            protocolA.toString()));
                }
            }
        }
        for (RoutingProtocol protocolB : 
            bgpB.getRedistributionPolicies().keySet()) {
            if (!bgpA.getRedistributionPolicies().containsKey(protocolB)) {
                modifications.add(new ConfigModification(Action.ADD, 
                        Stanza.ROUTER, processB.getName(),
                        Substanza.BGP_REDISTRIBUTION, protocolB.toString()));
            }
        } 
        */
        return modifications;
    }
    
    /**
     * Compare OSPF routing processes.
     * @param processA OSPF process on deviceA
     * @param processB OSPF process on deviceB
     * @return a list of modifications
     */
    
    
	
    private List<ConfigModification> compareOspfProcesses(Process processA,
            Process processB) {
        List<ConfigModification> modifications = 
                new ArrayList<ConfigModification>();
        
        // FIXME
        /*  org.batfish.datamodel.ospf.OspfProcess ospfA = processA.getOspfConfig();
        OspfProcess ospfB = processB.getOspfConfig();
       
        // Compare redistribution
        for (RoutingProtocol protocolA : 
                ospfA.getRedistributionPolicies().keySet()) {
            if (!ospfB.getRedistributionPolicies().containsKey(protocolA)) {
                modifications.add(new ConfigModification(Action.REMOVE, 
                        Stanza.ROUTER, processA.getName(),
                        Substanza.OSPF_REDISTRIBUTION, protocolA.toString()));
            } else {
                OspfRedistributionPolicy policyA = 
                        ospfA.getRedistributionPolicies().get(protocolA);
                OspfRedistributionPolicy policyB = 
                        ospfB.getRedistributionPolicies().get(protocolA);
                if (!policyA.getMetric().equals(policyB.getMetric())) {
                    modifications.add(new ConfigModification(Action.CHANGE, 
                            Stanza.ROUTER, processA.getName(),
                            Substanza.OSPF_REDISTRIBUTION, 
                            protocolA.toString()));
                }
            }
        }
        for (RoutingProtocol protocolB : 
            ospfB.getRedistributionPolicies().keySet()) {
            if (!ospfA.getRedistributionPolicies().containsKey(protocolB)) {
                modifications.add(new ConfigModification(Action.ADD, 
                        Stanza.ROUTER, processB.getName(),
                        Substanza.OSPF_REDISTRIBUTION, protocolB.toString()));
            }
        }*/
        
        return modifications;
    }
    
    public List<ConfigModification> compareRoutemap() {
		List<ConfigModification> modifications = new ArrayList<ConfigModification>();
		
//		  Map<String,Process> processesA = new LinkedHashMap<String,Process>();
//	        for (Process processA : deviceA.getRoutingProcesses()) {
//	            processesA.put(processA.getName(), processA);
//	        }
//	        Map<String,Process> processesB = new LinkedHashMap<String,Process>();
//	        for (Process processB : deviceB.getRoutingProcesses()) {
//	            processesB.put(processB.getName(), processB);
//	            Process processA = processesA.get(processB.getName());
//	            if (null != processA && (processA.getType()==edu.wisc.cs.arc.graphs.Process.ProcessType.BGP)) {
//	            	org.batfish.datamodel.BgpProcess bgpA=processA.getBgpConfig();
//	            	 Collection<BgpActivePeerConfig> peers = bgpA.getActiveNeighbors().values();
//	            	 for(BgpActivePeerConfig peer: peers) {
//	            		 if (peer.getImportPolicy() != null) {
//	             		    RoutingPolicy routingPolicy =
//	             		            deviceA.getRoutingPolicy(peer.getImportPolicy());
//	             		   if (routingPolicy != null) {
//	             			  routingPolicy.getStatements().iterator();
//	             			   ArrayList<Integer> sequenceNumbers = 
//	             		    			new ArrayList<Integer>(routeMap.getClauses().keySet());
//	             		    	Collections.sort(sequenceNumbers);
//	             		    	for (Integer sequenceNumber : sequenceNumbers) {
//	             		    		RouteMapClause clause = routeMap.getClauses().get(sequenceNumber);
//	             		    		boolean denied = (clause.getAction() == LineAction.REJECT);
//	             		    		
//	             		    		// "if no matching statements, all prefixes are matched implicitly."
//	             		    		// [https://learningnetwork.cisco.com/message/254239#254239]
//	             		    		if (0 == clause.getMatchList().size()) {
//	             		    			return denied;
//	             		    		}
//	             		   }
//	             		    }
//	            	 }
//	            	logger.debug("BGP " + bgpA.toString() + " IP peer groups:");
//	            	
//					for (IpBgpPeerGroup peerGroup : bgpA.getActiveNeighbors().values()) {
//		                logger.debug("\t" + peerGroup.getName() 
//		                    + " RemoteAS=" + peerGroup.getRemoteAS()
//		                    + " InboundRouteMap=" + peerGroup.getInboundRouteMap()
//		                    + " Template=" + peerGroup.getGroupName());
//		            }
//	            }
//	        }
//        org.batfish.datamodel.BgpProcess bgpA = processA.getBgpConfig();
//        org.batfish.datamodel.BgpProcess bgpB = processB.getBgpConfig();
//        
//        // Compare neighbors
//        for (Prefix peerA : bgpA.getActiveNeighbors().keySet()) {
//            if(!bgpB.getActiveNeighbors().containsKey(peerA)) {
//                modifications.add(new ConfigModification(Action.REMOVE, 
//                        Stanza.ROUTER, processA.getName(),
//                        Substanza.BGP_NEIGHBOR, peerA.toString()));
//            }
//        }
//        
		for(String routeNameA :deviceA.getRoutePoliNames()) {
			 RoutingPolicy RpA=deviceA.getRoutingPolicy(routeNameA);
			 RoutingPolicy RpB=deviceB.getRoutingPolicy(routeNameA);
			 if(null==RpB) {
				 if(!routeNameA.toString().contains("default"))
				 modifications.add(new ConfigModification(Action.REMOVE, 
	                        Stanza.ROUTER, routeNameA));
			 }else {
//				 System.out.println("lyh------++++++++ "+routeNameA);
				 modifications.addAll(this.compareRoutePols(routeNameA, RpA, 
	            			RpB));
			 }
		}
		for(String routeNameB :deviceB.getRoutePoliNames()) {
			 RoutingPolicy RpB=deviceB.getRoutingPolicy(routeNameB);
			 RoutingPolicy RpA=deviceA.getRoutingPolicy(routeNameB);
			 if(null==RpA) {
				 //reparied by lyh, for abs compare, routemap
				 if(!routeNameB.toString().contains("default"))
				 modifications.add(new ConfigModification(Action.ADD, 
	                        Stanza.ROUTER, routeNameB));
			 } 
		}
		return modifications;
	}
    

    /**
     * Compare the devices' standard ACLs.
     * @return a list of modifications
     */
    
    private List<ConfigModification> compareStandardAcls() {
        List<ConfigModification> modifications = 
                new ArrayList<ConfigModification>();
       
        // FIXME
        for (String aclNameA : deviceA.getAclNames()) {
//        	System.out.println("(((((((((((((((((((((((((((( "+deviceA+"  "+deviceB+"  "+aclNameA);
            IpAccessList aclA = deviceA.getAcl(aclNameA);
            IpAccessList aclB = deviceB.getAcl(aclNameA);
            // Determine if an ACL was removed
            if (null == aclB) {
                modifications.add(new ConfigModification(Action.REMOVE, 
                        Stanza.STANDARD_ACL, aclNameA));
            } else if (this.refAcls.contains(aclNameA)) {
            	modifications.addAll(this.compareStandardAcls(aclNameA, aclA, 
            			aclB));
            }else {
//            	System.out.println("##########################  "+deviceA+"  "+aclNameA);
            	modifications.addAll(this.compareStandardAcls(aclNameA, aclA, 
            			aclB));
            }
        }
        
        // Determine which ACLs were added
        for (String aclNameB : deviceB.getAclNames()) {
            if (null == deviceA.getAcl(aclNameB)) {
                modifications.add(new ConfigModification(Action.ADD, 
                        Stanza.STANDARD_ACL, aclNameB));
            }
        }
        
        return modifications;
    }
    
    
	// add by lyh
	private boolean findMatchPrefixLyh(BooleanExpr expr, String indent, Configuration c) {// Prefix prefix,
		if (expr instanceof MatchPrefixSet) {
			//IP_PREFIX_LIST, IP_ACCESS_LIST:
			MatchPrefixSet matchPrefixSet = (MatchPrefixSet) expr;
			PrefixSetExpr prefixSetExpr = matchPrefixSet.getPrefixSet();
			if (prefixSetExpr instanceof ExplicitPrefixSet) {
				ExplicitPrefixSet explicitPrefixSet = (ExplicitPrefixSet) prefixSetExpr;
				// System.out.println(indent + " PrefixSpace: " +
				// explicitPrefixSet.getPrefixSpace());
				Set<PrefixRange> ranges = explicitPrefixSet.getPrefixSpace().getPrefixRanges();
				for (PrefixRange r : ranges) {
					System.out.println("xxxxxxxxxxxxxxxxxxx----- " + r.getPrefix());
				}
			} else {
				// System.out.println(indent + " aaa onePrefixSpace: " +
				// matchPrefixSet.getPrefix() + " aaa PrefixSpace: "
				// + prefixSetExpr);
				if (matchPrefixSet.getPrefix() instanceof DestinationNetwork) {
					DestinationNetwork d = (DestinationNetwork) matchPrefixSet.getPrefix();
					// System.out.println("why-----"+d);
				}
				if (matchPrefixSet.getPrefixSet() instanceof NamedPrefixSet) {
					NamedPrefixSet st = (NamedPrefixSet) matchPrefixSet.getPrefixSet();
					String listName = st.getName();
					RouteFilterList routeFilterList = c.getRouteFilterLists().get(listName);
					List<RouteFilterLine> _lines = routeFilterList.getLines();
					for (RouteFilterLine l : _lines) {
						System.out.println("hehhehhe----- " + l);
					}

				}

			}
		} else if (expr instanceof WithEnvironmentExpr) {
			WithEnvironmentExpr withEnvExpr = (WithEnvironmentExpr) expr;
			System.out.println(indent + "  WithEnv: " + withEnvExpr);
			for (Statement preStmt : withEnvExpr.getPreStatements()) {
				System.out.println(indent + "    PreStmt: " + preStmt);
			}
			for (Statement postStmt : withEnvExpr.getPostStatements()) {
				System.out.println(indent + "    PostStmt: " + postStmt);
			}
		} else if (expr instanceof Conjunction) {
			Conjunction conditions = (Conjunction) expr;
			for (BooleanExpr condition : conditions.getConjuncts()) {
				System.out.println(indent + "  Conjunction condition:  " + condition);
			}
		} else if (expr instanceof Disjunction) {
			Disjunction conditions = (Disjunction) expr;
			for (BooleanExpr condition : conditions.getDisjuncts()) {
				System.out.println(indent + "  Disjunction condition:  " + condition);
			}
		}
		return false;
	}

	// add by lyh
	private List<ConfigModification> compareRoutePols2beifen(String rpn, RoutingPolicy rpa, RoutingPolicy rpb) {
		List<ConfigModification> modifications = new ArrayList<ConfigModification>();
		// don't compare names

		List<Statement> statements;
		statements = rpa.getStatements();
		System.out.println("dededede size = " + statements.size());
		for (Statement stmt : statements) {
			if (stmt instanceof If) {
				If ss = (If) stmt;
				System.out.println("dededede " + rpn + "   -++++++++-    " + ss.getComment() + "   -++++++++-    "
						+ ss.getFalseStatements());
				// +" -++++++++- "+ss.getTrueStatements()
				this.findMatchPrefixLyh(ss.getGuard(), " inner: ++++++   ", rpa.getOwner());
			    Boolean flags= true;
			    //TODO: recursive lookup;
				while (flags) {
					if (ss.getFalseStatements() != null) {
						List<Statement> ls = ss.getFalseStatements();
						if (this.ifanyif(ls)) {
							for (Statement sls : ls) {
								if (sls instanceof If) {
									If slsi = (If) sls;
									this.findMatchPrefixLyh(slsi.getGuard(), " inner2: ++++++   ", rpa.getOwner());
									ss = slsi;
								}
							}
						} else {
							flags = false;
						}
					} else {
						flags = false;
					}
				}
			}

		}
		return modifications;
	}

	Boolean ifanyif(List<Statement> all) {
		Boolean flag =false;
		for (Statement sls : all) {
			if (sls instanceof If) {
				flag =true;
				return flag;
				}
		}
		return flag;
		
	}
    
	private List<ConfigModification> compareRoutePols(String rpn, RoutingPolicy rpa, RoutingPolicy rpb) {
		List<ConfigModification> modifications = new ArrayList<ConfigModification>();
		// don't compare names

		// only compare to comments, don't compare matchs'lists, decisions;
		// TODO:
		List<Statement> statements = rpa.getStatements();
		List<Statement> statementsb = rpb.getStatements();
		for (Statement stmt : statements) {
			if (stmt instanceof If) {
				Boolean intmp0 = false;
				for (Statement stmtb : statementsb) {
					if (stmt.getComment() != null && stmtb.getComment() != null) {
						if (stmt.getComment().equals(stmtb.getComment())) {
							intmp0 = true;
							// part one is equal;
							If ss = (If) stmt;
							List<Statement> lsa = ss.getFalseStatements();
//							System.out.println("kkkkkkkkkkk-1   " + stmt.getComment() + "flasestatements " + lsa
//									 );
							If ssb = (If) stmtb;
							List<Statement> lsb = ssb.getFalseStatements();
//							System.out.println("kkkkkkkkkkk-1   " + stmt.getComment() + "flasestatements " + lsb
//									 );
							for (Statement sls : lsa) {
//								System.out.println("yyyyyyyyyyyyyyyyyyy   " + sls.getComment());
								if (sls.getComment() == null) {
									for (Statement slsb : lsb) {
										if (slsb.getComment() != null) {
//											System.out.println("null-kkkkkkkkkkk   " + slsb.getComment());
											modifications.add(new ConfigModification(Action.CHANGE, Stanza.ROUTER, rpn,
													Substanza.ROUTE_MAP, rpa.toString()));
											break;
										}
									}
								} else {
									Boolean intmp = false;
									for (Statement slsb : lsb) {
										if (sls.getComment() != null && slsb.getComment() != null) {
//											System.out.println("zzzzzzzzzzzzzzzzzz   " + sls.getComment()
//													+ " zzzzzzzzzzzzzzzzzz   " + slsb.getComment());
											if (sls.getComment().equals(slsb.getComment())) {
//												System.out.println("kkkkkkkkkkk0   " + sls.getComment());
												intmp = true;
												break;
											}
										}
									}

									if (intmp0 = true && intmp == false) {
//										System.out.println("kkkkkkkkkkk2   " + sls.toString());
										modifications.add(new ConfigModification(Action.CHANGE, Stanza.ROUTER, rpn,
												Substanza.ROUTE_MAP, rpa.toString()));
									}
								}
							}
						}
					}
				}
				if (intmp0 = false) {
					modifications.add(new ConfigModification(Action.CHANGE, Stanza.ROUTER, rpn, Substanza.ROUTE_MAP,
							rpa.toString()));
					System.out.println("kkkkkkkkkkk1 " + stmt.getComment());
				}
			}
		}
		return modifications;
	}

//	if (!(stmtb instanceof If)) {
//	modifications.add(new ConfigModification(Action.CHANGE, Stanza.ROUTER, rpn, Substanza.ROUTE_MAP,
//			rpa.toString()));
//} else {
	// both are if;
//	if (stmt.getComment() != null && stmtb.getComment() != null) {
//		if (!stmt.getComment().equals(stmtb.getComment())) {
//			// System.out.println("kkkkkkkkkkk1 "+stmt.getComment()+" ==
//			// "+stmtb.getComment());
//			modifications.add(new ConfigModification(Action.CHANGE, Stanza.ROUTER, rpn,
//					Substanza.ROUTE_MAP, rpa.toString()));
//		} else {
			// in all, comments are the same;
	
	
    /**
     * Compare standard ACLs.
     * @param aclA standard ACL on deviceA
     * @param aclB standard ACL on deviceB
     * @return a list of modifications
     */
    
    private List<ConfigModification> compareStandardAcls(String aclName,
    		IpAccessList aclA, IpAccessList aclB) {
        List<ConfigModification> modifications = 
                new ArrayList<ConfigModification>();
     
        // FIXME 
        //20190306 changed by lyh
//        System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% "+aclName);
        Iterator<IpAccessListLine> iteratorA = aclA.getLines().iterator();
        Iterator<IpAccessListLine> iteratorB = aclB.getLines().iterator();
        while(iteratorA.hasNext() && iteratorB.hasNext()) {
            IpAccessListLine lineA = iteratorA.next();
            IpAccessListLine lineB = iteratorB.next();
            if (lineA.getAction() != lineB.getAction()
                    || !lineA.getMatchCondition().equals(lineB.getMatchCondition())) {
//            	System.out.println("change1@@@@@@@@@@@@@@@@@@@@@@@@  "+aclName);
                modifications.add(new ConfigModification(Action.CHANGE, 
                        Stanza.STANDARD_ACL, aclName, Substanza.LINE,
                        lineA.getAction() + " " + lineA.getMatchCondition() + " -> "
                        + lineB.getAction() + " "+ lineB.getMatchCondition()));
            }
        }
        
        if (iteratorA.hasNext() != iteratorB.hasNext()) {
//        	System.out.println("change2@@@@@@@@@@@@@@@@@@@@@@@@  "+aclName);
            modifications.add(new ConfigModification(Action.CHANGE, 
                    Stanza.STANDARD_ACL, aclName));
        }
        
        return modifications;
    }
    
    /**
     * Compare the devices' extended ACLs.
     * @return a list of modifications
     */
    private List<ConfigModification> compareExtendedAcls() {
        List<ConfigModification> modifications = 
                new ArrayList<ConfigModification>();
        
        // FIXME
        //add by lyh 20190306
       /* for (String aclNameA : deviceA.getExtendedAclNames()) {
            ExtendedAccessList aclA = deviceA.getExtendedAcl(aclNameA);
            ExtendedAccessList aclB = deviceB.getExtendedAcl(aclNameA);
            // Determine if an ACL was removed
            if (null == aclB) {
                modifications.add(new ConfigModification(Action.REMOVE, 
                        Stanza.EXTENDED_ACL, aclNameA));
            } else if (this.refAcls.contains(aclNameA)) {
                modifications.addAll(this.compareExtendedAcls(aclNameA, aclA, 
                        aclB));
            }
        }
        
        // Determine which ACLs were added
        for (String aclNameB : deviceB.getExtendedAclNames()) {
            if (null == deviceA.getExtendedAcl(aclNameB)) {
                modifications.add(new ConfigModification(Action.ADD, 
                        Stanza.EXTENDED_ACL, aclNameB));
            }
        }*/
        
        return modifications;
    }
    
    /**
     * Compare extended ACLs.
     * @param aclA extended ACL on deviceA
     * @param aclB extended ACL on deviceB
     * @return a list of modifications
     */


    private List<ConfigModification> compareExtendedAcls(String aclName,
            ExtendedAccessList aclA, ExtendedAccessList aclB) {
        List<ConfigModification> modifications = 
                new ArrayList<ConfigModification>();
       
       // FIXME 
      /*  List<String> linesA = new ArrayList<String>();
        for (ExtendedAccessListLine line : aclA.getLines()) {
        	linesA.add(line.getAction() + " " + line.getProtocol() + " " 
        			+ line.getSourceIpWildcard() + " "
        			+ line.getDestinationIpWildcard());
        	// FIXME: Add ports
        }
        
        List<String> linesB = new ArrayList<String>();
        for (ExtendedAccessListLine line : aclB.getLines()) {
        	linesB.add(line.getAction() + " " + line.getProtocol() + " "
        			+ line.getSourceIpWildcard() + " "
                    + line.getDestinationIpWildcard());
        	// FIXME: Add ports
        }
      
        int offsetA = 0;
        int offsetB = 0;
        for (int i = 0; i + offsetA < linesA.size() 
        		&& i + offsetB < linesB.size(); i++) {
            String lineA = linesA.get(i + offsetA);
            String lineB = linesB.get(i + offsetB);
            if (!lineA.equals(lineB)) {
            	List<String> restLinesA = linesA.subList(i + offsetA, 
            			linesA.size());
            	List<String> restLinesB = linesB.subList(i + offsetB, 
            			linesB.size());
            	int aInB = restLinesB.indexOf(lineA);
            	int bInA = restLinesA.indexOf(lineB);
            	if (aInB > 0) {
            		offsetB += aInB;
            		modifications.add(new ConfigModification(Action.CHANGE, 
	                        Stanza.EXTENDED_ACL, aclName,Substanza.LINE,
	                        "(null) -> " + lineB));
            	} else if (bInA > 0) {
            		offsetA += bInA;
            		modifications.add(new ConfigModification(Action.CHANGE, 
	                        Stanza.EXTENDED_ACL, aclName,Substanza.LINE,
	                        lineA + " -> (null)"));
            	}
            	else {
	                modifications.add(new ConfigModification(Action.CHANGE, 
	                        Stanza.EXTENDED_ACL, aclName,Substanza.LINE,
	                        lineA + " -> " + lineB));
            	}
            }
        } 
        
        /*if (linesA.size() != linesB.size()) {
            modifications.add(new ConfigModification(Action.CHANGE, 
                    Stanza.EXTENDED_ACL, aclName));
        }*/
        
        // FIXME: Handle removals/additions at the end
        
        return modifications;
    }
}
