package edu.wisc.cs.arc.graphs;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.batfish.datamodel.Configuration;

import edu.wisc.cs.arc.GeneratorException;
import edu.wisc.cs.arc.Logger;
import edu.wisc.cs.arc.Settings;
import edu.wisc.cs.arc.policies.PolicyException;
import edu.wisc.cs.arc.repair.VirtualDirectedEdge;
import edu.wisc.cs.arc.repair.graph.GraphModification;
import edu.wisc.cs.arc.repair.graph.GraphModification.Action;

/**
 * Perform tasks related to constructing ETGs.
 * @author Aaron Gember-Jacobson (agemberjacobson@colgate.edu)
 */
public class EtgTasks {

    /**
     * Create ETGs for every possible flow, along with creating intermediate
     * graphs.
     * @param settings
     * @param configs configurations from which to generate ETGs
     * @param violatedFlows set of flows for which the ETGs are to be generated
     * @return the created ETGs
     */
    public static Map<Flow, ProcessGraph> generateFlowETGs(Settings settings,
                Set<Flow> violatedFlows, Map<String, Configuration> configs){
        Logger logger = settings.getLogger();

        // Extract configuration details
        List<Device> devices = new ArrayList<Device>();
        for (Map.Entry<String, Configuration> entry : configs.entrySet()) {
            Device device = new Device(entry.getKey(), entry.getValue(),logger);
            devices.add(device);
        }

        // Create intermediate ETGs
        DeviceGraph deviceEtg = new DeviceGraph(devices, settings, null);
        ProcessGraph baseEtg = new ProcessGraph(deviceEtg, settings);

        Queue<Flow> queue = new ConcurrentLinkedQueue<Flow>(violatedFlows);

        // Create process-based flow ETGs
        return EtgTasks.generateSpecificFlowETGs(settings, baseEtg, queue);
    }

    /**
     * Create ETGs for every possible flow, along with creating intermediate
     * graphs.
     * @param settings
     * @param configs configurations from which to generate ETGs
     * @param policyGroups policy groups from which to define flows
     * @return the created ETGs
     */
    public static Map<Flow, ProcessGraph> generateFlowETGs(Settings settings,
            Map<String, Configuration> configs, List<PolicyGroup> policyGroups){
        Logger logger = settings.getLogger();

        // Extract configuration details
        List<Device> devices = new ArrayList<Device>();
        for (Map.Entry<String, Configuration> entry : configs.entrySet()) {
            Device device = new Device(entry.getKey(), entry.getValue(),logger);
            devices.add(device);
        }

        // Create itermediate ETGs
        DeviceGraph deviceEtg = new DeviceGraph(devices, settings, null);
        ProcessGraph baseEtg = new ProcessGraph(deviceEtg, settings);

        // Create process-based flow ETGs
        return EtgTasks.generateFlowETGs(settings, baseEtg,
                policyGroups);
    }

    /**
     * Create ETGs for every possible flow
     * @param settings
     * @param baseEtg the ETG on which to base the ETG for each flow
     * @param policyGroups the policy groups from which to define flows
     * @return the created ETGs
     */
    public static Map<Flow, ProcessGraph> generateFlowETGs(Settings settings,
            ProcessGraph baseEtg,  List<PolicyGroup> policyGroups) {
        Logger logger = settings.getLogger();

        // Create a queue of flows for which to construct ETGs
        Queue<Flow> queue = new ConcurrentLinkedQueue<Flow>();
        List<Flow> flows = new ArrayList<Flow>();
        for (PolicyGroup source : policyGroups) {
            for (PolicyGroup destination : policyGroups) {
                if (source.equals(destination)) {
                    continue;
                }
                Flow flow = new Flow(source, destination);
                queue.add(flow);
                flows.add(flow);
            }
        }
        logger.info("Need to generate " + queue.size() + " ETGs");
        
        return generateSpecificFlowETGs(settings,baseEtg,queue);
    }

