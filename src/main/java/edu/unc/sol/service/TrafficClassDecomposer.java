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
	
	Set<Criterion> criteria_set1 = c1.getSelector().criteria();
	Set<Criterion> criteria_set2 = c2.getSelector().criteria();

	Criterion.Type source_criterion_type = Criterion.Type.IPV4_SRC;
	Criterion.Type dest_criterion_type = Criterion.Type.IPV4_DST;

	IpPrefix source_prefix1 = ((IPCriterion) c1.getSelector().getCriterion(source_criterion_type).ip());
	IpPrefix source_prefix2 = ((IPCriterion) c2.getSelector().getCriterion(source_criterion_type).ip());

	logger.info(source1.toString());
	logger.info(source2.toString());

	boolean source_overlap;
	
	if (source1 == null || source2 == null) {
	    source_overlap = false;
	}
	else {
	    source_overlap = source_prefix1.contains(source_prefix2) || source_prefix2.contains(source_prefix1);
	}

	IpPrefix dest_prefix1 = ((IPCriterion) c1.getSelector().getCriterion(source_criterion_type).ip());
	IpPrefix dest_prefix2 = ((IPCriterion) c2.getSelector().getCriterion(source_criterion_type).ip());

	boolean dest_overlap;
	
	if (dest1 == null || dest2 == null) {
	    dest_overlap = false;
	}
	else {
	    dest_overlap = dest_prefix1.contains(dest_prefix2) || dest_prefix2.contains(dest_prefix1);
	}
        return source_overlap || dest_overlap;
    }

    public static Set<List<TrafficClass>> decompose(List<TrafficClass> listOfTrafficClasses) {

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
