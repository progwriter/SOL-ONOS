package edu.unc.sol.service;
// Unirest imports
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
// SOL imports
import edu.unc.sol.app.Optimization;
import edu.unc.sol.app.PathUpdateListener;
import edu.unc.sol.app.TrafficClass;
import edu.unc.sol.util.Config;
// JSON + Annotation imports
import org.apache.felix.scr.annotations.*;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONObject;
// ONLAB imports
import org.onlab.graph.Edge;
import org.onlab.graph.Vertex;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.EthType.EtherType;
// ONOSPROJECT imports
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.Device;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.intent.PathIntent;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.DefaultEdgeLink;
import org.onosproject.net.PortNumber;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.topology.*;
// Logger imports
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// Java imports
import java.lang.System;
import java.lang.Integer;
import java.lang.String;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
// Fairness Metric imports
import static edu.unc.sol.service.Fairness.PROP_FAIR;

@Component(immediate = true)
@Service
public final class SolServiceImpl implements SolService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    protected Map<ApplicationId, List<TrafficClass>> tcMap;
    protected Map<ApplicationId, List<PathUpdateListener>> listenerMap;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService core;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LinkService linkService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowService;
    
    private Edge[][] edge_mapping;
    private Vertex[] vertex_mapping;
    private Boolean running;
    private HashMap<DeviceId, Integer> deviceMap;
    private HashMap<Integer, DeviceId> linkMap;
    private HashMap<ApplicationId, Optimization> optimizations;
    private HashMap<ApplicationId, String> predicates;
    private HashMap<ApplicationId, List<Integer>> middleboxes;
    private HashMap<String, ApplicationId> app_ids;
    private String remoteURL;
    private List<TrafficClass> allTrafficClasses;
    private static Lock lock = new ReentrantLock();
    private static Condition newApp = lock.newCondition();
    private static boolean appsChanged = false;
    protected long global_start_time;

    private static SolService instance = null;

    public static SolService getInstance() {
        return instance;
    }


    //    private void tcMap_wait() {
    //        lock.lock();
    //        try {
    //            while (!appsChanged) {
    //                try {
    //                    log.info("Calling cond.await! with 'appsChanged' = " + appsChanged);
    //                    newApp.await();
    //                    log.info("cond.await returned with 'appsChanged' = " + appsChanged);
    //                } catch (InterruptedException e) {
    //                    log.error("Error trying to cond_wait for tcMap modification");
    //                }
    //            }
    //            appsChanged = false;
    //            log.info("Just set 'appsChanged' = " + appsChanged);
    //        } finally {
    //            lock.unlock();
    //        }
    //    }


    //    private void tcMap_put(ApplicationId id, List<TrafficClass> trafficClasses) {
    //        lock.lock();
    //        log.info("Putting into TCMAP- ID: " + id.toString());
    //        log.info("Putting into TCMAP- LIST: ");
    //        int count = 0;
    //        for (TrafficClass curr : trafficClasses) {
    //            log.info("Index " + count + ": " + curr.toString());
    //        }
    //        log.info("END TrafficClasses LIST");
    //        tcMap.put(id, trafficClasses);
    //        appsChanged = true;
    //        log.info("Just set 'appsChanged' = " + appsChanged);
    //        newApp.signalAll();
    //        lock.unlock();
    //    }
    
    // this thread will wait for new apps to register with SOL and properly recompute the intents for ONOS accordingly
    private class SolutionCalculator implements Runnable {
        @Override
        public void run() {
	    log.debug("Recompute thread started");
            // monitor changes to the traffic classes
            // upon change, trigger recompute
            // results of recompute will be sent to the apps
            // using the PathUpdateListener object
            while (running) {
                log.debug("Going to WAIT!!");
                lock.lock();
                try {
                    while (!appsChanged && running)
                        newApp.await();
                    log.debug("Woken up going to recompute!!!");
                    if (running) {
                        recompute();
			log.info("Just recomputed Intents at " + String.valueOf(System.currentTimeMillis() - global_start_time) + "ms");
                        appsChanged = false;
                    } else {
                        break;
                    }
                } catch (InterruptedException e) {
                    continue;
                } finally {
                    lock.unlock();
                }
            }
            log.debug("Recompute thread ending");
        }
    }

    public SolServiceImpl() {
        // Initialize basic structures
        tcMap = new HashMap<ApplicationId, List<TrafficClass>>();
        listenerMap = new HashMap<ApplicationId, List<PathUpdateListener>>();
        running = false;
        deviceMap = new HashMap<DeviceId, Integer>();
        linkMap = new HashMap<Integer, DeviceId>();
	middleboxes = new HashMap<ApplicationId, List<Integer>>();
	predicates = new HashMap<ApplicationId, String>();
        optimizations = new HashMap<ApplicationId, Optimization>();
        allTrafficClasses = new ArrayList<TrafficClass>();
	app_ids = new HashMap<String, ApplicationId>();
	global_start_time = System.currentTimeMillis();
    }

    @Override
    public void registerApp(ApplicationId id, List<TrafficClass> trafficClasses, Optimization opt,
                            PathUpdateListener listener, String predicate, List<Integer> app_middleboxes) {
	
	// Acquire the lock to ensure only one app registers at a time
        lock.lock();
	
        try {
	    log.info(id.name() + " is registering with SOL at " + String.valueOf(System.currentTimeMillis() - global_start_time) + "ms");

	    // create a linked list of listeners for the application
	    listenerMap.put(id, new LinkedList<>());
            listenerMap.get(id).add(listener);
	    // keep track of the predicate function for this app
	    predicates.put(id, predicate);
	    // keep track of the middleboxes this app recognizes
	    middleboxes.put(id, app_middleboxes);
	    // keep track of the optimization this app wants
            optimizations.put(id, opt);
	    // keep track of the traffic classes for this app
            tcMap.put(id, trafficClasses);
	    // keep track of the App ID for this app
	    app_ids.put(id.name(), id);
	    
	    // install flow on switch i to forward traffic to host i
	    int prefix = 167772161; // 10.0.0.1
	    for (Device dev : deviceService.getAvailableDevices()) {

		// build the traffic selector to match on the traffic that the switch sees
		TrafficSelector.Builder ts_builder = DefaultTrafficSelector.builder();
		ts_builder.matchEthType(EtherType.IPV4.ethType().toShort());
		int ip_off = Integer.parseInt(dev.id().toString().substring(3), 16) - 1;
		ts_builder.matchIPDst(IpPrefix.valueOf(prefix+ip_off,32));
		TrafficSelector ts = ts_builder.build();

		// build the traffic treatment which decides the output action for the traffic
		TrafficTreatment.Builder tt_builder = DefaultTrafficTreatment.builder();
		tt_builder.setOutput(PortNumber.portNumber(1));
		TrafficTreatment tt = tt_builder.build();

		// build the flow rule for the switch 
		FlowRule.Builder flowrule_builder = DefaultFlowRule.builder();
		flowrule_builder.forDevice(dev.id());
		flowrule_builder.makePermanent();
		flowrule_builder.withPriority(200);
		flowrule_builder.withSelector(ts);
		flowrule_builder.withTreatment(tt);
		flowrule_builder.fromApp(id);
		FlowRule flowrule = flowrule_builder.build();
		
		// build the traffic selector to match on all ARP traffic 
		TrafficSelector.Builder as_builder = DefaultTrafficSelector.builder();
		as_builder.matchEthType(EtherType.ARP.ethType().toShort());
		TrafficSelector as = as_builder.build();

		// build the traffic treatment for the ARP traffic
		TrafficTreatment.Builder aa_builder = DefaultTrafficTreatment.builder();
		aa_builder.setOutput(PortNumber.portNumber(1));
		TrafficTreatment aa = aa_builder.build();

		// build the flow rule for the switch
		FlowRule.Builder arprule_builder = DefaultFlowRule.builder();
		arprule_builder.forDevice(dev.id());
		arprule_builder.makePermanent();
		arprule_builder.withPriority(50);
		arprule_builder.withSelector(as);
		arprule_builder.withTreatment(aa);
		arprule_builder.fromApp(id);
		FlowRule arprule = arprule_builder.build();

		// install the flows using the flow service
		flowService.applyFlowRules(flowrule);
		flowService.applyFlowRules(arprule);
		
	    }

	    // indicate we've changed the apps to reinstall the intents with the SolutionCalculator
            appsChanged = true;
	    // wake up any other apps who are waiting on the lock to registers with Chopin
            newApp.signal();
	    
        } finally {

	    // release the lock
            lock.unlock();
	    
        }
    }

    //TODO: implement locking for these functions as well (not a priority)
