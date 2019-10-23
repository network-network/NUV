package edu.wisc.cs.arc.minesweeper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.batfish.datamodel.Prefix;

import edu.wisc.cs.arc.Logger;
import edu.wisc.cs.arc.Settings;
import edu.wisc.cs.arc.graphs.Device;
import edu.wisc.cs.arc.graphs.Flow;
import edu.wisc.cs.arc.graphs.Interface;
import edu.wisc.cs.arc.graphs.PolicyGroup;
import edu.wisc.cs.arc.policies.Policy;
import edu.wisc.cs.arc.policies.Policy.PolicyType;
import edu.wisc.cs.arc.policies.PolicyFile;

/**
 * Perform tasks related to Minesweeper.
 * @author Aaron Gember-Jacobson (agemberjacobson@colgate.edu)
 */
public class MinesweeperTasks {
	
	/**
	 * Generate a file of policies to check with Minesweeper.
	 * @param settings
	 * @param policyGroups traffic classes in the network
	 */
	public static void generateMinesweeperPolicyList(Settings settings, 
			List<PolicyGroup> policyGroups, Collection<Device> devices) {
		Logger logger = settings.getLogger();
		logger.info("*** Generate Minesweeper policy list ***");
		List<String> policies = new ArrayList<String>();
		
        //policies.add("smt-routing-loop");
		
		/*for (PolicyGroup dstGroup : policyGroups) {			
			Prefix dstPrefix = dstGroup.getPrefix();
			
			// Determine where prefix is connected
			Device dstDevice = null;
			for (Device device : devices) {
				if (device.isSubnetConnected(dstPrefix)) {
					dstDevice = device;
				}
			}
			
			// Ignore prefixes that are not connected to a device
			if (null == dstDevice) {
				continue;
			}
			
			for (PolicyGroup srcGroup : policyGroups) {
				// Ignore equivalent src and destination
				if (srcGroup.equals(dstGroup)) {
					continue;
				}
				
				Prefix srcPrefix = srcGroup.getPrefix();
				
				Device srcDevice = null;
				for (Device device : devices) {
					if (device.isSubnetConnected(srcPrefix)) {
						srcDevice = device;
					}
				}
				
				// Ignore prefixes that are not connected to a device or are 
				// connected to the same device as the destination
				if (null == srcDevice || srcDevice.equals(dstDevice)) {
					continue;
				}
				
				logger.debug("Add policies for " + srcPrefix.toString() + ","
						+ srcDevice.getName() + " -> " + dstPrefix.toString() 
						+ "," + dstDevice.getName());
				
				policies.addAll(generateMinesweeperMulti(srcPrefix, 
						srcDevice, dstPrefix, dstDevice));
			}
		}*/
		
		for (Device dstDevice : devices) {
		    for (Interface dstIface : dstDevice.getInterfaces()) {
		        if (dstIface.isInternal() || !dstIface.hasPrefix()) {
		            continue;
		        }
		        
		        Prefix dstPrefix = dstIface.getPrefix();
		        
		        for (Device srcDevice : devices) {
                    if (srcDevice == dstDevice) {
                        continue;
                    }

		            for (Interface srcIface : srcDevice.getInterfaces()) {
		                if (srcIface.isInternal() || !srcIface.hasPrefix()) {
		                    continue;
		                }
		                
		                Prefix srcPrefix = srcIface.getPrefix();
		                
		                logger.debug("Add policies for " 
		                        + srcPrefix.toString() + "," 
		                        + srcDevice.getName() + " -> " 
		                        + dstPrefix.toString() + "," 
		                        + dstDevice.getName());
		                
		                policies.addAll(generateMinesweeperMulti(srcPrefix, 
		                        srcDevice, dstPrefix, dstDevice));
		            }
		        }
		    }
		}
		
		File file = Paths.get(settings.getMinesweeperPoliciesFile()).toFile();
        try {
            FileUtils.writeLines(file, policies);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	/**
	 * Generate a list of all policies for a flow to check with Minesweeper.
	 * @param srcPrefix the source subnet of the flow
	 * @param srcDevice the source device for the flow
	 * @param dstPrefix the destination subnet of the flow
	 * @param dstDevice the destination device for the flow
	 * @return a list of all policies for a flow to check with Minesweeper
	 */
	private static List<String> generateMinesweeperMulti(Prefix srcPrefix, 
			Device srcDevice, Prefix dstPrefix, Device dstDevice) {
		List<String> policies = new ArrayList<String>();
		
		String ips = String.format("srcIps=[\"%s\"], dstIps=[\"%s\"]", 
				srcPrefix.toString(), dstPrefix.toString());
		String nodes = String.format("ingressNodeRegex=%s, finalNodeRegex=%s", 
				srcDevice.getName(), dstDevice.getName());
		
		policies.add(String.format("smt-reachability %s, %s", ips, nodes));
		/*policies.add(String.format("smt-reachability %s, %s, failures=1", 
				ips, nodes));
		policies.add(String.format("smt-reachability %s, %s, failures=2",
				ips, nodes));*/
		//policies.add(String.format("smt-bounded-length %s, %s, bound=4", 
		//		ips, nodes));
		//policies.add(String.format("smt-blackhole %s", ips));
		return policies;
	}
	
	/**
	 * Convert a file of Minesweeper policies to ARC policies.
	 * @param settings
	 */
	public static void convertMinesweeperPolicies(Settings settings) {
		Logger logger = settings.getLogger();
		logger.info("*** Convert Minesweeper policy list ***");
		List<Policy> policies = new ArrayList<Policy>();
		
		try {
		     // Open file of policies
	        File inputFile = Paths.get(settings.getPoliciesConvertFile()).toFile();
	        FileReader fileReader = new FileReader(inputFile);
	        BufferedReader bufferedReader = new BufferedReader(fileReader);
	        
	        // Process every line in the file
	        // Get first line
	        String inputLine = bufferedReader.readLine();
	        while (inputLine != null) {
	            Policy policy = convertMinesweeperPolicy(inputLine);
	            if (policy != null) {
	                policies.add(policy);
	            }
	            
	            // Get next line
	            inputLine = bufferedReader.readLine();
	        }
	        fileReader.close();
		}
		catch(FileNotFoundException ex) {
		    logger.error(ex.toString());
		}
		catch(IOException ex) {
		    logger.error(ex.toString());
        }
		
		logger.info("Converted " + policies.size() + " policies");
		
		// Save converted policies
		if (settings.shouldSavePolicies()) {
		    PolicyFile.savePolicies(policies, settings.getPoliciesSaveFile());
		}
	}
	
	/**
	 * Convert a Minesweepr policy to an ARC policy.
	 * @param minesweeperPolicy Minesweeper policy
	 */
	private static Policy convertMinesweeperPolicy(String minesweeperPolicy) {
	    minesweeperPolicy.replace(',', ' ');
	    String parts[] = minesweeperPolicy.split("  *");
	    
	    // Reachability
	    if (parts[0].equals("smt-reachability")) {
	        // Extract flow
	        String srcPrefix = null;
	        String dstPrefix = null;
	        int failures = 1;
	        for (int i = 1; i < parts.length; i++) {
	            if (parts[i].startsWith("srcIps")) {
	                srcPrefix = parts[i].split("\"")[1];
	            } else if (parts[i].startsWith("dstIps")) {
                    dstPrefix = parts[i].split("\"")[1];
                } else if (parts[i].startsWith("failures")) {
                    failures = Integer.parseInt(parts[i].split("=")[1]);
                }
	        }
	        
	        Flow flow = new Flow(new PolicyGroup(Prefix.parse(srcPrefix)),
	                new PolicyGroup(Prefix.parse(dstPrefix)));
	        return new Policy(PolicyType.ALWAYS_REACHABLE, flow, failures);
	    }
	    return null;
	}
}
