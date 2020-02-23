package edu.wisc.cs.arc.configs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.batfish.common.BatfishLogger;
import org.batfish.common.Warnings;
import org.batfish.common.plugin.IBatfish;
import org.batfish.config.Settings.TestrigSettings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.questions.smt.HeaderQuestion;
import org.batfish.symbolic.abstraction.DestinationClasses;
import org.batfish.symbolic.abstraction.NetworkSlice;
import org.batfish.symbolic.bdd.BDDNetwork;
import org.batfish.symbolic.utils.Tuple;

import edu.wisc.cs.arc.Settings;
import edu.wisc.cs.arc.graphs.Device;
import edu.wisc.cs.arc.graphs.DeviceGraph;
import edu.wisc.cs.arc.graphs.PolicyGroup;
import edu.wisc.cs.arc.repair.graph.ConfigModification;

import org.batfish.symbolic.Graph;
import org.batfish.symbolic.abstraction.Abstraction;
import org.batfish.symbolic.abstraction.AbstractionBuilder;
import org.batfish.symbolic.abstraction.AbstractionMap;
import org.batfish.symbolic.abstraction.BatfishCompressor;
import org.batfish.common.BatfishException;
import org.batfish.common.BatfishLogger;
import org.batfish.common.RedFlagBatfishException;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.ConfigurationFormat;
import org.batfish.datamodel.answers.ConvertConfigurationAnswerElement;
import org.batfish.grammar.BatfishCombinedParser;
import org.batfish.grammar.ControlPlaneExtractor;
import org.batfish.grammar.ParseTreePrettyPrinter;
import org.batfish.grammar.VendorConfigurationFormatDetector;
import org.batfish.grammar.cisco.CiscoCombinedParser;
import org.batfish.grammar.cisco.CiscoControlPlaneExtractor;
import org.batfish.main.Batfish;
import org.batfish.main.BatfishTestUtils;
import org.batfish.main.ParserBatfishException;
import org.batfish.config.Settings.TestrigSettings;
import org.batfish.vendor.VendorConfiguration;
import org.junit.rules.TemporaryFolder;

/**
 * Compute equilent network for the original network.
 * Suppot the reduction in NUV.
 * @author Yahui Li (li-yh15@mails.tsinghua.edu.cn) 
 */
public class comAbs {
	private Batfish _batfish;
	// cache configs and so on.
	private static org.batfish.config.Settings batfishSettings;

	private static Warnings batfishWarnings;

	public Boolean compareAbcon(Settings settings, Map<String, Device> abdevices, Map<String, Device> abdevices2,
			Map<Integer, Map<String, List<ConfigModification>>> aballConfigModifications) {

		// 2,3,4,5; 2,3,8,0; abstract graphs are different;
		// from destination, compare its all neighbor BDD; then;
		// NetworkSlice n1 = null,n2=null;
		// Abstraction a1=n1.getAbstraction();
		// Abstraction a2=n2.getAbstraction();
		// HeaderSpace h = n1.getHeaderSpace();
		// Graph g1=a1.getGraph();
		// Map<String, Configuration> _configurations =g1.getConfigurations();
		// for(Entry <String, Configuration> e1: _configurations.entrySet()) {
		// System.out.println("========================~~~~~~~~~~~~~~~~~~~~"+e1.getKey()+e1.getValue());
		//
		// }
		return true;
	}

	public Map<String, Configuration> getCompressMap(Settings settings, NetworkSlice n1) {
		Abstraction a1 = n1.getAbstraction();
		// HeaderSpace h = n1.getHeaderSpace();
		Graph g1 = a1.getGraph();

		// add by lyh to see abstract graph
		// System.out.println("========================~~~~~~~~~~~~~~~~~~~~");
		// System.out.println(g1);

		// for(Entry <String, Configuration> e1: _configurations.entrySet()) {
		// System.out.println("========================~~~~~~~~~~~~~~~~~~~~"+e1.getKey()+e1.getValue());
		//
		// }
		// AbstractionMap amp=a1.getAbstractionMap();
		// Map<Integer, Set<String>> tabstractChoices=amp.getabstractChoices();
		// Map<String, Integer> tgroupMap=amp.getgroupMap();
		// System.out.println("========================~~~~~~~~~~~~~~~~~~~~");
		// for (Entry<Integer, Set<String>>entry : tabstractChoices.entrySet()) {
		// System.out.print("abstractChoices: "+ entry.getKey()+" value ");
		// for(String t: entry.getValue())
		// System.out.println(" " + t+" ");
		// }
		// System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
		// for (Entry<String, Integer>entry : tgroupMap.entrySet()) {
		// System.out.println("groupMap: "+ entry.getKey()+" value " +
		// entry.getValue());
		// }
		Map<String, Configuration> _configurations = g1.getConfigurations();
		return _configurations;
	}

