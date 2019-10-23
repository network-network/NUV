package edu.wisc.cs.arc.graphs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.SwitchportMode;
import org.batfish.datamodel.SubRange;

import edu.wisc.cs.arc.GeneratorException;
import edu.wisc.cs.arc.graphs.Vertex.VertexType;

public class Interface implements Serializable {
	private static final long serialVersionUID = -6473140553946259836L;

	public final static String NAME_PREFIX_PORT_CHANNEL = "port-channel";
	public final static String NAME_PREFIX_VLAN = "Vlan";

	/** Device on which the interface resides */
	private Device device;

	/** Name of the interface */
	private String name;

	/** Description of the interface */
	private String description;

	/** IP address prefix assigned to the interface */
	private Ip ip;

    /** Network bits */
    private int networkBits;

	/** Whether the interface is active */
	private boolean active;

	/** OSPF cost associated with the interface */
	private Integer ospfCost;

	/** Bandwidth of the interface */
	private Double bandwidth;

	/** Name of the filter applied to traffic entering the device on this
	 * interface */
	private String incomingFilter;

	/** Name of the filter applied to traffic leaving the device on this
	 * interface */
	private String outgoingFilter;

	/** Access VLAN number */
	private Integer accessVlan;

	/** Allowed VLAN numbers */
	private List<SubRange> allowedVlans;

	/** ETG vertex used to enter the device via this interface */
	private InterfaceVertex inVertex;

	/** ETG vertex used to exit the device via this interface */
	private InterfaceVertex outVertex;

	/** Interfaces participating in the VLAN or port-channel */
	private List<Interface> subInterfaces;

	/** Channel group */
	private Integer channelGroup;

	/** Is the interface internal to the device? */
	private boolean internal;

	public enum InterfaceType {
		VLAN,
		PORT_CHANNEL,
		LOOPBACK,
		MANAGEMENT,
		ETHERNET,
        TUNNEL,
        BRIDGE,
		SERIAL,
		ATM,
		GMPLS
	}

	private InterfaceType type;

	/**
	 * Creates a simple interface.
	 * @param device
	 * @param name
	 * @param ip
     * @param networkBits
	 */
	public Interface(Device device, String name, Ip ip, int networkBits) {
		this.device = device;
//		System.out.println("why configuration so interface"+name);
		this.name = name;
		this.ip = ip;
        this.networkBits = networkBits;
		this.active = true;
		this.inVertex = new InterfaceVertex(this, VertexType.IN);
		this.outVertex = new InterfaceVertex(this, VertexType.OUT);
		this.allowedVlans = new ArrayList<SubRange>();
		this.accessVlan = null;
		this.internal = true;

		if (name.toLowerCase().contains("vlan")) {
			this.type = InterfaceType.VLAN;
			this.accessVlan = Integer.parseInt(
					name.toLowerCase().replace("vlan", ""));
			this.subInterfaces = new ArrayList<Interface>();
			this.allowedVlans = null;
			this.internal = false;
		} else if (name.toLowerCase().contains(NAME_PREFIX_PORT_CHANNEL)) {
			this.type = InterfaceType.PORT_CHANNEL;
			this.subInterfaces = new ArrayList<Interface>();
		} else if (name.toLowerCase().contains("loopback")) {
			this.type = InterfaceType.LOOPBACK;
		} else if (name.toLowerCase().contains("mgmt")) {
			this.type = InterfaceType.MANAGEMENT;
		} else if (name.toLowerCase().contains("ethernet")
			|| name.toLowerCase().contains("tengige")) {
			this.type = InterfaceType.ETHERNET;
			this.internal = false;
		} else if (name.toLowerCase().contains("tunnel")) {
			this.type = InterfaceType.TUNNEL;
		// Description of bridge virtual interfaces (BVIs)
		// http://www.cisco.com/c/en/us/support/docs/lan-switching/integrated-routing-bridging-irb/200650-Understanding-Bridge-Virtual-Interface.html
		} else if (name.toLowerCase().contains("bvi")) {
		    this.type = InterfaceType.BRIDGE;
		} else if (name.toLowerCase().contains("serial")){
			this.type = InterfaceType.SERIAL;
			this.internal = false;
		} else if (name.toLowerCase().contains("atm")){
            this.type = InterfaceType.ATM;
		} else if (name.toLowerCase().contains("gmpls")){
            this.type = InterfaceType.GMPLS;
		}  else {
			throw new GeneratorException("Unknown interface type: "+name);
		}
		
	}

