package edu.unc.sol.service;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import edu.unc.sol.app.Optimization;
import edu.unc.sol.app.PathUpdateListener;
import edu.unc.sol.app.TrafficClass;
import edu.unc.sol.util.Config;
import org.apache.felix.scr.annotations.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.onlab.graph.Edge;
import org.onlab.graph.Vertex;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.intent.PathIntent;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.topology.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static edu.unc.sol.service.Fairness.PROP_FAIR;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

@Service
@Component(immediate = true)
public class SolServiceImpl implements SolService {
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
    private Edge[][] edge_mapping;
    private Vertex[] vertex_mapping;
    private Boolean running;
    private HashMap<DeviceId, Integer> deviceMap;
    private HashMap<Integer, DeviceId> linkMap;
    private HashMap<ApplicationId, Optimization> optimizations;
    private String remoteURL;
    private List<TrafficClass> allTrafficClasses;

    private class SolutionCalculator implements Runnable {
        @Override
        public void run() {
            log.debug("Recompute thread started");
//            while (running) {
            // TODO: monitor changes to the traffic classes
            // Upon change, trigger recompute
//                recompute();
            // TODO: results of recompute should be sent to the apps
            // using the PathUpdateListener object
//            }
            log.debug("Recompute thread ending");
        }
    }

    public SolServiceImpl() {
        // Initialize basic structures
        tcMap = new HashMap<>();
        listenerMap = new HashMap<>();
        running = false;
        deviceMap = new HashMap<>();
        linkMap = new HashMap<>();
    }

    @Override
    public void registerApp(ApplicationId id, List<TrafficClass> trafficClasses, Optimization opt,
                            PathUpdateListener listener) {
        tcMap.put(id, trafficClasses);
        listenerMap.put(id, new LinkedList<>());
        listenerMap.get(id).add(listener);
        optimizations.put(id, opt);
    }

    @Override
    public void updateTrafficClasses(ApplicationId id, List<TrafficClass> trafficClasses) {
        tcMap.put(id, trafficClasses);
    }

    @Override
    public void addListener(ApplicationId id, PathUpdateListener listener) {
        // Adds a listener that awaits updated path results from the SOL instance
        listenerMap.get(id).add(listener);
    }

    @Override
    public void removeListener(ApplicationId id, PathUpdateListener listener) {
        // Removes a callback listener for a given app
        listenerMap.get(id).remove(listener);
        if (listenerMap.get(id).isEmpty()) {
            unregisterApp(id);
        }
    }

    @Override
    public void unregisterApp(ApplicationId id) {
        //Cleanup the app from the list of apps
        tcMap.remove(id);
        listenerMap.remove(id);
        optimizations.remove(id);
    }

    @Activate
    protected void activate() {
        running = true;
        // Get the address of the SOL server from an environment variable
        String solServer = System.getenv(Config.SOL_ENV_VAR);
        if (solServer == null) {
            log.warn("No SOL server configured, using default values");
            solServer = "127.0.0.1:5000";
        }
        // TEST HERE:
	/*        try {
            HttpResponse<com.mashape.unirest.http.JsonNode> sup =
                    Unirest.post("http://localhost:3333").asJson();
        } catch (UnirestException e) {
            e.printStackTrace();
        }
	*/
        // Build a proper url for the rest client
        StringBuilder builder = new StringBuilder();
        remoteURL = builder.append("http://").append(solServer).append("/api/v1/").toString();
        // Send the topology to the SOL server
        sendTopology(remoteURL);
        // Start the monitor-solve loop in a new thread
        new Thread(new SolutionCalculator()).run();
        log.info("Started the SOL service");
    }

//    private int findVertexId(Vertex v) {
//        for (int i = 0; i < vertex_mapping.length; i++) {
//            if (vertex_mapping[i].equals(v)) {
//                return i;
//            }
//        }
//        return -1;
//    }

    /**
     * Send the topology to the SOL python server.
     *
     * @param url the url of the endpoint.
     */
    private void sendTopology(String url) {
        // WARNING: we are running with the implicit assumption that that the topology does not change
        // Extract the topology from the topology service
        TopologyGraph topo = topologyService.getGraph(topologyService.currentTopology());
        // Now build our request
        JSONObject topoj = new JSONObject();
        // Make sure the graph is directed
        topoj.put("graph", new JSONObject().put("directed", true));
        // Create holders for nodes and link
        JSONArray nodes = new JSONArray();
        topoj.put("nodes", new JSONObject().put("items", nodes));
        JSONArray links = new JSONArray();
        topoj.put("links", new JSONObject().put("items", links));
        // Extract nodes and links from the ONOS topology
        Set<TopologyVertex> topology_vertexes = topo.getVertexes();
        Set<TopologyEdge> topology_edges = topo.getEdges();
        int num_vertexes = topology_vertexes.size();
        int num_edges = topology_edges.size();
        vertex_mapping = new Vertex[num_vertexes];
        edge_mapping = new Edge[num_vertexes][num_vertexes];
        int vertex_index = 0;
        for (Vertex v : topology_vertexes) {
            DeviceId dev = ((DefaultTopologyVertex) v).deviceId();
            deviceMap.put(dev, vertex_index);
            linkMap.put(vertex_index, dev);
            JSONObject node = new JSONObject();
            nodes.put(node);
            node.put("id", getIntegerID(dev));
            // Devices in ONOS are by default switches (hosts are a separate category),
            // which is EXACTLY what we need
            node.put("services", "switch");
            // We need resources to be present, but for now we are not extracting any info
            // from ONOS
            node.put("resources", new JSONObject());
            // TODO: Put resources of nodes, if any, like CPU  @victor
            // TODO: extract middlebox info from ONOS somehow? @victor
            vertex_index += 1;
        }
        for (Edge e : topology_edges) {
            // Note: by default ONOS graphs (and thus edges) are directed.
            JSONObject link = new JSONObject();

            int srcid = getIntegerID(((DefaultTopologyVertex) e.src()).deviceId());
            int dstid = getIntegerID(((DefaultTopologyVertex) e.dst()).deviceId());
            link.put("source", srcid);
            link.put("target", dstid);
            JSONObject resources = new JSONObject();
            link.put("resources", resources);
            ConnectPoint edge_link_src = (((DefaultTopologyEdge) e).link()).src();
            Port src_port = deviceService.getPort(edge_link_src.deviceId(), edge_link_src.port());
            // Store the bandwidth capacity in Mbps, easier this way
            long src_bandwith_mbps = src_port.portSpeed();
            resources.put("bw", src_bandwith_mbps);
        }
        // Send request to the specified URL as a HTTP POST request.
        HttpResponse resp = null;
        try {
            resp = Unirest.post(url).body(topoj).asJson();
        } catch (UnirestException e) {
            log.error("Failed to post topology", e);
        }
        if (resp.getStatus() != 200) {
            log.error(resp.getStatusText());
        } else {
            log.info("Successfully POSTed the topology");
        }
    }