	public SortedMap<String, Configuration> compressNetworks(Map<String, Configuration> configs,
			HeaderSpace headerSpace) throws IOException {
		TemporaryFolder tmp = new TemporaryFolder();
		tmp.create();
		IBatfish batfish = BatfishTestUtils.getBatfish(new TreeMap<>(configs), tmp);
		// batfish.computeDataPlane(false);
		return new TreeMap<>(new BatfishCompressor(batfish, configs).compress(headerSpace));
	}

	public List<NetworkSlice> comAslices(Map<String, Configuration> configs, HeaderSpace headerSpace)
			throws IOException {
		TemporaryFolder tmp = new TemporaryFolder();
		tmp.create();
		IBatfish batfish = BatfishTestUtils.getBatfish(new TreeMap<>(configs), tmp);
		return new ArrayList<NetworkSlice>(new BatfishCompressor(batfish, configs).comSliceslyh(headerSpace));
	}

	public DestinationClasses comDst(Map<String, Configuration> configs, HeaderSpace headerSpace) throws IOException {
		TemporaryFolder tmp = new TemporaryFolder();
		tmp.create();
		IBatfish batfish = BatfishTestUtils.getBatfish(new TreeMap<>(configs), tmp);
		return new BatfishCompressor(batfish, configs).comDstslyh(headerSpace);
	}

	public static void consGrpah(Map<String, Configuration> configs) {
		Graph g = new Graph(null, configs, null);
		Abstraction a = new Abstraction(g, null);
		// NetworkSlice slice = new NetworkSlice(q.getHeaderSpace(), a, false);
	}

	public List<HeaderSpace> getHeadlist(DestinationClasses comDst, Map<String, List<ConfigModification>> re) {
		List<HeaderSpace> all = new ArrayList<HeaderSpace>();
		List<String> tmp = new ArrayList<String>();
		Set<Entry<String, List<ConfigModification>>> entryseSet = re.entrySet();
		for (Map.Entry<String, List<ConfigModification>> entry : entryseSet) {
			tmp.add(entry.getKey());
			// System.out.println("key ------------- "+entry.getKey());
		}
		for (String tt : tmp) {
			for (Entry<Set<String>, Tuple<HeaderSpace, Tuple<List<Prefix>, Boolean>>> entry : comDst.getHeaderspaceMap()
					.entrySet()) {
				Set<String> td = entry.getKey();
				for (String t : td) {
					// System.out.println("string ------------- "+t);
					if (t.contains(tt)) {
						all.add(entry.getValue().getFirst());
						// System.out.println("value ------------- "+entry.getValue().getFirst());
					}
				}
			}
		}
		return all;
	}

