package edu.wisc.cs.arc.graphs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import org.batfish.datamodel.BgpActivePeerConfig;
import org.batfish.datamodel.BgpPeerConfig;
import org.batfish.datamodel.BgpProcess;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.IpProtocol;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.IpWildcardIpSpace;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.RouteFilterLine;
import org.batfish.datamodel.RouteFilterList;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.acl.MatchHeaderSpace;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;
import org.batfish.datamodel.routing_policy.expr.ExplicitPrefixSet;
import org.batfish.datamodel.routing_policy.expr.MatchPrefixSet;
import org.batfish.datamodel.routing_policy.expr.NamedPrefixSet;
import org.batfish.datamodel.routing_policy.expr.PrefixSetExpr;
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.datamodel.routing_policy.statement.Statement;
//import org.batfish.datamodel.Interface;
import org.jgrapht.alg.ConnectivityInspector;
import edu.wisc.cs.arc.Logger;
import edu.wisc.cs.arc.Settings;
import edu.wisc.cs.arc.configs.Config;
import edu.wisc.cs.arc.configs.ConfigurationTasks;
import edu.wisc.cs.arc.repair.graph.ConfigModification;
import edu.wisc.cs.arc.repair.graph.ConfigModification.Action;
import edu.wisc.cs.arc.repair.graph.ConfigModification.Stanza;
import edu.wisc.cs.arc.repair.graph.ConfigModification.Substanza;
import scala.collection.immutable.HashMap;
import edu.wisc.cs.arc.graphs.Interface;

import java.util.Map.Entry;

public class FlowComp {

	public List<Flow> comFlowsHeader(Settings settings, HeaderSpace h, List<PolicyGroup> policyGroups2) {
		List<Flow> diff = new ArrayList<Flow>();
		IpSpace ips = h.getDstIps();
		String tmp = ips.toString();
		String tmp2 = tmp.substring(tmp.indexOf("whitelist=[") + 11);
		String tmp3 = tmp2.substring(0, tmp2.indexOf("]"));
		// System.out.println("h---dstips---"+tmp+"----"+tmp3);
		List<PolicyGroup> policylist = new ArrayList<>();
		while (tmp3.indexOf(",") != -1) {
			// if (first==true) {
			String tmp4 = tmp2.substring(0, tmp3.indexOf(","));
			// }
			// System.out.println("tmp4---"+tmp4);
			if (tmp4.indexOf("/") == -1) {
				tmp4 = tmp4 + "/32";
			}
			Prefix pAB = Prefix.parse(tmp4);
			PolicyGroup source = new PolicyGroup(pAB);
			policylist.add(source);
			tmp3 = tmp3.substring(tmp3.indexOf(",") + 2);
		}
		if (tmp3.indexOf("/") == -1) {
			tmp3 = tmp3 + "/32";
		}
		// System.out.println("tmp3------"+tmp3);
		Prefix pAB = Prefix.parse(tmp3);
		PolicyGroup sourcet = new PolicyGroup(pAB);
		policylist.add(sourcet);
		for (PolicyGroup source : policylist) {
			for (PolicyGroup destination : policyGroups2) {
				if (source.equals(destination)) {
					continue;
				}
				Flow flow2 = new Flow(destination, source);
				diff.add(flow2);
			}
		}
		return diff;
	}

	public boolean withinacl(Ip is, Ip id, PolicyGroup s, PolicyGroup d) {// prefix cover this policy group
		if (is.asLong() <= s.getStartIp().asLong() && id.asLong() >= s.getEndIp().asLong())
			return true;
		else
			return false;
	}

	public boolean getaclips(IpAccessList acls, PolicyGroup s, PolicyGroup d) {// prefix cover this policy group
		// for(ips.getLines())
		for (IpAccessListLine line : acls.getLines()) {
			MatchHeaderSpace match = (MatchHeaderSpace) line.getMatchCondition();
			HeaderSpace headerSpace = match.getHeaderspace();
			IpWildcard srcWildcard = ((IpWildcardIpSpace) headerSpace.getSrcIps()).getIpWildcard();
			Ip startIp = srcWildcard.getIp();
			Ip endIp = srcWildcard.getIp().getWildcardEndIp(srcWildcard.getWildcard());
			if (this.withinacl(startIp, endIp, s, d)) {
				return true;
			}
		}
		return false;
	}

	public Boolean checkAClsTake(PolicyGroup s, PolicyGroup d, Map<String, List<ConfigModification>> cm,
			DeviceGraph deviceEtgt1) {
		Boolean flag = false;
		for (Entry<String, List<ConfigModification>> entry : cm.entrySet()) {
			Device d1 = deviceEtgt1.getDevice(entry.getKey());
			List<ConfigModification> lc = entry.getValue();
			for (ConfigModification c : lc) {
				String tmp = c.toString();
				String tmp2 = tmp.substring(tmp.indexOf(" STANDARD_ACL") + 2);
				String tmp3 = tmp2.substring(0, tmp2.indexOf("~"));
				IpAccessList ips = d1.getAcl(tmp3);
				if (getaclips(ips, s, d))
					return true;
			}
		}
		return flag;
	}

