package edu.wisc.cs.arc.graphs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;

import edu.wisc.cs.arc.graphs.Flow;

/**
 * A task that invokes a constructor to make a flow-specific extended topology 
 * graph.
 * @author Aaron Gember-Jacobson (agember@cs.wisc.edu)
 */
public class ConstructTask implements Callable<Map<Flow,ProcessGraph>> {

	/** Non-flow-specific extended topology graph */
	private ProcessGraph baseEtg;
	
	/** Queue from which to pull flows to verify */
	private Queue<Flow> queue;
	
	/**
	 * Create a task to invoke a constructor to make flow-specific extended
	 * topology graphs.
	 * @param baseEtg non-flow-specific extended topology graph
	 * @param queue queue from which to pull flows to verify
	 */
	public ConstructTask(ProcessGraph baseEtg, Queue<Flow> queue) {
		this.baseEtg = baseEtg;
		this.queue = queue;
	}
	
	/**
	 * Pulls flows from a queue and invokes the constructor for a flow-specific
	 * extended topology graph for each flow, until the queue is empty.
	 * @return extended topology graph for each flow for which the constructor
	 * 			was invoked
	 */
	@Override
	public Map<Flow,ProcessGraph> call() throws Exception {
        int count = 0;
		Map<Flow,ProcessGraph> results = new LinkedHashMap<Flow,ProcessGraph>();
		Flow flow = this.queue.poll();
		while(flow != null) {
		    ProcessGraph flowEtg = (ProcessGraph)this.baseEtg.clone();
			flowEtg.customize(flow);
			results.put(flow, flowEtg);
            count++;
            if (count % 100 == 0) {
                baseEtg.logger.debug("Constructed "+count+" ETGs");
            }
			flow = this.queue.poll();
		}
		return results;
	}
}
