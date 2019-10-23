package edu.wisc.cs.arc.graphs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.batfish.datamodel.IpAccessList;
import org.batfish.representation.cisco.ExtendedAccessList;
import org.batfish.representation.cisco.StandardAccessList;

/**
 * Groups flows based on the set of ACLs affecting the flow.
 * @author Aaron Gember-Jacobson (agember@cs.wisc.edu)
 */
public class FlowGrouper {

	public static Collection<List<Flow>> groupFlows(List<Flow> flows,
			ProcessGraph baseEtg) {
		
		Map<String,List<Flow>> flowGroups = new HashMap<String,List<Flow>>();
	
		for (Flow flow : flows) {
			List<String> acls = getFlowACLs(flow, baseEtg);
			String aclKey = flow.getSource().toString() + "-" 
					+ String.join("-", acls);
			List<Flow> flowGroup = flowGroups.get(aclKey);
			if (null == flowGroup) {
				flowGroup = new ArrayList<Flow>();
				flowGroups.put(aclKey, flowGroup);
			}
			flowGroup.add(flow);
		}

		return flowGroups.values();
	}
	
	private static List<String> getFlowACLs(Flow flow, ProcessGraph baseEtg) {
		List<String> acls = new ArrayList<String>();
		for (Device device : baseEtg.getDevices()) {
			for (Process process : device.getRoutingProcesses()) {
				for (DirectedEdge<ProcessVertex> edge : 
						baseEtg.getOutgoingEdges(process.getOutVertex())) {
					if (null == edge.getSourceInterface()) {
						continue;
					}
					String acl = getOutgoingEdgeAcl(flow, 
							edge.getSourceInterface(), process);
					if (acl != null) {
						acls.add(acl);
					}
				}
				
				for (DirectedEdge<ProcessVertex> edge : 
						baseEtg.getIncomingEdges(process.getInVertex())) {
					if (null == edge.getDestinationInterface()) {
						continue;
					}
					String acl = getIncomingEdgeAcl(flow, 
							edge.getDestinationInterface(), process);
					if (acl != null) {
						acls.add(acl);
					}
				}
			}
		}
		return acls;
	}
	
	private static String getOutgoingEdgeAcl(Flow flow, Interface iface, 
			Process sourceProcess) {
		if (iface.getOutgoingFilter() != null) {
			IpAccessList acl = 
					sourceProcess.getDevice().getAcl(
							iface.getOutgoingFilter());
			
			if (acl != null && flow.isBlocked(acl)) {
				return sourceProcess.getDevice() + "." + acl.getName();
			}
		}
		return null;
	}
	
	private static String getIncomingEdgeAcl(Flow flow, Interface iface, 
			Process destinationProcess) {
		if (iface.getIncomingFilter() != null) {
			IpAccessList acl = 
					destinationProcess.getDevice().getAcl(
							iface.getIncomingFilter());
			
			if (acl != null && flow.isBlocked(acl)) {
				return destinationProcess.getDevice() + "." + acl.getName();
			}
		}
		return null;
	}
}