	public Boolean checkRouteTake(PolicyGroup d, Map<String, List<ConfigModification>> cm) {
		Boolean flag = true;
		return flag;
	}

	// add for pruning abconfig, routemap
	// public Map<Integer, Map<String, List<ConfigModification>>> prunermconfig
	// (Map<Integer, Map<String, List<ConfigModification>>> cms){
	//
	// Map<String, List<ConfigModification>> routecm = cms.get(2);
	// for(Entry <String, List<ConfigModification>> entry: routecm.entrySet() ) {
	//
	// }
	// return cms;
	// }
	//

	// add for abstraction configs, type=3;
	public List<Flow> comFlowsRouteHeader(Settings settings, HeaderSpace h,
			Map<Integer, Map<String, List<ConfigModification>>> cms, List<PolicyGroup> policyGroups,
			List<PolicyGroup> policyGroups2, DeviceGraph deviceEtgt1, DeviceGraph deviceEtgt2,
			Map<String, Config> configs2) {

		Logger logger = settings.getLogger();
		List<Flow> diff = new ArrayList<Flow>();
		IpSpace ips = h.getDstIps();
		String tmp = ips.toString();
		String tmp2 = tmp.substring(tmp.indexOf("whitelist=[") + 11);
		String tmp3 = tmp2.substring(0, tmp2.indexOf("]"));
		List<PolicyGroup> policylist = new ArrayList<>();
		while (tmp3.indexOf(",") != -1) {
			String tmp4 = tmp2.substring(0, tmp3.indexOf(","));
			if (tmp4.indexOf("/") == -1) {
				tmp4 = tmp4 + "/32";
			}
			Prefix pAB = Prefix.parse(tmp4);
			PolicyGroup source = new PolicyGroup(pAB);
			policylist.add(source);
			tmp3 = tmp3.substring(tmp3.indexOf(",") + 2);
		}
		if (tmp3.indexOf("/") == -1) {
			tmp3 = tmp3 + "/32";
		}
		Prefix pAB = Prefix.parse(tmp3);
		PolicyGroup sourcet = new PolicyGroup(pAB);
		policylist.add(sourcet);

		// compute he routemap's interfaces; it is the same with concrete configs,
		// routecm: prune changed configs, if route map doen't relatead to the dst
		// policygrup, remove the configs;

		// cms = this.prunermconfig (cms);
		Map<String, Map<RoutingPolicy, List<Prefix>>> allprefix = this.getpolicyGroupsRoute(cms, deviceEtgt1);
		Map<PolicyGroup, List<Interface>> pintlist = this.getmatchedinterfaces(policylist, allprefix, deviceEtgt2);

		logger.info("lyh: route maps don't take affect on d");

		for (PolicyGroup destination : policylist) {
			// Boolean rflag = this.checkRouteTake(destination, cms.get(2));
			Boolean rflag = true;
			if (pintlist.get(destination) == null) {
				rflag = false;
			}
			if (rflag) {// did have realted route map change
				for (PolicyGroup source : policyGroups2) {
					if (source.equals(destination)) {
						continue;
					}
					// whether routemap take affect
					Boolean c2flag = true;
					if (destination.isInternal()) {
						// compute det and src' location,
						Map<Integer, Map<String, String>> ifacemap = PolicyGroup.getPlolicyIface(destination, source,
								configs2, deviceEtgt2);
						Map<Integer, Interface> fimap = new LinkedHashMap<Integer, Interface>();
						Map<String, String> tmap = ifacemap.get(2);
						Map<String, String> tmap1 = ifacemap.get(1);
						for (Device t : deviceEtgt2.getDevices().values()) {
							for (Entry<String, String> entry : tmap.entrySet()) {
								if (t.getName().equals(entry.getKey())) {
									for (Interface intf : t.getInterfaces()) {
										if (intf.getName().equals(entry.getValue()))
											fimap.put(2, intf);
									}
								}
							}

							for (Entry<String, String> entry : tmap1.entrySet()) {
								if (t.getName().equals(entry.getKey())) {
									for (Interface intf : t.getInterfaces()) {
										if (intf.getName().equals(entry.getValue()))
											fimap.put(1, intf);
									}
								}
							}
						}

						Interface idst = fimap.get(2);
						Interface isrc = fimap.get(1);
						List<Interface> interfaces = pintlist.get(destination);
						c2flag = this.getDcomponents(settings, idst, isrc, interfaces, deviceEtgt2);
					}
					if (c2flag) {
						logger.info("lyh: route maps take affect on d,s");
						Flow flow2 = new Flow(source, destination);
						diff.add(flow2);
						continue;
					} else {
						logger.info("lyh: route maps don't take affect on d,s");
						// for each acl, check s,d; one direction;
						Map<String, List<ConfigModification>> aclcm = cms.get(1);
						Boolean aclflag = this.checkAClsTake(source, destination, aclcm, deviceEtgt1);
						if (aclflag) {
							logger.info("lyh: cahnged acls  take affect on d,s");
							Flow flow2 = new Flow(source, destination);
							diff.add(flow2);
							continue;
						}
					}
				}
			}
		}
		return diff;
	}

