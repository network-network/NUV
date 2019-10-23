package edu.wisc.cs.arc;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


import edu.wisc.cs.arc.graphs.*;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.batfish.representation.Configuration;
import org.batfish.representation.Ip;
import org.batfish.representation.VendorConfiguration;
import org.batfish.representation.cisco.CiscoVendorConfiguration;
import org.batfish.representation.cisco.ExtendedAccessList;
import org.batfish.representation.cisco.StandardAccessList;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.FloydWarshallShortestPaths;
import org.jgrapht.alg.KShortestPaths;

import edu.wisc.cs.arc.configs.ConfigCompare;
import edu.wisc.cs.arc.configs.ConfigurationParser;
import edu.wisc.cs.arc.graphs.Process.ProcessType;
import edu.wisc.cs.arc.verifiers.AlwaysBlocked;
import edu.wisc.cs.arc.verifiers.AlwaysIsolated;
import edu.wisc.cs.arc.verifiers.AlwaysReachable;
import edu.wisc.cs.arc.verifiers.ComputedPaths;
import edu.wisc.cs.arc.verifiers.CurrentlyBlocked;
import edu.wisc.cs.arc.verifiers.Equivalent;
import edu.wisc.cs.arc.virl.Scenario;
import edu.wisc.cs.arc.virl.VirlConfigurationGenerator;
import edu.wisc.cs.arc.virl.VirlOutputParser;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.alg.KShortestPaths;

import edu.wisc.cs.arc.Settings;
import edu.wisc.cs.arc.virl.Scenario;
/**
 * Starts diffVer.
 * @author yahuili(li-yh15@mails.tsinghua.edu.cn)
 */
public class DriverDiffVer {

