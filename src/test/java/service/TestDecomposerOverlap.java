package service;


import edu.unc.sol.app.TrafficClass;
import edu.unc.sol.service.TrafficClassDecomposer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.onlab.packet.IpPrefix;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class TestDecomposerOverlap {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {IpPrefix.valueOf("192.168.1.0/24"), IpPrefix.valueOf("192.168.1.0/24"), true},
                {IpPrefix.valueOf("192.168.1.0/8"), IpPrefix.valueOf("192.168.1.0/24"), true},
                {IpPrefix.valueOf("192.168.1.0/24"), IpPrefix.valueOf("192.168.1.0/8"), true},
                {IpPrefix.valueOf("192.168.1.199/25"), IpPrefix.valueOf("192.168.1.0/25"), false},
                {IpPrefix.valueOf("10.168.1.199/8"), IpPrefix.valueOf("192.168.1.0/8"), false},
        });
    }

    @Parameterized.Parameter
    public IpPrefix p1;
    @Parameterized.Parameter(value = 1)
    public IpPrefix p2;
    @Parameterized.Parameter(value = 2)
    public boolean result;

    @Test
    public void test() {
        TrafficSelector.Builder b1 = DefaultTrafficSelector.builder();
        TrafficSelector.Builder b2 = DefaultTrafficSelector.builder();
        b1.matchIPSrc(p1);
        b2.matchIPSrc(p2);

        TrafficSelector s1 = b1.build();
        TrafficSelector s2 = b2.build();
        TrafficClass c1 = new TrafficClass(s1);
        TrafficClass c2 = new TrafficClass(s2);

        Assert.assertEquals(TrafficClassDecomposer.hasOverlap(c1, c2), result);
    }
}

//    @Test
//    public void testDecompose() {
//        Set<TrafficClass> set;
//        List<TrafficClass> l = new ArrayList<TrafficClass>();
//        l.add(c1);
//        l.add(c2);
//        set = TrafficClassDecomposer.decompose(l);
//
//        if (set != null) {
//            log.info(set.toString());
//        } else {
//            log.info("null");
//        }
