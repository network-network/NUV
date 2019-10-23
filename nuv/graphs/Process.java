package edu.wisc.cs.arc.graphs;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.batfish.datamodel.BgpProcess;
import org.batfish.datamodel.BgpRoute;
import org.batfish.datamodel.ConnectedRoute;
import org.batfish.datamodel.GeneratedRoute;
import org.batfish.datamodel.ospf.OspfProcess;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.StaticRoute;
import org.batfish.datamodel.Vrf;
import org.batfish.datamodel.routing_policy.Environment;
import org.batfish.datamodel.routing_policy.Environment.Direction;
import org.batfish.datamodel.routing_policy.Result;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;
import org.batfish.datamodel.routing_policy.expr.Conjunction;
import org.batfish.datamodel.routing_policy.expr.Disjunction;
import org.batfish.datamodel.routing_policy.expr.ExplicitPrefixSet;
import org.batfish.datamodel.routing_policy.expr.IntExpr;
import org.batfish.datamodel.routing_policy.expr.LiteralInt;
import org.batfish.datamodel.routing_policy.expr.LiteralLong;
import org.batfish.datamodel.routing_policy.expr.LongExpr;
import org.batfish.datamodel.routing_policy.expr.MatchPrefixSet;
import org.batfish.datamodel.routing_policy.expr.MatchProtocol;
import org.batfish.datamodel.routing_policy.expr.PrefixSetExpr;
import org.batfish.datamodel.routing_policy.expr.WithEnvironmentExpr;
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.datamodel.routing_policy.statement.SetMetric;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.Statements;
import org.batfish.datamodel.routing_policy.statement.Statements.StaticStatement;

import edu.wisc.cs.arc.Settings;
import edu.wisc.cs.arc.graphs.Vertex.VertexType;

import org.batfish.datamodel.Ip;
import org.batfish.datamodel.OspfExternalRoute;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.RoutingProtocol;

/**
 * A routing process running on a router.
 * @author Aaron Gember-Jacobson
 */
public class Process implements Comparable<Process>, Serializable{
	private static final long serialVersionUID = -6455256694157735816L;

	/** Default administrative distances for various types of protocols */
	//https://en.wikipedia.org/wiki/Administrative_distance
	private static final int AD_STATICROUTE = 1;
	private static final int AD_EBGP = 20;
	private static final int AD_OSPF = 110;

	/** Name of the device on which this process is running */
	private Device device;

	/** BGP configuration snippet, if this is a BGP process */
	private BgpProcess bgpConfig;

	/** OSPF configuration snippet, if this is an OSPF process */
	private OspfProcess ospfConfig;

	/** Static route configuration snippet, if this is a static route process */
	private StaticRoute staticConfig;

	/** Interfaces over which routing messages are sent/received */
	private Collection<Interface> interfaces;

	/** ETG vertex used to enter the device via this process */
	private ProcessVertex inVertex;

	/** ETG vertex used to exit the device via this process */
	private ProcessVertex outVertex;

	/** Administrative distance for this process */
	private int administrativeDistance;

	/** Processes that are adjacent */
	private Set<Process> adjacentProcesses;

	/** VRF with which the process is associated */
	private Vrf vrf;

	public enum ProcessType {
		BGP,
		OSPF,
		STATIC
	}

	/** Type of process */
	private ProcessType type;

	/**
	 * Creates a routing process.
	 * @param device the device on which the process is running
	 * @param vrf the VRF with which the process is associated
	 */
	private Process(Device device, Vrf vrf) {
		this.device = device;
		this.adjacentProcesses = new HashSet<Process>();
		this.vrf = vrf;
	}

	/**
	 * Creates a BGP routing process.
	 * @param device the device on which the process is running
	 * @param vrf the VRF with which the process is associated
	 * @param bgpConfig configuration snippet for the process
	 * @param interfaces interfaces on which BGP messages are sent/received
	 */
	public Process(Device device, Vrf vrf, BgpProcess bgpConfig,
			Collection<Interface> interfaces) {
		this(device, vrf);
		this.type = ProcessType.BGP;
		this.bgpConfig = bgpConfig;
		this.interfaces = interfaces;
		this.administrativeDistance = AD_EBGP;
		this.createVertices();
	}