	public static void main(String[] args) {
		Logger logger = new Logger(Logger.Level.DEBUG);

		Settingsnew settings = null;
		try {
			settings = new Settingsnew(args, logger);
		} catch (ParseException e) {
			logger.fatal(e.getMessage());
			logger.fatal("Run with '-" + Settings.HELP + "' to show help");
			return;
		}

		logger.info(settings.toString());
//		addData2File("/home/yahui/arc", "icnp2", "Configuation: " + settings.getConfigsDirection()+"    ");
		
		// Parse configurations
		ConfigurationParser parser = new ConfigurationParser(logger,
				settings.getConfigsDirection(), settings.shouldParallelize());
		long startTime = System.currentTimeMillis();
		Map<String, VendorConfiguration> vendorConfigs = parser.parse();
		Map<String, String> rawConfigs = parser.getRawConfigurations();
		long endTime = System.currentTimeMillis();
		System.out.println("TIME: parse "+(endTime - startTime)+" ms");
//		addData2File("/home/yahui/arc", "icnp2","Time: paerse "+(endTime - startTime)+" ms    ");
		
		
		
		ConfigurationParser parser2 = new ConfigurationParser(logger,
				settings.getConfigsDirection2(), settings.shouldParallelize());
		long startTime2 = System.currentTimeMillis();
		Map<String, VendorConfiguration> vendorConfigs2 = parser2.parse();
		Map<String, String> rawConfigs2 = parser2.getRawConfigurations();
		long endTime2 = System.currentTimeMillis();
		System.out.println("TIME: parse2 "+(endTime2 - startTime2)+" ms");
//		addData2File("/home/yahui/arc", "icnp2","Time: paerse2 "+(endTime2 - startTime2)+" ms    ");
		
		
		// Exclude non-routers, if requested
		if (settings.shouldExcludeNonRouters()) {
			List<String> devicesToExclude = new ArrayList<String>();

			// Check each device configuration to see if it contains a router
			// stanza
			for (Entry<String,VendorConfiguration> configEntry :
					vendorConfigs.entrySet()) {
				if (configEntry.getValue() instanceof CiscoVendorConfiguration){
					CiscoVendorConfiguration ciscoConfig =
							(CiscoVendorConfiguration)configEntry.getValue();
					if (0 == ciscoConfig.getBgpProcesses().size()
							&& 0 == ciscoConfig.getOspfProcesses().size()) {
						devicesToExclude.add(configEntry.getKey());
					}
				}
				else {
					throw new GeneratorException(
							"Only Cisco configurations are supported");
				}
			}

			// Remove devices without a router stanza
			for (String deviceToExclude : devicesToExclude) {
				vendorConfigs.remove(deviceToExclude);
				rawConfigs.remove(deviceToExclude);
			}
		}

		
		// Extract configuration details
		List<Device> devices = new ArrayList<Device>();
		Map <String, Device> cdevices;
		for (Entry<String, VendorConfiguration> entry :
				vendorConfigs.entrySet()) {
			if (entry.getValue() instanceof CiscoVendorConfiguration) {
				Device device = new Device(entry.getKey(),
						(CiscoVendorConfiguration)entry.getValue(), logger);
				devices.add(device);
				cdevices.put(entry.getKey(), device);
			}
			else {
				throw new GeneratorException(
						"Only Cisco configurations are supported");
			}
		}
		
		
		// Extract configuration details for new
		// change by lyh,  very important to compar
		List<Device> devices2 = new ArrayList<Device>();
		Map <String, Device> cdevices2;
		for (Entry<String, VendorConfiguration> entry :
				vendorConfigs2.entrySet()) {
			if (entry.getValue() instanceof CiscoVendorConfiguration) {
				Device device = new Device(entry.getKey(),
						(CiscoVendorConfiguration)entry.getValue(), logger);
				devices2.add(device);
				cdevices2.put(entry.getKey(), device);
			}
			else {
				throw new GeneratorException(
						"Only Cisco configurations are supported");
			}
		}


//		// List devices
//		System.out.println("COUNT: devices "+devices.size());
////		addData2File("/home/yahui/arc", "icnp2","COUNT: devices "+devices.size()+"   ");
//		logger.info("Devices:");
//		for  (Device device : devices) {
//			logger.info("\t"+device.getName());
//		}
//		
//		// List devices
//		System.out.println("COUNT: devices2 "+devices.size());
////		addData2File("/home/yahui/arc", "icnp2","COUNT: devices2 "+devices.size()+"   ");
//		logger.info("Devices:");
//		for  (Device device : devices2) {
//			logger.info("\t"+device.getName());
//		}

		// Generate device-based ETG
		logger.info("*** Generate device-based ETG ***");
		DeviceGraph deviceEtg = new DeviceGraph(devices, settings);
		System.out.println("COUNT: deviceETGVertices "
				+ deviceEtg.getVertexCount());
		System.out.println("COUNT: deviceETGEdges "
				+ deviceEtg.getEdgeCount());

		// Generate device-based ETG2
		logger.info("*** Generate device-based ETG ***");
		DeviceGraph deviceEtg2 = new DeviceGraph(devices2, settings);
	
		
		// Create process-based ETG
		logger.info("*** Generate process-based ETG ***");
		startTime = System.currentTimeMillis();
		ProcessGraph processEtg = new ProcessGraph(deviceEtg, settings);
		ExtendedTopologyGraph baseEtg = processEtg;
		endTime = System.currentTimeMillis();
		System.out.println("TIME: baseETG "+(endTime - startTime)+" ms");
		//logger.info(baseEtg.toString());
		
		// Create process-based ETG2
				logger.info("*** Generate process-based ETG ***");
				ProcessGraph processEtg2 = new ProcessGraph(deviceEtg2, settings);
				ExtendedTopologyGraph baseEtg2 = processEtg2;
		

		// Convert process-based ETG to interface-based ETG
		if (settings.shouldGenerateInterfaceETG()) {
			logger.info("*** Generate interface-based ETG ***");
			baseEtg = new InterfaceGraph(processEtg);
		}

		// Convert process-based ETG to interface-based ETG2
		if (settings.shouldGenerateInterfaceETG()) {
			logger.info("*** Generate interface-based ETG ***");
			baseEtg2 = new InterfaceGraph(processEtg2);
		}
		System.out.println("COUNT: baseETGVertices "
				+ baseEtg.getVertexCount());
		System.out.println("COUNT: baseETGEdges "
				+ baseEtg.getEdgeCount());

		System.out.println("COUNT: ospfProcesses "
				+ processEtg.numberOfType(ProcessType.OSPF));
		System.out.println("COUNT: bgpProcesses "
				+ processEtg.numberOfType(ProcessType.BGP));
		System.out.println("COUNT: staticProcesses "
				+ processEtg.numberOfType(ProcessType.STATIC));

		FloydWarshallShortestPaths<Vertex,DirectedEdge<Vertex>> fwsp =
				new FloydWarshallShortestPaths<Vertex, DirectedEdge<Vertex>>(
						baseEtg.getGraph());
		// TODO : Check if this definition of diameter is correct. Is it the max
		// of all the pair-wise shortest paths or the longest path through the
		// graph ?
		int diameter = 0;
		for (GraphPath<Vertex,DirectedEdge<Vertex>> path :
				fwsp.getShortestPaths()) {
			if (path.getEdgeList().size() > diameter) {
				diameter = path.getEdgeList().size();
			}
		}
		System.out.println("COUNT: baseETGDiameter " + diameter);
		
		//for new
		FloydWarshallShortestPaths<Vertex,DirectedEdge<Vertex>> fwsp2 =
				new FloydWarshallShortestPaths<Vertex, DirectedEdge<Vertex>>(
						baseEtg2.getGraph());
		// TODO : Check if this definition of diameter is correct. Is it the max
		// of all the pair-wise shortest paths or the longest path through the
		// graph ?
		int diameter2 = 0;
		for (GraphPath<Vertex,DirectedEdge<Vertex>> path :
				fwsp2.getShortestPaths()) {
			if (path.getEdgeList().size() > diameter) {
				diameter2 = path.getEdgeList().size();
			}
		}
		

		// Generate Base Instance Graph
		logger.info("*** Generate instance-based ETG ***");
		InstanceGraph instanceEtg = processEtg.getInstanceEtg();
		System.out.println("COUNT: instanceETGVertices "
				+ instanceEtg.getVertexCount());
		System.out.println("COUNT: instanceETGEdges "
				+ instanceEtg.getEdgeCount());

		System.out.println("COUNT: ospfInstances "
				+ instanceEtg.numberOfType(ProcessType.OSPF));
		System.out.println("COUNT: bgpInstances "
				+ instanceEtg.numberOfType(ProcessType.BGP));
		System.out.println("COUNT: staticInstances "
				+ instanceEtg.numberOfType(ProcessType.STATIC));

		System.out.println("PROP: instanceIsDag "
				+ !((InstanceGraph)instanceEtg).hasCycles());

		//for new
		InstanceGraph instanceEtg2 = processEtg2.getInstanceEtg();
		
		// Generate VIRL
		if (settings.shouldGenerateVirl()) {
			generateVirl(settings, rawConfigs, deviceEtg);
		}

		// Determine policy groups
		Set<PolicyGroup> policyGroups = determinePolicyGroups(vendorConfigs,
				settings);

		Map<Flow,? extends ExtendedTopologyGraph> flowEtgs = null;

		if (settings.shouldGenerateFlowETGs()) {
			// Create ETGs for every possible flow
			flowEtgs = generateFlowETGs(settings, baseEtg, policyGroups, devices);
		}

		// Generate graphs
		if (settings.shouldGenerateGraphs()) {
			generateGraphs(settings, baseEtg, instanceEtg, deviceEtg, flowEtgs);
		}


		// change by lyh, determine the different gropus
	//for new
	// Determine policy groups
			Set<PolicyGroup> policyGroups2 = determinePolicyGroups(vendorConfigs2,
					settings);

			Map<Flow,? extends ExtendedTopologyGraph> flowEtgs2 = null;

			if (settings.shouldGenerateFlowETGs()) {
				// Create ETGs for every possible flow
				flowEtgs2 = generateFlowETGs(settings, baseEtg2, policyGroups2, devices2);
			}
			
			
			// step 1 compare configs and save changes for each device
			List<String[]> quires= new ArrayList<String[]>();
			
			// determine groups
//			Set<PolicyGroup> policyGroups = determinePolicyGroups(vendorConfigs,
//					settings);
			
			
			ConfigCompare configcompare = new ConfigCompare();
			long startTimet = System.currentTimeMillis();
//			Map<String, VendorConfiguration> vendorConfigs = configcompare.parse();
			
			//determineChangeCase
			int flag=2;
			// the first string is the type, the second string is name, acl, route, complex
			// for example: acl: r1-chagedacl, r2-changedal,
			// for example: routemap: r1-chagedmap, r2-changemap,
			// for example: staticroute: r1-stacticmap, r2-staticmap,
//			 Map<String, Configuration> v1 =parser.convertConfigurations(vendorConfigs);
//			 Map<String, Configuration> v2=parser2.convertConfigurations(vendorConfigs2);
//			 configcompare.comVendorConfigurations(v1, v2, flag);
			 
			 configcompare.comVendorConfigurations(cdevices, cdevices2, flag);
			 
			 
//			 Map<String, Map<String, partVendorConfiguration>> changedConfigs = configcompare.comVendorConfigurations(vendorConfigs, vendorConfigs2, flag);
//			
//			 
//			 if(flag==0) {
//			// step 2 if only acl
//				 Set<PolicyGroup> policyGroupsAcl= determinePolicyGroups(policyGroups, changedConfigs.acllist);
//				 update(quires, policyGroupsAcl); 
//				 }
//			 else if (flag==1){// improve route map, then acl;
//			// step 3 if only route map (block/permit), static route; find Equivalent by abstraction
//				Set<PolicyGroup> policyGroupsAroute=  determinePolicyGroups(policyGroups, changedConfigs.ruotelist);
//				for(policy : PolicyGroup) {
//				if(findSlice(vendorConfigs, policy.src, policy.dst)==findSlice(vendorConfigs2, policy.src, policy.dst)) {
//					continue;
//				}else {
//					update(quires,policy);
//				}
//				}
//			 } else {
//			// step 4 if complex, find Equivalent by abstraction
//				 dstEquivalence = computeEquivalence(vendorConfigs2);
//				 for(dst:dstEquivalence) {
//				 abs1=findAbstaction(vendorConfigs);
//				 abs2=findAbstaction(vendorConfigs2);
//				 if(abs1==abs2) {
//					 continue;
//				 }else if (findSlice(abs1)==findSlice(abs2))
//				 {
//					continue; 
//				 }
//				 
//				 }
//			 }
			// finally return the changed flows that you need to query
			

// change by lyh
//			// Run verification tasks
//			if (flowEtgs2 != null) {
//				runVerificationTasks2(settings, baseEtg2, flowEtgs, deviceEtg, flowEtgs2, deviceEtg2);
//			}
		}
	
	
	