	public Map<Integer, Device> commines(PolicyGroup dst, PolicyGroup src, Map<String, Config> configs2,
			DeviceGraph deviceEtg2) {
		Map<Integer, Map<String, String>> ifacemap = PolicyGroup.getPlolicyIface(dst, src, configs2, deviceEtg2);
		Map<Integer, Device> fimap = new LinkedHashMap<Integer, Device>();
		Map<String, String> tmap = ifacemap.get(2);
		Map<String, String> tmap1 = ifacemap.get(1);
		for (Device t : deviceEtg2.getDevices().values()) {
			for (Entry<String, String> entry : tmap.entrySet()) {
				if (t.getName().equals(entry.getKey())) {
					// for (Interface intf : t.getInterfaces()) {
					// if (intf.getName().equals(entry.getValue()))
					fimap.put(2, t);
					// }
				}
			}
			// }
			for (Entry<String, String> entry : tmap1.entrySet()) {
				if (t.getName().equals(entry.getKey())) {
					// for (Interface intf : t.getInterfaces()) {
					// if (intf.getName().equals(entry.getValue()))
					fimap.put(1, t);
					// }
				}
			}
		}
		return fimap;
	}

	public List<Flow> comFlowsAclHeader(Settings settings, PolicyGroup source, HeaderSpace h,
			Map<Integer, Map<String, List<ConfigModification>>> cm, DeviceGraph ds, DeviceGraph deviceEtgt2,
			Map<String, Config> configs2) {

		Logger logger = settings.getLogger();
		List<Flow> diff = new ArrayList<Flow>();
		IpSpace ips = h.getDstIps();
		String tmp = ips.toString();
		String tmp2 = tmp.substring(tmp.indexOf("whitelist=[") + 11);
		String tmp3 = tmp2.substring(0, tmp2.indexOf("]"));
		if (tmp3.indexOf("/") == -1) {
			tmp3 = tmp3 + "/32";
		}
		Prefix pAB = Prefix.parse(tmp3);

		PolicyGroup destination = new PolicyGroup(pAB);

		// todo, for each acl, check s,d; one direction;
		Map<String, List<ConfigModification>> aclcm = cm.get(1);
		Boolean aclflag = this.checkAClsTake(source, destination, aclcm, deviceEtgt2);
		if (aclflag) {
			// logger.info("lyh: cahnged acls take affect on d,s");
			Flow flow2 = new Flow(source, destination);
			diff.add(flow2);
		}
		return diff;
	}

	// handel acl
	public List<Flow> comFlows(Settings settings, List<PolicyGroup> policyGroups, List<PolicyGroup> policyGroups2) {
		List<Flow> diff = new ArrayList<Flow>();
		Logger logger = settings.getLogger();

		List<Flow> flows = new ArrayList<Flow>();
		for (PolicyGroup source : policyGroups) {
			for (PolicyGroup destination : policyGroups) {
				if (source.equals(destination)) {
					continue;
				}
				Flow flow = new Flow(source, destination);
				flows.add(flow);
			}
		}
		// logger.info("lyh: coun flow1 --" + flows.size());
		List<Flow> flows2 = new ArrayList<Flow>();
		for (PolicyGroup source : policyGroups2) {
			for (PolicyGroup destination : policyGroups2) {
				if (source.equals(destination)) {
					continue;
				}
				Flow flow2 = new Flow(source, destination);
				flows2.add(flow2);
			}
		}
		// logger.info("lyh: count flow2 --" + flows2.size());
		for (Flow str : flows) {
			if (!flows2.contains(str)) {
				diff.add(str);
			}
		}
		// logger.info("lyh: count diffflow --" + diff.size());
		return diff;
	}

	public Boolean testAbconfig(Settings settings, SortedMap<String, Configuration> compressedConfigss,
			SortedMap<String, Configuration> compressedConfigs2s) {
		Boolean flag = true;
		for (Entry<String, Configuration> source : compressedConfigss.entrySet()) {
			if (compressedConfigs2s.get(source.getKey()) != null) {
				if (!(source.getValue() == compressedConfigs2s.get(source.getKey()))) {
					flag = false;
					break;
				}
			} else {
				flag = false;
				break;
			}
		}
		return flag;
	}