//    @Override
//    public void updateTrafficClasses(ApplicationId id, List<TrafficClass> trafficClasses) {
//        lock.lock();
//        try {
//            tcMap_put(id, trafficClasses);
//
//        } finally {
//            lock.unlock();
//        }
//    }

//    @Override
//    public void addListener(ApplicationId id, PathUpdateListener listener) {
//        // Adds a listener that awaits updated path results from the SOL instance
//        listenerMap.get(id).add(listener);
//    }
//
//    @Override
//    public void removeListener(ApplicationId id, PathUpdateListener listener) {
//        // Removes a callback listener for a given app
//        listenerMap.get(id).remove(listener);
//        if (listenerMap.get(id).isEmpty()) {
//            unregisterApp(id);
//        }
//    }

    @Override
    public void unregisterApp(ApplicationId id) {
        // cleanup the app from the list of apps

	// acquire the lock
	lock.lock();
	
        try {

	    // remove the app related info
            tcMap.remove(id);
            listenerMap.remove(id);
            optimizations.remove(id);
	    app_ids.remove(id.name());
	    
	    // indicate we've changed the apps to reinstall the intents with the SolutionCalculator
            appsChanged = true;
	    // wake up any other apps who are waiting on the lock to registers with Chopin
            newApp.signal();

        } finally {

	    // release the lock
            lock.unlock();

	}
    }

    @Activate
    protected void activate() {
        running = true;
        instance = this;

        // Get the address of the SOL server from an environment variable
        String solServer = System.getenv(Config.SOL_ENV_VAR);
        if (solServer == null) {
            log.warn("No SOL server configured, using default values");
            solServer = "127.0.0.1:5000";
        }

        // Build a proper url for the rest client
        StringBuilder builder = new StringBuilder();
        remoteURL = builder.append("http://").append(solServer).append("/api/v1/").toString();
        log.info("The SOL Server is configured at: " + remoteURL);
        // Send the topology to the SOL server
        sendTopology(remoteURL + "topology/", topologyToJson(null));

	
        // Start the monitor-solve loop in a new thread
        log.info("Going to start new SolutionCalculator Thread");
        new Thread(new SolutionCalculator()).start();
        log.info("Started the SOL service");
    }

    private JSONObject topologyToJson(ArrayList<Integer> middleboxes) {

	// Get the Topology Graph
        TopologyGraph topo = topologyService.getGraph(topologyService.currentTopology());
	// check if middleboxes are provided
	if (middleboxes == null) {
	    middleboxes = new ArrayList<Integer>();
	}
        // Now build our request
        JSONObject topoj = new JSONObject();
        // Make sure the graph is directed
        topoj.put("graph", new JSONObject());
	topoj.put("directed",true);
        // Create holders for nodes and link
        JSONArray nodes = new JSONArray();
        JSONArray links = new JSONArray();
        // Extract nodes and links from the ONOS topology
        Set<TopologyVertex> topology_vertexes = topo.getVertexes();
        Set<TopologyEdge> topology_edges = topo.getEdges();
        int num_vertexes = topology_vertexes.size();
        int num_edges = topology_edges.size();
        vertex_mapping = new Vertex[num_vertexes];
        edge_mapping = new Edge[num_vertexes][num_vertexes];
        for (Vertex v : topology_vertexes) {
            DeviceId dev = ((DefaultTopologyVertex) v).deviceId();
	    // indexed from 0 while the hosts are indexed by 1
	    int vertex_index = Integer.parseInt(dev.toString().substring(3), 16) - 1;
	    deviceMap.put(dev, vertex_index);
            linkMap.put(vertex_index, dev);
            JSONObject node = new JSONObject();
            nodes.put(node);
            node.put("id", getIntegerID(dev));
            // Devices in ONOS are by default switches (hosts are a separate category)OA
	    
	    if (middleboxes.contains(new Integer(vertex_index))) {
		node.put("hasMbox", "true");
	    }
	    
            node.put("services", "switch");
            // We need resources to be present, but for now we are not extracting any info from ONOS
            node.put("resources", new JSONObject());
            // TODO: Put resources of nodes, if any, like CPU  @victor
            // TODO: extract middlebox info from ONOS somehow? @victor
            vertex_index += 1;
        }
        for (Edge e : topology_edges) {
            // Note: by default ONOS graphs (and thus edges) are directed.
            JSONObject link = new JSONObject();
            links.put(link);

            int srcid = getIntegerID(((DefaultTopologyVertex) e.src()).deviceId());
            int dstid = getIntegerID(((DefaultTopologyVertex) e.dst()).deviceId());
            link.put("source", srcid);
            link.put("target", dstid);
	    link.put("srcname", srcid);
	    link.put("dstname", dstid);
            JSONObject resources = new JSONObject();
            link.put("resources", resources);
            ConnectPoint edge_link_src = (((DefaultTopologyEdge) e).link()).src();
            Port src_port = deviceService.getPort(edge_link_src.deviceId(), edge_link_src.port());
            // Store the bandwidth capacity in Mbps, easier this way
            long src_bandwith_mbps = src_port.portSpeed();
            resources.put("bw", src_bandwith_mbps);
        }
        topoj.put("nodes", nodes);
        topoj.put("links", links);

        return topoj;
    }

    /**
     * Send the topology to the SOL python server.
     *
     * @param url the url of the endpoint.
     */
    private void sendTopology(String url, JSONObject topo) {

        // Send request to the specified URL as a HTTP POST request.
        HttpResponse resp = null;
        try {
            resp = Unirest.post(url)
                    .header(HTTP.CONTENT_TYPE, "application/json")
                    .body(topo)
                    .asString();
            if (resp.getStatus() != 200) {
                log.error(resp.getStatus() + ": " + resp.getStatusText());
            } else {
                log.info("Successfully POSTed the topology to SOL server");
            }
        } catch (UnirestException e) {
            log.error("Failed to post topology to SOL server", e);
        }
        log.info(deviceMap.toString());
    }

    @Deactivate
    protected void deactivate() throws IOException {
        running = false;
        lock.lock();
        try {
            newApp.signalAll();
        } finally {
            lock.unlock();
        }
        Unirest.shutdown();
        instance = null;
        log.info("Stopped the SOL service");
    }

    /**
     * Compute a new solution from all of the registered apps.
     */
    private void recompute() {
        // No need to POST anything if there are no apps
        if (optimizations.isEmpty()) {
            return;
        }
        // Serialize all of our apps and post them to the server
        JSONObject composeObject = new JSONObject();
        composeObject.put("fairness", PROP_FAIR.toString());

        HashMap<TrafficClass, Integer> tc_ids = new HashMap<>();
        int tc_counter = 0;
        JSONArray applist = new JSONArray();
	ArrayList<Integer> all_middleboxes = new ArrayList<Integer>();
        for (ApplicationId appid : optimizations.keySet()) {
            JSONObject app = new JSONObject();
            applist.put(app);
            app.put("id", appid.name());
            app.put("predicate", predicates.get(appid));
	    List<Integer> curr_middleboxes = middleboxes.get(appid);
	    if (curr_middleboxes != null) {
		for (Integer mbid : curr_middleboxes) {
		    if (!all_middleboxes.contains(mbid)) {
			all_middleboxes.add(mbid);
		    }
		}
	    }
		    
            JSONObject opnode = optimizations.get(appid).toJSONnode();
            for (String key : JSONObject.getNames(opnode)) {
                app.put(key, opnode.get(key));
            }
            JSONArray tc_list = new JSONArray();
            for (TrafficClass tc : tcMap.get(appid)) {
                tc_list.put(tc.toJSONnode(tc_counter++));
                // Keep track of all traffic classes so we can decode the response
                allTrafficClasses.add(tc);
            }
            app.put("traffic_classes", tc_list);
        }
        composeObject.put("apps", applist);
        composeObject.put("topology", topologyToJson(all_middleboxes));

        // Send request to the specified URL as a HTTP POST request.
        try {
            HttpResponse<JsonNode> resp = Unirest.post(remoteURL + "compose")
                    .header(HTTP.CONTENT_TYPE, "application/json")
                    .body(composeObject)
                    .asJson();
            if (resp.getStatus() != 200) {
                log.error(resp.getStatus() + ": " + resp.getStatusText());
            } else {
                log.info("Successfully POSTed the composition to SOL server");
            }
            processComposeResponse(resp);
        } catch (UnirestException e) {
            log.error("Failed to post composition to SOL server", e);
        }
    }

    private void processComposeResponse(HttpResponse<JsonNode> resp) {
        // Get the response payload
        JSONArray data = resp.getBody().getArray();
        JSONObject app_paths;
        for (int i = 0; i < data.length(); i++) {
            app_paths = data.getJSONObject(i);

            // Extract the app name, and id
            String appname = app_paths.getString("app");
            ApplicationId appid = core.getAppId(appname);

            // Extract paths
            JSONArray all_paths = app_paths.getJSONArray("tcs");

            // Grab all the listeners registered for this app
            for (PathUpdateListener l : listenerMap.get(appid)) {
                // Send the created path intents to the listeners
                l.updatePaths(computeIntents(appname, all_paths));
            }
        }
    }

    //function to sort our custom arr we use in computeIntents
    private long[][] sort_weights(long[][] arr) {
        //copy argument
        long[][] new_arr = new long[arr.length][2];
        for (int i = 0; i < arr.length; i++) {
            new_arr[i][0] = arr[i][0];
            new_arr[i][1] = arr[i][1];
        }

        //in place selection sort
        for (int j = 0; j < arr.length; j++) {
            long curr_max = -1;
            int max_i = -1;
            for (int i = j; i < arr.length; i++) {
                if (new_arr[i][1] > curr_max) {
                    curr_max = new_arr[i][1];
                    max_i = i;
                }
            }
            new_arr[j][0] = new_arr[max_i][0];
            new_arr[j][1] = new_arr[max_i][1];
        }
        return new_arr;
    }

    private List<Link> create_links(JSONArray pathnodes) {
        List<Link> link_list = new ArrayList<Link>();

	int prev_id = -1;
	int curr_id = -1;
        for (int i = 0; i < pathnodes.length(); i++) {
            if (prev_id == -1) {
		prev_id = pathnodes.getInt(i);

		// Get the Host that will start the path		
		Host start_host = null;
		Set<Host> conhosts = hostService.getConnectedHosts(linkMap.get(prev_id));
		if (conhosts.size() != 1) {
		    log.error("Problem Finding the Hosts with the HostService!");
		}
			
		for (Host chost : conhosts) {
		    start_host = chost;
		    break;
		}
		link_list.add(DefaultEdgeLink.createEdgeLink(start_host, true));
                continue;
            } else {
		curr_id = pathnodes.getInt(i);
		
                DeviceId prev_dev = linkMap.get(prev_id);
                DeviceId next_dev = linkMap.get(curr_id);

                ConnectPoint src_connect_point = null;
                ConnectPoint dst_connect_point = null;

                Set<Link> src_egress =
                        linkService.getDeviceEgressLinks(prev_dev);

                for (Link curr_link : src_egress) {
                    ConnectPoint curr_connect_point = curr_link.dst();
                    DeviceId curr_dev = curr_connect_point.deviceId();
                    if (curr_dev.equals(next_dev)) {
                        dst_connect_point = curr_connect_point;
                        src_connect_point = curr_link.src();
                    }
                }
		Link link = linkService.getLink(src_connect_point,dst_connect_point);
                link_list.add(link);
		prev_id = curr_id;
            }
        }
        return link_list;
    }

    private List<Map<IpPrefix, Double>> create_fractions(IpPrefix prefix, JSONArray paths) {

        List<Map<IpPrefix, Double>> tables = new ArrayList<Map<IpPrefix, Double>>();

        for (int i = 0; i < paths.length(); i++) {
            tables.add(new HashMap<IpPrefix, Double>());
        }

        int precision = 32 - prefix.prefixLength();
        long power = precision;

        double fraction_weight = Math.pow(2.0, precision);
        long new_total = (long) fraction_weight;

        //Normalize the Weights for each of the paths with this new pow of 2
        //index 0 is the path the weight is for when we sort the array
        //index 1 is the curr_load for the path
        long[][] normalized_weights = new long[paths.length()][2];
        long curr_new_total = new_total;
        int weight_index = 0;
        for (int i = 0; i < paths.length(); i++) {
            JSONObject pathobj = paths.getJSONObject(i);
            double fraction = pathobj.getDouble("fraction");
            long weighted = (long) (fraction * fraction_weight);
            if (weight_index == (paths.length() - 1)) {
                normalized_weights[weight_index][0] = weight_index;
                normalized_weights[weight_index][1] = curr_new_total;
            } else {
                normalized_weights[weight_index][0] = weight_index;
                normalized_weights[weight_index][1] = weighted;
                curr_new_total -= weighted;
            }
            weight_index += 1;
        }

        boolean has_non_zero = true;

        //keep creating prefix rules, along with fractions for largest load
        long cumulative_load = 0;
        while (has_non_zero) {
            long[][] sorted_weights = sort_weights(normalized_weights);
            int i = 0; //always create a rule for the max load after sorting
            long curr_load = sorted_weights[i][1];

            if (curr_load == 0) {
                has_non_zero = false;
                continue;
            }

            long highest_power_two = (long)
                    (Math.log(curr_load) / Math.log(2.0));

            String binary = Integer.toBinaryString((int) cumulative_load);

            //take the rightmost 'power' bits
            String proper_binary = "";
            if (power > binary.length()) {
                for (int k = 0; k < power - binary.length(); k++) {
                    proper_binary += "0";
                }
                proper_binary += binary;
            } else {
                proper_binary = binary.substring(binary.length() - (int) power, binary.length());
            }

            //add the first ('power' - 'highest_power_two') bits to the prefix
            String prefix_add_bits =
                    proper_binary.substring(0, (int) (power - highest_power_two));
            Double prefix_fraction =
                    new Double(Math.pow(2.0, highest_power_two) / (new_total * 1.0));

            String extra_bits = prefix_add_bits;
            IpPrefix curr_prefix = prefix;
            int curr_prefix_len = curr_prefix.prefixLength();
            int shift = 31 - curr_prefix_len;
            Ip4Address curr_ip =
                    curr_prefix.address().getIp4Address();
            int curr_ip_int = curr_ip.toInt();
            int curr_mask = 0;
            for (int ind = 0; ind < extra_bits.length(); ind++) {
                String curr_char =
                        extra_bits.substring(ind, ind + 1);
                int curr_bit =
                        Integer.valueOf(curr_char).intValue();
                curr_mask =
                        curr_mask | (curr_bit << (shift - ind));
            }
            int new_ip_int = curr_ip_int | curr_mask;
            IpAddress new_ip = IpAddress.valueOf(new_ip_int);
            int new_prefix_len =
                    curr_prefix_len + extra_bits.length();
            IpPrefix new_prefix =
                    IpPrefix.valueOf(new_ip, new_prefix_len);

            tables.get((int) sorted_weights[i][0]).put(new_prefix, prefix_fraction);

            normalized_weights[(int) (sorted_weights[i][0])][1] -= (long) Math.pow(2.0, highest_power_two);
            cumulative_load += (int) Math.pow(2.0, highest_power_two);
        }

        return tables;
    }

    private Collection<PathIntent> computeIntents(String appname, JSONArray all_paths) {
	
        ArrayList<PathIntent> result = new ArrayList<PathIntent>();

        for (int i = 0; i < all_paths.length(); i++) {
            JSONObject tcjson = all_paths.getJSONObject(i);

            // Extract the traffic class
            int tcid = tcjson.getInt("tcid");
            TrafficClass tc = allTrafficClasses.get(tcid);
            TrafficSelector original_selector = tc.getSelector();

            JSONArray paths = tcjson.getJSONArray("paths");

            //@victor is this correct? how do we add the tcid to the intent we are adding? What should first arg me, "of", "snmp"?
            ProviderId provider_id = new ProviderId("of", Integer.toString(tcid));

            if (paths.length() == 1) {
                PathIntent.Builder intent_builder = PathIntent.builder();
                JSONObject pathobj = paths.getJSONObject(0);
                JSONArray pathnodes = pathobj.getJSONArray("nodes");
		DefaultAnnotations.Builder annotations_builder = DefaultAnnotations.builder();
		// TODO: provider ID could be incorrect
		// TODO: translation from paths to flows can be incorrect (create_links_pathnodes?)
                DefaultPath curr_path =
		    new DefaultPath(provider_id, create_links(pathnodes), 1.0, annotations_builder.build());
                intent_builder.path(curr_path);
		intent_builder.appId(app_ids.get(appname));
                intent_builder.selector(original_selector);
		
		
                PathIntent path_intent = intent_builder.build();
                result.add(path_intent);
                continue;
            }
            IpPrefix prefix = ((IPCriterion) original_selector.getCriterion(Criterion.Type.IPV4_SRC)).ip();

            List<Map<IpPrefix, Double>> tables = create_fractions(prefix, paths);

            int path_index = -1;
            for (int j = 0; j < paths.length(); j++) {
                JSONObject pathobj = paths.getJSONObject(j);
                path_index++;

                // Get the path nodes
                JSONArray pathnodes = pathobj.getJSONArray("nodes");

                List<Link> link_list = create_links(pathnodes);

                Set<Criterion> original_criteria = original_selector.criteria();

                Map<IpPrefix, Double> curr_table = tables.get(path_index);
                // Loop through each wildcard rule we have for this path
                for (IpPrefix new_prefix : curr_table.keySet()) {
                    TrafficSelector.Builder ts_builder =
                            DefaultTrafficSelector.builder();
                    for (Criterion curr_criteria : original_criteria) {
                        if (curr_criteria.type() != Criterion.Type.IPV4_SRC) {
                            ts_builder.add(curr_criteria);
                        } else {
                            ts_builder.add(Criteria.matchIPSrc(new_prefix));
                        }
                    }

                    TrafficSelector new_selector = ts_builder.build();
		    DefaultAnnotations.Builder annotations_builder = DefaultAnnotations.builder();
                    PathIntent.Builder intent_builder = PathIntent.builder();
                    DefaultPath curr_path =
			new DefaultPath(provider_id, link_list,
					tables.get(path_index).get(new_prefix), annotations_builder.build());
                    intent_builder.path(curr_path);
		    intent_builder.appId(app_ids.get(appname));
                    intent_builder.selector(new_selector);
                    PathIntent path_intent = intent_builder.build();
                    result.add(path_intent);
                }
            }
        }

        //printing path intents
	/*
        log.info("Printing Path Intents as Sanity Check");
        int count = 0;
        for (PathIntent curr : result) {
            log.info("Intent " + count + ": " + curr.toString());
            count++;
        }
        log.info("Finished Printing Intents");
	*/
        return result;
    }


    /**
     * Get the integer id assigned to an ONOS DeviceID
     *
     * @param id the device id of a switch
     */
    public int getIntegerID(DeviceId id) {
        // Grab the id from the device mapping
        Integer int_id = deviceMap.get(id);
        if (int_id == null) {
            log.error("ID not found int deviceMap: " + id.toString());
            return -1;
        } else {
            return int_id;
        }
    }
}
    
