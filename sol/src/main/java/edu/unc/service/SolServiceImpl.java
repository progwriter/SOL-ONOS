package edu.unc.service;

import edu.unc.app.PathUpdateListener;
import edu.unc.app.TrafficClass;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Deactivate;
import org.onosproject.core.ApplicationId;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 */
public class SolServiceImpl implements SolService {
    protected Map<ApplicationId, List<TrafficClass>> tcMap;
    protected Map<ApplicationId, List<PathUpdateListener>> listenerMap;
    private Boolean running;

    private class SolutionCalculator implements Runnable {
        @Override
        public void run() {
            while (running)
                //TODO: wait here
                recompute();
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
//        log.info("Started");
        running = true;
        new Thread(new SolutionCalculator()).run();
    }

    @Deactivate
    protected void deactivate() {
//        log.info("Stopped");
        // TODO: do any cleanup that is necessary
    }

    private void recompute() {
        //TODO: Feed the data to SOL instance via REST(?) API
    }

}