	public List<PolicyGroup> getPolicyGroup(List<HeaderSpace> th1, List<HeaderSpace> th2) {
		List<PolicyGroup> all = new ArrayList<PolicyGroup>();
		Set<String> dataSet6 = new HashSet<String>();
		for (HeaderSpace t1 : th1) {
			// System.out.println("add policy1 --------------- "+t1);
			IpSpace ips = t1.getDstIps();
			String tmp = ips.toString();
			if (dataSet6.contains(tmp)) {
				continue;
			}
			dataSet6.add(tmp);
			String tmp2 = tmp.substring(tmp.indexOf("whitelist=[") + 11);
			String tmp3 = tmp2.substring(0, tmp2.indexOf("]"));
			if (tmp3.indexOf("/") == -1) {
				tmp3 = tmp3 + "/32";
			}
			if (tmp3.contains(",")) {
				tmp3 = tmp2.substring(0, tmp3.indexOf(","));
				if (tmp3.indexOf("/") == -1) {
					tmp3 = tmp3 + "/32";
				}
			}
			Prefix pdst = Prefix.parse(tmp3);
			PolicyGroup pdest = new PolicyGroup(pdst);
			// System.out.println("add policy2 --------------- "+pdest);
			all.add(pdest);
		}
		for (HeaderSpace t1 : th2) {
			// System.out.println("add policy3 --------------- "+t1);
			IpSpace ips = t1.getDstIps();
			String tmp = ips.toString();
			if (dataSet6.contains(tmp)) {
				continue;
			}
			dataSet6.add(tmp);
			String tmp2 = tmp.substring(tmp.indexOf("whitelist=[") + 11);
			String tmp3 = tmp2.substring(0, tmp2.indexOf("]"));
			if (tmp3.indexOf("/") == -1) {
				tmp3 = tmp3 + "/32";
			}
			Prefix pdst = Prefix.parse(tmp3);
			PolicyGroup pdest = new PolicyGroup(pdst);
			all.add(pdest);
		}
		return all;

	}

	public Boolean comConAbsChange(Map<String, List<ConfigModification>> ad, Map<String, List<ConfigModification>> re,
			Map<String, List<ConfigModification>> m2, Map<String, List<ConfigModification>> m1) {
		Boolean flag = true;
		if (ad == null) {
			if (m1 != null) {
				return false;
			}
		} else {
			if (m1 == null)
				return false;
			else {
				if (ad.size() != m1.size())
					return false;
			}
		}

		if (re == null) {
			if (m2 != null) {
				return false;
			}
		} else {
			if (m2 == null) {
				return false;
			} else {
				if (re.size() != m2.size())
					return false;
			}
		}
		List<String> tmp = new ArrayList<String>();
		if (ad != null && m1 != null) {
			for (Entry<String, List<ConfigModification>> entry : ad.entrySet()) {
				tmp.add(entry.getKey());
//				System.out.println("why why --------------- ");
			}
			for (String tt : tmp) {
				if (!m1.containsKey(tt)) {
					return false;
				}
			}
		}
		List<String> tmp2 = new ArrayList<String>();
		if (re != null && m2 != null) {
			Set<Entry<String, List<ConfigModification>>> entryseSet2 = re.entrySet();
			for (Map.Entry<String, List<ConfigModification>> entry : entryseSet2) {
				tmp2.add(entry.getKey());
			}
			for (String tt : tmp) {
				if (!m2.containsKey(tt)) {
					return false;
				}
			}
		}
		return flag;
	}
	//
	// String ips = String.format("srcIps=[\"%s\"], dstIps=[\"%s\"]",
	// srcPrefix.toString(), dstPrefix.toString());
	// String nodes = String.format("ingressNodeRegex=%s, finalNodeRegex=%s",
	// srcDevice.getName(), dstDevice.getName());
	// String policies = String.format("smt-reachability %s, %s", ips, nodes);

	// HeaderQuestion q;
	// HeaderSpace h = q.getHeaderSpace();
	// DestinationClasses dcs = DestinationClasses.create(null, null, h, true);
	// Graph graph=null;
	// Graph g = graph == null ? new Graph(_batfish) : graph;
	public static List<NetworkSlice> allSlices(DestinationClasses dcs, int fails) {
		BDDNetwork network = BDDNetwork.create(dcs.getGraph());
		List<NetworkSlice> sup = null;
		// AbstractionBuilder ab;
		for (Entry<Set<String>, Tuple<HeaderSpace, Tuple<List<Prefix>, Boolean>>> entry : dcs.getHeaderspaceMap()
				.entrySet()) {
			Set<String> devices = entry.getKey();
			HeaderSpace headerspace = entry.getValue().getFirst();
			List<Prefix> prefixes = entry.getValue().getSecond().getFirst();
			Boolean isDefaultCase = entry.getValue().getSecond().getSecond();
			sup.add(AbstractionBuilder.createGraph(dcs, network, devices, headerspace, prefixes, fails, isDefaultCase));
		}
		return sup;
	}

}
