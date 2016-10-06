package edu.unc.sol.service;

import edu.unc.sol.app.TrafficClass;
import org.onlab.packet.IpPrefix;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class TrafficClassDecomposer {

    final static Logger logger = LoggerFactory.getLogger(TrafficClassDecomposer.class);

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

        Criterion source_selector1 = c1.getSelector().getCriterion(source_criterion_type);
        Criterion source_selector2 = c2.getSelector().getCriterion(source_criterion_type);

        boolean source_overlap;

        if (source_selector1 == null || source_selector2 == null) {
            source_overlap = false;
        } else {
            IpPrefix source_prefix1 = ((IPCriterion) source_selector1).ip();
            IpPrefix source_prefix2 = ((IPCriterion) source_selector2).ip();
            source_overlap = source_prefix1.contains(source_prefix2) || source_prefix2.contains(source_prefix1);
        }

        Criterion dest_selector1 = c1.getSelector().getCriterion(source_criterion_type);
        Criterion dest_selector2 = c2.getSelector().getCriterion(source_criterion_type);

        boolean dest_overlap;

        if (dest_selector1 == null || dest_selector2 == null) {
            dest_overlap = false;
        } else {
            IpPrefix dest_prefix1 = ((IPCriterion) dest_selector1).ip();
            IpPrefix dest_prefix2 = ((IPCriterion) dest_selector2).ip();
            dest_overlap = dest_prefix1.contains(dest_prefix2) || dest_prefix2.contains(dest_prefix1);
        }
        return source_overlap || dest_overlap;
    }

    public static Set<List<TrafficClass>> decompose(List<TrafficClass> listOfTrafficClasses) {

        // TODO: Write a functions that takes a list of traffic classes and if there is any overlap between
        // them, returns "smaller" traffic classes that are non-overlapping.

        logger.info("Calling 'decompose'");

        Set<List<TrafficClass>> decomp_set = new HashSet<List<TrafficClass>>();
        boolean found_list;
        boolean overlap_found;
        for (TrafficClass traffic_class : listOfTrafficClasses) {
            found_list = false;
            for (List<TrafficClass> curr_list : decomp_set) {
                overlap_found = false;
                for (TrafficClass curr_class : curr_list) {
                    if (hasOverlap(traffic_class, curr_class)) {
                        overlap_found = true;
                        break;
                    }
                }
                if (!overlap_found) {
                    curr_list.add(traffic_class);
                    found_list = true;
                }
            }
            if (!found_list) {
                List<TrafficClass> new_list = new ArrayList<TrafficClass>();
                new_list.add(traffic_class);
                decomp_set.add(new_list);
            }
        }
        return decomp_set;
    }
}
