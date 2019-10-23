package edu.wisc.cs.arc.graphs;

import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.BgpPeerConfig;
import org.batfish.datamodel.BgpProcess;
import org.jgrapht.graph.DefaultWeightedEdge;

import com.sun.media.jfxmedia.logging.Logger;

import edu.wisc.cs.arc.GeneratorException;
import edu.wisc.cs.arc.repair.VirtualDirectedEdge;

/**
 * A directed edge for an extended topology graph.
 *  @author Aaron Gember-Jacobson (agember@cs.wisc.edu)
 */
public class DirectedEdge<V extends Vertex> extends DefaultWeightedEdge {

	private static final long serialVersionUID = 1662486723525719870L;

	public static final double INFINITE_WEIGHT = Double.MAX_VALUE;

	private Interface sourceInterface = null;

	private Interface destinationInterface = null;

	private boolean blocked = false;
	
	private boolean waypoint = false;

	public enum EdgeType {
		INTER_DEVICE,
		INTRA_DEVICE,
		INTER_INSTANCE,
		ENDPOINT
	}

	private EdgeType type;

	/**
	 * Get the name of the edge.
	 * @return name of the edge
	 */
	public String getName() {
		return this.getSource().getName()+"->"+this.getDestination().getName();
	}

	/**
	 * Get the source vertex for the edge.
	 * @return source of the edge
	 */
	@SuppressWarnings("unchecked")
	public V getSource() {
		return (V)super.getSource();
	}

	/**
	 * Get the destination vertex for the edge.
	 * @return destination of the edge
	 */
	@SuppressWarnings("unchecked")
	public V getDestination() {
		return (V)super.getTarget();
	}

	/**
	 * Get the weight assigned to the edge.
	 * @return weight of the edge
	 */
	public double getWeight() {
		return super.getWeight();
	}

	/**
	 * Set the type of the edge.
	 * @param type type of edge
	 */
	public void setType(EdgeType type) {
		this.type = type;
	}

	/**
	 * Get the type assigned to the edge.
	 * @return type of the edge
	 */
	public EdgeType getType() {
		return this.type;
	}

	/**
	 * Store the interfaces associated with the edge.
	 * @param sourceInterface source interface
	 * @param destinationInterface destination interface
	 */
	public void setInterfaces(Interface sourceInterface,
			Interface destinationInterface) {
		this.sourceInterface = sourceInterface;
		this.destinationInterface = destinationInterface;
	}

	/**
	 * Get the source interface associated with the edge.
	 * @return the source interface associated with the edge
	 */
	public Interface getSourceInterface() {
		return this.sourceInterface;
	}

	/**
	 * Get the destination interface associated with the edge.
	 * @return the destination interface associated with the edge
	 */
	public Interface getDestinationInterface() {
		return this.destinationInterface;
	}

	/**
	 * Indicate that an ACL prevents traffic from traversing the edge.
	 */
	public void markBlocked() {
		this.blocked = true;
	}

	/**
	 * Check if an ACL prevents traffic from traversing the edge.
	 * @return true if an ACL blocks traffic on the edge, otherwise false
	 */
	public boolean isBlocked() {
		return this.blocked;
	}

	/**
	 * Mark the edge as blocked if an ACL prevents the provided traffic class
	 * from traversing the edge.
	 * @param flow check if an ACL blocks this traffic class
	 * @param device the device on which the ACL is configured
	 */
	public void checkAndBlockOutgoing(Flow flow, Device device) {
		Interface iface = this.getSourceInterface();
        if (null == iface) {
            throw new GeneratorException(this.toString() + " in ETG for "
            		+ flow.toString() + " should have a source interface");
        }
		if (iface.getOutgoingFilter() != null) {
			IpAccessList acl = device.getAcl(
							iface.getOutgoingFilter());

			if ((acl != null && flow.isBlocked(acl))) {
				this.markBlocked();
			}
		}
	}

	/**
	 * Check if a router filter prevents the provided destination from being
	 * reached via the edge.
	 * @param destination check if an route filter blocks this destination
	 * @param device the device on which the router filter is configured
	 */
	public boolean isAdvertisementFiltered(PolicyGroup destination,
	        Device device) {
	    // Only inter-device edges can filter route advertisements between peers
	    if (this.getType() != EdgeType.INTER_DEVICE) {
	        return false;
	    }

		// Make sure the vertices are processes
		if (!((this.getSource() instanceof ProcessVertex)
				|| (this.getDestination() instanceof ProcessVertex))) {
			throw new GeneratorException(this.toString() + " in ETG for "
            		+ destination.toString() + " should have process vertices");
		}

		// Obtain the processes
		Process sourceProcess =
				((ProcessVertex)this.getSource()).getProcess();
		Process destinationProcess =
                ((ProcessVertex)this.getDestination()).getProcess();

		switch(sourceProcess.getType()) {
		case BGP:
		{
		    // Check if routes from peer are filtered
            // Locate peer that sends routes; this is the destination of the
            // edge because routes flow in the opposite direction of edges
		    BgpProcess srcBgpProcess = sourceProcess.getBgpConfig();
		    Prefix peerPrefix = new Prefix(
		            this.getDestinationInterface().getAddress(), 
                    Prefix.MAX_PREFIX_LENGTH);
		    BgpPeerConfig peer = srcBgpProcess.getActiveNeighbors().get(peerPrefix);
		    // FIXME: Does this cover both prefix lists and route maps?
		    if (peer.getImportPolicy() != null) {
    		    RoutingPolicy routingPolicy =
    		            device.getRoutingPolicy(peer.getImportPolicy());
    			if (routingPolicy != null && destination.isBlocked(routingPolicy,
    			        sourceProcess.getDevice())) {
    			    return true;
    			}
		    }

			// Check if routes to peer are filtered
			// Locate peer that receives routes; this is the source of the
            // edge because routes flow in the opposite direction of edges
            BgpProcess dstBgpProcess = destinationProcess.getBgpConfig();
            peerPrefix = new Prefix(this.getSourceInterface().getAddress(), 
                    Prefix.MAX_PREFIX_LENGTH);
            peer = dstBgpProcess.getActiveNeighbors().get(peerPrefix);
            
            // FIXME: Does this cover both prefix lists and route maps?
            if (peer.getExportPolicy() != null) {
                RoutingPolicy routingPolicy = 
                        device.getRoutingPolicy(peer.getExportPolicy());
                if (routingPolicy != null && destination.isBlocked(routingPolicy,
                        destinationProcess.getDevice())) {
                    return true;
                }
            }

            return false;
		}
		case OSPF:
		{
			// FIXME: Need to parse route filters for OSPF
			return false;
		}
		case STATIC:
		    // No route filtering
			return false;
		}

		return false;
	}