	/**
	 * Creates an OSPF routing process.
	 * @param device the device on which the process is running
	 * @param vrf the VRF with which the process is associated
	 * @param ospfConfig configuration snippet for the process
	 * @param interfaces interfaces on which OSPF messages are sent/received
	 */
	public Process(Device device, Vrf vrf, OspfProcess ospfConfig,
			Collection<Interface> interfaces) {
		this(device, vrf);
		this.type = ProcessType.OSPF;
		this.ospfConfig = ospfConfig;
		this.interfaces = interfaces;
		this.administrativeDistance = AD_OSPF;
		this.createVertices();
	}

	/**
	 * Creates a static routing process.
	 * @param device the device on which the static route is setup
	 * @param vrf the VRF with which the process is associated
	 * @param staticConfig configuration snippet for the static route
	 * @param interfaces interfaces out which traffic is routed
	 */
	public Process(Device device, Vrf vrf,  StaticRoute staticConfig,
			Collection<Interface> interfaces) {
		this(device, vrf);
		this.type = ProcessType.STATIC;
		this.staticConfig = staticConfig;
		this.interfaces = interfaces;
		this.administrativeDistance = AD_STATICROUTE;
		this.createVertices();
	}

	private void createVertices() {
		this.inVertex = new ProcessVertex(this, VertexType.IN);
		this.outVertex = new ProcessVertex(this, VertexType.OUT);
	}

	/**
     * Get an identifier for the process.
     * @return an identifier for the process
     */
    public String getId() {
        switch (this.getType()) {
        case BGP:
            /*return this.bgpConfig.getNeighbors().get(
                    this.bgpConfig.getNeighbors().firstKey()).getLocalAs().toString();*/
        case OSPF:
            return this.vrf.getName();
        case STATIC:
            return this.staticConfig.getNetwork().getStartIp().toString();
        }
        return this.vrf.getName();
    }

	/**
	 * Get the device on which this process is running.
	 * @return the device on which this process is running
	 */
	public Device getDevice() {
		return this.device;
	}

	/**
     * Get the VRF with which this process is associated.
     * @return the VRF with which this process is associated
     */
    public Vrf getVrf() {
        return this.vrf;
    }

	/**
	 * Determine if this process is a BGP routing process.
	 * @return true if this process is a BGP process, otherwise false
	 */
	public boolean isBgpProcess() {
		return (this.bgpConfig != null);
	}

	/**
	 * Determine if this process is an OSPF routing process.
	 * @return true if this process is an OSPF process, otherwise false
	 */
	public boolean isOspfProcess() {
		return (this.ospfConfig != null);
	}

	/**
	 * Determine if this process is a static routing process.
	 * @return true if this process is a static process, otherwise false
	 */
	public boolean isStaticProcess() {
		return (this.staticConfig != null);
	}

	/**
	 * Get the routing protocol used by the routing process.
	 * @return routing protocol used by the routing process
	 */
	public RoutingProtocol getProtocol() {
		if (this.isBgpProcess()) {
			return RoutingProtocol.BGP;
		}
		else if (this.isOspfProcess()) {
			return RoutingProtocol.OSPF;
		}
		else if (this.isStaticProcess()) {
			return RoutingProtocol.STATIC;
		}
		else {
			return null;
		}
	}

	/**
	 * Get the BGP configuration snippet for this process.
	 * @return BGP configuration snippet, or null if not a BGP process
	 */
	public BgpProcess getBgpConfig() {
		return this.bgpConfig;
	}

	/**
	 * Get the OSPF configuration snippet for this process.
	 * @return OSPF configuration snippet, or null if not an OSPF process
	 */
	public OspfProcess getOspfConfig() {
		return this.ospfConfig;
	}

