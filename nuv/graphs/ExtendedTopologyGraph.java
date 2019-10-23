package edu.wisc.cs.arc.graphs;

import edu.wisc.cs.arc.GeneratorException;
import edu.wisc.cs.arc.Logger;
import edu.wisc.cs.arc.Settings;
import edu.wisc.cs.arc.graphs.DirectedEdge.EdgeType;
import edu.wisc.cs.arc.graphs.Vertex.VertexType;
import edu.wisc.cs.arc.modifiers.CanonicalETGConverter;
import edu.wisc.cs.arc.modifiers.ModifierException;
import edu.wisc.cs.arc.repair.RepairException;
import edu.wisc.cs.arc.repair.VirtualDirectedEdge;
import edu.wisc.cs.arc.repair.graph.GraphModification;
import edu.wisc.cs.arc.virl.Link;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * An extended topology graph.
 *  @author Aaron Gember-Jacobson (agember@cs.wisc.edu)
 */
public abstract class ExtendedTopologyGraph<V extends Vertex> implements
    Serializable, Cloneable {
  private static final long serialVersionUID = -6140832442214863195L;
  
  public static final String GRAPHVIZ_FILENAME = "base.gv";

  /** Logger for ETG generation */
  public transient Logger logger;

  /** Settings for ETG generation */
  protected transient Settings settings;

  /** Underlying graph representation */
  private DefaultDirectedWeightedGraph<V,DirectedEdge<V>> graph;

  /** Flow whose extended topology graph this is */
  private Flow flow;
  
  /** Destination whose extended topology graph this is */
  private PolicyGroup destination;
  
  /** Vertex representing the flow's source */
  private V flowSourceVertex;

  /** Vertex representing the flow's destination */
  private V flowDestinationVertex;

  /**
   * Create an empty extended topology graph.
   */
  protected ExtendedTopologyGraph(Settings settings) {
    this.settings = settings;
    this.logger = settings.getLogger();
    this.flow = null;
    this.graph = new DefaultDirectedWeightedGraph<V,DirectedEdge<V>>(
        new DirectedEdgeFactory<V>());
  }

  /**
   * Create a copy of an extended topology graph.
   * @param etg extended topology graph to copy
   */
  /*public ExtendedTopologyGraph(ExtendedTopologyGraph<V> etg) {
    this.logger = etg.logger;
    this.settings = etg.settings;
    this.graph = new DefaultDirectedWeightedGraph<V,DirectedEdge<V>>(
        new DirectedEdgeFactory<V>());
    Graphs.addAllVertices(this.graph, etg.graph.vertexSet());
    for (DirectedEdge<V> edge : etg.graph.edgeSet()) {
      DirectedEdge<V> newEdge = this.graph.addEdge(edge.getSource(),
          edge.getDestination());
      newEdge.setInterfaces(edge.getSourceInterface(),
          edge.getDestinationInterface());
      this.graph.setEdgeWeight(newEdge, edge.getWeight());
    }
    this.flow = etg.flow;
    this.flowSourceVertex = this.getVertex(SOURCE_VERTEX_NAME);
    this.flowDestinationVertex = this.getVertex(DESTINATION_VERTEX_NAME);
  }*/

  /**
   * Create a copy of the extended topology graph.
   * @return a new extended topology graph
   */
  @SuppressWarnings("unchecked")
  @Override
  public Object clone() {
    ExtendedTopologyGraph<V> etgClone = null;
    try {
      etgClone = (ExtendedTopologyGraph<V>)super.clone();
    } catch (CloneNotSupportedException e) {
      return null;
    }
    etgClone.logger = this.logger;
    etgClone.settings = this.settings;
    etgClone.graph = new DefaultDirectedWeightedGraph<V,DirectedEdge<V>>(
        new DirectedEdgeFactory<V>());
    Graphs.addAllVertices(etgClone.graph, this.graph.vertexSet());
    for (DirectedEdge<V> edge : this.graph.edgeSet()) {
      etgClone.addEdge(edge);
    }
    etgClone.destination = this.destination;
    etgClone.flow = this.flow;
    etgClone.flowSourceVertex =
        etgClone.getVertex(VertexType.SOURCE.toString());
    etgClone.flowDestinationVertex =
        etgClone.getVertex(VertexType.DESTINATION.toString());
    return etgClone;
  }

  /**
   * Create an extended topology graph for a specific flow based on an
   * existing extended topology graph.
   * @param baseEtg non-flow-specific extended topology graph
   * @param flow flow whose extended topology graph this should be
   */
  /*public ExtendedTopologyGraph(ExtendedTopologyGraph<V> baseEtg, Flow flow) {
    this(baseEtg);
    this.flow = flow;
    this.constructFlowVertices();
  }

  private boolean constructFlowVertices() {
    // Create source and destination vertices
    if (!this.addVertex(SOURCE_VERTEX_NAME)) {
       return false;
    }
    this.flowSourceVertex = this.getVertex(SOURCE_VERTEX_NAME);
    if (!this.addVertex(DESTINATION_VERTEX_NAME)) {
       return false;
    }
    this.flowDestinationVertex = this.getVertex(DESTINATION_VERTEX_NAME);
    return (this.flowSourceVertex != null
        && this.flowDestinationVertex != null);
  }*/
  
  /**
   * Customize the graph for a specific destination.
   * @param destination the destination for which the graph should be customized
   * @param flowDestinationVertex a vertex representing the destination
   *    endpoint(s)
   * @return true if the graph was successfully customized, otherwise false
   */
  protected boolean customize(PolicyGroup destination,
      V flowDestinationVertex) {
    if (this.destination != null || this.flow != null) {
      return false;
    }
    this.destination = destination;
    this.flowDestinationVertex = flowDestinationVertex;
    this.addVertex(flowDestinationVertex);
    return true;
  }

  /**
   * Customize the graph for a specific flow.
   * @param flow the flow for which the graph should be customized
   * @return true if the graph was successfully customized, otherwise false
   */
  public abstract boolean customize(Flow flow);

  /**
   * Customize the graph for a specific flow.
   * @param flow the flow for which the graph should be customized
   * @param flowSourceVertex a vertex representing the source endpoint(s)
   * @param flowDestinationVertex a vertex representing the destination
   *    endpoint(s)
   * @return true if the graph was successfully customized, otherwise false
   */
  protected boolean customize(Flow flow, V flowSourceVertex,
      V flowDestinationVertex) {
    if (this.flow != null) {
      return false;
    }
    this.flow = flow;
    this.destination = flow.getDestination();
    this.flowSourceVertex = flowSourceVertex;
    this.addVertex(flowSourceVertex);
    this.flowDestinationVertex = flowDestinationVertex;
    this.addVertex(flowDestinationVertex);
    this.constructEndpointEdges();
    this.filterEdgesAndVertices();
    return true;
  }

  /**
   * Add edges for the source and destination of the flow.
   */
  protected abstract void constructEndpointEdges();

  protected abstract void filterEdgesAndVertices();

  /**
   * Add a vertex to the graph.
   * @param vertex to add
   * @return true if the vertex was added, otherwise false
   */
  public boolean addVertex(V vertex) {
    return this.graph.addVertex(vertex);
  }

  /**
   * Remove a vertex from the graph.
   * @param vertex to remove
   * @return true if the vertex was removed, otherwise false
   */
  public boolean removeVertex(V vertex) {
	  return this.graph.removeVertex(vertex);
  }

  /**
   * Get a vertex based on its name.
   * @param name vertex name
   * @return the vertex with the corresponding name, null if none exists
   */
  public V getVertex(String name) {
    for (V vertex : this.graph.vertexSet()) {
      if (vertex.getName().equals(name)) {
        return vertex;
      }
    }
    return null;
  }

  /**
   * Get the vertex representing the flow's source.
   * @return the vertex representing the flow's source, or null if the graph
   *       is not customized for a specific flow
   */
  public V getFlowSourceVertex() {
    return this.flowSourceVertex;
  }

  /**
   * Get the vertex representing the flow's destination.
   * @return the vertex representing the flow's destination, or null if the
   *       graph is not customized for a specific flow
   */
  public V getFlowDestinationVertex() {
    return this.flowDestinationVertex;
  }

  /**
   * Gets an iterator over the vertex set.
   * @return iterator over the vertices
   */
  public Iterator<V> getVerticesIterator() {
    return this.graph.vertexSet().iterator();
    //return this.vertices.values().iterator();
  }

  /**
   * Checks if the graph contains a vertex.
   * @param vertex the vertex to check for
   * @return true if the graph contains the vertex, otherwise false
   */
  public boolean containsVertex(V vertex) {
	  return this.graph.containsVertex(vertex);
  }

  /**
   * Add an edge to the graph.
   * @param source source vertex
   * @param destination destination vertex
   * @param weight weight of the edge
   * @param type type of edge
   * @return the edge that was added
   */
  protected DirectedEdge<V> addEdge(V source, V destination,
      double weight, EdgeType type) {
    DirectedEdge<V> edge = this.graph.addEdge(source, destination);
    if (null == edge) {
      edge = this.graph.getEdge(source, destination);
    }
    this.graph.setEdgeWeight(edge, weight);
    edge.setType(type);
    return edge;
  }

  /**
   * Add an edge to the graph.
   * @param source source vertex
   * @param destination destination vertex
   * @param weight weight of the edge
   * @param type type of edge
   * @param sourceInterface source interface
   * @param destinationInterface destination interface
   * @return the edge that was added
   */
  protected DirectedEdge<V> addEdge(V source, V destination, double weight, 
          EdgeType type, Interface sourceInterface,
          Interface destinationInterface) {
    DirectedEdge<V> edge = this.addEdge(source, destination, weight, type);
    edge.setInterfaces(sourceInterface, destinationInterface);
    return edge;
  }

  /**
   * Add an edge to the graph.
   * @param edge to add
   * @return the edge that was added
   */
  protected DirectedEdge<V> addEdge(DirectedEdge<V> edge) {
    DirectedEdge<V> newEdge = this.addEdge(edge.getSource(), 
            edge.getDestination(), edge.getWeight(), edge.getType(), 
            edge.getSourceInterface(), edge.getDestinationInterface());
    if (edge.isBlocked()) {
        newEdge.markBlocked();
    }
    if (edge.hasWaypoint()) {
        newEdge.markWaypoint();
    }
    return newEdge;
  }

  /**
   * Add an edge to the graph.
   * @param desiredEdge the edge to add
   * @return the edge that was added
   */
  protected DirectedEdge<V> addEdge(VirtualDirectedEdge<V> desiredEdge) {
      DirectedEdge<V> edge = this.graph.addEdge(desiredEdge.getSource(),
    		desiredEdge.getDestination());
    if (null == edge) {
    edge = this.graph.getEdge(desiredEdge.getSource(),
    		  desiredEdge.getDestination());
    }
    this.graph.setEdgeWeight(edge, desiredEdge.getWeight());
    edge.setType(desiredEdge.getType());
    return edge;
  }

  /**
   * Remove an edge from the graph.
   * @param source source vertex
   * @param destination destination vertex
   * @return the edge that was removed
   */
  protected DirectedEdge<V> removeEdge(V source, V destination) {
    return this.graph.removeEdge(source, destination);
  }

  /**
   * Remove an edge from the graph.
   * @param edge to remove
   * @return true if the edge was successfully removed, otherwise false
   */
  protected boolean removeEdge(DirectedEdge<V> edge) {
    return this.graph.removeEdge(edge);
  }

  /**
   * Remove an edge from the graph.
   * @param desiredEdge the edge to remove
   * @return true if the edge was successfully removed, otherwise false
   */
  protected boolean removeEdge(VirtualDirectedEdge<V> desiredEdge) {
      return this.removeEdge(this.getEdge(desiredEdge.getSource(),
    		desiredEdge.getDestination()));
  }

  /**
   * Retrieve an already existing edge.
   * @param source
   * @param destination
   * @return The DirectedEdge from the graph. Returns null if no edge is
   * present
   */
  public DirectedEdge<V> getEdge(V source, V destination) {
    if(this.containsEdge(source, destination))
      return this.graph.getEdge(source, destination);
    else
      return null;
  }
  
  /**
   * Retrieve an already existing edge.
   * @param edge edge to check for
   * @return The DirectedEdge from the graph. Returns null if no edge is
   * present
   */
  public DirectedEdge<V> getEdge(VirtualDirectedEdge<V> edge) {
    return this.getEdge(edge.getSource(), edge.getDestination());
  }

  /**
   * Update the weight of an edge.
   * @param source edge's source vertex
   * @param destination edge's destination vertex
   * @param weight new weight for the edge
   */
  public void setEdgeWeight(V source, V destination, double weight){
    DirectedEdge<V> edge = this.getEdge(source, destination);
    if (edge != null) {
      this.graph.setEdgeWeight(edge, weight);
    }
  }

  /**
   * Checks if the graph contains an edge.
   * @param source source vertex
   * @param destination destination vertex
   * @return true if the graph contains an edge from the source to the
   *    destination, otherwise false
   */
  public boolean containsEdge(V source, V destination) {
    return this.graph.containsEdge(source, destination);
  }

  /**
   * Checks if the graph contains an edge.
   * @param edge edge to check for
   * @return true if the graph contains the edge, otherwise false
   */
  public boolean containsEdge(VirtualDirectedEdge<V> edge) {
    return this.containsEdge(edge.getSource(), edge.getDestination());
  }

  /**
   * Checks if the graph contains a non-blocked edge.
   * @param edge edge to check for
   * @return true if the graph contains the edge and it is not blocked,
   * 		otherwise false
   */
  public boolean containsEdgeExcludeBlocked(VirtualDirectedEdge<V> edge) {
	  DirectedEdge<V> actualEdge =
			  this.getEdge(edge.getSource(), edge.getDestination());
	  return (actualEdge != null && !actualEdge.isBlocked());
  }

  /**
   * Get the incoming edges of a vertex.
   * @param vertex
   * @return the incoming edges of a vertex
   */
  public Set<DirectedEdge<V>> getIncomingEdges(V vertex) {
    return this.graph.incomingEdgesOf(vertex);
  }

  /**
   * Get the outgoing edges of a vertex.
   * @param vertex
   * @return the outgoing edges of a vertex
   */
  public Set<DirectedEdge<V>> getOutgoingEdges(V vertex) {
    return this.graph.outgoingEdgesOf(vertex);
  }

  /**
   * Gets an iterator over the edge set.
   * @return iterator over the edges
   */
  public Iterator<DirectedEdge<V>> getEdgesIterator() {
    return this.graph.edgeSet().iterator();
  }

  /**
   * Get the flow associated with the graph.
   * @return the flow associated with the graph, null if the graph is not
   *       customized to a particular flow
   */
  public Flow getFlow() {
    return this.flow;
  }
  
  /**
   * Get the destination associated with the graph.
   * @return the destination associated with the graph, null if the graph is not
   *       customized to a particular destination
   */
  public PolicyGroup getDestination() {
    return this.destination;
  }


  /**
   * Get a list of the vertices and edges in the graph.
   */
  @Override
  public String toString() {
    String result = "Vertices:\n";
    for (Vertex vertex : this.graph.vertexSet()) {
      result += "\t" + vertex + "\n";
    }
    result += "Edges:\n";
    for (DirectedEdge<V> edge : this.graph.edgeSet()) {
      result += "\t" + edge + "\n";
    }
    return result;
  }

  /**
   * Get a graphviz representation of the graph.
   * @return graphviz code for the graph
   */
  public String toGraphviz() {
    String gvCode = "digraph {\n";
    gvCode += edgesToGraphviz();
    gvCode += verticesToGraphviz();
    if (this.getFlow() != null) {
    	gvCode += "label=\"" + this.getFlow() + "\"\n";
    }
    else {
		gvCode += "label=\"" + this.getClass().getSimpleName() + "\"\n";
    }
    gvCode += "}";
    return gvCode;
  }
  
  /**
   * Get the name of the file where the graphviz encoding should be stored.
   * @return filename for the graphviz encoding
   */
  public String getGraphvizFilename() {
	  if (null == this.getDestination() && null == this.getFlow()) {
		  return GRAPHVIZ_FILENAME;
	  }
      String source = "W";
      if (this.getFlow() != null) {
    	  source = String.format("%s-%s",
                  this.getFlow().getSource().getStartIp(),
                  this.getFlow().getSource().getEndIp());
      }
      return String.format("%s_%s-%s.gv", source,
              this.getDestination().getStartIp(),
              this.getDestination().getEndIp());
  }

  /**
   * Get the color of the ETG vertex
   * @param vertex ETG vertex of interest
   * @return A string notation of the vertex color
   */
  public String getGraphvizVertexColor(Vertex vertex) {
    if (vertex == this.getFlowSourceVertex()
        || vertex == this.getFlowDestinationVertex()) {
      return "red";
    }
    else if ( vertex.getName().contains("STATIC.")) {
      return "green";
    }
    else if (vertex.getName().contains("BGP.")) {
      return "cyan";
    }
    else if (vertex.getName().contains("OSPF.")) {
      return "orange";
    }
    else {
      return "white";
    }
  }

  /**
   * Get the Graphviz representation for ETG edges
   * @return A Graphviz representation of edges of ETG
   */
  protected String edgesToGraphviz() {
    String gvCode = "";
    // Add edges
    Iterator<DirectedEdge<V>> edgesIterator = this.getEdgesIterator();

    while (edgesIterator.hasNext()) {
      DirectedEdge<V> edge = edgesIterator.next();
      String label = "" + edge.getWeight();
      if (DirectedEdge.INFINITE_WEIGHT == edge.getWeight()) {
        label = "inf";
      }
      if (edge.isBlocked()) {
          label += " [BLOCKED]";
      }
      if (edge.hasWaypoint()) {
          label += " [WAYPOINT]";
      }
      gvCode += "\t\"" + edge.getSource().getName() + "\" -> \""
          + edge.getDestination().getName() +"\" [label=\""
          + label + "\"]\n";
    }

    return gvCode;
  }

  /**
   * Get the Graphviz representation for ETG vertices
   * @return A Graphviz representation of vertices of ETG
   */
  protected String verticesToGraphviz() {
    String gvCode = "";
    // Add vertices
    Iterator<V> verticesIterator = this.getVerticesIterator();
    while (verticesIterator.hasNext()) {
      Vertex vertex = verticesIterator.next();
      gvCode += "\t\"" + vertex.getName()
          + "\"[shape=oval, style=filled, fillcolor=";
      gvCode += this.getGraphvizVertexColor(vertex);

      gvCode += "]\n";

    }
    return gvCode;
  }

  /**
   * Remove blocked/infinite weight edges, and optinally waypoint edges, from 
   * the extended topology graph.
   * @param waypoint whether to also prune waypoint edges
   */
  public void prune(boolean waypoint) {
    // Remove all blocked and waypoint edges, as requested
    List<DirectedEdge<V>> edgesToRemove = new ArrayList<DirectedEdge<V>>();
    for (DirectedEdge<V> edge : this.graph.edgeSet()) {
      if (DirectedEdge.INFINITE_WEIGHT == edge.getWeight()
              || edge.isBlocked()) {
        edgesToRemove.add(edge);
      }
      if (waypoint && edge.hasWaypoint()) {
        edgesToRemove.add(edge);
      }
    }
    this.graph.removeAllEdges(edgesToRemove);
  }

 
  
  
  
  /**
   * Remove a set of links from the topology graph.
   * @param links the links to remove
   */
  public void removeLinks(List<Link> links) {
    for (Link link : links) {
      this.removeLink(link);
    }
  }

  /**
   * Remove a link from the topology graph.
   * @param link the link to remove
   */
  public abstract void removeLink(Link link);

  /**
   * Raise an exception or log a warning when an assumption is violated
   * during ETG construction.
   * @param violation details on the violated assumption
   */
  protected void assumptionViolated(String violation) {
    if (settings.shouldWarnAssumptions()) {
      logger.warn(violation);
    }
    else {
      throw new GeneratorException(violation);
    }
  }

  /**
   * Get the number of edges in the graph.
   * @return the number of edges in the graph
   */
  public int getEdgeCount() {
    return this.graph.edgeSet().size();
  }

  /**
   * Get the number of vertices in the graph.
   * @return the number of vertices in the graph
   */
  public int getVertexCount() {
    return this.graph.vertexSet().size();
  }

  public DefaultDirectedWeightedGraph<V,DirectedEdge<V>> getGraph() {
    return this.graph;
  }

  /**
   * Determine if two ETGs are equivalent.
   * @param other the ETG to compare against
   * @return true if the ETGs are equivalent, otherwise false
   */
  public boolean isEquivalent(ExtendedTopologyGraph<V> other) {
	  // ETGs should have the same number of vertices
	  if (this.graph.vertexSet().size() != other.graph.vertexSet().size()) {
		  logger.debug("ETGs for " + this.getFlow()
		  		+ " contain a different number of vertices ("
		  		+ this.graph.vertexSet().size() + " != "
		  		+ other.graph.vertexSet().size() + ")");
		  /*logger.debug("This vertices:");
		  for (V vertex : this.graph.vertexSet()) {
			  logger.debug("\t" + vertex);
		  }
		  logger.debug("Other vertices:");
		  for (V vertex : other.graph.vertexSet()) {
			  logger.debug("\t" + vertex);
		  }*/
		  return false;
	  }

	  // ETGs should have the same vertices
	  for (V vertexA : this.graph.vertexSet()) {
		  if (!other.graph.vertexSet().contains(vertexA)) {
			  logger.debug("ETGs for " + this.getFlow()
		  			+ " do not both contain a vertex " + vertexA.getName());
			  return false;
		  }
	  }

	  // ETGs should have the same number of edges
	  if (this.graph.edgeSet().size() != other.graph.edgeSet().size()) {
		  logger.debug("ETGs for " + this.getFlow()
	  			+ " contain a different number of edges");
		  return false;
	  }

	  boolean compareWeights = true;
	  Map<DirectedEdge<V>, Double> canonicalWeightsA = null;
	  Map<DirectedEdge<V>, Double> canonicalWeightsB = null;
	  try {
		  CanonicalETGConverter<V> convThis =
				  (CanonicalETGConverter<V>)Class.forName(
						  "edu.wisc.cs.arc.modifiers.CanonicalETGConverterGurobi").getConstructor(
						  this.getClass().getSuperclass()).newInstance(this);
		  CanonicalETGConverter<V> convOther =
				  (CanonicalETGConverter<V>)Class.forName(
						  "edu.wisc.cs.arc.modifiers.CanonicalETGConverterGurobi").getConstructor(
						  this.getClass().getSuperclass()).newInstance(other);

		  canonicalWeightsA = convThis.getCanonicalEdgeWeights();
		  canonicalWeightsB = convOther.getCanonicalEdgeWeights();

	  } catch (ModifierException e) {
		  logger.error("Gurobi (LP) failure while obtaining canonical weights. "
				  + "Ignoring weight comparison. Error: " + e.getMessage());
		  compareWeights = false;
	  } catch (ClassNotFoundException e) {
		  logger.warn("Cannot convert to canonical weights");
		  compareWeights = false;
	  } catch (InstantiationException e) {
		  logger.warn("Cannot convert to canonical weights");
		  compareWeights = false;
	  } catch (IllegalAccessException e) {
		  logger.warn("Cannot convert to canonical weights");
		  compareWeights = false;
	  } catch (IllegalArgumentException e) {
		  logger.warn("Cannot convert to canonical weights");
		  compareWeights = false;
	  } catch (InvocationTargetException e) {
		  logger.warn("Cannot convert to canonical weights");
		  compareWeights = false;
	  } catch (NoSuchMethodException e) {
		  logger.warn("Cannot convert to canonical weights");
		  compareWeights = false;
	  } catch (SecurityException e) {
		  logger.warn("Cannot convert to canonical weights");
		  compareWeights = false;
	  }

	  // ETGs should have the same edges
	  for (DirectedEdge<V> edgeA : this.graph.edgeSet()) {
		  V srcB = other.getVertex(edgeA.getSource().getName());
		  V dstB = other.getVertex(edgeA.getDestination().getName());
		  DirectedEdge<V> edgeB = other.getEdge(srcB, dstB);
		  if (null == edgeB) {
			  logger.debug("ETGs for " + this.getFlow()
			  + " do not both contain an edge " + edgeB);
			  return false;
		  }
		  if (compareWeights && (!canonicalWeightsA.get(edgeA).equals(
				  canonicalWeightsB.get(edgeB)))) {
			  return false;
		  }
	  }

	  return true;
  }

  public DefaultDirectedWeightedGraph<V,DirectedEdge<V>> getUnitWeightCopy() {
  		//Create unit weight graph
		DefaultDirectedWeightedGraph<V,DirectedEdge<V>> unitWeightGraph =
				new DefaultDirectedWeightedGraph<V,DirectedEdge<V>>(
						new DirectedEdgeFactory<V>());
		Graphs.addAllVertices(unitWeightGraph, this.graph.vertexSet());
		Iterator<DirectedEdge<V>> iterator = this.getEdgesIterator();
		while (iterator.hasNext()) {
			DirectedEdge<V> edge = iterator.next();
			DirectedEdge<V> newEdge = unitWeightGraph.addEdge(edge.getSource(),
					edge.getDestination());
			if (EdgeType.INTER_DEVICE == edge.getType()) {
				unitWeightGraph.setEdgeWeight(newEdge, 1);
			}
			else {
				unitWeightGraph.setEdgeWeight(newEdge,
						DirectedEdge.INFINITE_WEIGHT);
			}
		}
		return unitWeightGraph;
  }

    /**
     * Make a list of modifications to the ETG.
     * @param modifications the modifications to make
     */
  	public void modify(List<GraphModification<V>> modifications) {
  		for (GraphModification<V> modification : modifications) {
  			this.modify(modification);
  		}
	}

  	/**
  	 * Make a modification to the ETG.
  	 * @param modification the modification to make
  	 */
  	public void modify(GraphModification<V> modification) {
  		switch(modification.getAction()) {
		case ADD:
			if (this.containsEdge(modification.getEdge())) {
				throw new RepairException("Modification "
						+ modification.toString() + " already applied");
			}
			this.addEdge(modification.getEdge());
			break;
		case REMOVE:
			if (!this.containsEdge(modification.getEdge())) {
				throw new RepairException("Modification "
						+ modification.toString() + " already applied");
			}
			this.removeEdge(modification.getEdge());
			break;
  		}
  	}

}
