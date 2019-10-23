
import edu.wisc.cs.arc.configs.comAbs;
import edu.wisc.cs.arc.configs.Config;
import edu.wisc.cs.arc.configs.ConfigurationTasks;
import edu.wisc.cs.arc.graphs.*;
import edu.wisc.cs.arc.graphs.Process;
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
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.Vrf;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.symbolic.abstraction.Abstraction;
import org.batfish.symbolic.abstraction.DestinationClasses;
import org.batfish.symbolic.abstraction.NetworkSlice;
import org.batfish.symbolic.utils.Tuple;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import org.batfish.datamodel.AclIpSpace;
import org.batfish.datamodel.BgpActivePeerConfig;
import org.batfish.datamodel.BgpProcess;

public class DriverDiff {
	public static void main(String[] args) throws IOException {
		long startTime = System.currentTimeMillis();
		// Load settings, configs, and configs2 //lyh
		Settings settings = null;

		Settingsnew settingsnew = null;
		try {
			settings = new Settings(args);
			Logger logger1 = settings.getLogger();
			settingsnew = new Settingsnew(args, logger1);

		} catch (ParseException e) {
			System.err.println(e.getMessage());
			System.err.println("Run with '-" + Settings.HELP + "' to show help");
			return;
		}
		Logger logger = settings.getLogger();
		// logger.info("*** Settings ***");
		// logger.info(settings.toString());
		// System.out.println(settingsnew.toString());

		// Edit/convert policies
		if (settings.shouldEditPolicies()) {
			PolicyEditor editor = new PolicyEditor(settings.getPoliciesEditFile());
			editor.run();
			return;
		} else if (settings.shouldConvertPolicies()) {
			MinesweeperTasks.convertMinesweeperPolicies(settings);
			return;
		}

		// Load configurations
		Map<String, Config> configs = ConfigurationTasks.loadConfigurations(settings);

		logger.info("*** lyh load config2 ***");
		// Load the second configurations
		Map<String, Config> configs2 = ConfigurationTasks.loadConfigurations2(settingsnew);
		logger.info("*** lyh load config2 ***");

		// Filter and anonymize configurations
		if (settings.shouldAnonymize() || settings.shouldExcludeNonRouters()) {
			ConfigurationTasks.filterAndAnonymizeConfigurations(settings, configs);
		}
		if (0 == configs.size()) {
			logger.fatal("No configurations (after filtering)");
			System.exit(1);
		}

		// Simplify configurations
		if (settings.shouldSimplifyConfigs()) {
			ConfigurationTasks.simplifyConfigs(settings, configs);
			if (settings.shouldSaveSimpleConfigs()) {
				ConfigurationTasks.saveSimplifiedConfig(settings, configs);
			}
		}

		List<PolicyGroup> policyGroups = ConfigurationTasks.extractPolicyGroups(settings, configs);
		if (0 == policyGroups.size()) {
			logger.fatal("No policy groups");
			System.exit(1);
		}

		// change by lyh
		// Determine policy groups2
		List<PolicyGroup> policyGroups2 = ConfigurationTasks.extractPolicyGroups(settings, configs2);
		if (0 == policyGroups2.size()) {
			logger.fatal("No policy groups");
			System.exit(1);
		}

		// Create devices from configurations
		Map<String, Device> devices = new LinkedHashMap<String, Device>();
		for (Entry<String, Config> entry : configs.entrySet()) {
			// if( entry.getValue().getGenericConfiguration()!=null) {
			// logger.info("getGenericConfiguration() is not null");
			// }
			Device device = new Device(entry.getKey(), entry.getValue().getGenericConfiguration(), logger);
			devices.put(device.getName(), device);
		}
		logger.info("COUNT: devices " + devices.size());
		logger.info("Devices:");
		// for (Device device : devices.values()) {
		// logger.info("\t"+device.getName());
		// }

		// change by lyh
		// Create devices from configurations2
		Map<String, Device> devices2 = new LinkedHashMap<String, Device>();
		for (Entry<String, Config> entry : configs2.entrySet()) {
			Device device = new Device(entry.getKey(), entry.getValue().getGenericConfiguration(), logger);
			devices2.put(device.getName(), device);
		}
		logger.info("COUNT: devices2 " + devices2.size());
		// logger.info("Devices2:");
		// for (Device device : devices2.values()) {
		// logger.info("\t"+device.getName());
		// }

		// Generate file of policies to check with Minesweeper
		if (settings.shouldGenerateMinesweeperPolicies()) {
			MinesweeperTasks.generateMinesweeperPolicyList(settings, policyGroups, devices.values());
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

		// change by lyh, important
		// TODO:
		Map<Integer, Map<String, List<ConfigModification>>> allConfigModifications = ConfigurationTasks
				.compareAllConfigs(devices, devices2);

		FlowComp flowcmp = new FlowComp();
		List<Flow> diffs = new ArrayList<Flow>();
		comAbs cab = new comAbs();

		//
		int type = ConfigurationTasks.compareConfigsType(allConfigModifications);
		System.out.println("lyh----config change type ----~~~~~~~~ " + type);

		// Get configs for batfish
		Map<String, Configuration> configs2s = new LinkedHashMap<String, Configuration>();
		for (Entry<String, Config> en2 : configs2.entrySet()) {
			configs2s.put(en2.getKey(), en2.getValue().getGenericConfiguration());
		}
		Map<String, Configuration> configss = new LinkedHashMap<String, Configuration>();
		for (Entry<String, Config> en2 : configs.entrySet()) {
			configss.put(en2.getKey(), en2.getValue().getGenericConfiguration());
		}
		System.out.println("lyh----policyGroups' size ----~~~~~~~~ " + policyGroups.size());

		Map<String, List<ConfigModification>> re = allConfigModifications.get(4);
		Map<String, List<ConfigModification>> ad = allConfigModifications.get(5);

		long endTime = System.currentTimeMillis();
		logger.info("TIME: stage1 parsing " + (endTime - startTime) + " ms");

		// add, if it is from campus, then we will not make abstraction for it;
		int flagcampus = 0;
		for (String t : args) {
			if (t.contains("campus")) {
				flagcampus = 1;
				break;
			}
		}
		if (flagcampus == 1) {
			// test acl and routemap first;
			System.out.println("lyh ########################## for campus networks  " + type);
			if (type == 1) {
				logger.info("Campus: Only routemaps change.\t");
				DeviceGraph deviceEtgt1 = new DeviceGraph(devices.values(), settings, null);
				DeviceGraph deviceEtgt2 = new DeviceGraph(devices2.values(), settings, null);
				// if cm only have routemap
				List<Flow> diffroute = flowcmp.comFlowsRoute(settings, allConfigModifications, policyGroups,
						policyGroups2, deviceEtgt1, deviceEtgt2, configs2);
				for (Flow fl : diffroute) {
					diffs.add(fl);
				}
				logger.info("Campus: Count routemap, diff flows --" + diffroute.size());
			}
			if (type == 2) {
				List<Flow> diff = flowcmp.comFlows(settings, policyGroups, policyGroups2);
				for (Flow fl : diff) {
					diffs.add(fl);
				}
				logger.info("Campus: Only acls change, diff flows --" + diff.size());
			}
			if (type == 3) {
				logger.info("Campus: Routemaps and acl change \t");
				DeviceGraph deviceEtgt1 = new DeviceGraph(devices.values(), settings, null);
				DeviceGraph deviceEtgt2 = new DeviceGraph(devices2.values(), settings, null);
				List<Flow> diffroute = flowcmp.comFlowsRoute(settings, allConfigModifications, policyGroups,
						policyGroups2, deviceEtgt1, deviceEtgt2, configs2);
				for (Flow fl : diffroute) {
					diffs.add(fl);
				}
				List<Flow> diff = new ArrayList<Flow>();
				diff = flowcmp.comAclFlows(settings, allConfigModifications, policyGroups, policyGroups2, deviceEtgt1,
						deviceEtgt2, configs2);
				for (Flow t : diff) {
					diffs.add(t);
				}
				logger.info("Campus: ACL add.  diff flows -- " + diffs.size());
			}

			if (allConfigModifications.get(3).size() > 0) {
				int ebgptag = 0;
				for (Entry<String, List<ConfigModification>> entryss : allConfigModifications.get(3).entrySet()) {
					// System.out.println("show get(3) ************************************ " +
					// entryss);
					String ipaddress = entryss.toString();
					if (ipaddress.contains("BGP_NEIGHBOR")) {
						String tmp2 = ipaddress.substring(ipaddress.indexOf("BGP_NEIGHBOR") + 13);
						String tmp3 = tmp2.substring(0, tmp2.indexOf(" ~"));
						// System.out.println("show get(3) ---------------------------------- " +
						// entryss.getKey()
						// + " ***" + tmp3 + "**");
						Configuration thisconf = configs.get(entryss.getKey()).getGenericConfiguration();
						BgpProcess thisp = thisconf.getDefaultVrf().getBgpProcess();
						for (BgpActivePeerConfig n : thisp.getActiveNeighbors().values()) {
							if (n.getPeerAddress() != null && (n.getPeerAddress().toString().contains(tmp3)
									|| tmp3.contains(n.getPeerAddress().toString()))) {
								// System.out.println(
								// "got it ----------------------------------" + n.getPeerAddress().toString());
								if (n.getLocalAs() == n.getRemoteAs()) {
									ebgptag = 0;
									break;
								}
							}
						}
						ebgptag = 1;
					}
				}
				if (ebgptag == 1) {
					logger.info("Campus: Only inter-bgp change. \t");
					System.out.println("Campus: all traffic - internal-traffic   ************************************");
					Map<Long, Map<String, Config>> mapdevice = new LinkedHashMap<Long, Map<String, Config>>();
					for (Entry<String, Config> entry : configs.entrySet()) {
						Configuration conf = entry.getValue().getGenericConfiguration();
						BgpProcess p = conf.getDefaultVrf().getBgpProcess();
						if (p != null) {
							int one = 0;
							for (BgpActivePeerConfig n : p.getActiveNeighbors().values()) {
								if (one == 0) {
									if (mapdevice.isEmpty() || mapdevice.get(n.getLocalAs()) == null) {
										Map<String, Config> tp = new LinkedHashMap<String, Config>();
										tp.put(entry.getKey(), entry.getValue());
										// System.out.println("BGP AS -----------> " + n.getLocalAs() + " " +
										// entry.getKey());
										mapdevice.put(n.getLocalAs(), tp);
									} else {
										Map<String, Config> tp = mapdevice.get(n.getLocalAs());
										tp.put(entry.getKey(), entry.getValue());
										mapdevice.put(n.getLocalAs(), tp);
									}
									one = 1;
								}
							}
						}
					}
					List<Flow> internal = flowcmp.comInter(settings, mapdevice);
					logger.info("Campus:  internal.  except diffflow-- " + internal.size());
					List<Flow> all = flowcmp.comAll(settings, configs2);
					logger.info("Campus:  all-internal.  except diffflow ******************************** " + all.size()
							+ "  " + internal.size() +" " + (all.size()-internal.size()));

					long endTime2 = System.currentTimeMillis();
					logger.info("TIME: stage1 parsing " + (endTime - startTime) + " ms");
					logger.info("TIME: stage2 dermining " + (endTime2 - endTime) + " ms");
					logger.info("TIME: ALL " + (endTime - startTime + endTime2 - endTime) + " ms");
					logger.info("************************");
					
					return;
				} else {
					System.out.println("=> all traffic");
					return;
				}
			}
			return;
			// then remove inter-BGP process
		}

		Map<String, List<ConfigModification>> m11 = allConfigModifications.get(4);// add
		Map<String, List<ConfigModification>> m22 = allConfigModifications.get(5);
		//// compute plocy.src for removed or added devices

		Map<String, Config> configst1 = new LinkedHashMap<String, Config>();
		Map<String, Config> configst2 = new LinkedHashMap<String, Config>();

		List<String> tkey2 = new ArrayList<String>();
		if(m22!=null) {
		for (Entry<String, List<ConfigModification>> me : m22.entrySet()) {// remove
			String t = me.getKey();
			System.out.print(t + " ++++++  ");
			tkey2.add(t);
		}
		}
		System.out.println();

		if (m22 != null) {
			for (Entry<String, Config> ec : configs.entrySet()) {
				for (String t : tkey2) {// remove
					if (ec.getKey().contentEquals(t)) {
						System.out.print(ec.getKey() + " +++  ");
						configst2.put(ec.getKey(), ec.getValue());
					}
					// else {
					// System.out.print(ec.getKey() + " ~ ");
					// }
				}
			}
		}
		List<PolicyGroup> policyGroupst2 = ConfigurationTasks.extractPolicyGroups(settings, configst2);
		if (0 == policyGroupst2.size()) {
			System.out.println("0 == policyGroupst2.size()");
		} else {
			System.out.println("0 != policyGroupst2.size() ~~~~~~~~~~~~~ " + policyGroupst2.size());
		}

		if (m11 != null) {
			for (Entry<String, Config> ec : configs2.entrySet()) {
				for (Entry<String, List<ConfigModification>> me : m11.entrySet()) {// add
					String t = me.getKey();
					if (ec.getKey().contentEquals(t)) {
						System.out.print(ec.getKey() + " - ");
						configst1.put(ec.getKey(), ec.getValue());
					}
				}
			}
		}
		List<PolicyGroup> policyGroupst1 = ConfigurationTasks.extractPolicyGroups(settings, configst1);
		if (0 == policyGroupst1.size()) {
			System.out.println("0 == policyGroupst1.size()");
		} else {
			System.out.println("0 != policyGroupst1.size() ------  " + policyGroupst1.size());
		}

		// if (type == 0) {
		// test bonsai // route map miner type=-1 //only acl type2
		if (type == 0 || type == 1 || type == -1 || type == 2) {
			logger.info("Changes: instances, redistribution, agreegation etc.\t");

			HeaderSpace line1 = new HeaderSpace();
			HeaderSpace line2 = new HeaderSpace();
			comAbs cabt1 = new comAbs();
			comAbs cabt2 = new comAbs();
			logger.info("Compute abstraction for each EC------- ");
			List<NetworkSlice> ecs = cabt1.comAslices(configss, line1);
			// System.out.println(
			// "************************************************************************************************");
			List<NetworkSlice> ecs2 = cabt2.comAslices(configs2s, line2);
			System.out.println("Abstraction slice  sizes ------- " + ecs.size() + "----" + ecs2.size());
			// find related sources//gigger one as all
			DestinationClasses comDst = cabt1.comDst(configss, line1);
			DestinationClasses comDst2 = cabt1.comDst(configs2s, line2);
			List<HeaderSpace> th1 = new ArrayList<HeaderSpace>();
			List<HeaderSpace> th2 = new ArrayList<HeaderSpace>();
			if (re != null) {
				th1 = cabt1.getHeadlist(comDst2, re);
			}
			if (ad != null) {
				th2 = cabt1.getHeadlist(comDst, ad);
			}
			List<PolicyGroup> apg = cabt1.getPolicyGroup(th1, th2);

			List<Flow> flowa = new ArrayList<Flow>();
			int tt = 0;
			int acltypes = 0; // final handle acl change, even it is abstraction config

			for (NetworkSlice t1 : ecs) {
				for (NetworkSlice t2 : ecs2) {
					HeaderSpace h1 = t1.getHeaderSpace();
					HeaderSpace h2 = t2.getHeaderSpace();
					if (h1.equals(h2)) {
						tt++;
						// if (tt > 6)
						// break;
						logger.info("Find the same header ------- " + h1.toString());
						IpSpace ips = h1.getDstIps();
						String tmp = ips.toString();
						String tmp2 = tmp.substring(tmp.indexOf("whitelist=[") + 11);
						String tmp3 = tmp2.substring(0, tmp2.indexOf("]"));
						if (tmp3.indexOf("/") == -1) {
							tmp3 = tmp3 + "/32";
						}
						// TODO improve, to handle the second one also; whitelist=[1.2.2.2,
						if (tmp3.contains(",")) {
							tmp3 = tmp2.substring(0, tmp3.indexOf(","));
							if (tmp3.indexOf("/") == -1) {
								tmp3 = tmp3 + "/32";
							}
						}
						Prefix pdst = Prefix.parse(tmp3);
						PolicyGroup pdest = new PolicyGroup(pdst);

						Map<String, Device> abdevices = new LinkedHashMap<String, Device>();
						Map<String, Configuration> _configurations = cab.getCompressMap(settings, t1);
						Map<String, Configuration> _configurations2 = cab.getCompressMap(settings, t2);
						Map<String, Device> abdevices2 = new LinkedHashMap<String, Device>();
						for (Entry<String, Configuration> entry : _configurations.entrySet()) {
							Device abdevice = new Device(entry.getKey(), entry.getValue(), logger);
							abdevices.put(abdevice.getName(), abdevice);
							// logger.info("Device name ----- " + abdevice.getName());
						}
						// logger.info("COUNT: devices " + abdevices.size());
						for (Entry<String, Configuration> entry : _configurations2.entrySet()) {
							// TODO: Something wrong with abstraction config
							Device abdevice2 = new Device(entry.getKey(), entry.getValue(), logger);
							abdevices2.put(abdevice2.getName(), abdevice2);
							// logger.info("Device name2 ----- " + abdevice2.getName());
						}
						logger.info("COUNT: devices2 " + abdevices2.size());

						// compare abstract configs may TODO
						Map<Integer, Map<String, List<ConfigModification>>> aballConfigModifications = ConfigurationTasks
								.compareAllConfigs(abdevices, abdevices2);

						// compare topo, edge6, 7;
						// Get 4 or 5, add or remove device
						Map<String, List<ConfigModification>> m1 = aballConfigModifications.get(4);// add
						Map<String, List<ConfigModification>> m2 = aballConfigModifications.get(5);
						Map<String, Integer> tm = t1.getAbstraction().getAbstractionMap().getgroupMap();
						int alflag = 0;
						if (m1 != null && m2 != null && m1.entrySet().size() == m2.entrySet().size()) {
							for (Entry<String, List<ConfigModification>> e1 : m1.entrySet()) {
								int flag = 0;
								for (Entry<String, List<ConfigModification>> e2 : m1.entrySet()) {
									if (tm.get(e1.getKey()) == tm.get(e2.getKey())) {
										flag = 1;
									}
								}
								if (flag == 0) {
									alflag = 1;
									break;
								}
							}
							if (alflag == 0) {// topology changed easy
								System.out.println("Topo indeed same abstraction. edge easy!");
							}
						} else if (m1 == null && m2 == null) {
							System.out.println("Abstract topo unchanged. same!");
							alflag = 2;
						} else {
							System.out.println("Abstract topo changed. complex!");
							// special, like edge6 and edge7
							if (cabt1.comConAbsChange(ad, re, m1, m2)) {
								alflag = 0;//
								System.out.println("Topo indeed same. edge easy!");
							} else {
								alflag = 1;
							}
						}
						// alflag=2 topo same, alflag=0, indeed same, afllag=1 complex
						DeviceGraph abdeviceEtgt1 = new DeviceGraph(abdevices.values(), settings, null);
						DeviceGraph abdeviceEtgt2 = new DeviceGraph(abdevices2.values(), settings, null);
						// TODO: Boolean cflag = cab.compareAbcon(settings, abdevices, abdevices2,
						int typeab = ConfigurationTasks.compareConfigsType(aballConfigModifications);

						if (alflag == 0) {
							// don't compare routinprocess now; don't compare some and its neighbor,future;
							// compare routemap and acl
							if (allConfigModifications.get(2).size() == 0
									&& allConfigModifications.get(1).size() == 0) {
								// no routemap and acl change;
								// add changed flows
								if (policyGroupst1.size() != 0) {
									for (PolicyGroup pg : policyGroupst1) {
										Flow flow = new Flow(pg, pdest);
										flowa.add(flow);
									}
								}
								if (policyGroupst2.size() != 0) {
									for (PolicyGroup pg : policyGroupst2) {
										Flow flow = new Flow(pg, pdest);
										flowa.add(flow);
									}
								}
								logger.info("They are the same!!!!   diffflow--" + flowa.size());
							} else {
								if (policyGroupst1.size() != 0) {
									for (PolicyGroup pg : policyGroupst1) {
										Flow flow = new Flow(pg, pdest);
										flowa.add(flow);
									}
								}
								if (policyGroupst2.size() != 0) {
									for (PolicyGroup pg : policyGroupst2) {
										Flow flow = new Flow(pg, pdest);
										flowa.add(flow);
									}
								}
								// TODO
								logger.info("They are the part same!!!!, only routemap and acl");
								if (allConfigModifications.get(2).size() != 0) {
									// for (Entry<String, List<ConfigModification>> en :
									// allConfigModifications.get(2)
									// .entrySet()) {
									// logger.info("TODO: show routemap change----- " + en.getKey() + " "
									// + en.getValue());
									// }
									logger.info("For routemapschange, then check if related to this dst \t");
									Boolean routeflag = false;
									DeviceGraph deviceEtgt1 = new DeviceGraph(devices.values(), settings, null);
									DeviceGraph deviceEtgt2 = new DeviceGraph(devices2.values(), settings, null);
									Map<String, Map<RoutingPolicy, List<Prefix>>> allprefix = flowcmp
											.getpolicyGroupsRoute(aballConfigModifications, deviceEtgt1);
									Map<String, Map<RoutingPolicy, List<Prefix>>> allprefix2 = flowcmp
											.getpolicyGroupsRoute(aballConfigModifications, deviceEtgt2);
									routeflag = flowcmp.checkAbroutedst(pdst, allprefix, allprefix2);
									if (routeflag) {
										logger.info(
												"Use concrete config, add somthing to dst. TODO: can be improve \t");
										for (PolicyGroup pg : policyGroups) {
											Flow flow = new Flow(pg, pdest);
											flowa.add(flow);
										}
										logger.info(
												"They are added due to ab routemap!!!!   diffflow--" + flowa.size());
									} else {
										logger.info(
												"perfixes in routemaps are not related to this dst. so the control plane are the same\t");

										logger.info("No addition !!!!   diffflow--" + flowa.size());
									}
								}
								if (allConfigModifications.get(1).size() != 0) {
									acltypes = 1;
								}
							}
						} else if ((typeab == -1 || typeab == 2)) {
							logger.info(
									"cp abstraction is eq; then don't need compute dabs, only compare specialed acls");
							if (allConfigModifications.get(1).size() == 0) {
								logger.info("They are the same (data plane) !!!!");
								if(policyGroupst1.size()!=0) {
									for (PolicyGroup pg : policyGroupst1) {
										Flow flow = new Flow(pg, pdest);
										flowa.add(flow);
									}
								}
								if(policyGroupst2.size()!=0) {
									for (PolicyGroup pg : policyGroupst2) {
										Flow flow = new Flow(pg, pdest);
										flowa.add(flow);
									}
								}
								logger.info("They are the same!!!!   diff flow size --" + flowa.size());
							} else {
								// TODO
								if(policyGroupst1.size()!=0) {
									for (PolicyGroup pg : policyGroupst1) {
										Flow flow = new Flow(pg, pdest);
										flowa.add(flow);
									}
								}
								if(policyGroupst2.size()!=0) {
									for (PolicyGroup pg : policyGroupst2) {
										Flow flow = new Flow(pg, pdest);
										flowa.add(flow);
									}
								}
								logger.info("TODO: They are the part same!!!!, only acl different!");
								acltypes = 1;

							}
							// bug
							// for (PolicyGroup src : policyGroups2) {
							// // TODO:
							// List<Flow> diffacl = flowcmp.comFlowsAclHeader(settings, src, h1,
							// aballConfigModifications, abdeviceEtgt1, abdeviceEtgt2, configs2);
							// for (Flow t : diffacl) {
							// diffs.add(t);
							// }
							// }
						} else {
							logger.info("cp's ab is not eq, then check difference, if only route map and acl");
							if (typeab == 0) {
								logger.info("Routing process change \t");
								List<Flow> tmpabdiff = flowcmp.comFlowsHeader(settings, h1, policyGroups2);
								for (Flow t : tmpabdiff) {
									diffs.add(t);
								}
								logger.info("All diff flows --" + diffs.size());
							}
							if (typeab == 1 || typeab == 3) {// type=3, route map and acl, only handel route map, acl
																// for the last
								logger.info("For routemapschange, then check if related to this dst \t");
								Boolean routeflag = false;
								DeviceGraph deviceEtgt1 = new DeviceGraph(devices.values(), settings, null);
								DeviceGraph deviceEtgt2 = new DeviceGraph(devices2.values(), settings, null);
								Map<String, Map<RoutingPolicy, List<Prefix>>> allprefix = flowcmp
										.getpolicyGroupsRoute(aballConfigModifications, deviceEtgt1);
								Map<String, Map<RoutingPolicy, List<Prefix>>> allprefix2 = flowcmp
										.getpolicyGroupsRoute(aballConfigModifications, deviceEtgt2);
								// check the dst is in these prefixes
								routeflag = flowcmp.checkAbroutedst(pdst, allprefix, allprefix2);
								if (routeflag) {
									logger.info("Use concrete config, add somthing to dst. TODO: can be improve \t");
									// TODO: can be improve, the slicing of condition 1
									// List<Flow> diffroute = flowcmp.comFlowsRoute(settings,
									// allConfigModifications, policyGroups, policyGroups2,
									// deviceEtgt1, deviceEtgt2, configs2);
									for (PolicyGroup pg : policyGroups) {
										Flow flow = new Flow(pg, pdest);
										flowa.add(flow);
									}
									logger.info("They are added due to ab routemap!!!!   diffflow--" + flowa.size());
								} else {
									logger.info(
											"perfixes in routemaps are not related to this dst. so the control plane are the same\t");
									if(policyGroupst1.size()!=0) {
										for (PolicyGroup pg : policyGroupst1) {
											Flow flow = new Flow(pg, pdest);
											flowa.add(flow);
										}
									}
									if(policyGroupst2.size()!=0) {
										for (PolicyGroup pg : policyGroupst2) {
											Flow flow = new Flow(pg, pdest);
											flowa.add(flow);
										}
									}
									logger.info("No addition !!!!   diffflow--" + flowa.size());
								}
								// then chenk whether need to handel acl

								if (typeab == 3) {
									logger.info("Then handel acls change; acltypes=1. \t");
									acltypes = 1;
								}
							}
						}
					}
				}
			}
			if (acltypes == 1) {
				List<Flow> diff = new ArrayList<Flow>();
				DeviceGraph deviceEtgt1 = new DeviceGraph(devices.values(), settings, null);
				DeviceGraph deviceEtgt2 = new DeviceGraph(devices2.values(), settings, null);
				diff = flowcmp.comAclFlows(settings, allConfigModifications, policyGroups, policyGroups2, deviceEtgt1,
						deviceEtgt2, configs2);
				for (Flow t : diff) {
					flowa.add(t);
				}
				logger.info("ACL add.  diffflow-- " + flowa.size());
			}

			logger.info("Count3: Affected Flows --" + (diffs.size() + flowa.size()));
			logger.info("Count: Affected Flows **************  " + flowa.size());
			logger.info("************************" + tt);
		}

		if (type == 1) {
			logger.info("lyh: only routemaps change \t");
			DeviceGraph deviceEtgt1 = new DeviceGraph(devices.values(), settings, null);
			DeviceGraph deviceEtgt2 = new DeviceGraph(devices2.values(), settings, null);
			// if cm only have routemap
			List<Flow> diffroute = flowcmp.comFlowsRoute(settings, allConfigModifications, policyGroups, policyGroups2,
					deviceEtgt1, deviceEtgt2, configs2);
			for (Flow fl : diffroute) {
				diffs.add(fl);
			}
			logger.info("Count: Affected Flows --" + diffs.size());
		}

		if (type == 2) {
			logger.info("lyh: only acls change \t");
			List<Flow> diff = flowcmp.comFlows(settings, policyGroups, policyGroups2);
			for (Flow fl : diff) {
				diffs.add(fl);
			}
			logger.info("Count:Affected Flows --" + diffs.size());
		}
		logger.info("Count2: Affected Flows --" + diffs.size());

		long endTime2 = System.currentTimeMillis();
		logger.info("TIME: stage1 parsing " + (endTime - startTime) + " ms");
		logger.info("TIME: stage2 dermining " + (endTime2 - endTime) + " ms");
		logger.info("TIME: ALL " + (endTime - startTime + endTime2 - endTime) + " ms");
		logger.info("************************");
		// for (Flow fl : diffs) {
		// Prefix srcs=fl.getSource().getPrefix();
		// Prefix dsts=fl.getDestination().getPrefix();
		// // which devices;
		// DeviceGraph deviceEtg2 = new DeviceGraph(devices2.values(), settings, null);
		// FlowComp flocmp =new FlowComp();
		// Map<Integer, Device> fimap= flocmp.commines(fl.getDestination(),
		// fl.getSource(), configs2, deviceEtg2);
		// fimap.get(1);
		// fimap.get(2);
		// }

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
		// logger.info("*** Generate device-based ETG ***");
		DeviceGraph deviceEtg = new DeviceGraph(devices.values(), settings, waypoints);
		// System.out.println("COUNT: deviceETGVertices " + deviceEtg.getVertexCount());
		// System.out.println("COUNT: deviceETGEdges " + deviceEtg.getEdgeCount());
		// System.out.println("device-based ETG "
		// + deviceEtg);
		// List<Interface> itface = new ArrayList<Interface>();
		// for(Device t:deviceEtg.getDevices().values()) {
		// String strt = t.getName();
		// String strt2 = "as2border2";
		//// System.out.println("lyh~~~~~~~~~~~~~~~~~~~~~~~~~"+strt);
		// if(strt.equals(strt2)) {
		// for(Interface intf: t.getInterfaces()) {
		// itface.add(intf);
		//// System.out.println("lyh----------------------"+intf);
		// }
		//
		// }
		// }
		// System.out.println("device-based ETG "
		// + deviceEtg);
		// for (Entry<String, Config> entry : configs2.entrySet()) {
		// for (Interface iface :
		// entry.getValue().getGenericConfiguration().getInterfaces().values()) {
		// System.out.println("lyh+++++++++++"+iface.getName());
		//
		// }
		// }
		// flowcmp.getDcomponents( settings, itface.get(0), itface.get(1),
		// itface, deviceEtg);

		// Generate VIRL
		if (settings.shouldGenerateVirl()) {
			VirlConfigurationGenerator.generateVirl(settings, configs, deviceEtg);
		}

		// Create process-based ETG
		// logger.info("*** Generate process-based ETG ***");
		ProcessGraph baseEtg = new ProcessGraph(deviceEtg, settings);
		// logger.info("COUNT: baseETGVertices "
		// + baseEtg.getVertexCount());
		// logger.info("COUNT: baseETGEdges "
		// + baseEtg.getEdgeCount());
		// logger.info("COUNT: ospfProcesses "
		// + baseEtg.numberOfType(ProcessType.OSPF));
		// logger.info("COUNT: bgpProcesses "
		// + baseEtg.numberOfType(ProcessType.BGP));
		// logger.info("COUNT: staticProcesses "
		// + baseEtg.numberOfType(ProcessType.STATIC));

		// Generate Instance Graph
		// logger.info("*** Generate instance-based ETG ***");
		InstanceGraph instanceEtg = baseEtg.getInstanceEtg();
		// logger.info("COUNT: instanceETGVertices "
		// + instanceEtg.getVertexCount());
		// logger.info("COUNT: instanceETGEdges "
		// + instanceEtg.getEdgeCount());
		// logger.info("COUNT: ospfInstances "
		// + instanceEtg.numberOfType(ProcessType.OSPF));
		// logger.info("COUNT: bgpInstances "
		// + instanceEtg.numberOfType(ProcessType.BGP));
		// logger.info("COUNT: staticInstances "
		// + instanceEtg.numberOfType(ProcessType.STATIC));
		// logger.info("PROP: instanceIsDag "
		// + !((InstanceGraph)instanceEtg).hasCycles());

		// Generate destination-based process graphs
		// add by lyh;

		Map<PolicyGroup, ProcessGraph> destinationEtgs = null;
		if (settings.shouldGenerateDestinationETGs()) {
			destinationEtgs = EtgTasks.generateDestinationETGs(settings, baseEtg, policyGroups);
		}

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

		int a = 1;
		if (a == 1)
			return;

		// Generate flow-based process graphs
		Map<Flow, ProcessGraph> flowEtgs = null;
		if (settings.shouldGenerateFlowETGs()) {
			flowEtgs = EtgTasks.generateFlowETGs(settings, baseEtg, policyGroups);
			logger.info("COUNT: flows " + flowEtgs.size());
		}

		//// add by lyh
		// Generate device-based ETG
		// logger.info("*** Generate device-based ETG2 ***");
		DeviceGraph deviceEtg2 = new DeviceGraph(devices2.values(), settings, waypoints);
		// System.out.println("COUNT: deviceETGVertices "
		// + deviceEtg.getVertexCount());
		// System.out.println("COUNT: deviceETGEdges "
		// + deviceEtg.getEdgeCount());

		// Create process-based ETG
		// logger.info("*** Generate process-based ETG2 ***");
		ProcessGraph baseEtg2 = new ProcessGraph(deviceEtg2, settings);
		// logger.info("COUNT: baseETGVertices "
		// + baseEtg.getVertexCount());
		// logger.info("COUNT: baseETGEdges "
		// + baseEtg.getEdgeCount());
		// logger.info("COUNT: ospfProcesses "
		// + baseEtg.numberOfType(ProcessType.OSPF));
		// logger.info("COUNT: bgpProcesses "
		// + baseEtg.numberOfType(ProcessType.BGP));
		// logger.info("COUNT: staticProcesses "
		// + baseEtg.numberOfType(ProcessType.STATIC));

		// Generate Instance Graph
		// logger.info("*** Generate instance-based ETG2 ***");
		InstanceGraph instanceEtg2 = baseEtg2.getInstanceEtg();
		// logger.info("COUNT: instanceETGVertices "
		// + instanceEtg.getVertexCount());
		// logger.info("COUNT: instanceETGEdges "
		// + instanceEtg.getEdgeCount());
		// logger.info("COUNT: ospfInstances "
		// + instanceEtg.numberOfType(ProcessType.OSPF));
		// logger.info("COUNT: bgpInstances "
		// + instanceEtg.numberOfType(ProcessType.BGP));
		// logger.info("COUNT: staticInstances "
		// + instanceEtg.numberOfType(ProcessType.STATIC));
		// logger.info("PROP: instanceIsDag "
		// + !((InstanceGraph)instanceEtg).hasCycles());

		// Generate destination-based process graphs
		Map<PolicyGroup, ProcessGraph> destinationEtgs2 = null;
		if (settings.shouldGenerateDestinationETGs()) {
			destinationEtgs2 = EtgTasks.generateDestinationETGs(settings, baseEtg2, policyGroups2);
		}

		// Generate flow-based process graphs
		Map<Flow, ProcessGraph> flowEtgs2 = null;
		if (settings.shouldGenerateFlowETGs()) {
			flowEtgs2 = EtgTasks.generateFlowETGs(settings, baseEtg2, policyGroups2);
			logger.info("COUNT: flows2 " + flowEtgs2.size());
		}

		/////

		// Clean-up baseETG to be all-tcs ETG
		baseEtg.customize();

		// Generate graphviz
		if (settings.shouldGenerateGraphviz()) {
			EtgTasks.generateGraphviz(settings, baseEtg, instanceEtg, deviceEtg, destinationEtgs, flowEtgs);
		}

		// Test Generated graphs
		if (settings.shouldTestGraphs() && flowEtgs != null) {
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

		// Check Policies File --invoked by using the option -checkpolicies <DIR>
		if (settings.shouldCheckPolicies()) {
			logger.info("*** Check policies ****");
			Map<Flow, List<Policy>> policiesByFlow = PolicyFile.loadPolicies(settings.getCheckPoliciesFile());

			// Output statistics for policies
			Map<PolicyType, Integer> policiesByType = new LinkedHashMap<PolicyType, Integer>();
			for (PolicyType policyType : PolicyType.values()) {
				policiesByType.put(policyType, 0);
			}
			List<PolicyGroup> dstsWithPolicies = new ArrayList<PolicyGroup>();
			int policyCount = 0;
			for (Entry<Flow, List<Policy>> entry : policiesByFlow.entrySet()) {
				PolicyGroup dst = entry.getKey().getDestination();
				if (!dstsWithPolicies.contains(dst)) {
					dstsWithPolicies.add(dst);
				}
				List<Policy> policies = entry.getValue();
				for (Policy policy : policies) {
					policyCount++;
					policiesByType.put(policy.getType(), policiesByType.get(policy.getType()) + 1);
				}
			}
			logger.info("COUNT: flowsWithPolicies " + policiesByFlow.size());
			logger.info("COUNT: destinationsWithPolicies " + dstsWithPolicies.size());
			logger.info("COUNT: policies " + policyCount);
			for (PolicyType policyType : PolicyType.values()) {
				logger.info("COUNT: policies" + policyType + " " + policiesByType.get(policyType));
			}

			Map<Policy, List<DirectedEdge>> violations = VerificationTasks.verifyPolicies(settings, policiesByFlow,
					flowEtgs);

			// Output statistics for violations
			Map<PolicyType, Integer> violationsByType = new LinkedHashMap<PolicyType, Integer>();
			for (PolicyType policyType : PolicyType.values()) {
				violationsByType.put(policyType, 0);
			}
			List<PolicyGroup> dstsWithViolations = new ArrayList<PolicyGroup>();
			for (Policy violatedPolicy : violations.keySet()) {
				logger.info("Violated: " + violatedPolicy);
				violationsByType.put(violatedPolicy.getType(), violationsByType.get(violatedPolicy.getType()) + 1);
				PolicyGroup dst = violatedPolicy.getTrafficClass().getDestination();
				if (!dstsWithViolations.contains(dst)) {
					dstsWithViolations.add(dst);
				}
			}
			logger.info("COUNT: policiesViolated " + violations.size());
			logger.info("COUNT: destinationsWithViolations " + dstsWithViolations.size());
			for (PolicyType policyType : PolicyType.values()) {
				logger.info("COUNT: violated" + policyType + " " + violationsByType.get(policyType));
			}

			// Nominate tokens for mutation
			if (settings.shouldLocalizeFaults()) {
				RepairTasks.localizeFaults(settings, configs, policyGroups, violations);
			}
		}

		// Repair
		if (settings.shouldRepair() && flowEtgs != null && destinationEtgs != null) {
			repair(settings, configs, deviceEtg, (Map<Flow, ProcessGraph>) flowEtgs, baseEtg, destinationEtgs);
		}

		// Translate
		if (settings.shouldExportRepairs()) {
			translate(settings, configs);
		}

		// Compare ETGs
		if (settings.shouldCompareETGs() && flowEtgs != null) {
			EtgTasks.compareETGs(settings, (Map<Flow, ProcessGraph>) flowEtgs);
		}
	}

	/**
	 * Checks if generated ETGs conform to testcases.
	 * 
	 * @param settings
	 * @param flowEtgs
	 *            the generated ETGs
	 */
	private static void checkFlowETGs(Settings settings, Map<Flow, ? extends ExtendedTopologyGraph> flowEtgs) {
		Logger logger = settings.getLogger();
		String testDir = settings.getTestGraphsDirectory();
		File testFolder = new File(settings.getTestGraphsDirectory());

		if (!testFolder.isDirectory()) {
			logger.error("Must provide a directory containing test graphs");
			return;
		}

		// Get list of test files
		String[] testGVs = testFolder.list(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".gv");
			}
		});
		// create a hashmap from the genGVs array
		// ***use this to find matching filenames in both directories
		if (testGVs == null) {
			logger.error(testFolder.getPath() + " does not contain any test graphs");
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
			if (!testFiles.contains(etg.getGraphvizFilename())) {
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
				// System.out.println("Test graph\n" + graphTest.toString());
				// System.out.println("Generated graph\n" + graphGen.toString());
			} else {
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
		logger.info(passCount + "/" + targetCount + " passed");
		logger.info(failCount + "/" + targetCount + " failed");
		logger.info(missingTest + "/" + targetCount + " missing tests");
		logger.info(missingEtg + "/" + testCount + " missing ETGs");
	}

	/**
	 * Helper function for verifyPolicy(above)
	 * 
	 * @param flowEtgs
	 *            Map from Flow to ETG
	 * @param flow
	 *            flow to be checked for in the KeySet of flowEtgs
	 * @return true if there is an entry corresponding to the flow in the flowEtgs
	 *         map
	 */
	public static boolean flowExists(Map<Flow, ? extends ExtendedTopologyGraph> flowEtgs, Flow flow) {
		if (!flowEtgs.containsKey(flow)) {
			System.out.println("No ETG found for flow: " + flow.toString() + " in provided map.");
			return false;
		}
		return true;
	}

	/**
	 * Repair the network to satisfy a set of provided policies.
	 * 
	 * @param settings
	 *            settings
	 * @param configs
	 * @param deviceEtg
	 *            the physical network topology
	 * @param flowEtgs
	 *            the per-flow ETGs to repair
	 */
	private static void repair(Settings settings, Map<String, Config> configs, DeviceGraph deviceEtg,
			Map<Flow, ProcessGraph> flowEtgs, ProcessGraph baseEtg, Map<PolicyGroup, ProcessGraph> destinationEtgs) {
		Logger logger = settings.getLogger();

		// Load policies
		logger.info("*** Load Policies ***");
		Map<Flow, List<Policy>> policies = PolicyFile.loadPolicies(settings.getPoliciesRepairFile());
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
		switch (settings.getModifierAlgorithm()) {
		case MAXSMT_ALL_TCS:
		case MAXSMT_PER_TC:
		case MAXSMT_PER_PAIR:
		case MAXSMT_PER_DST:
		case MAXSMT_PER_SRC:
			modifier = new MaxSmtModifier(policies, deviceEtg, baseEtg, destinationEtgs, flowEtgs, settings);
			break;
		case ISOLATED:
			modifier = new IsolatedModifier(policies, deviceEtg, baseEtg, flowEtgs, settings);
			break;
		default:
			throw new RepairException("Unknown graph modifier algorithm: " + settings.getModifierAlgorithm());
		}
		long startTime = System.currentTimeMillis();

		ConfigModificationGroup modifications = modifier.getModifications();
		if (null == modifications) {
			return;
		}
		modifications.setEtgs(flowEtgs, destinationEtgs, baseEtg);
		long endTime = System.currentTimeMillis();
		System.out.println("TIME: graphModification " + (endTime - startTime) + " ms");

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
	 * 
	 * @param settings
	 *            settings
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
			if (!Files.exists(path)) {
				logger.fatal(String.format("Cannot export repaired configuration files:" + " %s does not exist.",
						exportDirectory));
			} else if (!Files.isDirectory(path)) {
				logger.fatal(String.format("Cannot export repaired configuration files:" + " %s is not a directory.",
						exportDirectory));
			}
		}

		configWriter.write(configModifications, exportDirectory);
	}

	private static void compareDevices(List<Device> devices) {

		// Compare each Device
		SimilarityChecker checker = new SimilarityChecker(devices);
		checker.getDifferences();
	}
}
