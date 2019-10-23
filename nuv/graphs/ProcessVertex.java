package edu.wisc.cs.arc.graphs;

/**
 * A vertex for a process-based extended topology graph.
 * @author Aaron Gember-Jacobson
 */
public class ProcessVertex extends Vertex {
	private static final long serialVersionUID = 8318085428941828229L;
	
	/** Routing process associated with the vertex */ 
	private Process process;
		
	/**
	 * Create a vertex for a process-based extended topology graph.
	 * @param process process associated with the vertex
	 * @param type type of vertex
	 */
	public ProcessVertex(Process process, VertexType type) {
		super(type);
		this.setName(process.getName() + "." + type.toString());
		this.process = process;
	}
	
	/**
	 * Create a vertex for a process-based extended topology graph.
	 * @param type type of vertex
	 */
	public ProcessVertex(VertexType type) {
		super(type);
		this.setName(type.toString());
		this.process = null;
	}
	
	/**
	 * Get the routing process associated with the vertex.
	 * @return process associated with the vertex
	 */
	public Process getProcess() {
		return this.process;
	}
}