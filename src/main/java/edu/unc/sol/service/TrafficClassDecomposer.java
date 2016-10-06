package edu.unc.sol.service;

import edu.unc.sol.app.TrafficClass;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.Criterion.Type;
import org.onosproject.net.flow.criteria.IPCriterion;

public class TrafficClassDecomposer {

    final static Logger logger = LoggerFactory.getLogger(TrafficClassDecomposer.class);

    public static Criterion findCriterion(Set<Criterion> s, Criterion.Type val) {
	for (Criterion c : s) {
	    if (c.type() == val) {
		return c;
	    }
	}
	return null;
    }
    
    public static boolean hasOverlap(TrafficClass c1, TrafficClass c2) {

	// TODO: write a function that determines if two classes' IP addresses and ports overlap
        // Use the selector member of traffic class
        // to determine what traffic the class is identifying.

	
	logger.info("Calling 'hasOverlap'");

	//QUESTION: is there only one of each criteria in a TrafficClass's selector's set of criterion?
	
	Set<Criterion> set1 = c1.getSelector().criteria();
	Set<Criterion> set2 = c2.getSelector().criteria();

	Criterion.Type source = Criterion.Type.IPV4_SRC;
	Criterion.Type dest = Criterion.Type.IPV4_DST;

	//QUESTION: are we trying to compare the source IPs / dst IPs (what is source and what is dst?)
	
	IPCriterion source1 = (IPCriterion) c1.getSelector().getCriterion(source);
	IPCriterion source2 = (IPCriterion) c2.getSelector().getCriterion(source);

	if (source1 == null) logger.info("source 1 is null");
	if (source2 == null) logger.info("source 2 is null");

	logger.info(source1.toString());
	logger.info(source2.toString());

	boolean boo1;
	
	if (source1 == null || source2 == null) {
	    boo1 = false;
	}
	else {
	    boo1 = source1.toString().equals(source2.toString());
	    if (boo1) logger.info("overlap function says true");
	    else logger.info("overlap function says false");
	}

	IPCriterion dest1 = (IPCriterion) c1.getSelector().getCriterion(dest);
	IPCriterion dest2 = (IPCriterion) c2.getSelector().getCriterion(dest);

	boolean boo2;
	
	if (dest1 == null) logger.info("dest 1 is null");
	if (dest2 == null) logger.info("dest 2 is null");
	
	if (dest1 == null || dest2 == null) {
	    boo2 = false;
	}
	else {
	    boo2 = dest1.toString().equals(dest2.toString());
	    if (boo2) logger.info("overlap function says dest true");
	    else logger.info("overlap function says dest false");
	}
        return boo1 || boo2;
    }

    public static Set<TrafficClass> decompose(List<TrafficClass> listOfTrafficClasses) {

	// TODO: Write a functions that takes a list of traffic classes and if there is any overlap between
        // them, returns "smaller" traffic classes that are non-overlapping.
	
	logger.info("Calling 'decompose'");

	// Question: Can we return any smaller subset? If A and B overlap can we JUST include A in the final set?

	Set<TrafficClass> ret = new HashSet<TrafficClass>();
	boolean overlap;
	for (TrafficClass t : listOfTrafficClasses) {
	    overlap = false;
	    for (TrafficClass u : ret) {
		if (hasOverlap(t,u)) {
		    overlap = true;
		}
	    }
	    if (!overlap) {
		ret.add(t);
	    }
	}
        return null;
    }
}