	/**
	 * Get the static route configuration snippet for this process.
	 * @return Static route configuration snippet, or null if not a static
	 * 		   process
	 */
	public StaticRoute getStaticRouteConfig() {
		return this.staticConfig;
	}

	/**
	 * Get the interfaces over which routing messages are sent/received.
	 * @return interfaces over which routing messages are sent/received
	 */
	public Collection<Interface> getInterfaces() {
		return this.interfaces;
	}

	/**
	 * Get the interface over which routing messages are sent/received to a
	 * particular peer.
	 * @return interface over which routing messages are sent/received; null if
	 * 		no matching interface
	 */
	public Interface getInterfaceToReach(Ip peerIp) {
		for (Interface iface : this.interfaces) {
			if (iface.getPrefix().containsIp(peerIp)) {
				return iface;
			}
		}
		return null;
	}

	/**
	 * Add an adjacent routing process.
	 * @param adjacent the adjacent routing process
	 */
	public void addAdjacentProcess(Process adjacentProcess) {
		this.adjacentProcesses.add(adjacentProcess);
	}

	public Iterator<Process> getAdjacentProcessesIterator() {
		return this.adjacentProcesses.iterator();
	}

	/**
	 * Get the name of the routing process.
	 * @return name of the routing process
	 */
	public String getName() {
	    return this.device.getName() + "." + this.getType() + "."
	            + this.getId();
	}

	/**
	 * Set the entry and exit vertices for this routing process.
	 * @param inVertex ETG vertex used to enter the device via this process
	 * @param outVertex ETG vertex used to exit the device via this process
	 */
	public void setVertices(ProcessVertex inVertex, ProcessVertex outVertex) {
		this.inVertex = inVertex;
		this.outVertex = outVertex;
	}

	/**
	 * Get the ETG vertex used to enter the device via this process.
	 * @return ETG vertex used to enter the device via this process.
	 */
	public ProcessVertex getInVertex() {
		return this.inVertex;
	}

	/**
	 * Get the ETG vertex used to exit the device via this process.
	 * @return ETG vertex used to exit the device via this process.
	 */
	public ProcessVertex getOutVertex() {
		return this.outVertex;
	}

	/**
	 * Get the type of process.
	 * @return type of process
	 */
	public ProcessType getType() {
		return this.type;
	}

	/**
	 * Get the process's administrative distance.
	 * @return administrative distance
	 */
	public int getAdministrativeDistance() {
	    return this.administrativeDistance;
	}

	/**
	 * Get the name of this routing process.
	 * @return name of this routing process
	 */
	@Override
	public String toString() {
		return this.getName();
	}

	/**
	 * Compare two routing process based on administrative distance (AD).
	 * @param other routing process to compare to
	 * @return difference in AD between the two processes; negative if this
	 * 			process has a lower AD (i.e., is preferred); positive if this
	 * 			process has a higher AD (i.e., is not preferred)
	 */
	@Override
	public int compareTo(Process other) {
		if (this == other) {
			return 0;
		}
		if (this.administrativeDistance != other.administrativeDistance) {
			return (this.administrativeDistance - other.administrativeDistance);
		}
		//FIXME tiebreaking by pid
//		if (this.isOspfProcess() && other.isOspfProcess()) {
//			return (this.ospfConfig.getPid() - other.ospfConfig.getPid());
//		}
//		if (this.isBgpProcess() && other.isBgpProcess()) {
//			return (this.bgpConfig.getPid() - other.bgpConfig.getPid());
//		}
		//FIXME: What is the default?
		return 0;
	}

	public boolean advertises(PolicyGroup group, ProcessGraph rpg) {
        // Handle more complex types of advertising
	    switch (this.getType()) {
	    case BGP:
			return this.advertisesBgp(group, rpg);
	    case OSPF:
			return this.advertisesOspf(group, rpg);
		default:
		    return false;
	    }
	}