	/**
	 * Creates an interface based on a Cisco device interface.
	 * @param device the device on which the process is running
	 * @param ciscoIface the Cisco device interface
	 */
	public Interface(Device device,
			org.batfish.datamodel.Interface Iface) {
		this(device, Iface.getName(), Iface.getAddress().getIp(), 
                Iface.getAddress().getNetworkBits());
		this.description = Iface.getDescription();
		this.active = Iface.getActive();
		this.ospfCost = Iface.getOspfCost();
		this.bandwidth = Iface.getBandwidth();
		if (Iface.getAccessVlan() > 0) {
			this.accessVlan = Iface.getAccessVlan();
		}
		this.allowedVlans = Iface.getAllowedVlans();
		if (Iface.getSwitchportMode() == SwitchportMode.TRUNK) {
		    if (0 == this.allowedVlans.size()) {
		        this.allowedVlans.add(new SubRange(1,4095));
		    }
		}
		else if (Iface.getSwitchportMode() == SwitchportMode.ACCESS) {
		    if (0 == this.allowedVlans.size() && this.accessVlan != null) {
		        this.allowedVlans.add(new SubRange(this.accessVlan,
		                this.accessVlan));
		    }
		}
		if (Iface.getIncomingFilter() != null) {
			this.incomingFilter = Iface.getIncomingFilter().getName();
		}
		if (Iface.getOutgoingFilter() != null){
			this.outgoingFilter = Iface.getOutgoingFilter().getName();
		}
		//this.channelGroup = Iface.getChannelGroup(); //FIXME
	}

	/**
	 * Get the name of the interface.
	 * @return name of the interface
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Set the name of the interface.
	 * @param name new name for the interface
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	/**
     * Get the full name of the interface including the device name.
     * @return name of the device plus the interface
     */
    public String getFullName() {
        return this.device.getName() + "." + this.name;
    }

	/**
	 * Get the description of the interface.
	 * @return description of the interface
	 */
	public String getDescription() {
		return this.description;
	}

	/**
	 * Determine if the interface has an prefix assigned.
	 * @return true if a prefix is assigned to the interface; otherwise false
	 */
	public boolean hasPrefix() {
		return (this.ip != null);
	}

	/**
	 * Get the address prefix assigned to the interface, if any.
	 * @return address prefix assigned to the interface; null if none
	 */
	public Prefix getPrefix() {
		return new Prefix(this.ip, this.networkBits);
	}

	/**
	 * Get the address assigned to the interface, if any.
	 * @return address assigned to the interface; null if none
	 */
	public Ip getAddress() {
		return this.ip;
	}

	/**
	 * Determine whether the interface is active.
	 * @return true if the interface is active, otherwise false
	 */
	public boolean getActive() {
		return this.active;
	}

	/**
	 * Get the OSPF cost associated with the interface, if specified, or
	 * calculate based on bandwidth, if specified.
	 * @return OSPF cost associated to the interface; null if unspecified
	 */
	public Integer getOspfCost() {
		if (this.ospfCost != null) {
			return this.ospfCost;
		}
		else if (this.bandwidth != null) {
			return (int)(1E8 / this.bandwidth);
		}
		return null;
	}

	/**
	 * Get the interface's bandwidth, if specified.
	 * @return interface's bandwidth; null if unspecified
	 */
	public Double getBandwidth() {
		return this.bandwidth;
	}

	/**
	 * Get the access VLAN number, if specified.
	 * @return access VLAN number; null if unspecified or not in access mode
	 */
	public Integer getAccessVlan() {
		return this.accessVlan;
	}

	/**
	 * Get the allowed VLAN numbers.
	 * @return allowed VLAN numbers
	 */
	public List<SubRange> getAllowedVlans() {
		return this.allowedVlans;
	}

	/**
	 * Get the name of the filter applied to traffic entering the device via
	 * this interface.
	 * @return the name of the incoming filter; null if none
	 */
	public String getIncomingFilter() {
		return this.incomingFilter;
	}

	/**
	 * Get the name of the filter applied to traffic leaving the device via
	 * this interface.
	 * @return the name of the outgoing filter; null if none
	 */
	public String getOutgoingFilter() {
		return this.outgoingFilter;
	}

	/**
	 * Get the ETG vertex used to enter the device via this interface.
	 * @return ETG vertex used to enter the device via this interface.
	 */
	public InterfaceVertex getInVertex() {
		return this.inVertex;
	}

	/**
	 * Get the ETG vertex used to exit the device via this interface.
	 * @return ETG vertex used to exit the device via this interface.
	 */
	public InterfaceVertex getOutVertex() {
		return this.outVertex;
	}

	/**
	 * Get the device on which the interface resides.
	 * @return the device on which the interface resides
	 */
	public Device getDevice() {
		return this.device;
	}

	/**
	 * Get the interfaces participating in a VLAN or port-channel interface.
	 * @return sub interfaces
	 */
	public List<Interface> getSubInterfaces() {
		return this.subInterfaces;
	}

	/**
	 * Get the channel group in which the interface participates.
	 * @return channel group number, null if the interface doesn't participate
	 * 		in a channel group
	 */
	public Integer getChannelGroup() {
		return this.channelGroup;
	}

	/**
	 * Add an interface participating in a VLAN or port-channel interface.
	 * @param sub interface
	 */
	public void addSubInterface(Interface subInterface) {
		if (null == this.subInterfaces) {
			throw new GeneratorException("Cannot add sub interface to "
					+ this.type + " interface " + this.getName());
		}
		this.subInterfaces.add(subInterface);
	}

	/**
	 * Get the type of the interface.
	 * @return interface type
	 */
	public InterfaceType getType() {
		return this.type;
	}

	/**
	 * Determine whether the interface is internal to the device.
	 */
	public boolean isInternal() {
	    return this.internal;
	}

	@Override
	public String toString() {
		return this.name;
	}
}
