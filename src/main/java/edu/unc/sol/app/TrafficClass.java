package edu.unc.sol.app;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.net.flow.TrafficSelector;


public class TrafficClass {
    protected TrafficSelector selector;
    protected long estimated_volume;

    public TrafficClass(TrafficSelector selector, long estimated_volume) {
        this.selector = selector;
        this.estimated_volume = estimated_volume;
    }

    public TrafficSelector getSelector() {
	return selector;
    }

    public ObjectNode serialize() {
        // TODO: serialize the Traffic class (selector and volume) into and object node.
        return null;
    }

}