	/**
	 * Determine policy groups
	 * @param vendorConfigs
	 * @param settings
	 * @return non-overlapping policy groups
	 */
	private static Set<PolicyGroup> determinePolicyGroups(
			Map<String, VendorConfiguration> vendorConfigs, Settings settings) {
		Logger logger = settings.getLogger();

		// Extract policy groups
		logger.info("*** Extract Policy Groups ***");
		long startTime = System.currentTimeMillis();
		Set<PolicyGroup> groups = PolicyGroup.extract(vendorConfigs);
		long endTime = System.currentTimeMillis();
		System.out.println("TIME: policyGroups "+(endTime - startTime)+" ms");

		// Output raw policy groups
		List<PolicyGroup> sortedGroups = new ArrayList<PolicyGroup>(groups);
		Collections.sort(sortedGroups);
		for (PolicyGroup group : sortedGroups) {
			if (settings.shouldExcludeExternalFlows() && !group.isInternal()) {
				groups.remove(group);
				continue;
			}

			String groupString = group.toString();
			if (settings.shouldAnonymize()) {
				group.makeAnonymous();
				//groupString += "(" + group.toString() + ")";
				groupString = group.toString();
			}
			logger.debug("\t" + groupString
					+ (group.isInternal() ? " INTERNAL" : " EXTERNAL"));
		}
		System.out.println("COUNT: policyGroups "+groups.size());

		// Add entire address space
		if (settings.shouldIncludeEntireFlowspace()) {
			groups.add(new PolicyGroup(new Ip("0.0.0.0"),
					new Ip("255.255.255.255")));
		}


		// Compute non-overlapping policy groups
		logger.info("*** Processed Policy Groups ***");
		startTime = System.currentTimeMillis();
		Set<PolicyGroup> nonOverlappingGroups =
				PolicyGroup.getNonOverlapping(groups);
		endTime = System.currentTimeMillis();
		System.out.println("TIME: separatePolicyGroups " +
				(endTime - startTime) + " ms");

		// Remove policy groups with tiny prefixes
		List<PolicyGroup> toRemove = new ArrayList<PolicyGroup>();
		for (PolicyGroup group : nonOverlappingGroups) {
			if (group.getEndIp().asLong() - group.getStartIp().asLong()
					< settings.getMinPolicyGroupsSize()) {
				toRemove.add(group);
			}
		}
		nonOverlappingGroups.removeAll(toRemove);

		// Output unfiltered, non-overlapping policy groups
		System.out.println("COUNT: separatePolicyGroups "
				+nonOverlappingGroups.size());
		sortedGroups = new ArrayList<PolicyGroup>(nonOverlappingGroups);
		Collections.sort(sortedGroups);
		for (PolicyGroup group : sortedGroups) {
			if (settings.shouldExcludeExternalFlows() && !group.isInternal()) {
				nonOverlappingGroups.remove(group);
				continue;
			}

			String groupString = group.toString();
			if (settings.shouldAnonymize()) {
				group.makeAnonymous();
				//groupString += "(" + group.toString() + ")";
				groupString = group.toString();
			}
			logger.info("\t" + groupString
					+ (group.isInternal() ? " INTERNAL" : " EXTERNAL"));
		}

		return nonOverlappingGroups;
	}

