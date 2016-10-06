package edu.unc.sol.service;

import edu.unc.sol.app.PathUpdateListener;
import edu.unc.sol.app.TrafficClass;
import edu.unc.sol.util.Config;
import org.apache.felix.scr.annotations.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Component(immediate = true)
@Service
public class SolServiceImpl implements SolService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    protected Map<ApplicationId, List<TrafficClass>> tcMap;
    protected Map<ApplicationId, List<PathUpdateListener>> listenerMap;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;
    private Boolean running;
    private Client restClient;

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
    }

    @Override
    public void registerApp(ApplicationId id, List<TrafficClass> trafficClasses, PathUpdateListener listener) {
        tcMap.put(id, trafficClasses);
        listenerMap.put(id, new LinkedList<>());
        listenerMap.get(id).add(listener);
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
    }

    @Activate
    protected void activate() {
        running = true;

        // Initialize the rest client
        // Get the address from an environment variable
        String solServer = System.getenv(Config.SOL_ENV_VAR);
        if (solServer != null) {
            // Upon activation, get the topology from the topology service and send it to SOL
            // using rest API
            StringBuilder builder = new StringBuilder();
            String url = builder.append("http://").append(solServer)
                    .append("/topology").toString();
            sendTopology(url);
        } else {
            log.error("No SOL server configured");
        }
        // Start the monitor-solve loop in a new thread
        new Thread(new SolutionCalculator()).run();
        log.info("Started");
    }

    private void sendTopology(String url) {
        WebTarget target = restClient.target(url);
        // WARNING: we are running with the implicit assumption that that the topology does not change
        TopologyGraph topo = topologyService.getGraph(topologyService.currentTopology());
        Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON_TYPE);
        //TODO: figure out how to serialize TopologyGraph according to the schema
        // and send it to the specified URL as a HTTP POST request.
//        Response response = builder.post(Entity.entity(Topo), MediaType.APPLICATION_JSON_TYPE);
//        if (response.getStatus() != 200) {
//            logger.error(response.getStatusInfo().toString())
//        }
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        // TODO: do any cleanup that is necessary
        log.info("Stopped");
    }

    private void recompute() {
        //TODO: Feed the data to SOL instance via REST(?) API
    }

}