	// add by lyh for routemap
	String getRoutemapname(ConfigModification c) {
		String tmp = c.toString();
		if (!tmp.contains("ADD") && !tmp.contains("REMOVE")) {
			String tmp2 = tmp.substring(tmp.indexOf("CHANGE ROUTER ") + 14);
			String name = tmp2.substring(0, tmp2.indexOf(" ROUTE_MAP"));
			System.out.println("route map's name-----------------------" + name);
			return name;
		} else {
			String tmp2 = tmp.substring(tmp.indexOf("ROUTER ") + 7);
			String name = tmp2.substring(0, tmp2.indexOf(" ~"));
			// System.out.println("route map's name--------------+++---------" + name);
			return name;
		}
	}

	private List<Prefix> findMatchPrefixList(If ss, RoutingPolicy RpA, List<Prefix> lp) {// Prefix prefix,
		Configuration c = RpA.getOwner();
		BooleanExpr expr = ss.getGuard();
		if (expr instanceof MatchPrefixSet) {
			// IP_PREFIX_LIST, IP_ACCESS_LIST:
			MatchPrefixSet matchPrefixSet = (MatchPrefixSet) expr;
			PrefixSetExpr prefixSetExpr = matchPrefixSet.getPrefixSet();
			if (prefixSetExpr instanceof ExplicitPrefixSet) {
				ExplicitPrefixSet explicitPrefixSet = (ExplicitPrefixSet) prefixSetExpr;
				// System.out.println(
				// "FLowcomp: PrefixSpaceRanges: " +
				// explicitPrefixSet.getPrefixSpace().getPrefixRanges());
				Set<PrefixRange> ranges = explicitPrefixSet.getPrefixSpace().getPrefixRanges();
				for (PrefixRange r : ranges) {
					System.out.println("  ----- " + r.getPrefix());
					lp.add(r.getPrefix());
				}
			} else {
				if (matchPrefixSet.getPrefixSet() instanceof NamedPrefixSet) {
					NamedPrefixSet st = (NamedPrefixSet) matchPrefixSet.getPrefixSet();
					String listName = st.getName();
					RouteFilterList routeFilterList = c.getRouteFilterLists().get(listName);
					List<RouteFilterLine> _lines = routeFilterList.getLines();
					for (RouteFilterLine l : _lines) {
						// System.out.println("hehhehhe----- " + l.getIpWildcard().toPrefix());
						lp.add(l.getIpWildcard().toPrefix());
					}
				}
			}
		}
		if (ss.getFalseStatements() != null) {
			List<Statement> ls = ss.getFalseStatements();
			if (this.ifanyif(ls)) {
				for (Statement sls : ls) {
					if (sls instanceof If) {
						If slsi = (If) sls;
						lp = findMatchPrefixList(slsi, RpA, lp);
					}
				}
				return lp;
			} else {
				return lp;
			}
		} else {
			return lp;
		}

	}

	Boolean ifanyif(List<Statement> all) {
		Boolean flag = false;
		for (Statement sls : all) {
			if (sls instanceof If || sls.toString().contains("~RMCLAUSE")) {
				flag = true;
				return flag;
			}
		}
		return flag;

	}

	List<Prefix> getPrefix(String mapname, Device deviceA) {
		List<Prefix> lp = new ArrayList<>();
		for (String routeNameA : deviceA.getRoutePoliNames()) {
			RoutingPolicy RpA = deviceA.getRoutingPolicy(routeNameA);

			if (routeNameA.contains("~RMCLAUSE~")) { // route map;
				List<Statement> statements = RpA.getStatements();
				for (Statement stmt : statements) {
					if (stmt instanceof If) {
						If ss = (If) stmt;
						lp = this.findMatchPrefixList(ss, RpA, lp);
					}
				}
			}
		}
		return lp;
	}

	List<Prefix> getPrefix2(RoutingPolicy RpA) {
		List<Prefix> lp = new ArrayList<>();
		if (RpA != null && RpA.getStatements() != null) {
			List<Statement> statements = RpA.getStatements();

			for (Statement stmt : statements) {
				if (stmt instanceof If && stmt.toString().contains("RMCLAUSE")) {
					If ss = (If) stmt;
					lp = this.findMatchPrefixList(ss, RpA, lp);
				}
			}
		}
		return lp;
	}