	/**
	 * Create ETGs for every possible flow
	 * @param settings
	 * @param baseEtg the ETG on which to base the ETG for each flow
	 * @param policyGroups the policy groups from which to define flows
	 * @return the created ETGs
	 */
	private static Map<Flow,ExtendedTopologyGraph> generateFlowETGs(
			Settings settings, ExtendedTopologyGraph baseEtg,
			Set<PolicyGroup> policyGroups, List<Device> devices) {
		Logger logger = settings.getLogger();

		// Create a queue of flows for which to construct ETGs
		Queue<Flow> queue = new ConcurrentLinkedQueue<Flow>();
		List<Flow> flows = new ArrayList<Flow>(policyGroups.size()
				* policyGroups.size() - policyGroups.size());
		for (PolicyGroup source : policyGroups) {
			for (PolicyGroup destination : policyGroups) {
				if (source.equals(destination)) {
					continue;
				}
				Flow flow = new Flow(source, destination);
				flows.add(flow);
			}
		}

		System.out.println("Flows requiring specific ETGs:");
		Map<PolicyGroup, List<PolicyGroup>> dstToSources =
				new HashMap<PolicyGroup, List<PolicyGroup>>();
		// Check if a flow needs a om ETG or we can use an ETG with multiple
		// sources and a common destination
		for (Flow flow : flows) {
			if (flowNeedsCustomEtg(flow, devices)) {
				System.out.println(flow.toString());
				queue.add(flow);
			}
			else {
				if (!dstToSources.containsKey(flow.getDestination())) {
					dstToSources.put(flow.getDestination(),
							new ArrayList<PolicyGroup>());
					Flow wildcardFlow = new Flow(flow.getDestination());
					queue.add(wildcardFlow);
				}
				dstToSources.get(flow.getDestination()).add(flow.getSource());
			}
		}

		System.out.println("Flows requiring general ETGs:");
		for (PolicyGroup destination : dstToSources.keySet()) {
			System.out.println("* -> " + destination.toString());
		}

		logger.debug("Need to generate " + queue.size() + " ETGs");
//		addData2File("/home/yahui/arc", "icnp2", "Need to generate " + queue.size() + " ETGs  ");
		

		// Generate flow-specific ETGs
		Map<Flow, ExtendedTopologyGraph> flowEtgs =
				new LinkedHashMap<Flow, ExtendedTopologyGraph>();
		long startTime = System.currentTimeMillis();
		if (settings.shouldParallelize()) {
			// Create a thread pool
			int numThreads = Runtime.getRuntime().availableProcessors();
			ExecutorService threadPool = Executors.newFixedThreadPool(
					numThreads);

			// Start a VerificationTask for each thread
			List<Future<Map<Flow,ExtendedTopologyGraph>>> futures =
					new ArrayList<Future<Map<Flow,ExtendedTopologyGraph>>>(
							numThreads);
			for (int t = 0; t < numThreads; t++) {
				ConstructTask task = new ConstructTask(baseEtg, queue, dstToSources);
				futures.add(threadPool.submit(task));
			}

			// Get the results from each thread
			try {
				for (Future<Map<Flow,ExtendedTopologyGraph>> future : futures) {
					// Get the result from the thread, waiting for the thread to
					// complete, if necessary
					Map<Flow,ExtendedTopologyGraph> result = future.get();
					flowEtgs.putAll(result);
				}
			}
			catch (Exception exception) {
				throw new GeneratorException("Generation task failed",
						exception);
			}
			finally {
				threadPool.shutdown();
			}
		}
		else {
			while (!queue.isEmpty()) {
				Flow flow = queue.remove();
				ExtendedTopologyGraph flowEtg =
						(ExtendedTopologyGraph)baseEtg.clone();
				if (flow.hasWildcardSource()) {
					List<PolicyGroup> sources = dstToSources.get(flow.getDestination());
					flowEtg.customize(flow, sources);
					for (PolicyGroup source : sources) {
						Flow tmpFlow = new Flow(source, flow.getDestination());
						flowEtgs.put(tmpFlow, flowEtg);
					}
				} else {
					flowEtg.customize(flow);
					flowEtgs.put(flow, flowEtg);
				}
			}
		}
		

		long endTime = System.currentTimeMillis();
		System.out.println("TIME: flowETGs "+(endTime - startTime)+" ms");
//		addData2File("/home/yahui/arc", "icnp2","   TIME: flowETGs "+(endTime - startTime)+" ms    ");

		return flowEtgs;
	}

