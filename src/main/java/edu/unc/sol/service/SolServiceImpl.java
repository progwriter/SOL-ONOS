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

    private Collection<PathIntent> computeIntents(JsonNode paths) {
        assert paths.isArray();
        ArrayList<PathIntent> result = new ArrayList<>();
        for (final JsonNode pathobj: paths) {
            // Extract the traffic class
            int tcid = pathobj.get("tcid").asInt();
            TrafficClass tc = allTrafficClasses.get(tcid);
            // Get the path nodes
            JsonNode pathnodes = pathobj.get("nodes");
            // Get the fraction of flows on this path
            double fraction = pathobj.get("fraction").asDouble();

            //TODO: construct path intents @sanjay
            // 1. Get the traffic selector from traffic class
            // 2. Figure out how to "split" the traffic selector if the number of paths is >1:
            // If the fraction is .7 (70%) for a path, how do we partition the IP space to route 70%
            // along that path?
            // One approach is https://www.usenix.org/event/hotice11/tech/full_papers/Wang_Richard.pdf
            // Around section 2, but maybe there are alternative ways?

            // 3. Convert path nodes to a series of links (because that's how intents are constructed in ONOS)
            // 4. Create the intent(s), with the new traffic selectors and add it to the result list.
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