	/**
     * Check if a router filter prevents the provided destination from being
     * reached via a route-redistribution edge.
     * @param destination check if an route filter blocks this destination
     * @param device the device on which the router filter is configured
     */
    public boolean isRedistributionFiltered(PolicyGroup destination,
            Device device) {
        // Only consider intra-device edges
        if (this.getType() != EdgeType.INTRA_DEVICE) {
            return false;
        }

        // Make sure the vertices are processes
        if (!((this.getSource() instanceof ProcessVertex)
                || (this.getDestination() instanceof ProcessVertex))) {
            throw new GeneratorException(this.toString() + " in ETG for "
                    + destination.toString() + " should have process vertices");
        }

        // Obtain the processes
        Process sourceProcess =
                ((ProcessVertex)this.getSource()).getProcess();
        Process destinationProcess =
                ((ProcessVertex)this.getDestination()).getProcess();

        // Only consider different processes
        if (sourceProcess.equals(destinationProcess)) {
            return false;
        }

        // Get the routing process configuration for the source process; the
        // source redistributes routes from the destination because route
        // redistribution happens in the opposite direction of edges
        if (sourceProcess.redistributes(destinationProcess.getProtocol()) >= 0) {
            // FIXME: get route map
            /*// Check if routes are filtered
            RouteMap routeMap =
                    device.getRouteMap(redistributionPolicy.getMap()); //getRouteMap
            if (routeMap != null && destination.isDenied(routeMap, device)) {
                return true;
            }*/
        }

        return false;
    }

	/**
	 * Mark the edge as blocked if an ACL prevents the provided traffic class
	 * from traversing the edge.
	 * @param flow check if an ACL blocks this traffic class
	 * @param device the device on which the ACL is configured
	 */
	public void checkAndBlockIncoming(Flow flow, Device device) {
		Interface iface = this.getDestinationInterface();
        if (null == iface) {
        	throw new GeneratorException(this.toString() + " in ETG for "
            		+ flow.toString() + " should have a destination interface");
        }
		if (iface.getIncomingFilter() != null) {
		    IpAccessList acl = device.getAcl(iface.getIncomingFilter());
			if (acl != null && flow.isBlocked(acl)) {
				this.markBlocked();
			}
		}
	}
	
	/**
	 * Indicate that a waypoint is traversed when traversing this edge.
	 */
	public void markWaypoint() {
		this.waypoint = true;
	}

	/**
	 * Check if a waypoint is traversed when traversing this edge.
	 * @return true if a waypoint is traversed, otherwise false
	 */
	public boolean hasWaypoint() {
		return this.waypoint;
	}

	/**
	 * Get the name and weight of the edge.
	 */
	@Override
	public String toString() {
		String result = this.getName();
		if (this.getWeight() == INFINITE_WEIGHT) {
			result += " INF";
		}
		else {
			result += " " + this.getWeight();
		}
		if (this.isBlocked()) {
			result += " BLOCKED";
		}
		if (this.hasWaypoint()) {
			result += " WAYPOINT";
		}
		if (this.sourceInterface != null && this.destinationInterface != null) {
			result += " (" + this.sourceInterface.getName() + "->"
					+ this.destinationInterface.getName() + ")";
		}
		return result;
	}

	/**
	 * Determine if two edges have the same source and destination vertices,
	 * interfaces, and weight.
	 * @param other edge to compare against
	 */
	public boolean equals(Object other) {
		if (other instanceof DirectedEdge) {
			DirectedEdge<V> otherEdge = (DirectedEdge<V>)other;
			return (otherEdge.getSource().equals(this.getSource())
					&& otherEdge.getDestination().equals(this.getDestination())
					&& ((otherEdge.getSourceInterface() == null
						&& this.getSourceInterface() == null)
							|| otherEdge.getSourceInterface().equals(
									this.getSourceInterface()))
					&& ((otherEdge.getDestinationInterface() == null
							&& this.getDestinationInterface() == null)
							|| otherEdge.getDestinationInterface().equals(
									this.getDestinationInterface()))
					&& otherEdge.getWeight() == this.getWeight());
		}
		if (other instanceof VirtualDirectedEdge) {
			VirtualDirectedEdge<V> otherEdge = (VirtualDirectedEdge<V>)other;
			return (otherEdge.getSource().equals(this.getSource())
					&& otherEdge.getDestination().equals(
							this.getDestination()));
		}
		return false;
	}
}
