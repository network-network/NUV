package edu.wisc.cs.arc.graphs;

/**
 * A vertex for a device-based extended topology graph.
 * @author Aaron Gember-Jacobson (agember@cs.wisc.edu)
 */
public class DeviceVertex extends Vertex {	
	private static final long serialVersionUID = -1046057989380346332L;
	
	/** Device associated with the vertex */ 
	private Device device;
	
	/**
	 * Create a vertex for a device-based extended topology graph.
	 * @param device device associated with the vertex
	 */
	public DeviceVertex(Device device) {
		super(VertexType.NORMAL);
		this.setName(device.getName());
		this.device = device;
	}
	
	/**
	 * Create a vertex for a device-based extended topology graph.
	 * @param type type of vertex
	 */
	public DeviceVertex(VertexType type) {
		super(type);
		this.device = null;
	}
	
	/**
	 * Get the device associated with the vertex.
	 * @return device associated with the vertex
	 */
	public Device getDevice() {
		return this.device;
	}
}
