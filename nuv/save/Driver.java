package edu.wisc.cs.arc;

import edu.wisc.cs.arc.configs.Config;
import edu.wisc.cs.arc.configs.ConfigurationTasks;
import edu.wisc.cs.arc.graphs.*;
import edu.wisc.cs.arc.graphs.Process.ProcessType;
import edu.wisc.cs.arc.minesweeper.MinesweeperTasks;
import edu.wisc.cs.arc.policies.Policy;
import edu.wisc.cs.arc.policies.Policy.PolicyType;
import edu.wisc.cs.arc.policies.PolicyEditor;
import edu.wisc.cs.arc.policies.PolicyFile;
import edu.wisc.cs.arc.repair.*;
import edu.wisc.cs.arc.repair.graph.*;
import edu.wisc.cs.arc.repair.graph.configwriters.CiscoIOSConfigWriter;
import edu.wisc.cs.arc.testgraphs.Graph;
import edu.wisc.cs.arc.verifiers.VerificationTasks;
import edu.wisc.cs.arc.virl.VirlConfigurationGenerator;

import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;

/**
 * Starts the ETG generator.
 * @author Aaron Gember-Jacobson (agember@cs.wisc.edu)
 */
public class Driver {
	public static void main(String[] args) {
		// Load settings
		Settings settings = null;
		try {
			settings = new Settings(args);
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			System.err.println("Run with '-" + Settings.HELP + "' to show help");
			return;
		}
		Logger logger = settings.getLogger();
		logger.info("*** Settings ***");
		logger.info(settings.toString());

		// Edit/convert policies
        if (settings.shouldEditPolicies()) {
            PolicyEditor editor =
                    new PolicyEditor(settings.getPoliciesEditFile());
            editor.run();
            return;
        } else if (settings.shouldConvertPolicies()) {
            MinesweeperTasks.convertMinesweeperPolicies(settings);
            return;
        }

        // Load configurations
        Map<String, Config> configs =
                ConfigurationTasks.loadConfigurations(settings);

		// Filter and anonymize configurations
		if (settings.shouldAnonymize() || settings.shouldExcludeNonRouters()) {
		    ConfigurationTasks.filterAndAnonymizeConfigurations(settings,
		            configs);
		}
		if (0 == configs.size()) {
		    logger.fatal("No configurations (after filtering)");
		    System.exit(1);
		}

		// Simplify configurations
        if (settings.shouldSimplifyConfigs()){
            ConfigurationTasks.simplifyConfigs(settings, configs);
            if (settings.shouldSaveSimpleConfigs()){
                ConfigurationTasks.saveSimplifiedConfig(settings, configs);
            }
        }

        // Determine policy groups
        List<PolicyGroup> policyGroups = ConfigurationTasks.extractPolicyGroups(
                settings, configs);
        if (0 == policyGroups.size()) {
            logger.fatal("No policy groups");
            System.exit(1);
        }
        
        // Create devices from configurations
        Map<String, Device> devices = new LinkedHashMap<String, Device>();
        for (Entry<String, Config> entry : configs.entrySet()) {
            Device device = new Device(entry.getKey(),
                    entry.getValue().getGenericConfiguration(), logger);
            devices.put(device.getName(), device);
        }
        logger.info("COUNT: devices "+devices.size());
        logger.info("Devices:");
        for  (Device device : devices.values()) {
            logger.info("\t"+device.getName());
        }		
        
        // Generate file of policies to check with Minesweeper
		if (settings.shouldGenerateMinesweeperPolicies()) {
			MinesweeperTasks.generateMinesweeperPolicyList(settings, 
					policyGroups, devices.values());
		}
        
        // Exclude irrelevant static routes
        for (Device device : devices.values()) {
        	device.pruneStaticProcesses(policyGroups);
        }

        // Serialize configs
        if (settings.shouldSerializeConfigs()) {
            ConfigurationTasks.serializeConfigs(settings, devices.values());
        }

        // Compare configs
        if (settings.shouldCompareConfigs()) {
            ConfigurationTasks.compareConfigs(settings, devices.values());
        }

		// Compute token stats
		if (settings.shouldComputeTokenStats()) {
			Tokenizer tokenizer = new Tokenizer(settings);
			tokenizer.build();
			tokenizer.compareConfigs();
			return;
		}
		
        // Load waypoint edges
        List<VirtualDirectedEdge<DeviceVertex>> waypoints = null;
        if (settings.hasWaypoints()) {
        	waypoints = ConfigurationTasks.loadWaypointEdges(settings, devices);
        }

		// Genetic repair
		if (settings.shouldGeneticRepair()) {
			RepairTasks.geneticRepair(settings, configs, policyGroups);
			return;
		}

		// Generate device-based ETG
		logger.info("*** Generate device-based ETG ***");
		DeviceGraph deviceEtg = new DeviceGraph(devices.values(), settings, 
				waypoints);
        System.out.println("COUNT: deviceETGVertices "
                + deviceEtg.getVertexCount());
        System.out.println("COUNT: deviceETGEdges "
                + deviceEtg.getEdgeCount());

        // Generate VIRL
        if (settings.shouldGenerateVirl()) {
            VirlConfigurationGenerator.generateVirl(settings, configs,
                    deviceEtg);
        }

		// Create process-based ETG
		logger.info("*** Generate process-based ETG ***");
		ProcessGraph baseEtg = new ProcessGraph(deviceEtg, settings);
		logger.info("COUNT: baseETGVertices "
				+ baseEtg.getVertexCount());
		logger.info("COUNT: baseETGEdges "
				+ baseEtg.getEdgeCount());
		logger.info("COUNT: ospfProcesses "
				+ baseEtg.numberOfType(ProcessType.OSPF));
		logger.info("COUNT: bgpProcesses "
				+ baseEtg.numberOfType(ProcessType.BGP));
		logger.info("COUNT: staticProcesses "
				+ baseEtg.numberOfType(ProcessType.STATIC));

		// Generate Instance Graph
		logger.info("*** Generate instance-based ETG ***");
		InstanceGraph instanceEtg = baseEtg.getInstanceEtg();
		logger.info("COUNT: instanceETGVertices "
		        + instanceEtg.getVertexCount());
		logger.info("COUNT: instanceETGEdges "
				+ instanceEtg.getEdgeCount());
		logger.info("COUNT: ospfInstances "
				+ instanceEtg.numberOfType(ProcessType.OSPF));
		logger.info("COUNT: bgpInstances "
				+ instanceEtg.numberOfType(ProcessType.BGP));
		logger.info("COUNT: staticInstances "
				+ instanceEtg.numberOfType(ProcessType.STATIC));
		logger.info("PROP: instanceIsDag "
				+ !((InstanceGraph)instanceEtg).hasCycles());

		// Generate destination-based process graphs
		Map<PolicyGroup, ProcessGraph> destinationEtgs = null;
		if (settings.shouldGenerateDestinationETGs()) {
			destinationEtgs = EtgTasks.generateDestinationETGs(
			        settings, baseEtg, policyGroups);
		}

		// Generate flow-based process graphs
		Map<Flow, ProcessGraph> flowEtgs = null;
		if (settings.shouldGenerateFlowETGs()) {
			flowEtgs = EtgTasks.generateFlowETGs(settings, baseEtg,
			        policyGroups);
		    logger.info("COUNT: flows "+flowEtgs.size());
		}
		
        // Clean-up baseETG to be all-tcs ETG
		baseEtg.customize();

		// Generate graphviz
		if (settings.shouldGenerateGraphviz()) {
			EtgTasks.generateGraphviz(settings, baseEtg,
			        instanceEtg, deviceEtg, destinationEtgs, flowEtgs);
		}

		// Test Generated graphs
		if (settings.shouldTestGraphs() && flowEtgs != null){
			checkFlowETGs(settings, flowEtgs);
			return;
		}

		// Serialize ETGs
		if (settings.shouldSerializeETGs() && flowEtgs != null) {
			EtgTasks.serializeFlowETGs(settings, flowEtgs);
		}

		// Run verification tasks
		if (flowEtgs != null) {
			VerificationTasks.runVerifiers(settings, flowEtgs, deviceEtg);
		}

		//Check Policies File --invoked by using the option -checkpolicies <DIR>
		if (settings.shouldCheckPolicies()){
		    logger.info("*** Check policies ****");
		    Map<Flow, List<Policy>> policiesByFlow =
		            PolicyFile.loadPolicies(settings.getCheckPoliciesFile());
		    
		    // Output statistics for policies
			Map<PolicyType, Integer> policiesByType =
					new LinkedHashMap<PolicyType, Integer>();
			for (PolicyType policyType : PolicyType.values()) {
				policiesByType.put(policyType, 0);
			}
			List<PolicyGroup> dstsWithPolicies = new ArrayList<PolicyGroup>();
			int policyCount = 0;
			for (Entry<Flow, List<Policy>> entry : policiesByFlow.entrySet()){
				PolicyGroup dst = entry.getKey().getDestination();
				if (!dstsWithPolicies.contains(dst)) {
					dstsWithPolicies.add(dst);
				}
				List<Policy> policies = entry.getValue();
				for (Policy policy : policies) {
					policyCount++;
					policiesByType.put(policy.getType(), 
							policiesByType.get(policy.getType())+1);
				}
			}
			logger.info("COUNT: flowsWithPolicies " + policiesByFlow.size());
			logger.info("COUNT: destinationsWithPolicies " 
					+ dstsWithPolicies.size());
			logger.info("COUNT: policies " + policyCount);
			for (PolicyType policyType : PolicyType.values()) {
				logger.info("COUNT: policies" + policyType + " " + 
						policiesByType.get(policyType));
			}
			
			Map<Policy, List<DirectedEdge>> violations =
			        VerificationTasks.verifyPolicies(settings, policiesByFlow,
			                flowEtgs);
			
			// Output statistics for violations
			Map<PolicyType, Integer> violationsByType =
					new LinkedHashMap<PolicyType, Integer>();
			for (PolicyType policyType : PolicyType.values()) {
				violationsByType.put(policyType, 0);
			}
			List<PolicyGroup> dstsWithViolations = new ArrayList<PolicyGroup>();
			for (Policy violatedPolicy : violations.keySet()) {
			    logger.info("Violated: " + violatedPolicy);
			    violationsByType.put(violatedPolicy.getType(), 
			    		violationsByType.get(violatedPolicy.getType()) + 1);
			    PolicyGroup dst = 
			    		violatedPolicy.getTrafficClass().getDestination();
			    if (!dstsWithViolations.contains(dst)) {
			    	dstsWithViolations.add(dst);
			    }
			}
			logger.info("COUNT: policiesViolated " + violations.size());
			logger.info("COUNT: destinationsWithViolations " 
					+ dstsWithViolations.size());
			for (PolicyType policyType : PolicyType.values()) {
				logger.info("COUNT: violated" + policyType + " " 
						+ violationsByType.get(policyType));
			}

			// Nominate tokens for mutation
			if (settings.shouldLocalizeFaults()) {
				RepairTasks.localizeFaults(settings, configs, policyGroups,
			        violations);
			}
		}

		// Repair
		if (settings.shouldRepair() && flowEtgs != null
				&& destinationEtgs != null) {
			repair(settings, configs, deviceEtg, (Map<Flow, ProcessGraph>)flowEtgs,
					baseEtg, destinationEtgs);
		}

		// Translate
		if (settings.shouldExportRepairs()) {
			translate(settings, configs);
		}

		// Compare ETGs
		if (settings.shouldCompareETGs() && flowEtgs != null) {
			EtgTasks.compareETGs(settings,
			        (Map<Flow, ProcessGraph>)flowEtgs);
		}
	}