	/**
	 * Given a flow, and a list of network devices, check if the flow contains any ACL blocking its traffic class
	 */
	private static boolean flowNeedsCustomEtg(Flow flow, List<Device> devices) {
		for (Device device : devices) {
			for (Interface deviceIface: device.getInterfaces()) {
				if (deviceIface.hasPrefix()) {
					if ((flow.getDestination().contains(deviceIface.getPrefix()) &&
							checkIfIncomingIsBlocked(flow, deviceIface)) ||
							flow.getSource().contains(deviceIface.getPrefix()) &&
									checkIfOutgoingIsBlocked(flow, deviceIface)) {
						return true;
					}
				}
			}

			// FIXME: Also check for static routes
		}
		return false;
	}

	private static boolean checkIfIncomingIsBlocked(Flow flow, Interface deviceIface) {
		if (deviceIface.getIncomingFilter() != null) {
			Device device = deviceIface.getDevice();
			StandardAccessList stdAcl = device.getStandardAcl(
					deviceIface.getIncomingFilter());
			ExtendedAccessList extAcl = device.getExtendedAcl(
					deviceIface.getIncomingFilter());
			if ((stdAcl != null && flow.isBlocked(stdAcl))
					|| (extAcl != null && flow.isBlocked(extAcl))) {
				return true;
			}
		}
		return false;
	}

