package edu.unc.sol.app;


import edu.unc.sol.service.SolService;
import edu.unc.sol.service.SolServiceImpl;
import org.apache.felix.scr.annotations.Activate;
import org.json.JSONObject;
import org.onlab.packet.IpPrefix;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrafficClass {
    private final Logger log = LoggerFactory.getLogger(getClass());
    protected DefaultTrafficSelector selector;
    protected long estimated_volume;
    protected DeviceId src;
    protected DeviceId dst;

    protected SolService solService = SolServiceImpl.getInstance();
    /**
     * Create a new traffic class
     *
     * @param selector         ONOS traffic selector -- tells us what type of traffic belongs to this class.
     *                         It is assumed that traffic is IPv4 (at least for this prototype)
     * @param src              The ingress switch node (device) for this traffic class
     * @param dst              The egress switch node (device) for this traffic class
     * @param estimated_volume An estimate of the volume of traffic for this traffic class in Mbps
     */
    public TrafficClass(TrafficSelector selector, DeviceId src, DeviceId dst, long estimated_volume) {
        this.selector = (DefaultTrafficSelector) selector;
        this.estimated_volume = estimated_volume;
        this.src = src;
        this.dst = dst;
    }

    /**
     * Get the traffic selector for this traffic class
     *
     * @return
     */
    public TrafficSelector getSelector() {
        return selector;
    }

   /**
     * Serialize this traffic class into a JSON-compatible object
     *
     * @param id: Unique, sequential, integer ID assigned to the traffic class by the SOL Service
     * @return
     */
    public JSONObject toJSONnode(int id) {
        JSONObject traffic_node = new JSONObject();
        //id - unique ID number of this traffic class
        //name - human-readable name to identify this traffic class
        //src - source (ingress) node for the traffic class
        //dst - destination (egress) node for this traffic class
        //vol_flows - volume of this traffic class in flows
        //src_ip_prefix - src IP prefix that matches traffic in this class
        //dst_ip_prefix - dst IP prefix that matches traffic in this class

        traffic_node.put("tcid", id);
        if (solService == null) {
            log.error("SOL SERVICE IS NULLLL@!#@!$!@#");
        }
        traffic_node.put("src", solService.getIntegerID(this.src));
        traffic_node.put("dst", solService.getIntegerID(this.dst));
        traffic_node.put("vol_flows", this.estimated_volume);
        Criterion source_ip = this.selector.getCriterion(Criterion.Type.IPV4_SRC);
        if (source_ip != null) {
            IpPrefix source_prefix = ((IPCriterion) source_ip).ip();
            String src_ip_prefix = source_prefix.toString();
            traffic_node.put("src_ip_prefix", src_ip_prefix);
        } else {
            log.warn("Source IP prefix is unavailable for this traffic class");
        }
        Criterion dest_ip = this.selector.getCriterion(Criterion.Type.IPV4_DST);
        if (dest_ip != null) {
            IpPrefix dest_prefix = ((IPCriterion) dest_ip).ip();
            String dst_ip_prefix = dest_prefix.toString();
            traffic_node.put("dst_ip_prefix", dst_ip_prefix);
        } else {
            log.warn("Destination IP prefix is unavailable for this traffic class");
        }
        return traffic_node;
    }
    // TODO: in the future far far away we can attempt to estimate flow volumes using the flow service and flow stats. Ambitious.
}