	// add by lyh
	public Map<String, Map<RoutingPolicy, List<Prefix>>> getpolicyGroupsRoute(
			Map<Integer, Map<String, List<ConfigModification>>> cmss, DeviceGraph deviceEtgt2) {
		Map<String, List<ConfigModification>> allConfigModifications = cmss.get(2);
		Map<String, Map<RoutingPolicy, List<Prefix>>> devmapfprefix = new LinkedHashMap<>();
		for (Entry<String, List<ConfigModification>> entry : allConfigModifications.entrySet()) {
			Device tmp = deviceEtgt2.getDevices().get(entry.getKey());
			Map<RoutingPolicy, List<Prefix>> mapfprefix = new LinkedHashMap<>();
			for (ConfigModification c : entry.getValue()) {
				mapfprefix.clear();
				String routeNameA = this.getRoutemapname(c);
				RoutingPolicy RpA = tmp.getRoutingPolicy(routeNameA);
				// if(RpA!=null) {
				// System.out.println("route map name is !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
				// "+routeNameA+" "+RpA.getName());
				// }
				List<Prefix> devicelist = this.getPrefix2(RpA);
				// for(Prefix p: devicelist) {
				// System.out.println("prefix is !!!!!!!!!!!!!!!!!!!!!!!!!!!!!! "+p);
				// }
				mapfprefix.put(RpA, devicelist);
			}
			devmapfprefix.put(entry.getKey(), mapfprefix);
		}
		// interfaces, are the related to this dst, changed interfaces;
		// interfaces-prefix_list;
		// policy groups;
		// policy-interfaces'(prefix_list);
		return devmapfprefix;
	}

	// public boolean isBlocked(RouteFilterList routeFilterList) {
	// for (RouteFilterLine line : routeFilterList.getLines()) {
	// // Check if the policy group is covered by the current line
	// if (this.within(line.getIpWildcard().toPrefix())) {
	// return (line.getAction() == LineAction.REJECT);
	// }
	// }
	//
	// // fixme?
	// return true;
	// }

	public boolean within(Prefix prefix, PolicyGroup pg) {// prefix cover this policy group
		if (prefix.getStartIp().asLong() <= pg.getStartIp().asLong()
				&& prefix.getEndIp().asLong() >= pg.getEndIp().asLong())
			return true;
		else
			return false;
	}

	List<PolicyGroup> getmappolicies(Prefix p, List<PolicyGroup> ps, List<PolicyGroup> lpoligroup) {
		for (PolicyGroup pp : ps) {
			if (within(p, pp)) {
				lpoligroup.add(pp);
				// System.out.println("tttttttttttt--overlap - "+p +"------- pg------ "+pp);
			}
		}
		return lpoligroup;
	}

	Map<PolicyGroup, List<Interface>> getmatchedinterfaces(List<PolicyGroup> ps,
			Map<String, Map<RoutingPolicy, List<Prefix>>> allprefix, DeviceGraph deviceEtgt2) {
		Map<PolicyGroup, List<Interface>> all = new LinkedHashMap<>();
		Map<RoutingPolicy, List<Interface>> allintf = new LinkedHashMap<>();

		for (Entry<String, Map<RoutingPolicy, List<Prefix>>> entry : allprefix.entrySet()) {
			Device tmp = deviceEtgt2.getDevice(entry.getKey());
			Collection<Interface> li = tmp.getInterfaces();
			List<Process> procs = tmp.getRoutingProcesses();

			for (Process p : procs) {
				if (p.getType() == edu.wisc.cs.arc.graphs.Process.ProcessType.BGP) {
					org.batfish.datamodel.BgpProcess bgpA = p.getBgpConfig();
					Collection<BgpActivePeerConfig> peers = bgpA.getActiveNeighbors().values();
					// BGP policy group in conf;
					for (BgpActivePeerConfig peer : peers) {
						Interface inf = tmp.getInterface(peer.getLocalIp());
						// System.out.println("~~~~~~~~~~~~" + inf.getFullName());
						// System.out.println("66666666666666666----peer---"+peer.getLocalIp()+"------"+peer.getPeerAddress()+"------"+peer.getLocalAs()+"------"+peer.getRemoteAs());
						// if (peer.getImportPolicy() != null) {
						if (peer.getExportPolicySources() != null) {
							for (String t : peer.getExportPolicySources()) {
								RoutingPolicy routingPolicy = tmp.getRoutingPolicy(t);
								if (entry.getValue().get(routingPolicy) != null) {
									if (allintf.get(routingPolicy) != null) {
										List<Interface> inftmpl = allintf.get(routingPolicy);
										inftmpl.add(inf);
										allintf.put(routingPolicy, inftmpl);
									} else {
										List<Interface> inftmpl = new ArrayList<>();
										inftmpl.add(inf);
										allintf.put(routingPolicy, inftmpl);
										// System.out.println(
										// "77777777777777-----------------------" + routingPolicy + inftmpl);
									}
									// System.out.println("77777777777777-----------------------" + t);
								}
							}
							if (peer.getImportPolicySources() != null) {
								for (String t : peer.getImportPolicySources()) {
									RoutingPolicy routingPolicy = tmp.getRoutingPolicy(t);
									if (entry.getValue().get(routingPolicy) != null) {
										List<Interface> inftmpl = allintf.get(routingPolicy);
										inftmpl.add(inf);
										allintf.put(routingPolicy, inftmpl);
										// System.out.println("9999999999-----------------------" + t);
									} else {
										List<Interface> inftmpl = new ArrayList<>();
										inftmpl.add(inf);
										allintf.put(routingPolicy, inftmpl);
									}
								}
							}
						}
					}
				}
			}
		}
		for (Entry<String, Map<RoutingPolicy, List<Prefix>>> entry : allprefix.entrySet()) {
			Map<RoutingPolicy, List<Prefix>> plist = entry.getValue();
			for (Entry<RoutingPolicy, List<Prefix>> entryt : plist.entrySet()) {
				List<PolicyGroup> lpoligroup = new ArrayList<>();
				// compute PolicyGroup related to this routemap,
				for (Prefix p : entryt.getValue()) {
					lpoligroup = this.getmappolicies(p, ps, lpoligroup);
				}
				// add interfaces to these PolicyGroups
				for (PolicyGroup pg : lpoligroup) {
					// System.out.println("tttttttttttt--- " + pg);
					if (all.get(pg) != null) {
						List<Interface> inftmpl = all.get(pg);
						inftmpl.addAll(allintf.get(entryt.getKey()));
						all.put(pg, inftmpl);
					} else {
						List<Interface> inftmpl = new ArrayList<>();
						if (allintf.get(entryt.getKey()) != null)
							inftmpl.addAll(allintf.get(entryt.getKey()));
						all.put(pg, inftmpl);
					}
				}
			}
		}

		return all;
	}