	private static boolean checkIfOutgoingIsBlocked(Flow flow, Interface deviceIface) {
		if (deviceIface.getOutgoingFilter() != null) {
			Device device = deviceIface.getDevice();
			StandardAccessList stdAcl = device.getStandardAcl(
					deviceIface.getOutgoingFilter());
			ExtendedAccessList extAcl = device.getExtendedAcl(
					deviceIface.getOutgoingFilter());
			if ((stdAcl != null && flow.isBlocked(stdAcl))
					|| (extAcl != null && flow.isBlocked(extAcl))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Serialize the ETGs.
	 * @param "serializedETGsFile" file where the serialized ETGs should be stored
	 * @param flowEtgs the ETGs for each flow
	 * @param settings
	 */
	private static void serializeETGs(Settings settings,
									  Map<Flow,? extends ExtendedTopologyGraph> flowEtgs) {
		Logger logger = settings.getLogger();
		logger.info("*** Serialize ETGs ***");
		try {
			FileOutputStream fileOut = new FileOutputStream(
					settings.getSerializedETGsFile());
			ObjectOutputStream objOut = new ObjectOutputStream(fileOut);
			for (ExtendedTopologyGraph flowEtg : flowEtgs.values()) {
				objOut.writeObject(flowEtg);
			}
			objOut.close();
			fileOut.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	
	  //lyh wirte num constaints
	  private static void addData2File(File file, String conent) {
	    BufferedWriter out = null;
	    try {
	      out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true)));
	      out.write(conent);
	    } catch (Exception e) {
	      e.printStackTrace();
	    } finally {
	      try {
	        out.close();
	      } catch (IOException e) {
	        e.printStackTrace();
	      }
	    }
	  }
	  
	  private static void addData2File(String filePath, String fileName, String data) {
	    File path = new File(filePath);
	    File file = new File(path, fileName);
	    addData2File(file, data);
	  }
	  
	
	
	private static List<Interface> convertPathInterface(List<DirectedEdge> edgePath) {
		// If no edgePath is provided, then return null
		if (null == edgePath) {
			return null;
		}
		List<Interface> devicePathInter = new ArrayList<Interface>();
		for (DirectedEdge edge : edgePath) {
			Device currentDevice = null;
			Interface interfaces =null;
			// Get the device based on the type of vertices in the edge
			if (edge.getDestination() instanceof ProcessVertex) {
				ProcessVertex destination = 
						(ProcessVertex)edge.getDestination();
				if (destination.getProcess() != null) {
//					interfaces =destination.getProcess().getInterfaces();
					currentDevice = destination.getProcess().getDevice();
				}
			} else if (edge.getDestination() instanceof InterfaceVertex) {
				
				InterfaceVertex destination = 
						(InterfaceVertex)edge.getDestination();
				interfaces=destination.getInterface();
//				if (destination.getInterface() != null) {
//					currentDevice = destination.getInterface().getDevice();
//					interfaces=destination.getInterface();
//				}
			}		

				devicePathInter.add(interfaces);
			
		}
		return devicePathInter;
		}
	
	
	/**
	 * Generate Cisco Virtual Internet Routing Lab (VIRL) file.
	 * @param settings
	 * @param rawConfigs raw device configurations
	 * @param deviceEtg device graph
	 */
	private static void generateVirl(Settings settings,
									 Map<String, String> rawConfigs, DeviceGraph deviceEtg) {
		Logger logger = settings.getLogger();
		logger.info("*** Generate VIRL ***");
		VirlConfigurationGenerator virlGenerator =
				new VirlConfigurationGenerator(logger);
		String virl = virlGenerator.toVirl(rawConfigs, deviceEtg);
		try {
			FileUtils.writeStringToFile(
					Paths.get(settings.getVirlFile()).toFile(), virl, false);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Generate graph files.
	 * @param settings
	 * @param baseEtg the base ETG
	 * @param topoEtg an ETG representing the layer-3 network topology
	 * @param flowEtgs the ETGs for each flow
	 */
	private static void generateGraphs(Settings settings,
									   ExtendedTopologyGraph baseEtg, InstanceGraph instanceEtg,
									   DeviceGraph topoEtg,
									   Map<Flow,? extends ExtendedTopologyGraph> flowEtgs) {
		Logger logger = settings.getLogger();

		logger.info("***Generate Graphs***");
		File graphFile;

		if (baseEtg != null) {
			graphFile = Paths.get(settings.getGraphsDirectory(),
					"base.gv").toFile();
			try {
				FileUtils.writeStringToFile(graphFile,
						baseEtg.toGraphviz(), false);
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

		if (instanceEtg != null) {
			graphFile = Paths.get(settings.getGraphsDirectory(),
					"instance.gv").toFile();
			try {
				FileUtils.writeStringToFile(graphFile,
						instanceEtg.toGraphviz(), false);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		addData2File("/home/yahui/arc", "icnp2","   Count: pysical links "+topoEtg.getEdgeCount()+"   ");
		
		if (topoEtg != null) {
			graphFile = Paths.get(settings.getGraphsDirectory(),
					"topo.gv").toFile();
			try {
				FileUtils.writeStringToFile(graphFile,
						topoEtg.toGraphviz(), false);
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

		if (flowEtgs != null) {
			for (ExtendedTopologyGraph flowEtg : flowEtgs.values()) {
				String flowSrcStartIp, flowSrcEndIp;
				if (flowEtg.getFlow().hasWildcardSource()) { // if a flow has no ACLs, route filters,...
					flowSrcStartIp = "*";
					flowSrcEndIp = "*";
				} else {
					flowSrcStartIp = flowEtg.getFlow().getSource().getStartIp().toString();
					flowSrcEndIp = flowEtg.getFlow().getSource().getEndIp().toString();
				}
				graphFile = Paths.get(settings.getGraphsDirectory(),
						String.format("%s-%s_%s-%s.gv",
						flowSrcStartIp,
						flowSrcEndIp,
						flowEtg.getFlow().getDestination().getStartIp(),
						flowEtg.getFlow().getDestination().getEndIp())
				).toFile();
				try {
					FileUtils.writeStringToFile(graphFile,
							flowEtg.toGraphviz(), false);
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Run verification tasks.
	 * @param settings settings
	 * @param flowEtgs the per-flow ETGs to use for verification
	 */
	private static Map<String, List<String>>  runVerificationTasks(Settings settings,
											 Map<Flow, ? extends ExtendedTopologyGraph> flowEtgs,
											 DeviceGraph deviceEtg) {

		// Verify currently blocked
		Map<Flow, Boolean> currentlyBlockedResults = null;
		if (settings.shouldVerifyCurrentlyBlocked()) {
			CurrentlyBlocked verifier = new CurrentlyBlocked(flowEtgs,settings);

			// Run verification
			long startTime = System.currentTimeMillis();
			currentlyBlockedResults = verifier.verifyAll(null);
			long endTime = System.currentTimeMillis();
			System.out.println("TIME: currentlyBlocked " + (endTime - startTime)
					+ " ms");

			// Output results
			if (!settings.shouldSummarizeVerificationResults()) {
				System.out.println("*** Currently Blocked ***");
				for (Entry<Flow, Boolean> result :
						currentlyBlockedResults.entrySet()) {
					System.out.println("\t" + result.getValue() + "\t"
							+ result.getKey());
				}
			}
		}


		// Prune ETGs
		if (settings.shouldPrune()) {
			long startTime = System.currentTimeMillis();
			for (ExtendedTopologyGraph flowEtg : flowEtgs.values()) {
				flowEtg.prune();
			}
			long endTime = System.currentTimeMillis();
			System.out.println("TIME: pruneETGs "+(endTime - startTime)+" ms");
		}

		System.out.println("lyh print traffic and links:");
		long startTime2 = System.currentTimeMillis();
		int trafficcount = 0;
		Map<String, List<String>> retCover = new TreeMap<String, List<String>>();
		for (Flow flow : flowEtgs.keySet()) {
			ExtendedTopologyGraph flowEtg = flowEtgs.get(flow);
			if (flowEtg != null) {
				trafficcount++;
				Vertex vertex = flowEtgs.get(flow).getFlowSourceVertex(flow.getSource());
				// //addData2File("/home/yahui/arc", "path", "traffic---from " +vertex+ " to
				// "+flowEtg.getFlowDestinationVertex()+"\n");
				KShortestPaths shortestPaths = new KShortestPaths(flowEtg.getGraph(), vertex, 2);
				List<GraphPath> paths = shortestPaths.getPaths(flowEtg.getFlowDestinationVertex());
				List<List<DirectedEdge>> etgEdgePaths = new ArrayList<List<DirectedEdge>>();
				if (paths != null) {
					double minWeight = paths.get(0).getWeight();
					for (GraphPath path : paths) {
						if (path.getWeight() > minWeight) {
							break;
						}
						etgEdgePaths.add(path.getEdgeList());
					}
				}
				List<List<Device>> etgPaths = new ArrayList<List<Device>>();
				for (List<DirectedEdge> etgEdgePath : etgEdgePaths) {
					// List<Device> etgPath = convertPath(etgEdgePath);
//					 //addData2File("/home/yahui/arc", "path", "paths---" + etgEdgePath+"\n");
					String keytmp = vertex.getName() + flowEtg.getFlowDestinationVertex().getName();
					List<String> need1=new ArrayList<String>();
					for(DirectedEdge dDirectedEdge: etgEdgePath) {
						need1.add(dDirectedEdge.getName());
					}
					retCover.put(keytmp, need1);
				}
			}
		}
		SetCoverage setCoverage = new SetCoverage();
		List<String> needCover=new ArrayList<String>();
		needCover=setCoverage.sumLinks(retCover);
//	  	for(String no : needCover) {
//    		System.out.print(no+" ");
//    		//addData2File("/home/yahui/arc", "links", no+",   \n");
//    		
//    	}
		Map<String, List<String>> newCover = new TreeMap<String, List<String>>();
    	newCover=setCoverage.lsitCoverage(needCover, retCover);
    	int testPackets =0;
    	for (Map.Entry<String,List<String>> coverEntry : newCover.entrySet()){
    		testPackets++;
    	}
    	addData2File("/home/yahui/arc", "icnp2", " Count Choosed test packets (classes) " + testPackets+"    ");
		
    	long endTime2 = System.currentTimeMillis();
		System.out.println("TIME: Test packets generation  "+(endTime2 - startTime2)+" ms");
		addData2File("/home/yahui/arc", "icnp2","     TIME: Test packets generation "+(endTime2 - startTime2)+" ms    ");
		addData2File("/home/yahui/arc", "icnp2", " Count traffic classes " + trafficcount+"\n");
		return newCover;
	}

	
	
	private static Map<String, List<String>> runVerificationTasks2(Settings settings, ExtendedTopologyGraph baseEtg,
			Map<Flow, ? extends ExtendedTopologyGraph> flowEtgs, DeviceGraph deviceEtg,
			Map<Flow, ? extends ExtendedTopologyGraph> flowEtgs2, DeviceGraph deviceEtg2) {
		System.out.println("---table1---"+flowEtgs.size()+"  --table2-- "+flowEtgs2.size());
		// Prune ETGs
		if (settings.shouldPrune()) {
			long startTime = System.currentTimeMillis();
			for (ExtendedTopologyGraph flowEtg : flowEtgs.values()) {
				flowEtg.prune();
			}
			long endTime = System.currentTimeMillis();
			System.out.println("TIME: pruneETGs " + (endTime - startTime) + " ms");
		}

		System.out.println("Start compute min-set-cover for table 1");
		long startTime2 = System.currentTimeMillis();
		int trafficcount = 0;
		Map<String, List<String>> retCover = new TreeMap<String, List<String>>();
		for (Flow flow : flowEtgs.keySet()) {
			ExtendedTopologyGraph flowEtg = flowEtgs.get(flow);
			if (flowEtg != null) {
				trafficcount++;
				Vertex vertex = flowEtgs.get(flow).getFlowSourceVertex(flow.getSource());
				KShortestPaths shortestPaths = new KShortestPaths(flowEtg.getGraph(), vertex, 2);
				List<GraphPath> paths = shortestPaths.getPaths(flowEtg.getFlowDestinationVertex());
				List<List<DirectedEdge>> etgEdgePaths = new ArrayList<List<DirectedEdge>>();
				if (paths != null) {
					double minWeight = paths.get(0).getWeight();
					for (GraphPath path : paths) {
						if (path.getWeight() > minWeight) {
							break;
						}
						etgEdgePaths.add(path.getEdgeList());
					}
				}
				for (List<DirectedEdge> etgEdgePath : etgEdgePaths) {
					String keytmp = vertex.getName() + flowEtg.getFlowDestinationVertex().getName();
					List<String> need1=new ArrayList<String>();
					for(DirectedEdge dDirectedEdge: etgEdgePath) {
						if (dDirectedEdge.getSourceInterface()!=null && dDirectedEdge.getDestinationInterface()!=null )
							{need1.add(dDirectedEdge.toString());
							}

					}
					if(!need1.isEmpty())
					retCover.put(keytmp, need1);
				}
			}
		}

		System.out.println("the size of table 1 is "+retCover.size());
		Map<String, List<String>> retCover3 = new TreeMap<String, List<String>>();
		for (Flow flow : flowEtgs2.keySet()) {
			ExtendedTopologyGraph flowEtg = flowEtgs2.get(flow);
			if (flowEtg != null) {
				trafficcount++;
				Vertex vertex = flowEtgs2.get(flow).getFlowSourceVertex(flow.getSource());
				KShortestPaths shortestPaths = new KShortestPaths(flowEtg.getGraph(), vertex, 2);
				List<GraphPath> paths = shortestPaths.getPaths(flowEtg.getFlowDestinationVertex());
				List<List<DirectedEdge>> etgEdgePaths = new ArrayList<List<DirectedEdge>>();
				if (paths != null) {
					double minWeight = paths.get(0).getWeight();
					for (GraphPath path : paths) {
						if (path.getWeight() > minWeight) {
							break;
						}
						etgEdgePaths.add(path.getEdgeList());
					}
				}
				for (List<DirectedEdge> etgEdgePath : etgEdgePaths) {
					String keytmp = vertex.getName() + flowEtg.getFlowDestinationVertex().getName();
					List<String> need1=new ArrayList<String>();
					for(DirectedEdge dDirectedEdge: etgEdgePath) {
						if (dDirectedEdge.getSourceInterface()!=null && dDirectedEdge.getDestinationInterface()!=null )
							{need1.add(dDirectedEdge.toString());
							}

					}
					if(!need1.isEmpty())
					retCover3.put(keytmp, need1);
				}
			}
		}
		System.out.println("the size of table 2 is "+retCover3.size());
		
		SetCoverage setCoverage = new SetCoverage();
		List<List<String>> tmps = new ArrayList<List<String>>();
		List<String> tmp = new ArrayList<String>();
		tmps=setCoverage.compareset(retCover3,retCover);//different traffic classes
		//write something to rebuild graphs, write number and time;
		
		
		Set <Flow> flowsr= new HashSet<Flow> ();
		tmp = tmps.get(0);
		System.out.println(tmp.size()+ " tmp 0 lyh print traffic and links:second");
//		long endTime0= System.currentTimeMillis();
		if (!tmp.isEmpty()) {
			for (String adds : tmp) {
				Iterator<Flow> it = flowEtgs2.keySet().iterator();
				while (it.hasNext()) {
					Flow entry = it.next();
					ExtendedTopologyGraph flowEtg = flowEtgs.get(entry);
					if (flowEtg != null) {
						String keytmp1= flowEtgs.get(entry).getFlowSourceVertex(entry.getSource()).getName();
						String keytmp = flowEtg.getFlowDestinationVertex().getName();
						if(adds.contains(keytmp) ||adds.contains(keytmp1)) {
							flowsr.add(entry);
							break;
						}
					}
				}
			}
		}
		tmp = tmps.get(2);
		System.out.println(" add entries for new table   "+tmp.size());
		if (!tmp.isEmpty()) {
			for (String adds : tmp) {
				System.out.println("finish tmp 2  "+adds);
				Iterator<Flow> it =  flowEtgs2.keySet().iterator();
				while (it.hasNext()) {
					Flow entry = it.next();
					ExtendedTopologyGraph flowEtg = flowEtgs.get(entry);
					String keytmp1= flowEtgs.get(entry).getFlowSourceVertex(entry.getSource()).getName();
					String keytmp = flowEtg.getFlowDestinationVertex().getName();
					if(adds.contains(keytmp) ||adds.contains(keytmp1)) {
						flowsr.add(entry);
						break;
					}
				}
			}
		}
		
		System.out.println("related flowsrs"+flowsr.size());
		long endTime0= System.currentTimeMillis();
		for(Flow flow  : flowsr) {
			ExtendedTopologyGraph flowEtg =
					(ExtendedTopologyGraph)baseEtg.clone();
				flowEtg.customize(flow);
		}
		long endTime00= System.currentTimeMillis();
		System.out.println("finish update ETGs"+"     TIME: Update ETGs graph   " + (endTime00 - endTime0) + " ms    ");
		addData2File("/home/yahui/arc", "icnp2","     TIME: Update ETGs graph   " + (endTime00 - endTime0) + " ms    ");	
//		System.out.println("TIME: reachability-to-ports table generation  " + (endTime2 - startTime2) + " ms");
		
		List<String> needCover = new ArrayList<String>();
		needCover = setCoverage.sumLinks(retCover);
		Map<String, List<String>> newCover = new TreeMap<String, List<String>>();
		newCover = setCoverage.lsitCoverage(needCover, retCover);// old test packets
		System.out.println("oldCover.size()  "+newCover.size());
		addData2File("/home/yahui/arc", "icnp2",  "  oldCover.size()  "+newCover.size());
		newCover = setCoverage.lsitCoverage(needCover, retCover3);// old test packets
		System.out.println("newCover.size()  "+newCover.size());
		addData2File("/home/yahui/arc", "icnp2",  "   newCover.size()  "+newCover.size());
		
		long endTime2 = System.currentTimeMillis();
		newCover=setCoverage.updateCoverage(tmps,newCover, retCover3, retCover);//retCover is old, compute new entires
		System.out.println("newCover.size()  "+newCover.size());
		int testPackets = 0;
		for (Map.Entry<String, List<String>> coverEntry : newCover.entrySet()) {
			testPackets++;
		}
		System.out.print("finish all");
		addData2File("/home/yahui/arc", "icnp2", " New Count Updated  packets (classes) " + testPackets + "    ");
		long endTime3 = System.currentTimeMillis();
		System.out.println("TIME: Updated packets generation  " + (endTime3 - endTime2) + " ms");
		addData2File("/home/yahui/arc", "icnp2",
				"     TIME: Updated  packets generation " + (endTime3 - endTime2) + " ms    "+"\n");
		return newCover;
	}
}
