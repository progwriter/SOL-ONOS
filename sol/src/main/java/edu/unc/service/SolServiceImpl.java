package edu.unc.service;

import edu.unc.app.PathUpdateListener;
import edu.unc.app.TrafficClass;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.onosproject.core.ApplicationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 */
@Component(immediate = true)
@Service
public class SolServiceImpl implements SolService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    protected Map<ApplicationId, List<TrafficClass>> tcMap;
    protected Map<ApplicationId, List<PathUpdateListener>> listenerMap;
    private Boolean running;

    private class SolutionCalculator implements Runnable {
        @Override
        public void run() {
            while (running) {
                //TODO: monitor changes to the traffic classes
                // Upon change, trigger recompute
                recompute();
                // TODO: results of recompute should be sent to the apps
                // using the PathUpdateListener object
            }
        }
    }

    public SolServiceImpl() {
        tcMap = new HashMap<>();
        listenerMap = new HashMap<>();
        running = false;
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
        log.info("Started");
        running = true;
        new Thread(new SolutionCalculator()).run();
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