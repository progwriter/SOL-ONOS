/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.unc.te;
// SOL imports
import edu.unc.sol.app.*;
import edu.unc.sol.service.SolService;
// Annotation imports
import org.apache.felix.scr.annotations.*;
// ONLAB imports
import org.onlab.packet.EthType.EtherType;
import org.onlab.packet.IpPrefix;
// ONOSPROJECT imports
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyService;
// Logger imports
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// Java imports
import java.util.*;
import java.lang.Integer;
// Constraint Metric imports
import static edu.unc.sol.app.Constraint.*;

@Component(immediate = true)
public class TrafficEngineeringApp {

    private final Logger log = LoggerFactory.getLogger(getClass());
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private CoreService core;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private SolService sol;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private IntentService intentService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private TopologyService topologyService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private DeviceService deviceService;

    private ApplicationId appid;

    // Creating TCs so that all Hosts can send traffic to all other Hosts
    private List<TrafficClass> makeTrafficClasses() {
        Topology t = topologyService.currentTopology();
        List<Device> devices = new ArrayList<Device>();
        deviceService.getAvailableDevices().forEach(devices::add);

	int prefix = 167772161; // 10.0.0.1
	List<TrafficClass> result = new ArrayList<>();
	HashMap<Device, IpPrefix> ips = new HashMap<>();

	// parse the device ID number
	for (Device d : devices) {
	    int device_num = Integer.parseInt(d.id().toString().substring(3)) - 1; //zero-index
	    ips.put(d,IpPrefix.valueOf(prefix+device_num,32));
	}

	// create traffic classes for each pair of devices
	for (Device src : devices) {
	    for (Device dst : devices) {
		if (src.equals(dst)) {
		    continue;
		}
		TrafficSelector.Builder tcbuilder = DefaultTrafficSelector.builder();
		tcbuilder.matchEthType(EtherType.IPV4.ethType().toShort());
		tcbuilder.matchIPSrc(ips.get(src));
		tcbuilder.matchIPDst(ips.get(dst));
		result.add(new TrafficClass(tcbuilder.build(),
					    src.id(), dst.id(), 1 << 4));
	    }
	}
        return result;
    }

    /**
     * Create the constraint set for our app.
     *
     * @return
     */
    private List<Constraint> getConstraints() {
        // Empty set first
        List s = new ArrayList();
        // Add constraints one by one
        s.add(edu.unc.sol.app.Constraint.ALLOCATE_FLOW);
        s.add(edu.unc.sol.app.Constraint.ROUTE_ALL);
        return s;
    }

    private Objective getObjective() {
        return new Objective(edu.unc.sol.app.ObjectiveName.OBJ_MIN_LINK_LOAD, edu.unc.sol.app.Resource.BANDWIDTH);
    }


    @Activate
    protected void activate() {
	// register the application with the core service
        appid = core.registerApplication("TEApp3");
        HashMap<Resource, Double> costs = new HashMap<>();
        costs.put(Resource.BANDWIDTH, 1.0);
	// register the application with SOL
        sol.registerApp(appid, makeTrafficClasses(),
                new Optimization(getConstraints(), getObjective(), costs),
                paths -> paths.forEach(p -> intentService.submit(p)));
        log.info("TE app started");
    }

    @Deactivate
    protected void deactivate() {
	// unregister the application with SOL
        sol.unregisterApp(appid);
        log.info("TE app stopped");
    }

}