	/**
	 * Checks if generated ETGs conform to testcases.
	 * @param settings
	 * @param flowEtgs the generated ETGs
	 */
	private static void checkFlowETGs(Settings settings,
	        Map<Flow, ? extends ExtendedTopologyGraph> flowEtgs){
		Logger logger = settings.getLogger();
		String testDir = settings.getTestGraphsDirectory();
		File testFolder = new File(settings.getTestGraphsDirectory());

		if (!testFolder.isDirectory()){
			logger.error("Must provide a directory containing test graphs");
			return;
		}

		// Get list of test files
		String[] testGVs = testFolder.list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".gv");
            }
        });
		//create a hashmap from the genGVs array
		//***use this to find matching filenames in both directories
		if (testGVs == null){
			logger.error(testFolder.getPath()
			        + " does not contain any test graphs");
			return;
		}
		HashSet<String> testFiles = new HashSet<String>(Arrays.asList(testGVs));

		// Filter out non-flow graphs
		testFiles.remove(ExtendedTopologyGraph.GRAPHVIZ_FILENAME);
		testFiles.remove(DeviceGraph.GRAPHVIZ_FILENAME);
		testFiles.remove(InstanceGraph.GRAPHVIZ_FILENAME);
		int testCount = testFiles.size();

		// Check every ETG against its corresponding test graph
		int missingTest = 0;
		int failCount = 0;
		int passCount = 0;
		for (ExtendedTopologyGraph etg : flowEtgs.values()) {
		    // Make sure test graph exists
		    if (!testFiles.contains(etg.getGraphvizFilename())){
                missingTest++;
                logger.info("Cannot find test graph for " + etg.getFlow());
                continue;
            }

		    testFiles.remove(etg.getGraphvizFilename());

		    String testFilePath = testDir + "/" + etg.getGraphvizFilename();
		    Graph graphTest = Graph.gvToGraphObject(testFilePath);
		    Graph graphGen = Graph.etgToGraphObject(etg);
		    System.out.println("Checking " + etg.getFlow());
            if (!graphGen.equals(graphTest)) {
                failCount++;
                //System.out.println("Test graph\n" + graphTest.toString());
                //System.out.println("Generated graph\n" + graphGen.toString());
            }
            else {
                passCount++;
            }

            testFiles.remove(etg.getGraphvizFilename());
		}

		// Note which test graphs did not have a corresponding ETG
		int missingEtg = 0;
		for (String graphFile : testFiles) {
		    logger.info("Cannot find ETG for test graph " + graphFile);
		    missingEtg++;
		}

		int targetCount = flowEtgs.size();
		logger.info("Test results:");
		logger.info(passCount + "/" + targetCount + " passed") ;
		logger.info(failCount + "/" + targetCount + " failed");
		logger.info(missingTest + "/" + targetCount + " missing tests");
		logger.info(missingEtg + "/" + testCount + " missing ETGs");
	}
	/**
	 *Helper function for verifyPolicy(above)
	 *@param flowEtgs Map from Flow to ETG
	 *@param flow flow to be checked for in the KeySet of flowEtgs
	 *@return true if there is an entry corresponding to the flow in the flowEtgs map
	*/
	public static boolean flowExists(Map<Flow, ? extends ExtendedTopologyGraph> flowEtgs, Flow flow){
		if (!flowEtgs.containsKey(flow)){
			System.out.println("No ETG found for flow: " + flow.toString() + " in provided map.");
			return false;
		}
		return true;
	}

	/**
	 * Repair the network to satisfy a set of provided policies.
	 * @param settings settings
	 * @param configs
	 * @param deviceEtg the physical network topology
	 * @param flowEtgs the per-flow ETGs to repair
	 */
	private static void repair(Settings settings, Map<String, Config> configs, DeviceGraph deviceEtg,
							   Map<Flow, ProcessGraph> flowEtgs, ProcessGraph baseEtg,
							   Map<PolicyGroup, ProcessGraph> destinationEtgs) {
		Logger logger = settings.getLogger();

		// Load policies
		logger.info("*** Load Policies ***");
		Map<Flow, List<Policy>> policies =
				PolicyFile.loadPolicies(settings.getPoliciesRepairFile());
		int count = 0;
		for (List<Policy> policiesForFlow : policies.values()) {
			for (Policy policy : policiesForFlow) {
				logger.debug(policy.toString());
				count++;
			}
		}
		System.out.println("COUNT: policies " + count);

		// Make sure there are policies to satisfy
		if (0 == policies.size()) {
			return;
		}

		logger.info("*** Graph Modifications ***");
		GraphModifier modifier = null;
		switch(settings.getModifierAlgorithm()) {
		case MAXSMT_ALL_TCS:
		case MAXSMT_PER_TC:
		case MAXSMT_PER_PAIR:
		case MAXSMT_PER_DST:
		case MAXSMT_PER_SRC:
			modifier = 	new MaxSmtModifier(policies, deviceEtg,
					baseEtg, destinationEtgs, flowEtgs, settings);
			break;
		case ISOLATED:
			modifier = new IsolatedModifier(policies, deviceEtg,
					baseEtg, flowEtgs, settings);
			break;
		default:
			throw new RepairException("Unknown graph modifier algorithm: "
					+ settings.getModifierAlgorithm());
		}
		long startTime = System.currentTimeMillis();

		ConfigModificationGroup modifications =
				modifier.getModifications();
		if (null == modifications) {
			return;
		}
		modifications.setEtgs(flowEtgs, destinationEtgs, baseEtg);
		long endTime = System.currentTimeMillis();
		System.out.println("TIME: graphModification "+(endTime - startTime)
				+" ms");


		// Modify ETGs
//		for (Flow flow : modifications.getTcMods().keySet()) {
//			ProcessGraph etg = flowEtgs.get(flow);
//			List<GraphModification<ProcessVertex>> modsForFlow =
//					modifications.getTcMods().get(flow);
//			etg.modify(modsForFlow);
//		}

		// Modify Config files
		logger.info("*** Configuration Modifications ***");
		ConfigModifier configModifier = new SimpleConfigModifier(flowEtgs, modifications, settings);
        List<ModificationPair> configModifications = configModifier.getModifications();

        logger.info("*** Saving Modifications ***");
        String filepath = settings.getSerializedModificationsFile();
		ModificationsFile.saveModifications(configModifications, filepath);
		logger.info("Modifications saved to " + filepath);

		// Serialize modified ETGs, if requested
		if (settings.shouldSerializeETGs()) {
			EtgTasks.serializeFlowETGs(settings, flowEtgs);
		}
	}

	/**
	 * Generate and save repaired router configuration files.
	 * @param settings settings
	 * @param configs
	 */
	private static void translate(Settings settings, Map<String, Config> configs) {
		Logger logger = Logger.getInstance();
		logger.info("*** Loading Modifications ***");
		String filepath = settings.getSerializedModificationsFile();
		List<ModificationPair> configModifications = ModificationsFile.loadModifications(filepath);

		// Determine config syntax (assume Cisco IOS for now)
		logger.info("Writing configurations");

		ConfigWriter configWriter = new CiscoIOSConfigWriter(configs);

		String exportDirectory = "";
		if (settings.shouldExportRepairs()) {
			exportDirectory = settings.getRepairExportDirectory();
			Path path = Paths.get(exportDirectory);
			if (!Files.exists(path)){
				logger.fatal(String.format("Cannot export repaired configuration files:" +
						" %s does not exist.", exportDirectory));
			} else if (!Files.isDirectory(path)){
				logger.fatal(String.format("Cannot export repaired configuration files:" +
						" %s is not a directory.", exportDirectory));
			}
		}

		configWriter.write(configModifications, exportDirectory);
	}

    private static void compareDevices(List<Device> devices){

		// Compare each Device
		SimilarityChecker checker = new SimilarityChecker(devices);
		checker.getDifferences();
	}
}
