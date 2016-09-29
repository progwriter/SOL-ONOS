package edu.unc.sol.app;

import org.onosproject.net.flow.TrafficSelector;


public class TrafficClass {
    protected TrafficSelector selector;
//    protected Predicate predicate;

    public TrafficClass(TrafficSelector selector) {
        this.selector = selector;
//        this.predicate = p;
    }

}
