package edu.wisc.cs.arc.graphs;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.wisc.cs.arc.Settings;
import edu.wisc.cs.arc.graphs.DirectedEdge.EdgeType;
import edu.wisc.cs.arc.graphs.Vertex.VertexType;
import edu.wisc.cs.arc.virl.Link;

/**
 * An extended topology graph where vertices are interfaces and edges
 * represent routing process adjacencies and route redistribution.
 * @author Aaron Gember-Jacobson (agember@cs.wisc.edu)
 */
public class InterfaceGraph extends ExtendedTopologyGraph<InterfaceVertex> {
	private static final long serialVersionUID = -3964795149047137117L;
	
	/** A list of interfaces */
	private List<Interface> interfaces;
	
	/**
	 * Create an empty interface-based extended topology graph.
	 */
	protected InterfaceGraph(Settings settings) {
		super(settings);
		this.interfaces = new ArrayList<Interface>();
	}
	
	/**
	 * Create an interface-based extended topology graph from a process-based
	 * extended topology graph.
	 * @param rpg process-based extended topology graph
	 */
	public InterfaceGraph(ProcessGraph rpg) {
		this(rpg.settings);
		
		this.constructVertices(rpg);
		this.constructEdges(rpg);
	}
	
	/**
	 * Customize the graph for a specific flow.
	 * @param flow the flow for which the graph should be customized
	 * @return true if the graph was successfully customized, otherwise false
	 */
	@Override
	public boolean customize(Flow flow) {
		return this.customize(flow, new InterfaceVertex(null, VertexType.SOURCE), 
				new InterfaceVertex(null, VertexType.DESTINATION));
	}
	
	/**
	 * Create a copy of the interface-based extended topology graph.
	 * @return a new interface-based extended topology graph
	 */	
	@Override
	public Object clone() {
		InterfaceGraph igClone = (InterfaceGraph)super.clone();
		return igClone;
	}
	
	/**
	 * Add vertices to the interface-based ETG based on vertices in a 
	 * process-based ETG.
	 * @param rpg process-based ETG on which to base the vertices
	 */
	private void constructVertices(ProcessGraph rpg) {
		// Add vertices for every interface in every routing process
		for (Device device : rpg.getDevices()) {
			for (Process process: device.getRoutingProcesses()) {
				for (Interface iface : process.getInterfaces()) {
					this.interfaces.add(iface);
					this.addVertex(iface.getInVertex());
					this.addVertex(iface.getOutVertex());
				}
			}
		}
		
		// Add flow source/destination vertices
		if (rpg.getFlow() != null) {
			this.customize(rpg.getFlow());
		}
	}
	
	/**
	 * Add edges to the interface-based ETG based on edges in a process-based
	 * ETG.
	 * @param rpg process-based ETG on which to base the edges
	 */
	private void constructEdges(ProcessGraph rpg) {
		// Add edges for every edge in the process-based ETG
		Iterator<DirectedEdge<ProcessVertex>> edgesIterator = rpg.getEdgesIterator();
		while (edgesIterator.hasNext()) {
			// Get the source and destination vertices and associated processes
			// for the edge from the process-based ETG
			DirectedEdge<ProcessVertex> edge = edgesIterator.next();
			Process sourceProcess = edge.getSource().getProcess();
			Process destinationProcess = edge.getDestination().getProcess();
			
			// Special handling of flow source/destination vertices
			if (edge.getSource() == rpg.getFlowSourceVertex()) {
				// Add an edge between the flow source vertex and every 
				// interface for the destination process
				for (Interface destinationIface : 
						destinationProcess.getInterfaces()) {					
					this.addEdge(this.getFlowSourceVertex(),
							destinationIface.getOutVertex(),
							edge.getWeight(), EdgeType.INTER_DEVICE, null,
							destinationIface);
				}
				continue;
			}
			else if (edge.getDestination() == rpg.getFlowDestinationVertex()) {
				// Add an edge between every interface for the source process 
				// and the flow destination vertex 
				for (Interface sourceIface : sourceProcess.getInterfaces()) {					
					this.addEdge(sourceIface.getInVertex(), 
							this.getFlowDestinationVertex(), edge.getWeight(),
							EdgeType.INTER_DEVICE, sourceIface, null);
				}
				continue;
			}
			
			// Inter-device edges are limited to layer-3 adjacencies, so we
			// directly use the interfaces from the edge in the process-based
			// ETG
			if (EdgeType.INTER_DEVICE == edge.getType()) {
				this.addEdge(edge.getSourceInterface().getOutVertex(),
						edge.getDestinationInterface().getInVertex(),
						edge.getWeight(), EdgeType.INTER_DEVICE,
						edge.getSourceInterface(),
						edge.getDestinationInterface());
			}
			else if (EdgeType.INTRA_DEVICE == edge.getType()){
				// Add an edge between every pair of interfaces between the two
				// processes (the two processes may be the same process
				for (Interface sourceIface : sourceProcess.getInterfaces()) {
					for (Interface destinationIface : 
							destinationProcess.getInterfaces()) {
						// Do not connect an interface to itself
						if (sourceIface == destinationIface) {
							continue;
						}
						
						this.addEdge(sourceIface.getInVertex(),
								destinationIface.getOutVertex(),
								edge.getWeight(), EdgeType.INTRA_DEVICE,
								sourceIface, destinationIface);
					}
				}
			}
		}
	}
	
