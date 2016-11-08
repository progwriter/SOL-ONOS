package edu.unc.sol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.unc.sol.app.Optimization;
import edu.unc.sol.app.PathUpdateListener;
import edu.unc.sol.app.TrafficClass;
import edu.unc.sol.util.Config;
import org.apache.felix.scr.annotations.*;
import org.onlab.graph.Edge;
import org.onlab.graph.Vertex;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.intent.PathIntent;
import org.onosproject.net.topology.*;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.DefaultLink;
import org.onosproject.net.Link;
import edu.unc.sol.service.TrafficClassDecomposer;
import org.onlab.packet.IpPrefix;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.DefaultPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Path;
import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;
import java.util.*;

import static edu.unc.sol.service.Fairness.PROP_FAIR;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

@Component(immediate = true)
@Service
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
    private Edge[][] edge_mapping;
    private Vertex[] vertex_mapping;
    private Boolean running;
    private Client restClient;
    private HashMap<DeviceId, Integer> deviceMap;
    private HashMap<Integer, DeviceId> linkMap;
    private HashMap<ApplicationId, Optimization> optimizations;
    private String remoteURL;
    private List<TrafficClass> allTrafficClasses;

    private class SolutionCalculator implements Runnable {
        @Override
        public void run() {
            log.debug("Recompute thread started");
            while (running) {
                // TODO: monitor changes to the traffic classes
                // Upon change, trigger recompute
//                recompute();
                // TODO: results of recompute should be sent to the apps
                // using the PathUpdateListener object
            }
            log.debug("Recompute thread ending");
        }
    }

    public SolServiceImpl() {
        // Initialize basic structures
        tcMap = new HashMap<>();
        listenerMap = new HashMap<>();
        running = false;
        restClient = ClientBuilder.newClient();
        deviceMap = new HashMap<>();
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
        if (solServer != null) {
            // Build a proper url for the rest client
            StringBuilder builder = new StringBuilder();
            remoteURL = builder.append("http://").append(solServer).append("/api/v1/").toString();
            // Send the topology to the SOL server
            sendTopology(remoteURL);
        } else {
            log.error("No SOL server configured");
        }
        // Start the monitor-solve loop in a new thread
        // TODO: figure out an appropriate thread model here @victor
//        new Thread(new SolutionCalculator()).run();
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
        // Get a target from the rest client
        WebTarget target = restClient.target(url).path("/topology");
        // WARNING: we are running with the implicit assumption that that the topology does not change
        // Extract the topology from the topology service
        TopologyGraph topo = topologyService.getGraph(topologyService.currentTopology());
        // Now build our request
        Invocation.Builder builder = target.request(APPLICATION_JSON_TYPE);
        ObjectNode topoj = new ObjectNode(JsonNodeFactory.instance);
        // Make sure the graph is directed
        topoj.putObject("graph");
        topoj.put("directed", true);
        // Create holders for nodes and link
        ArrayNode nodes = topoj.putObject("nodes").putArray("items");
        ArrayNode links = topoj.putObject("links").putArray("items");
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
            ObjectNode node = nodes.addObject();
            node.put("id", getIntegerID(dev));
            // Devices in ONOS are by default switches (hosts are a separate category),
            // which is EXACTLY what we need
            node.put("services", "switch");
            // We need resources to be present, but for now we are not extracting any info
            // from ONOS
            node.putObject("resources");
            // TODO: Put resources of nodes, if any, like CPU  @victor
            // TODO: extract middlebox info from ONOS somehow? @victor
            vertex_index += 1;
        }
        for (Edge e : topology_edges) {
            // Note: by default ONOS graphs (and thus edges) are directed.
            ObjectNode link = links.addObject();

            int srcid = getIntegerID(((DefaultTopologyVertex) e.src()).deviceId());
            int dstid = getIntegerID(((DefaultTopologyVertex) e.dst()).deviceId());
            link.put("source", srcid);
            link.put("target", dstid);
            ObjectNode resources = link.putObject("resources");
            ConnectPoint edge_link_src = (((DefaultTopologyEdge) e).link()).src();
            Port src_port = deviceService.getPort(edge_link_src.deviceId(), edge_link_src.port());
            // Store the bandwidth capacity in Mbps, easier this way
            long src_bandwith_mbps = src_port.portSpeed();
            resources.put("bw", src_bandwith_mbps);
        }
        // Send request to the specified URL as a HTTP POST request.
        Response response = builder.post(Entity.json(topoj));
        if (response.getStatus() != 200) {
            log.error(response.getStatusInfo().toString());
        } else {
            log.info("Successfully POSTed the topology");
        }
    }

    @Deactivate
    protected void deactivate() {
        // TODO: any cleanup if necessary @sanjay
        log.info("Stopped the SOL service");
    }

    /**
     * Compute a new solution from all of the registered apps.
     */
    private void recompute() {
        // Serialize all of our apps and post them to the server
        ObjectNode composeObject = new ObjectNode(JsonNodeFactory.instance);
        composeObject.put("fairness", PROP_FAIR.toString());

        HashMap<TrafficClass, Integer> tc_ids = new HashMap<>();
        int tc_counter = 0;
        ArrayNode applist = composeObject.putArray("apps");
        for (ApplicationId appid : optimizations.keySet()) {
            ObjectNode app = applist.addObject();
            app.put("id", appid.toString());
            //TODO: allow predicate customization in the future @victor
            app.put("predicate", "null_predicate");
            app.setAll(optimizations.get(appid).toJSONnode());
            ArrayNode tc_list = app.putArray("traffic_classes");
            for (TrafficClass tc : tcMap.get(appid)) {
                tc_list.add(tc.toJSONnode(tc_counter++));
                // Keep track of all traffic classes so we can decode the response
                allTrafficClasses.add(tc);
            }
        }
        // Send the composition request over:
        WebTarget target = restClient.target(remoteURL).path("/compose");
        Invocation.Builder builder = target.request(APPLICATION_JSON_TYPE);
        Response resp = builder.post(Entity.json(composeObject));
        if (resp.getStatus() != 200) {
            log.error("Composition request failed! " + resp.getStatusInfo().toString());
        } else {
            log.debug("Composition POST successful");
        }
        processComposeResponse(resp);
    }

    private void processComposeResponse(Response resp) {
        // Get the response payload
        // TODO: I hope arrays as top-level elements are allowed, check this @victor
        ArrayNode data = (ArrayNode) resp.getEntity();
        JsonNode app_paths; Iterator<JsonNode> it = data.elements();
        while (it.hasNext()) {
            app_paths = it.next();

            // Extract the app name, and id
            String appname = app_paths.get("app").textValue();
            ApplicationId appid = core.getAppId(appname);

            // Extract paths
            JsonNode paths = data.get("paths");

            // Grab all the listeners registered for this app
            for (PathUpdateListener l : listenerMap.get(appid)) {
                // Send the created path intents to the listeners
                l.updatePaths(computeIntents(paths));
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
    
    private Collection<PathIntent> computeIntents(JsonNode paths) {
        assert paths.isArray();

	//FRACTION CALCULATION PROCESS:
	//1. create arr of [paths.size()] where each is array list for prefix, fraction
	//2. calculate normalized load for each leaf (create path id, curr_load)
	//3. sort by curr_load
	//4. find highest power of 2 for each curr_load, add prefix rule with its fraction to index in arr
	//5. subtract this highest power of 2 for curr_load
	//6. keep repeating until all curr_load = 0;

	//Calculate max #of decimal places in any fraction
	int max_places = 0;
	for (final JsonNode pathobj:paths) {
	    double fraction = pathobj.get("fraction").asDouble();
	    String fraction_string = Double.toString(fraction);
	    int integer_places = fraction_string.indexOf('.');
	    int decimal_places = fraction_string.length() - integer_places - 1;
	    max_places = Integer.max(max_places, decimal_places);
	}

	//Calculate the total weight, where all weights are > 1 now
	int total = 0;
	double weight = Math.pow(10.0, max_places);
	for (final JsonNode pathobj:paths) {
	    double fraction = pathobj.get("fraction").asDouble();
	    double weighted = weight * fraction;
	    total += (int) weighted;
	}

	//Calculate a new_total which is the next highest power of 2
	long power = Math.round(Math.log(total)/Math.log(2)); //calculate next highest power of 2
	double fraction_weight = Math.pow(2.0,power) / (total*1.0);
	long new_total = Math.round(Math.pow(2.0,power)); //should be a power of 2
	ArrayList<ArrayList<String>> prefixes = new ArrayList<ArrayList<String>>();;
	ArrayList<ArrayList<Double>> fractions = new ArrayList<ArrayList<Double>>();

	for (int i = 0; i < paths.size(); i++) {
	    prefixes.add(new ArrayList<String>());
	    fractions.add(new ArrayList<Double>());
	}

	//Normalize the Weights for each of the paths with this new pow of 2
	//index 0 is the path the weight is for when we sort the array
	//index 1 is the curr_load for the path
	long[][] normalized_weights = new long[paths.size()][2];
	long curr_new_total = new_total;
	int weight_index = 0;
	for (final JsonNode pathobj:paths) {
	    double fraction = pathobj.get("fraction").asDouble();
	    long weighted = (long) (weight*fraction*fraction_weight);
	    if (weight_index == (paths.size()-1)) {
		normalized_weights[weight_index][0] = weight_index;
		normalized_weights[weight_index][1] = curr_new_total;
	    }
	    else {
		normalized_weights[weight_index][0] = weight_index;
		normalized_weights[weight_index][1] = weighted;
		curr_new_total -= weighted;
	    }
	    weight_index += 1;
	}

	//should assert THAT sum of all normalized weights is power of 2

	boolean has_non_zero = true;

	//keep creating prefix rules, along with fractions, starting from the
	//largest load to the smallest each time
	long cumulative_load = 0;
	while (has_non_zero) {
	    long[][] sorted_weights = sort_weights(normalized_weights);
	    int i = 0; //always create a rule for the max load after sorting
	    long curr_load = sorted_weights[i][1];
	    
	    if (curr_load == 0) {
		has_non_zero = false;
		continue;
	    }
		
	    long highest_power_two = (long) (Math.log(curr_load) / Math.log(2.0));
	    //--make sure converting to int doesnt cause an issue
	    String binary = Integer.toBinaryString((int)cumulative_load);
	    
	    //take the rightmost 'power' bits
	    String proper_binary = binary.substring(binary.length()-(int)power, binary.length());
		
	    //take the first ('power' - 'highest_power_two') bits of this string as the bits to add to the prefix
	    String prefix_add_bits = proper_binary.substring(0, (int)(power-highest_power_two));
	    Double prefix_fraction = new Double(Math.pow(2.0, highest_power_two) / (new_total * 1.0));
		
	    prefixes.get((int)sorted_weights[i][0]).add(prefix_add_bits);
	    fractions.get((int)sorted_weights[i][0]).add(prefix_fraction);
		
	    normalized_weights[i][1] -= (long) Math.pow(2.0, highest_power_two);
	    cumulative_load += (int) Math.pow(2.0, highest_power_two);		
	}    
	
	
        ArrayList<PathIntent> result = new ArrayList<PathIntent>();
 		
	int path_index = -1;
	for (final JsonNode pathobj: paths) {
	    path_index++;
	    // Extract the traffic class
	    int tcid = pathobj.get("tcid").asInt();
	    TrafficClass tc = allTrafficClasses.get(tcid);
	    // Get the path nodes
	    
	    JsonNode pathnodes = pathobj.get("nodes");
	    
	    List<Link> link_list = new ArrayList<Link>();
	    
	    JsonNode prev_node = null;
	    for (JsonNode node:pathnodes) {
		if (prev_node == null) {
		    prev_node = node;
		    continue;
		}
		else {
		    DefaultLink.Builder link_builder = DefaultLink.builder();
		    
		    int prev_id = prev_node.get("id").asInt();
		    int curr_id = node.get("id").asInt();
		    
		    DeviceId prev_dev = linkMap.get(prev_id);
		    DeviceId curr_dev = linkMap.get(curr_id);
		    
		    //TODO need to get ConnectPoint from DeviceId
		    
		    ConnectPoint src_connect_point = null;
		    ConnectPoint dst_connect_point = null;

		    link_builder.src(src_connect_point);
		    link_builder.dst(dst_connect_point);
		    DefaultLink link = link_builder.build();
		    link_list.add((Link)link);
		    prev_node = node;
		}
	    }
	    
	    
	    //Get the fraction of flows on this path
	    double fraction = pathobj.get("fraction").asDouble();
	    
	    TrafficSelector original_selector = tc.getSelector();
	    Set<Criterion> original_criteria = original_selector.criteria();

	    //Loop through each wildcard rule we have for this path
	    for (int i = 0; i < prefixes.get(path_index).size(); i++) {
		    TrafficSelector.Builder ts_builder = DefaultTrafficSelector.builder();
		    for (Criterion curr_criteria : original_criteria) {
			if (curr_criteria.type() != Criterion.Type.IPV4_SRC) {
			    ts_builder.add(curr_criteria);
			}
			else {
			    String extra_bits = prefixes.get(path_index).get(i);
			    IpPrefix curr_prefix = ((IPCriterion) original_selector.getCriterion(Criterion.Type.IPV4_SRC)).ip();
			    //TODO need to add these extra bits to the curr_prefix and adjust the length and create a new IPCriterion
			    ts_builder.add(curr_criteria);
			}   
		    }
		    
		    TrafficSelector new_selector = ts_builder.build();
		    
		    PathIntent.Builder intent_builder = PathIntent.builder();
		    DefaultPath curr_path = new DefaultPath(null, link_list, fractions.get(path_index).get(i), null);
		    intent_builder.path(curr_path);
		    intent_builder.selector(new_selector);

		    PathIntent path_intent = intent_builder.build();
		    result.add(path_intent);
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
    
