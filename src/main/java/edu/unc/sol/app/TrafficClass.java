package edu.unc.sol.app;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.net.flow.TrafficSelector;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.onlab.packet.IpPrefix;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.criteria.PortCriterion;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.flow.DefaultTrafficSelector;

public class TrafficClass {
    protected DefaultTrafficSelector selector;
    protected long estimated_volume;

    public TrafficClass(TrafficSelector selector, long estimated_volume) {
        this.selector = (DefaultTrafficSelector) selector;
        this.estimated_volume = estimated_volume;
    }

    public TrafficSelector getSelector() {
	return selector;
    }

    public ObjectNode serialize() {
	// TODO: serialize the Traffic class (selector and volume) into and object node.
	
	ObjectNode traffic_node = new ObjectNode(JsonNodeFactory.instance);
	//id - unique ID number of this traffic class
	//name - human-readable name to identify this traffic class
	//src - source (ingress) node for the traffic class
	//dst - destination (egress) node for this traffic class
	//vol_flows - volume of this traffic class in flows
	//src_ip_prefix - src IP prefix that matches traffic in this class
	//dst_ip_prefix - dst IP prefix that matches traffic in this class

	//what should this id correspond to, potentially the metadata? -sanjay
	int id = -1;
	//am I creating the human readable name in this step? -sanjay
	String name = "";
	//do we want the src port number? -sanjay
	Criterion.Type source_port_criterion_type = Criterion.Type.IPV4_SRC;
	Criterion source_port_ip = this.selector.getCriterion(source_port_criterion_type);
	if (source_port_ip != null) {
	    PortNumber source_port = ((PortCriterion) source_port_ip).port();
	    long src = source_port.toLong();
	    traffic_node.put("src",src);
	}
	//do we want the dst port number? -sanjay
	Criterion.Type dest_port_criterion_type = Criterion.Type.IPV4_DST;
	Criterion dest_port_ip = this.selector.getCriterion(dest_port_criterion_type);
	if (dest_port_ip != null) {
	    PortNumber dest_port = ((PortCriterion) dest_port_ip).port();
	    long dst = dest_port.toLong();
	    traffic_node.put("dst",dst);
	}
	//Is this the correct way to initialize vol_flows? -sanjay
	long vol_flows = this.estimated_volume;
	traffic_node.put("vol_flows",vol_flows);
	Criterion.Type source_criterion_type = Criterion.Type.IPV4_SRC;
	Criterion source_ip = this.selector.getCriterion(source_criterion_type);
	if (source_ip != null) {
	    IpPrefix source_prefix = ((IPCriterion) source_ip).ip();
	    String src_ip_prefix = source_prefix.toString();
	    traffic_node.put("src_ip_prefix", src_ip_prefix);
	}
	Criterion.Type dest_criterion_type = Criterion.Type.IPV4_DST;
	Criterion dest_ip = this.selector.getCriterion(dest_criterion_type);
	if (dest_ip != null) {
	    IpPrefix dest_prefix = ((IPCriterion) dest_ip).ip();
	    String dst_ip_prefix = dest_prefix.toString();
	    traffic_node.put("dst_ip_prefix",dst_ip_prefix);
	}
        return traffic_node;
    }

}