	public List<Flow> comAll(Settings settings, Map<String, Config> mapdevice) {
		List<Flow> diff = new ArrayList<Flow>();
		List<PolicyGroup> policyGroupsin = ConfigurationTasks.extractPolicyGroups(settings, mapdevice);
		for (PolicyGroup source : policyGroupsin) {
			for (PolicyGroup destination : policyGroupsin) {
				if (source.equals(destination)) {
					continue;
				}
				Flow flow = new Flow(source, destination);
				diff.add(flow);
			}
		}
		return diff;
	}

	public List<Flow> comInter(Settings settings, Map<Long, Map<String, Config>> mapdevice) {
		List<Flow> diff = new ArrayList<Flow>();
		for (Entry<Long, Map<String, Config>> entrys : mapdevice.entrySet()) {
			List<PolicyGroup> policyGroupsin = ConfigurationTasks.extractPolicyGroups(settings, entrys.getValue());
			{
				for (PolicyGroup source : policyGroupsin) {
					for (PolicyGroup destination : policyGroupsin) {
						if (source.equals(destination)) {
							continue;
						}
						Flow flow = new Flow(source, destination);
						diff.add(flow);
					}
				}
			}
		}
		return diff;
	}

	public List<Flow> comFlowsRoute(Settings settings, Map<Integer, Map<String, List<ConfigModification>>> cmss,
			List<PolicyGroup> policyGroups, List<PolicyGroup> policyGroups2, DeviceGraph deviceEtgt1,
			DeviceGraph deviceEtgt2, Map<String, Config> configs2) {
		// suppose route map not impact new policygroups;
		Logger logger = settings.getLogger();
		// compute traffic class's dsts in these route map's configs;
		// map PolicyGroup to related List<Interface>s
		List<PolicyGroup> policyGroupsRoute = new ArrayList<>();
		Map<String, Map<RoutingPolicy, List<Prefix>>> allprefix = this.getpolicyGroupsRoute(cmss, deviceEtgt1);
		for (Map<RoutingPolicy, List<Prefix>> t : allprefix.values()) {
			for (List<Prefix> tp : t.values()) {
				for (Prefix p : tp) {
					PolicyGroup group = new PolicyGroup(p);
					policyGroupsRoute.add(group);
				}
			}
		}
		// it did work!
		// external;
		for (PolicyGroup group : policyGroupsRoute) {
			System.out.println("\t" + group.toString() + (group.isInternal() ? " INTERNAL" : " EXTERNAL"));
		}
		//
		// List<PolicyGroup> toRemove = new ArrayList<PolicyGroup>();
		// for (PolicyGroup group : policyGroupsRoute) {
		// // Remove policy groups that are external, if requested
		// if ( !group.isInternal()) {
		// toRemove.add(group);
		// continue;
		// }
		// }
		// policyGroupsRoute.removeAll(toRemove);
		// // Compute non-overlapping policy groups
		policyGroupsRoute = PolicyGroup.getNonOverlapping(policyGroupsRoute);
		Collections.sort(policyGroupsRoute);

		Map<PolicyGroup, List<Interface>> pintlist = this.getmatchedinterfaces(policyGroupsRoute, allprefix,
				deviceEtgt2);
		// for each route map's interfaces, map <group, interface> by its prefixlist

		List<Flow> flows = new ArrayList<Flow>();
		for (PolicyGroup dst : policyGroupsRoute) {
			if (dst.isInternal()) {
				logger.info("compute traffic: dst to thers");
				for (PolicyGroup source : policyGroups) {
					if (source.equals(dst))
						continue;
					List<Interface> interfaces = pintlist.get(dst);
					// lyh: map to src, dst interfaces use Ips;
					Map<Integer, Map<String, String>> ifacemap = PolicyGroup.getPlolicyIface(dst, source, configs2,
							deviceEtgt2);
					Map<Integer, Interface> fimap = new LinkedHashMap<Integer, Interface>();
					Map<String, String> tmap = ifacemap.get(2);
					Map<String, String> tmap1 = ifacemap.get(1);
					for (Device t : deviceEtgt2.getDevices().values()) {
						for (Entry<String, String> entry : tmap.entrySet()) {
							if (t.getName().equals(entry.getKey())) {
								for (Interface intf : t.getInterfaces()) {
									if (intf.getName().equals(entry.getValue()))
										fimap.put(2, intf);
								}
							}
						}

						for (Entry<String, String> entry : tmap1.entrySet()) {
							if (t.getName().equals(entry.getKey())) {
								for (Interface intf : t.getInterfaces()) {
									if (intf.getName().equals(entry.getValue()))
										fimap.put(1, intf);
								}
							}
						}
					}

					Interface idst = fimap.get(2);
					Interface isrc = fimap.get(1);
					logger.info("use condition 2, remove some dst-srcs");
					Boolean c2flag = this.getDcomponents(settings, idst, isrc, interfaces, deviceEtgt2);
					if (c2flag) {
						Flow flow = new Flow(source, dst);
						flows.add(flow);
					}
				}
			} else {
				for (PolicyGroup source : policyGroups) {
					if (source.equals(dst))
						continue;
					Flow flow = new Flow(source, dst);
					flows.add(flow);
				}
			}
		}
		logger.info("lyh: count routemap diffflow --" + flows.size());
		return flows;
	}

