package edu.unc.service;

import edu.unc.app.PathUpdateListener;
import edu.unc.app.TrafficClass;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.core.ApplicationId;

import java.util.List;

@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
public interface SolService {
    void registerApp(ApplicationId id, List<TrafficClass> trafficClasses, PathUpdateListener listener);
    void unregisterApp(ApplicationId id);
    void updateTrafficClasses(ApplicationId id, List<TrafficClass> trafficClasses);
    void addListener(ApplicationId id, PathUpdateListener listener);
    void removeListener(ApplicationId id, PathUpdateListener listener);
}
