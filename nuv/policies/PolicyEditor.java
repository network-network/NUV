package edu.wisc.cs.arc.policies;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.wisc.cs.arc.graphs.Flow;

/**
 * Command-line interface for modifying a file of policies.
 * @author Aaron Gember-Jacobson (agemberjacobson@colgate.edu)
 *
 */
public class PolicyEditor {
	private String filepath;
	
	private List<Policy> policies;
	
	public PolicyEditor(String filepath) {
		this.filepath = filepath;
	}
	
	/**
	 * Runs the policy editor.
	 */
	public void run() {
		this.loadPolicies();
		try {
			this.commandLoop();
		} catch (IOException e) {
			e.printStackTrace();
			throw new PolicyException(e.getMessage());
		}
	}
	
	/**
	 * Loads policies from the file specified on the command line.
	 */
	private void loadPolicies() {
		Map<Flow, List<Policy>> policiesByFlow = 
				PolicyFile.loadPolicies(this.filepath);
		this.policies = new ArrayList<Policy>();
		System.out.println("Policies in file '" + this.filepath + "':");
		int count = 0;
		for (List<Policy> policiesForFlow : policiesByFlow.values()) {
			for (Policy policy: policiesForFlow) {
				count++;
				System.out.println(count + "\t" + policy.toString());
				this.policies.add(policy);
			}
		}
	}
	
	/**
	 * Prints the current set of policies.
	 */
	private void printPolicies() {
		System.out.println("Policies in memory:");
		int count = 0;
		for (Policy policy: this.policies) {
			count++;
			System.out.println(count + "\t" + policy.toString());
		}
	}
	
	/**
	 * Repeatedly prompts for actions and takes the appropriate actions.
	 * @throws IOException
	 */
	private void commandLoop() throws IOException {
		BufferedReader reader = 
				new BufferedReader(new InputStreamReader(System.in));
		boolean modified = false;
		boolean quit = false;
		while(!quit) {
			System.out.print("Delete (D), Write (w), Quit (Q)? ");
			String command = reader.readLine();
			if (command.toLowerCase().contains("d")) {
				this.printPolicies();
				System.out.print("Policy number (1-" + this.policies.size() 
						+ ") to delete? ");
				int toDelete = Integer.parseInt(reader.readLine());
				if (toDelete < 1 || toDelete > this.policies.size()) {
					System.err.println("Invalid policy number");
					continue;
				}
				Policy deleted = this.policies.remove(toDelete-1);
				System.out.println("Removed policy '" + deleted.toString() 
						+ "'");
				modified = true;
			}
			if (command.toLowerCase().contains("q")) {
				if (modified && !command.toLowerCase().contains("w")) {
					do {
						System.out.print("Save policies before exiting (Y/N)? ");
						String option = reader.readLine();
						if (option.toLowerCase().equals("y")) {
							command += "w";
							break;
						} else if (option.toLowerCase().equals("n")) {
							break;
						} else {
							System.out.println("Invalid choice");
						}
					} while(true);
				}
				quit = true;
			}
			if (command.toLowerCase().contains("w")) {
				PolicyFile.savePolicies(this.policies, this.filepath);
				modified = false;
				System.out.println("Saved policies to '" + this.filepath 
						+ "'");
			}
		}
		reader.close();
	}
}