	private boolean advertisesBgp(PolicyGroup group, ProcessGraph rpg) {
	    // Check if the BGP process originates routes for the policy group
        if (this.originates(group)) {
            return true;
        }
        
		// Check if the BGP process redistributes connected prefixes
		if (this.redistributes(RoutingProtocol.CONNECTED) >= 0) {
			// Determine if the destination is part of the device's interfaces
			boolean groupConnected = false;
			for (Interface iface : device.getInterfaces()) {
				if (iface.hasPrefix() && group.within(iface.getPrefix())) {
					groupConnected = true;
				}
			}

			// FIXME: Check route maps
			return groupConnected;
			/*if (null == policy.getMap()) {
				return groupConnected;
			}
			else {
				RouteMap routeMap = this.routeMapsByDevice.get(
						localProcess.getDevice()).get(policy.getMap());
				for (Interface iface :
					this.interfacesByDevice.get(localProcess.getDevice())) {
					if (flow.getDestination().within(iface.getPrefix())
							&& (null == routeMap || !flow.isBlocked(routeMap,
							this.configsByDevice.get(localProcess.getDevice())))) {
						return true;
						break;
					}
				}
				return true;
			}*/
		}

		// Check if the BGP process redistributes OSPF
		if (this.redistributes(RoutingProtocol.OSPF) >= 0) {
		    // FIXME: Make sure only the proper OSPF process is redistributed
			//int ospfProccessNumber = (int)policy.getSpecialAttributes().get(
			//		BgpRedistributionPolicy.OSPF_PROCESS_NUMBER);
			for (Process process : this.device.getRoutingProcesses()) {
				if (process.isOspfProcess()) {
				//&& process.getOspfConfig().getPid() == ospfProccessNumber) {
					return process.advertisesOspf(group, rpg);
				}
			}
		}

		// FIXME: Are there other valid ways of deciding what is advertised?
		return false;
	}

	private boolean advertisesOspf(PolicyGroup group, ProcessGraph rpg) {
	    // Check if the OSPF process originates routes for the policy group
        if (this.originates(group)) {
            return true;
        }
        
	    // Check if OSPF redistributes routes
		if (this.redistributes(group)) {
		    return true;
		}

		// FIXME: Are there other valid ways of deciding what is advertised?
		return false;
	}
	
	/**
	 * Get the process's export policy.
	 * @return the process's export policy; null if none exists
	 */
	private RoutingPolicy getExportPolicy() {
	    String exportPolicyName = null;
        switch(this.getType()) {
        case BGP:
            // String constant from
            // org.batfish.representation.cisco.CiscoConfiguration.toBgpProcess
            exportPolicyName = "~BGP_COMMON_EXPORT_POLICY:" 
                    + this.getVrf().getName() + "~";
            break;
        case OSPF:
            exportPolicyName = this.ospfConfig.getExportPolicy();
            break;
        default:
            return null;
        }

        return this.getDevice().getRoutingPolicy(exportPolicyName);
	}
	
	/**
	 * Check if this process originates routes for a policy group.
	 * @param group policy group to determine origination for
	 * @return true if the process originates routes for the policy group,
	 *     otherwise false
	 */
	private boolean originates(PolicyGroup group) {
	    switch(this.getType()) {
        case BGP:
            // Check if prefix is within origination space
            if (bgpConfig.getOriginationSpace() != null) {
                for (PrefixRange prefixRange : 
                        bgpConfig.getOriginationSpace().getPrefixRanges()) {
                    if (group.within(prefixRange.getPrefix())) {
                        return true;
                    }
                }
            }
            
            // Check if a generated route is exported
            //System.out.print(this.toString() + " originates " + group + "? ");
            RoutingPolicy exportPolicy = this.getExportPolicy();
            if (null == exportPolicy) {
                return false;
            }
            ConnectedRoute route = new ConnectedRoute(group.getPrefix(), null);
            /*Environment env = new Environment(this.device.getConfiguration(),
                    this.getVrf().getName(), route, null,
                    new BgpRoute.Builder(), null, Direction.OUT);*/
            Environment env = new Environment(false, false, false, 
                    this.device.getConfiguration(), false, null, Direction.OUT,
                    false, new BgpRoute.Builder(), false, route, null, 
                    new BgpRoute.Builder(), null, null, false, false, 
                    this.getVrf(), false);
            // For loop is for debugging purposes only
            /*for (Statement stmt: exportPolicy.getStatements()) {
                System.out.println("  Smt: " + stmt);
                if (stmt instanceof If) {
                    If ifStmt = (If)stmt;
                    BooleanExpr guard = ifStmt.getGuard();
                    this.findMatchPrefix(guard, null, "    ",env);
                }
            }*/
            Result result = exportPolicy.call(env);
            //System.out.println(result.getBooleanValue());
            if (result.getBooleanValue()) {
                return true;
            }
            
            // Are there other ways to originate routes?
            return false;
        case OSPF:
            // Check if prefix matches interfaces on which OSPF operates
            for (Interface iface : this.interfaces) {
                if (group.within(iface.getPrefix())) {
                    return true;
                }
            }
            return false;
        case STATIC:
            return group.within(this.staticConfig.getNetwork());
        default:
            return false;
        }
	}
	
