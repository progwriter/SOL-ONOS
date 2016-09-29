package edu.unc.sol.util.codecs;


import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.onosproject.net.topology.TopologyGraph;

public class TopologyCodec extends JsonCodec<TopologyGraph> {

    @Override
    public ObjectNode encode(TopologyGraph entity, CodecContext context) {
        return super.encode(entity, context);
    }
}