	public static List<Flow> extract(IpAccessList acl) {
		List<Flow> groups = new ArrayList<Flow>();
		PolicyGroup t1 = new PolicyGroup();
		PolicyGroup t3 = new PolicyGroup();
		for (IpAccessListLine line : acl.getLines()) {
			MatchHeaderSpace match = (MatchHeaderSpace) line.getMatchCondition();
			HeaderSpace headerSpace = match.getHeaderspace();
			IpWildcard srcWildcard = ((IpWildcardIpSpace) headerSpace.getSrcIps()).getIpWildcard();
			Ip startIp = srcWildcard.getIp();
			Ip endIp = srcWildcard.getIp().getWildcardEndIp(srcWildcard.getWildcard());
			if (headerSpace.getSrcPorts().size() == 0) {
				t1 = new PolicyGroup(startIp, endIp, IpProtocol.IP);
				// groups.add(new PolicyGroup(startIp, endIp, IpProtocol.IP));
			} else {
				for (SubRange portRange : headerSpace.getSrcPorts()) {
					t1 = new PolicyGroup(startIp, endIp, portRange, IpProtocol.IP);
					// groups.add(new PolicyGroup(startIp, endIp, portRange, IpProtocol.IP)); //
					// FIXME add proper protocol
				}
			}

			IpWildcard dstWildcard = ((IpWildcardIpSpace) headerSpace.getDstIps()).getIpWildcard();
			startIp = dstWildcard.getIp();
			endIp = dstWildcard.getIp().getWildcardEndIp(dstWildcard.getWildcard());
			if (headerSpace.getDstPorts().size() == 0) {
				t3 = new PolicyGroup(startIp, endIp, IpProtocol.IP);
				// groups.add(new PolicyGroup(startIp, endIp, IpProtocol.IP));
			} else {
				for (SubRange portRange : headerSpace.getDstPorts()) {
					t3 = new PolicyGroup(startIp, endIp, portRange, IpProtocol.IP);
					// groups.add(new PolicyGroup(startIp, endIp, portRange, IpProtocol.IP)); //
					// FIXME add proper protocol
				}
			}
			Flow flow = new Flow(t1, t3);
//			System.out.println(flow.getSource().toString() + " ------------+++++++++++++--------------  "
//					+ flow.getDestination().toString());
			groups.add(flow);
		}
		return groups;
	}

