package edu.unc.sol.service;

import edu.unc.sol.app.PathUpdateListener;
import edu.unc.sol.app.TrafficClass;
import edu.unc.sol.service.TrafficClassDecomposer;
import edu.unc.sol.util.Config;
import org.apache.felix.scr.annotations.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficSelector.Builder;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.Criterion.Type;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onlab.packet.IpPrefix;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

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
            while (running) {
                // TODO: monitor changes to the traffic classes
                // Upon change, trigger recompute
                recompute();
                // TODO: results of recompute should be sent to the apps
                // using the PathUpdateListener object
            }
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
    public void activate() { //changed from protected
        log.info("Started");

	/*
	log.info("Running Unit Tests");

	IpPrefix p1 = IpPrefix.valueOf(123456,16);
	IpPrefix p2 = IpPrefix.valueOf(123456,16);
	
	TrafficSelector.Builder b1 = DefaultTrafficSelector.builder();
	TrafficSelector.Builder b2 = DefaultTrafficSelector.builder();
	b1.matchIPSrc(p1);
	b2.matchIPSrc(p2);
	
	TrafficSelector s1 = b1.build();
	TrafficSelector s2 = b2.build();
	TrafficClass c1 = new TrafficClass(s1);
	TrafficClass c2 = new TrafficClass(s2);
	log.info("Testing Overlap");
	if(TrafficClassDecomposer.hasOverlap(c1,c2)) {
	    log.info("true");
	}
	else {
	    log.info("false");
	}

	List<TrafficClass> l = new ArrayList<TrafficClass>();	
	l.add(c1);
	l.add(c2);

	log.info("Testing Decompose");
	Set<TrafficClass> set;
	set = TrafficClassDecomposer.decompose(l);
	if (set != null) {
	    log.info(set.toString());
	}
	else {
	    log.info("null");
	}
	*/
	
	//       running = true;

        // Initialize the rest client
        // Get the address from an environment variable
	//        String solServer = System.getenv(Config.SOL_ENV_VAR);
	//        if (solServer == null) {
	//      restClient.target(solServer);
	//  } else {
	//     log.error("No SOL server configured");
	//  }
        // Upon activation, get the topology from the topology service and send it to SOL
        // using rest API
        // FIXME: we are running with the implicit assumption that that the topology does not change
	// TopologyGraph topo = topologyService.getGraph(topologyService.currentTopology());


        // Start the monitor-solve loop in a new thread
	//        new Thread(new SolutionCalculator()).run();
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
