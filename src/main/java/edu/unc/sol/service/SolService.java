package edu.unc.sol.service;

import edu.unc.sol.app.Optimization;
import edu.unc.sol.app.PathUpdateListener;
import edu.unc.sol.app.TrafficClass;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;

import java.util.List;

public interface SolService {
    public void registerApp(ApplicationId id, List<TrafficClass> trafficClasses, Optimization opt,
                     PathUpdateListener listener);
    public void unregisterApp(ApplicationId id);
//    public void updateTrafficClasses(ApplicationId id, List<TrafficClass> trafficClasses);
//    public void addListener(ApplicationId id, PathUpdateListener listener);
//    public void removeListener(ApplicationId id, PathUpdateListener listener);
    public int getIntegerID(DeviceId id);
}