	public List<Flow> comAclFlows(Settings settings, Map<Integer, Map<String, List<ConfigModification>>> cmss,
			List<PolicyGroup> policyGroups, List<PolicyGroup> policyGroups2, DeviceGraph deviceEtgt1,
			DeviceGraph deviceEtgt2, Map<String, Config> configs2) {
		Logger logger = settings.getLogger();
		List<Flow> flows = new ArrayList<Flow>();
		Map<String, List<ConfigModification>> aclchange = cmss.get(1);
		for (Entry<String, List<ConfigModification>> entrys : aclchange.entrySet()) {
			logger.info("lyh: device name " + entrys.getKey());
			Device tmp2 = deviceEtgt2.getDevice(entrys.getKey());
			Device tmp1 = deviceEtgt1.getDevice(entrys.getKey());
			for (ConfigModification m : entrys.getValue()) {
				String mm = m.toString();
				logger.info("lyh: changed acls ================ " + mm);
				if (mm.contains("LINE")) {
					List<Flow> groups1 = new ArrayList<Flow>();
					List<Flow> groups2 = new ArrayList<Flow>();
					String sacl = mm.substring(mm.indexOf("ACL") + 4);
					String acl = sacl.substring(0, sacl.indexOf(" L"));
					IpAccessList aclA = tmp2.getAcl(acl);
					IpAccessList aclB = tmp1.getAcl(acl);
					groups1 = extract(aclA);
					groups2 = extract(aclB);
					for (Flow f : groups1) {
						int flag = 0;
						for (Flow f2 : groups2) {
							if (f.toString().contains(f2.toString())) {
								flag = 1;
								break;
							}
						}
						if (flag == 0) {
							if (f.getSource().toString().contains("0.0.0.0-255.255.255.255")) {
								// System.out.println("hehhehhehhe++++++++++++++== " + f);
								for (PolicyGroup src : policyGroups) {
									flows.add(new Flow(src, f.getDestination()));
								}
							} else if (f.getDestination().toString().contains("0.0.0.0-255.255.255.255")) {
								for (PolicyGroup dst : policyGroups) {
									flows.add(new Flow(f.getDestination(), dst));
								}
							} else {
								flows.add(f);
							}
						}
					}
				}
			}

		}
		return flows;
	}

	public Boolean getDcomponents(Settings settings, Interface dst, Interface src, List<Interface> interfaces,
			DeviceGraph etg) {
		Logger logger = settings.getLogger();
		Boolean pathflag = false;
		DeviceGraph etgClone = (DeviceGraph) etg.clone();

		// lyh: rewirte remove edges related to these interfaces;
		etgClone.prune(interfaces);

		// Check if connect, src, dst, and change
		List<String> changes = new ArrayList<String>();
		for (Interface intfs : interfaces) {
			changes.add(intfs.getDevice().getName());
		}
		String srcs = src.getDevice().getName();
		String dsts = dst.getDevice().getName();
		ConnectivityInspector connectivityInspector = new ConnectivityInspector(etgClone.getGraph());
		List<Set<DeviceVertex>> weaklyConnectedSet = connectivityInspector.connectedSets();
		System.out.println("Weakly connected components:");
		for (int i = 0; i < weaklyConnectedSet.size(); i++) {
			System.out.println(weaklyConnectedSet.get(i));
			if (Allornone(srcs, dsts, changes, weaklyConnectedSet.get(i))) {
				pathflag = true;
				if (pathflag) {
					// System.out.println("===================================true");
					break;
				}
			}
			// System.out.println("===================================false");
		}

		if (pathflag == false) {
			logger.info("if one of dvtsinterfaces's device neighoor and src, and dst in the same connected components");
		}
		// if true, in same component, route map will take affect
		return pathflag;
	}

	public Boolean checkAbroutedst(Prefix pdst, Map<String, Map<RoutingPolicy, List<Prefix>>> allprefix,
			Map<String, Map<RoutingPolicy, List<Prefix>>> allprefix2) {

		for (Map<RoutingPolicy, List<Prefix>> t : allprefix2.values()) {
			for (List<Prefix> tp : t.values()) {
				for (Prefix p : tp) {
					if ((p.getEndIp().asLong() >= pdst.getEndIp().asLong())
							&& (p.getStartIp().asLong() <= pdst.getEndIp().asLong())) {
						// System.out.println(" this is prefix ab "+p);
						return true;
					}
				}
			}
		}
		for (Map<RoutingPolicy, List<Prefix>> t : allprefix.values()) {
			for (List<Prefix> tp : t.values()) {
				for (Prefix p : tp) {
					if ((p.getEndIp().asLong() >= pdst.getEndIp().asLong())
							&& (p.getStartIp().asLong() <= pdst.getEndIp().asLong())) {
						// System.out.println(" this is prefix ab "+p);
						return true;
					}
				}
			}
		}
		return false;
	}

	public static Boolean Allornone(String srcs, String dsts, List<String> changes, Set<DeviceVertex> all) {
		Boolean flag = false;
		Boolean tflag = true;
		Boolean tmpflag = true;
		for (String tmp : changes) {
			tmpflag = false;
			for (DeviceVertex tmp2 : all) {
				if (tmp2.getName().equals(tmp)) {
					tmpflag = true;
				}
			}
			if (tmpflag == false) {
				tflag = false;
				break;
			}
		}

		Boolean sflag = false;
		if (tflag == true) {
			for (DeviceVertex tmp2 : all) {
				if (tmp2.getName().equals(srcs)) {
					sflag = true;
					continue;
				}
			}
			if (sflag) {
				for (DeviceVertex tmp2 : all) {
					if (tmp2.getName().equals(dsts)) {
						flag = true;
						// System.out.println("~~~~~~~~~~"+srcs+"~~~~~~~~~~"+dsts+ " yes");
						return flag;
					}
				}
			}
		}
		return flag;
	}
}