	/**
     * Find a match prefix expression for a particular prefix somewhere
     * in a tree of boolean expressions.
     * @param expr expression to start the search from
     * @param prefix the prefix for which to find a match expression
     * @param indent amount of identation for debugging purposes
     * @return true if a match prefix expression for the specified prefix
     *     was found, otherwise false
     */
    private boolean findMatchPrefix(BooleanExpr expr, 
            Prefix prefix, String indent, Environment env) {
        System.out.println(indent + "BoolExpr: " + expr);
        if (expr instanceof MatchPrefixSet) {
            MatchPrefixSet matchPrefixSet = (MatchPrefixSet)expr;
            Prefix dstPrefix = matchPrefixSet.getPrefix().evaluate(env);
            System.out.println(indent + "  Prefix: " + dstPrefix);
            PrefixSetExpr prefixSetExpr = matchPrefixSet.getPrefixSet();
            if (prefixSetExpr instanceof ExplicitPrefixSet) {
                ExplicitPrefixSet explicitPrefixSet = 
                    (ExplicitPrefixSet)prefixSetExpr;
                System.out.println(indent + "  PrefixSpace: " 
                    + explicitPrefixSet.getPrefixSpace());
                System.out.println(indent + "  Matches? " 
                        + explicitPrefixSet.matches(dstPrefix, env));
                System.out.println(indent + "  Contains? " 
                        + explicitPrefixSet.getPrefixSpace().containsPrefix(
                                dstPrefix));
            }
            
        } else if (expr instanceof WithEnvironmentExpr) {
            WithEnvironmentExpr withEnvExpr = (WithEnvironmentExpr)expr;
            System.out.println(indent + "  WithEnv: " + withEnvExpr);
            for (Statement preStmt : withEnvExpr.getPreStatements()) {
                System.out.println(indent + "    PreStmt: " + preStmt);
            }
            for (Statement postStmt : withEnvExpr.getPostStatements()) {
                System.out.println(indent + "    PostStmt: " + postStmt);
            }
            // TODO: Handle WithEnvironment expr
        } else if (expr instanceof Conjunction) {
            Conjunction conditions = (Conjunction)expr;
            for (BooleanExpr condition : conditions.getConjuncts()) {
                if (findMatchPrefix(condition, prefix, indent + "  ", env)) {
                    return true;
                }
            }
        } else if (expr instanceof Disjunction) {
            Disjunction conditions = (Disjunction)expr;
            for (BooleanExpr condition : conditions.getDisjuncts()) {
                if (findMatchPrefix(condition, prefix, indent + "  ", env)) {
                    return true;
                }
            }
        }
        return false;
    }