    /**
     * Create ETGs for the specified flows
     * @param settings
     * @param baseEtg the ETG on which to base the ETG for each flow
     * @param queue queue of flows for which to construct ETGs
     */
    public static Map<Flow, ProcessGraph> generateSpecificFlowETGs(Settings settings,
                                                                   ProcessGraph baseEtg,
                                                                   Queue<Flow> queue){
        Logger logger = settings.getLogger();

        logger.info("Need to generate " + queue.size() + " ETGs");
        
        // Generate flow-specific ETGs
        Map<Flow, ProcessGraph> flowEtgs =
                new LinkedHashMap<Flow, ProcessGraph>();
        long startTime = System.currentTimeMillis();
        if (settings.shouldParallelize()) {
            // Create a thread pool
            int numThreads = Runtime.getRuntime().availableProcessors();
            ExecutorService threadPool = Executors.newFixedThreadPool(
                    numThreads);

            // Start a VerificationTask for each thread
            List<Future<Map<Flow, ProcessGraph>>> futures =
                    new ArrayList<Future<Map<Flow,ProcessGraph>>>(
                            numThreads);
            for (int t = 0; t < numThreads; t++) {
                ConstructTask task = new ConstructTask(baseEtg, queue);
                futures.add(threadPool.submit(task));
            }

            // Get the results from each thread
            try {
                for (Future<Map<Flow,ProcessGraph>> future : futures) {
                    // Get the result from the thread, waiting for the thread to
                    // complete, if necessary
                    Map<Flow, ProcessGraph> result = future.get();
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
                ProcessGraph flowEtg = (ProcessGraph)baseEtg.clone();
                flowEtg.customize(flow);
                flowEtgs.put(flow, flowEtg);
            }
        }
        long endTime = System.currentTimeMillis();
        logger.info("TIME: flowETGs "+(endTime - startTime)+" ms");

        return flowEtgs;

    }

    /**
     * Create ETGs for every possible destination
     * @param settings
     * @param processEtg the ETG on which to base the ETG for each destination
     * @param policyGroups the destinations
     * @return the created ETGs
     */
    public static Map<PolicyGroup,ProcessGraph> generateDestinationETGs(
            Settings settings, ProcessGraph processEtg,
            List<PolicyGroup> policyGroups) {
        Logger logger = settings.getLogger();

        // Create a queue of policy groups for which to construct ETGs
        Queue<PolicyGroup> queue = new ConcurrentLinkedQueue<PolicyGroup>();
        for (PolicyGroup destination : policyGroups) {
            queue.add(destination);
        }

        // Generate destination-specific ETGs
        Map<PolicyGroup,ProcessGraph> destinationEtgs =
                new LinkedHashMap<PolicyGroup,ProcessGraph>();
        long startTime = System.currentTimeMillis();
        if (settings.shouldParallelize()) {
            // FIXME
            throw new GeneratorException(
                    "Cannot generate per-destination ETGs in parallel");
        }
        else {
            while (!queue.isEmpty()) {
                PolicyGroup destination = queue.remove();
                //logger.debug("Generate ETG " + destination.toString());
                ProcessGraph destinationEtg = (ProcessGraph)processEtg.clone();
                destinationEtg.customize(destination);
                destinationEtgs.put(destination, destinationEtg);
            }
        }
        long endTime = System.currentTimeMillis();
        logger.info("TIME: destinationETGs "+(endTime-startTime)+" ms");

        return destinationEtgs;
    }
    
    /**
     * Generate graphviz files.
     * @param settings
     * @param baseEtg the base ETG
     * @param instanceEtg an ETG representing the routing instance topology
     * @param topoEtg an ETG representing the layer-3 network topology
     * @param dstEtgs the process ETGs for each destination
     * @param flowEtgs the process ETGs for each flow
     */
    public static void generateGraphviz(Settings settings,
            ProcessGraph baseEtg, InstanceGraph instanceEtg,
            DeviceGraph topoEtg, Map<PolicyGroup, ProcessGraph> dstEtgs, 
            Map<Flow, ProcessGraph> flowEtgs) {
        Logger logger = settings.getLogger();

        logger.info("*** Generate graphviz ***");
        File graphFile;

        if (baseEtg != null) {
            graphFile = Paths.get(settings.getGraphvizDirectory(),
                    baseEtg.getGraphvizFilename()).toFile();
            try {
                FileUtils.writeStringToFile(graphFile,
                        baseEtg.toGraphviz(), false);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (instanceEtg != null) {
            graphFile = Paths.get(settings.getGraphvizDirectory(),
                    instanceEtg.getGraphvizFilename()).toFile();
            try {
                FileUtils.writeStringToFile(graphFile,
                        instanceEtg.toGraphviz(), false);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (topoEtg != null) {
            graphFile = Paths.get(settings.getGraphvizDirectory(),
                    topoEtg.getGraphvizFilename()).toFile();
            try {
                FileUtils.writeStringToFile(graphFile,
                        topoEtg.toGraphviz(), false);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        if (dstEtgs != null) {
            for (ProcessGraph dstEtg : dstEtgs.values()) {
            	logger.debug("Graphviz for " + dstEtg.getGraphvizFilename());
                graphFile = Paths.get(settings.getGraphvizDirectory(),
                		dstEtg.getGraphvizFilename()).toFile();
                try {
                    FileUtils.writeStringToFile(graphFile,
                    		dstEtg.toGraphviz(), false);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (flowEtgs != null) {
            for (ProcessGraph flowEtg : flowEtgs.values()) {
                graphFile = Paths.get(settings.getGraphvizDirectory(),
                        flowEtg.getGraphvizFilename()).toFile();
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
     * Serialize the ETGs.
     * @param settings
     * @param flowEtgs the ETGs for each flow
     */
    public static void serializeFlowETGs(Settings settings,
            Map<Flow, ProcessGraph> flowEtgs) {
        Logger logger = settings.getLogger();
        logger.info("*** Serialize flow ETGs ***");
        try {
            FileOutputStream fileOut = new FileOutputStream(
                    settings.getSerializedETGsFile());
            ObjectOutputStream objOut = new ObjectOutputStream(fileOut);
            for (ProcessGraph flowEtg : flowEtgs.values()) {
                objOut.writeObject(flowEtg);
            }
            objOut.close();
            fileOut.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Compare the ETGs.
     * @param settings
     * @param flowEtgs the ETGs for each flow
     */
    public static void compareETGs(Settings settings,
            Map<Flow, ProcessGraph> flowEtgs) {
        Logger logger = settings.getLogger();
        logger.info("*** Compare ETGs ***");

        // Load comparison ETGs
        Map<Flow, ProcessGraph> comparisonEtgs =
                new LinkedHashMap<Flow, ProcessGraph>();
        try {
            FileInputStream fileIn = new FileInputStream(
                    settings.getCompareETGsFile());
            ObjectInputStream objIn = new ObjectInputStream(fileIn);
            try {
                while (true) {
                    Object obj = objIn.readObject();
                    if (obj instanceof ProcessGraph) {
                        ProcessGraph etg = (ProcessGraph)obj;
                        PolicyGroup src = etg.getFlow().getSource();
                        PolicyGroup dst = etg.getFlow().getDestination();
                        if (src.getEndIp().asLong() - src.getStartIp().asLong()
                            < settings.getMinPolicyGroupsSize() ||
                            dst.getEndIp().asLong() - dst.getStartIp().asLong()
                            < settings.getMinPolicyGroupsSize()) {
                            continue;
                        }
                        comparisonEtgs.put(etg.getFlow(), etg);
                        // Remove blocked/infinite edges for fair comparison
                        etg.prune(false);
                    } else {
                        break;
                    }
                }
            }
            catch (EOFException e) {

            }
            objIn.close();
            fileIn.close();
        } catch(IOException e) {
            e.printStackTrace();
            throw new PolicyException(e.getMessage());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new PolicyException(e.getMessage());
        }
        System.out.println("COUNT: comparisonETGs " + comparisonEtgs.size());

        // Compare each ETG
        for (Flow flow : flowEtgs.keySet()) {
            ProcessGraph etg = flowEtgs.get(flow);
            ProcessGraph compareEtg = comparisonEtgs.get(flow);
            if (null == compareEtg) {
                logger.info(flow.toString());
                logger.info("\tADD ETG");
                continue;
            }

            List<GraphModification<ProcessVertex>> modifications =
                    new ArrayList<GraphModification<ProcessVertex>>();
            Iterator<DirectedEdge<ProcessVertex>> iterator =
                    etg.getEdgesIterator();
            while (iterator.hasNext()) {
                DirectedEdge<ProcessVertex> edge = iterator.next();
                if (!compareEtg.containsEdge(edge.getSource(),
                        edge.getDestination())) {
                    modifications.add(new GraphModification<ProcessVertex>(
                            Action.ADD,
                            new VirtualDirectedEdge<ProcessVertex>(edge)));
                }
            }

            iterator = compareEtg.getEdgesIterator();
            while (iterator.hasNext()) {
                DirectedEdge<ProcessVertex> edge = iterator.next();
                if (!etg.containsEdge(edge.getSource(),
                        edge.getDestination())) {
                    modifications.add(new GraphModification<ProcessVertex>(
                            Action.REMOVE,
                            new VirtualDirectedEdge<ProcessVertex>(edge)));
                }
            }

            if (modifications.size() > 0) {
                logger.info(flow.toString());
                for (GraphModification<ProcessVertex> mod : modifications) {
                    logger.info("\t" + mod.toString());
                }
            }
        }

        for (Flow flow : comparisonEtgs.keySet()) {
            if (!flowEtgs.containsKey(flow)) {
                logger.info(flow.toString());
                logger.info("\tREMOVE ETG");
            }
        }
    }
}
