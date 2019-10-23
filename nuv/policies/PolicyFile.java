package edu.wisc.cs.arc.policies;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.wisc.cs.arc.graphs.Flow;

/**
 * Saves and loads serialized policies.
 * @author Aaron Gember-Jacobson (agemberjacobson@colgate.edu)
 */
public class PolicyFile {

	/**
	 * Serializes policies to a file.
	 * @param policies the policies to serialize
	 * @param filepath the file to save the policies to
	 */
	public static void savePolicies(List<Policy> policies, String filepath) {
		// Save policies
		try {
			FileOutputStream fileOut = new FileOutputStream(filepath);
			ObjectOutputStream objOut = new ObjectOutputStream(fileOut);
			for (Policy policy : policies) {
				objOut.writeObject(policy);
			}
			objOut.close();
			fileOut.close();
		} catch(IOException e) {
			e.printStackTrace();
			throw new PolicyException(e.getMessage());
		}
	}

	/**
	 * Deserializes policies from a file.
	 * @param filepath the file to load the policies from
	 * @return the policies in the file
	 */
	public static Map<Flow, List<Policy>> loadPolicies(String filepath) {
		Map<Flow, List<Policy>> policies =
				new LinkedHashMap<Flow, List<Policy>>();
		try {
			FileInputStream fileIn = new FileInputStream(filepath);
			ObjectInputStream objIn = new ObjectInputStream(fileIn);
			try {
				while (true) {
					Object obj = objIn.readObject();
					if (obj instanceof Policy) {
						Policy policy = (Policy)obj;
						if (!policies.containsKey(policy.getTrafficClass())) {
                            policies.put(policy.getTrafficClass(),
                                    new ArrayList<Policy>());
                        }
					    policies.get(policy.getTrafficClass()).add(policy);
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
		return policies;
	}
}
