package edu.wisc.cs.arc.configs;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Vrf;

import edu.wisc.cs.arc.Logger;
import edu.wisc.cs.arc.Settings;
import edu.wisc.cs.arc.Settingsnew;
import edu.wisc.cs.arc.graphs.Device;
import edu.wisc.cs.arc.graphs.DeviceVertex;
import edu.wisc.cs.arc.graphs.DirectedEdge.EdgeType;
import edu.wisc.cs.arc.graphs.PolicyGroup;
import edu.wisc.cs.arc.graphs.Vertex.VertexType;
import edu.wisc.cs.arc.policies.PolicyException;
import edu.wisc.cs.arc.repair.VirtualDirectedEdge;
import edu.wisc.cs.arc.repair.graph.ConfigModification;

/**
 * Perform tasks related to configurations.
 * 
 * @author Aaron Gember-Jacobson (agemberjacobson@colgate.edu)
 */
public class ConfigurationTasks {

	/**
	 * Load configurations from the configs directory specified in settings.
	 * 
	 * @param settings
	 * @return configuration files
	 */
	public static Map<String, Config> loadConfigurations(Settings settings) {
		Logger logger = settings.getLogger();
		logger.info("*** Reading configuration files ***");

		// Get list of configuration files
		File configsPath = Paths.get(settings.getConfigsDirectory()).toFile();
		File[] configFilePaths = configsPath.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return !name.startsWith(".") && !name.endsWith(".csv");
			}
		});
		if (configFilePaths == null) {
			throw new ConfigurationException("Error reading configs directory");
		}

		// Load all configuration files from directory
		Map<String, Config> configs = new LinkedHashMap<String, Config>();
		Arrays.sort(configFilePaths);
		for (File file : configFilePaths) {
			// Load configuration
			Config config;
			try {
				config = new Config(file, logger);
			} catch (IOException e) {
				throw new ConfigurationException("Failed to read config " + file.getName(), e);
			} catch (ConfigurationException e) {
				throw new ConfigurationException("File=" + file.getName(), e);
			}

			configs.put(config.getHostname(), config);
		}

		logger.debug("COUNT: configFiles " + configs.size());
		return configs;
	}

	public static Map<String, Config> loadConfigurations2(Settingsnew settings) {
		Logger logger = settings.getLogger();
		logger.info("*** Reading configuration2 files ***");

		// Get list of configuration files
		File configsPath = Paths.get(settings.getConfigsDirection2()).toFile();
		logger.info("***+" + settings.getConfigsDirection2().toString());
		File[] configFilePaths = configsPath.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return !name.startsWith(".") && !name.endsWith(".csv");
			}
		});
		if (configFilePaths == null) {
			throw new ConfigurationException("Error reading configs directory");
		}

		// Load all configuration files from directory
		Map<String, Config> configs = new LinkedHashMap<String, Config>();
		Arrays.sort(configFilePaths);
		for (File file : configFilePaths) {
			// Load configuration
			Config config;
			try {
				config = new Config(file, logger);
			} catch (IOException e) {
				throw new ConfigurationException("Failed to read config " + file.getName(), e);
			} catch (ConfigurationException e) {
				throw new ConfigurationException("File=" + file.getName(), e);
			}

			configs.put(config.getHostname(), config);
		}

		logger.debug("COUNT: configFiles " + configs.size());
		logger.info("COUNT: config2Files " + configs.size());
		return configs;
	}

	/**
	 * Filter and anonymize configurations based on settings.
	 * 
	 * @param settings
	 * @param configs
	 *            configurations for devices
	 */
	public static void filterAndAnonymizeConfigurations(Settings settings, Map<String, Config> configs) {
		// Exclude non-routers, if requested
		if (settings.shouldExcludeNonRouters()) {
			List<String> devicesToExclude = new ArrayList<String>();

			// Check each device configuration to see if it contains a router
			// stanza
			for (Entry<String, Config> configEntry : configs.entrySet()) {
				Configuration genericConfig = configEntry.getValue().getGenericConfiguration();
				boolean hasRoutingProcess = false;
				for (Vrf vrf : genericConfig.getVrfs().values()) {
					if (vrf.getBgpProcess() != null || vrf.getOspfProcess() != null
							|| vrf.getStaticRoutes().size() > 0) {
						hasRoutingProcess = true;
					}
				}
				if (!hasRoutingProcess) {
					devicesToExclude.add(configEntry.getKey());
				}
			}

			// Remove devices without a router stanza
			for (String deviceToExclude : devicesToExclude) {
				configs.remove(deviceToExclude);
			}
		}

		// Anonymize device names, if requested
		if (settings.shouldAnonymize()) {
			Map<String, Config> anonConfigs = new LinkedHashMap<String, Config>();
			int i = 0;
			for (Entry<String, Config> entry : configs.entrySet()) {
				anonConfigs.put("dev" + i, entry.getValue());
				i++;
			}
			configs = anonConfigs;
		}
	}

	/**
	 * Extract policy groups from configurations
	 * 
	 * @param settings
	 * @param configs
	 *            configurations
	 * @return non-overlapping policy groups
	 */
	public static List<PolicyGroup> extractPolicyGroups(Settings settings, Map<String, Config> configs) {
		Logger logger = settings.getLogger();

		// Extract policy groups
		logger.info("*** Extract Policy Groups ***");
		List<PolicyGroup> groups = PolicyGroup.extract(configs);
		logger.debug("Unfiltered (and overlapping):");
		// remove by lyh
		// for (PolicyGroup group : groups) {
		// System.out.println("\t" + group.toString() + (group.isInternal() ? "
		// INTERNAL" : " EXTERNAL"));
		// logger.debug("\t" + group.toString() + (group.isInternal() ? " INTERNAL" : "
		// EXTERNAL"));
		// }

		List<PolicyGroup> toRemove = new ArrayList<PolicyGroup>();
		for (PolicyGroup group : groups) {
			// Remove policy groups that are external, if requested
			if (settings.shouldExcludeExternalFlows() && !group.isInternal()) {
				toRemove.add(group);
				continue;
			}
		}
		groups.removeAll(toRemove);

		// Add entire address space
		if (settings.shouldIncludeEntireFlowspace()) {
			groups.add(new PolicyGroup(new Ip("0.0.0.0"), new Ip("255.255.255.255")));
		}

		// Compute non-overlapping policy groups
		groups = PolicyGroup.getNonOverlapping(groups);

		// Filter policy groups
		toRemove = new ArrayList<PolicyGroup>();
		for (PolicyGroup group : groups) {
			// Remove policy groups that are external, if requested
			if (settings.shouldExcludeExternalFlows() && !group.isInternal()) {
				toRemove.add(group);
				continue;
			}

			// Remove policy groups that are too small, if requested
			if (group.getEndIp().asLong() - group.getStartIp().asLong() < settings.getMinPolicyGroupsSize()) {
				toRemove.add(group);
				continue;
			}

			// Anonymize policy groups, if requested
			if (settings.shouldAnonymize()) {
				group.makeAnonymous();
			}
		}
		groups.removeAll(toRemove);

		// Output policy groups
		List<PolicyGroup> sortedGroups = new ArrayList<PolicyGroup>(groups);
		Collections.sort(sortedGroups);
		logger.info("RESULTS_START: PolicyGroups");
		// for (PolicyGroup group : sortedGroups) {
		// logger.info("\t" + group.toString()
		// + (group.isInternal() ? " INTERNAL" : " EXTERNAL"));
		// }
		logger.info("RESULTS_END: PolicyGroups");
		logger.info("COUNT: policyGroups " + groups.size());

		return sortedGroups;
	}

	/**
	 * Serialize the configs.
	 * 
	 * @param settings
	 * @param devices
	 *            the configs for each device
	 */
	public static void serializeConfigs(Settings settings, Collection<Device> devices) {
		Logger logger = settings.getLogger();
		logger.info("*** Serialize Configs ***");
		try {
			FileOutputStream fileOut = new FileOutputStream(settings.getSerializedConfigsFile());
			ObjectOutputStream objOut = new ObjectOutputStream(fileOut);
			for (Device device : devices) {
				objOut.writeObject(device);
			}
			objOut.close();
			fileOut.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Compare the configs.
	 * 
	 * @param settings
	 * @param devices
	 *            the device configurations
	 */
	public static void compareConfigs(Settings settings, Collection<Device> devices) {
		Logger logger = settings.getLogger();
		logger.info("*** Compare Configs ***");

		// Load comparison devices
		Map<String, Device> comparisonDevices = new LinkedHashMap<String, Device>();
		try {
			FileInputStream fileIn = new FileInputStream(settings.getCompareConfigsFile());
			ObjectInputStream objIn = new ObjectInputStream(fileIn);
			try {
				while (true) {
					Object obj = objIn.readObject();
					if (obj instanceof Device) {
						Device device = (Device) obj;
						comparisonDevices.put(device.getName(), device);
					} else {
						break;
					}
				}
			} catch (EOFException e) {

			}
			objIn.close();
			fileIn.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new PolicyException(e.getMessage());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			throw new PolicyException(e.getMessage());
		}
		logger.info("COUNT: comparisonDevices " + comparisonDevices.size());

		// Compare each Device
		for (Device device : devices) {
			Device compareDevice = comparisonDevices.get(device.getName());
			if (null == compareDevice) {
				logger.info(device.getName());
				logger.info("\tADD device");
				continue;
			}
			ConfigComparer comparer = new ConfigComparer(compareDevice, device);
			List<ConfigModification> differences = comparer.getDifferences();
			if (differences.size() > 0) {
				logger.info(device.getName());
				for (ConfigModification mod : differences) {
					logger.info("\t" + mod.toString());
				}
			}
		}

		// FIXME: Check for removed devices
	}

	// add by lyh
	public static int compareConfigsType(Map<Integer, Map<String, List<ConfigModification>>> allConfigModifications) {
		int a = -1;// all types;
		// TODO: classify changed configs' types
		if (allConfigModifications.containsKey(4) || allConfigModifications.containsKey(5)) {
			a = 0;
			return a;
		}
		if (allConfigModifications.containsKey(3)) {
			if (allConfigModifications.get(3).size() > 0) {
				// routing process etc.
				a = 0;
				return a;
			}
		}
		if (allConfigModifications.containsKey(2)) {
			if (allConfigModifications.get(2).size() > 0) {
				// routemap, key :2
				if (allConfigModifications.get(1).size() > 0) {
					// acl key: 1;
					a = 3;// abstract configs
					return a;
				} else {
					// only map
					for (Entry<String, List<ConfigModification>> entry : allConfigModifications.get(2).entrySet()) {
						System.out.println(
								"0606: device name------~~~" + entry.getKey() + "----------------" + entry.getValue());
					}
					a = 1;
					return a;
				}
			}
		}
		if (allConfigModifications.get(1).size() > 0) {
			a = 2;// only acl
			for (Entry<String, List<ConfigModification>> entry : allConfigModifications.get(1).entrySet()) {
				System.out.println("device name------~~~" + entry.getKey() + "----------------" + entry.getValue());
			}
			return a;
		}

		return a;
	}

	//add by lyh for abs compare
	public static int compareAbConfigsType(Map<Integer, Map<String, List<ConfigModification>>> allConfigModifications) {
		int a = -1;// all types;
		// TODO: classify changed configs' types
		if (allConfigModifications.containsKey(2)) {
			if (allConfigModifications.get(2).size() > 0) {
				// routemap, key :2
				if (allConfigModifications.get(1).size() > 0) {
					// acl key: 1;
					a = 3;// abstract configs
					return a;
				} else {
					// only map
					for (Entry<String, List<ConfigModification>> entry : allConfigModifications.get(2).entrySet()) {
						System.out.println(
								"0606: device name------~~~" + entry.getKey() + "----------------" + entry.getValue());
					}
					a = 1;
					return a;
				}
			}
		}
		if (allConfigModifications.get(1).size() > 0) {
			a = 2;// only acl
			for (Entry<String, List<ConfigModification>> entry : allConfigModifications.get(1).entrySet()) {
				System.out.println("device name------~~~" + entry.getKey() + "----------------" + entry.getValue());
			}
			return a;
		}

		return a;
	}
	
	
	
	// change by lyh
	/**
	 * Compare the configs.
	 * 
	 * @param settings
	 * @param devices
	 *            the device configurations
	 */
	public static Map<Integer, Map<String, List<ConfigModification>>> compareAllConfigs(
			Map<String, Device> comparisonDevices, Map<String, Device> comparisonDevices2) {

		Map<String, List<ConfigModification>> allConfigModifications1 = new HashMap<>();
		Map<String, List<ConfigModification>> allConfigModifications2 = new HashMap<>();
		Map<String, List<ConfigModification>> allConfigModifications3 = new HashMap<>();
		Map<String, List<ConfigModification>> allConfigModifications4 = new HashMap<>();
		Map<String, List<ConfigModification>> allConfigModifications5 = new HashMap<>();
		// device name and configurations

		Map<Integer, Map<String, List<ConfigModification>>> alls = new HashMap<>();

		// Compare each Device
		Collection<Device> devices = comparisonDevices2.values();
		for (Device device : devices) {
			Device compareDevice = comparisonDevices.get(device.getName());
			if (null == compareDevice) {
				System.out.println(device.getName());
				System.out.println("\tADD device");
				allConfigModifications4.put(device.getName(), null);
				alls.put(4, allConfigModifications4);
				continue;
			}

			ConfigComparer comparer = new ConfigComparer(compareDevice, device);
			// System.out.println("====================================="+device.getName());
			Map<Integer, List<ConfigModification>> differences = comparer.getDifferences2();
			if (differences.get(1).size() > 0) {
				allConfigModifications1.put(device.getName(), differences.get(1));

			}
			if (differences.get(3).size() > 0) {
				allConfigModifications3.put(device.getName(), differences.get(3));

			}
			if (differences.get(2).size() > 0) {
				allConfigModifications2.put(device.getName(), differences.get(2));

			}
			// for(Entry<Integer, List<ConfigModification>> entry : differences.entrySet())
			// {
			// System.out.println("name------"+entry.getKey()+"----------------"+entry.getValue());
			// }

		}
		// FIXME: Check for removed devices
		Collection<Device> devicest = comparisonDevices.values();
		for (Device device : devicest) {
			Device compareDevice = comparisonDevices2.get(device.getName());
			if (null == compareDevice) {
				System.out.println(device.getName());
				System.out.println("\tREMOVE device");
				allConfigModifications5.put(device.getName(), null);
				alls.put(5, allConfigModifications5);
				continue;
			}
		}
		alls.put(1, allConfigModifications1);
		alls.put(3, allConfigModifications3);
		alls.put(2, allConfigModifications2);
		// for(Entry <Integer, Map<String, List<ConfigModification>>> entry :
		// alls.entrySet()) {
		// System.out.println("name~~~~~~~~~~~~~~~"+entry.getKey());
		// for(Entry<String, List<ConfigModification>> entry2 :
		// alls.get(entry.getKey()).entrySet()) {
		// System.out.println("name~~~~~~~~~~~~~~~"+entry2.getKey()+"~~~~~~~~~~~~~"+entry2.getValue());
		// }
		// }
		return alls;

	}

	/**
	 * Obtain simplified versions of configuration files
	 * 
	 * @param settings
	 * @param configs
	 *            configurations to simplify
	 */
	public static void simplifyConfigs(Settings settings, Map<String, Config> configs) {
		Logger logger = settings.getLogger();
		logger.info("*** Simplifying Configs ***");

		for (Entry<String, Config> entry : configs.entrySet()) {
			// Simplify config
			ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
			CiscoConfigSimplifier simplifier = new CiscoConfigSimplifier(entry.getValue(),
					new PrintStream(byteArrayOutput));
			simplifier.simplify();

			Config newConfig = new Config(byteArrayOutput.toString(), entry.getKey(), logger);
			configs.put(newConfig.getHostname(), newConfig);
		}
	}

	public static void saveSimplifiedConfig(Settings settings, Map<String, Config> configs) {
		Logger logger = settings.getLogger();
		logger.info("*** Saving Simplified Configs ***");
		for (Entry<String, Config> entry : configs.entrySet()) {
			String hostname = entry.getKey();
			String configText = entry.getValue().getText();
			File file = Paths.get(settings.getSimpleConfigsSaveFile(), hostname + ".cfg").toFile();
			try {
				FileUtils.writeStringToFile(file, configText);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Load list of waypoint edges from the waypoints file specified in settings.
	 * 
	 * @param settings
	 * @param devices
	 *            list of devices in the network
	 * @return list of waypoint edges
	 */
	public static List<VirtualDirectedEdge<DeviceVertex>> loadWaypointEdges(Settings settings,
			Map<String, Device> devices) {
		// Load waypoints list from file
		File waypointsFile = Paths.get(settings.getWaypointsFile()).toFile();
		List<String> waypointsData = null;
		try {
			waypointsData = FileUtils.readLines(waypointsFile);
		} catch (IOException e) {
			settings.getLogger().error("Could not load waypoints file: " + settings.getWaypointsFile());
			return null;
		}

		// Parse waypoint list
		Map<String, DeviceVertex> deviceVertices = new LinkedHashMap<String, DeviceVertex>();
		List<VirtualDirectedEdge<DeviceVertex>> waypointEdges = new ArrayList<VirtualDirectedEdge<DeviceVertex>>();
		for (String line : waypointsData) {
			// Get devices
			String deviceNames[] = line.split(",");
			if (deviceNames.length != 2) {
				settings.getLogger().error("Invalid line in waypoints file: " + line);
				return null;
			}

			// Get device vertices
			DeviceVertex vertices[] = new DeviceVertex[deviceNames.length];
			for (int i = 0; i < deviceNames.length; i++) {
				if (!devices.containsKey(deviceNames[i])) {
					settings.getLogger()
							.error("Ignoring waypoint edge " + line + " with unknown device " + deviceNames[i]);
					break;
				}
				vertices[i] = deviceVertices.get(deviceNames[i]);
				if (null == vertices[i]) {
					vertices[i] = new DeviceVertex(devices.get(deviceNames[i]));
					deviceVertices.put(deviceNames[i], vertices[i]);
				}
			}
			if (null == vertices[0] || null == vertices[1]) {
				continue;
			}

			// Add waypoint edge
			waypointEdges.add(new VirtualDirectedEdge<DeviceVertex>(vertices[0], vertices[1], EdgeType.INTER_DEVICE));
		}

		return waypointEdges;
	}
}