    @Deactivate
    protected void deactivate() throws IOException {
        // TODO: any cleanup if necessary @sanjay
        Unirest.shutdown();
        log.info("Stopped the SOL service");
    }

    /**
     * Compute a new solution from all of the registered apps.
     */
    private void recompute() {
        // Serialize all of our apps and post them to the server
        JSONObject composeObject = new JSONObject();
        composeObject.put("fairness", PROP_FAIR.toString());

        HashMap<TrafficClass, Integer> tc_ids = new HashMap<>();
        int tc_counter = 0;
	JSONArray applist = new JSONArray();
	composeObject.put("apps", new JSONObject().put("items", applist));
	for (ApplicationId appid : optimizations.keySet()) {
	    JSONObject app = new JSONObject();
	    applist.put(app);
	    app.put("id", appid.toString());
            //TODO: allow predicate customization in the future @victor
            app.put("predicate", "null_predicate");
	    //	    app.setAll(optimizations.get(appid).toJSONnode());
	    JSONObject opnode = optimizations.get(appid).toJSONnode();
	    for (String key : JSONObject.getNames(opnode)) {
		app.put(key, opnode.get(key));
	    }
	    JSONArray tc_list = new JSONArray();
	    app.put("traffic_classes", new JSONObject().put("items", tc_list));
            for (TrafficClass tc : tcMap.get(appid)) {
		tc_list.put(tc.toJSONnode(tc_counter++));
                // Keep track of all traffic classes so we can decode the response
                allTrafficClasses.add(tc);
            }
        }
        // Send the composition request over:
//        WebTarget target = restClient.target(remoteURL).path("/compose");
//        Invocation.Builder builder = target.request(APPLICATION_JSON_TYPE);
//        Response resp = builder.post(Entity.json(composeObject));
//        if (resp.getStatus() != 200) {
//            log.error("Composition request failed! " + resp.getStatusInfo().toString());
//        } else {
//            log.debug("Composition POST successful");
//        }
//        processComposeResponse(resp);
    }

//    private void processComposeResponse(Response resp) {
        // Get the response payload
        // TODO: I hope arrays as top-level elements are allowed, check this @victor
//        ArrayNode data = (ArrayNode) resp.getEntity();
//        JsonNode app_paths;
//        Iterator<JsonNode> it = data.elements();
//        while (it.hasNext()) {
//            app_paths = it.next();
//
//            // Extract the app name, and id
//            String appname = app_paths.get("app").textValue();
//            ApplicationId appid = core.getAppId(appname);
//
//            // Extract paths
//            JsonNode all_paths = data.get("tcs");
//
//            // Grab all the listeners registered for this app
//            for (PathUpdateListener l : listenerMap.get(appid)) {
//                // Send the created path intents to the listeners
//                l.updatePaths(computeIntents(all_paths));
//            }
//        }
//    }

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

        JSONObject prev_node = null;
	for (int i = 0; i < pathnodes.length(); i++) {
	    JSONObject node = pathnodes.getJSONObject(i);
            if (prev_node == null) {
                prev_node = node;
                continue;
            } else {
                DefaultLink.Builder link_builder =
                        DefaultLink.builder();
                int prev_id = prev_node.getInt("id");
                int curr_id = node.getInt("id");

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

                link_builder.src(src_connect_point);
                link_builder.dst(dst_connect_point);
		DefaultLink link = link_builder.build();
                link_list.add((Link) link);
                prev_node = node;
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

    private Collection<PathIntent> computeIntents(JSONArray all_paths) {

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
                DefaultPath curr_path =
                        new DefaultPath(provider_id, create_links(pathnodes), 1.0, null);
                intent_builder.path(curr_path);
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

                    PathIntent.Builder intent_builder = PathIntent.builder();
                    DefaultPath curr_path =
                            new DefaultPath(provider_id, link_list,
                                    tables.get(path_index).get(new_prefix), null);
                    intent_builder.path(curr_path);
                    intent_builder.selector(new_selector);
                    PathIntent path_intent = intent_builder.build();
                    result.add(path_intent);
                }
            }
        }
        return result;
    }
    

    /**
     * Get the integer id assigned to an ONOS DeviceID
     *
     * @param id the device id of a switch
     */
    public int getIntegerID(DeviceId id) {
        // Grab the id from the device mapping
        return deviceMap.get(id).intValue();
    }
}
    