	/**
	 * Add edges for the source and destination of the flow.
	 */
	protected void constructEndpointEdges() {
		Flow flow = this.getFlow();
		
		// If source and/or destination is external, then connect to all
		// external devices
		if (!flow.getSource().isInternal() 
				|| !flow.getDestination().isInternal()) {
			for (Interface iface : this.interfaces) {
				if (!iface.getDevice().isExternal()) {
					continue;
				}
				
				if (!flow.getSource().isInternal()) {
					this.addEdge(this.getFlowSourceVertex(), 
							iface.getOutVertex(), 0, 
							EdgeType.INTER_DEVICE);
				}
				if (!flow.getDestination().isInternal()) {
					this.addEdge(iface.getInVertex(),
							this.getFlowDestinationVertex(), 0,
							EdgeType.INTER_DEVICE);
				}
			}
		}
		
		// Iterate over all interfaces
		for (Interface iface : this.interfaces) {
			InterfaceVertex sourceVertex = null;
			InterfaceVertex destinationVertex = null;
			
			// We only care about interfaces whose prefix falls within the 
			// source or destination policy groups
			Interface sourceIface = null;
			Interface destinationIface = null;
			if (flow.getSource().contains(iface.getAddress())) {
				sourceVertex = this.getFlowSourceVertex();
				destinationVertex = iface.getOutVertex();
				destinationIface = iface;
			}
			else if (flow.getDestination().contains(iface.getAddress())) {
				destinationVertex = this.getFlowDestinationVertex();
				sourceVertex = iface.getInVertex();
				sourceIface = iface;
			}
			else {
				continue;
			}
			
			// See if the destination is also connected to the device
			if (null == sourceIface) {
				boolean hasDestination = false;
				for (Interface deviceIface : 
						destinationIface.getDevice().getInterfaces()) {
					if (flow.getDestination().contains(
							deviceIface.getPrefix())) {
						hasDestination = true;
						break;
					}
				}
				
				if (hasDestination) {
					destinationVertex = this.getFlowDestinationVertex();
					// Add an edge
					if (!this.containsEdge(sourceVertex, destinationVertex)) {
						// TODO: Check if blocked by ACL
						this.addEdge(sourceVertex, destinationVertex, 0, 
								EdgeType.INTER_DEVICE);
					}
					continue;
				}
			}
												
			// Add an edge
			if (!this.containsEdge(sourceVertex, destinationVertex)) {
				DirectedEdge<InterfaceVertex> edge = this.addEdge(sourceVertex,
						destinationVertex, 0, EdgeType.INTER_DEVICE,
						sourceIface, destinationIface);
				if (null == sourceIface) {
					edge.checkAndBlockIncoming(flow, 
							destinationIface.getDevice());
				}
				else {
					edge.checkAndBlockOutgoing(flow, sourceIface.getDevice());
				}
			}
		}
	}
	
	protected void filterEdgesAndVertices() {		
		this.applyAcls();
		// FIXME: Account for route-maps
	}
	
	private void applyAcls() {
		for (Interface iface : this.interfaces) {
			for (DirectedEdge<InterfaceVertex> edge : 
					this.getOutgoingEdges(iface.getOutVertex())) {
				if (null == edge.getSourceInterface()) {
					continue;
				}
				edge.checkAndBlockOutgoing(this.getFlow(), iface.getDevice());
			}
			
			for (DirectedEdge<InterfaceVertex> edge : 
					this.getIncomingEdges(iface.getInVertex())) {
				if (null == edge.getDestinationInterface()) {
					continue;
				}
				edge.checkAndBlockIncoming(this.getFlow(), iface.getDevice());
			}
		}
	}
	

	@Override
	public String getGraphvizVertexColor(Vertex vertex) {
		if (vertex == this.getFlowSourceVertex()
				|| vertex == this.getFlowDestinationVertex()) {
			return "red";
		}
		else {
			return "white";
		}
	}


	@Override
	public void removeLink(Link link) {
		Iterator<DirectedEdge<InterfaceVertex>> iterator =
				this.getEdgesIterator();
		while (iterator.hasNext()) {
			DirectedEdge<InterfaceVertex> edge = iterator.next();
			InterfaceVertex srcVertex = edge.getSource();
			InterfaceVertex dstVertex = edge.getDestination();
			String srcDeviceName = 
					srcVertex.getInterface().getDevice().getName();
			String dstDeviceName = 
					dstVertex.getInterface().getDevice().getName();
			if ((srcDeviceName.equals(link.getSourceDeviceName())
					&& dstDeviceName.equals(link.getDestinationDeviceName()))
				|| (srcDeviceName.equals(link.getDestinationDeviceName())
					&& dstDeviceName.equals(link.getSourceDeviceName()))) {
				this.removeEdge(srcVertex, dstVertex);
			}
		}
	}
}