	/**
	 * Check if this process redistributes routes from another routing protocol
	 * running a particular protocol.
	 * @param protocol the protocol whose routes are to be redistributed
	 * @return the weight of the redistributed routes, or -1 if routes are not
	 *     redistributed
	 */
	public int redistributes(RoutingProtocol protocol) {
	    // Get export policy
	    RoutingPolicy exportPolicy = this.getExportPolicy();
	    if (null == exportPolicy) {
	        return -1;
	    }

	    // Look for a statement that corresponds to a redistribution policy
	    // Code is based on reverse-engineering of code in
	    // org.batfish.representation.cisco.CiscoConfiguration.toOspfProcess and
	    // org.batfish.representation.cisco.CiscoConfiguration.toBgpProcess
	    //System.out.print(this.toString() + " redistributes " + protocol +"? ");
	    int weight = 0;
        for (Statement stmt: exportPolicy.getStatements()) {
            //System.out.println("  Smt: " + stmt);
            if (stmt instanceof If) {
                If ifStmt = (If)stmt;
                BooleanExpr guard = ifStmt.getGuard();
                if (this.findMatchProtocol(guard, protocol, "    ")) {
                    //System.out.println("true (weight=" + weight + ")");
                    return weight;
                }
            } else if (stmt instanceof SetMetric) { 
                SetMetric setMetricStmt = (SetMetric)stmt;
                LongExpr metric = setMetricStmt.getMetric();
                if (metric instanceof LiteralLong) {
                    weight = (int)((LiteralLong)metric).getValue();
                }
            }
        }
        //System.out.println("false");
        return -1;
	}
	
	/**
	 * Find a match protocol expression for a particular protocol somewhere
	 * in a tree of boolean expressions.
	 * @param expr expression to start the search from
	 * @param protocol the protocol for which to find a match expression
	 * @param indent amount of identation for debugging purposes
	 * @return true if a match protocol expression for the specified protocol
	 *     was found, otherwise false
	 */
	private boolean findMatchProtocol(BooleanExpr expr, 
	        RoutingProtocol protocol, String indent) {
	    //System.out.println(indent + "BoolExpr: " + expr);
	    if (expr instanceof MatchProtocol) {
            MatchProtocol matchProto = (MatchProtocol)expr;
            if (matchProto.getProtocol().equals(protocol)) {
                return true;
            }
	    } else if (expr instanceof Conjunction) {
            Conjunction conditions = (Conjunction)expr;
            for (BooleanExpr condition : conditions.getConjuncts()) {
                if (findMatchProtocol(condition, protocol, indent + "  ")) {
                    return true;
                }
            }
        } else if (expr instanceof Disjunction) {
            Disjunction conditions = (Disjunction)expr;
            for (BooleanExpr condition : conditions.getDisjuncts()) {
                if (findMatchProtocol(condition, protocol, indent + "  ")) {
                    return true;
                }
            }
        }
	    return false;
	}

	public boolean redistributes(PolicyGroup group) {
        String exportPolicyName = null;

        switch(this.getType()) {
        case BGP:
            return false; // FIXME
        case OSPF:
            exportPolicyName = this.ospfConfig.getExportPolicy();
            break;
        default:
            return false;
        }

        RoutingPolicy exportPolicy =
                this.getDevice().getRoutingPolicy(exportPolicyName);

        // Try connected routes
        for (Interface iface : this.getDevice().getInterfaces()) {
            if (iface.hasPrefix() && group.within(iface.getPrefix())) {
                ConnectedRoute route = new ConnectedRoute(group.getPrefix(),
                        iface.getName());
                /*Environment env = new Environment(this.device.getConfiguration(),
                        this.getVrf().getName(), route, null,
                        new OspfExternalRoute.Builder(), null, Direction.OUT);*/
                Environment env = new Environment(false, false, false, 
                        this.device.getConfiguration(), false, null, 
                        Direction.OUT, false, null, false, route, null, 
                        new OspfExternalRoute.Builder(), null, null, false, 
                        false, this.getVrf(), false);
            // For loop is for debugging purposes only
                Result result = exportPolicy.call(env);
                if (result.getBooleanValue()) {
                    return true;
                }

            }
        }

        return false;
    }
}
