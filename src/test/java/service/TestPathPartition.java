package service;


import edu.unc.sol.app.TrafficClass;
import edu.unc.sol.service.TrafficClassDecomposer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.IpAddress;
import org.onlab.packet.Ip4Address;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;
import java.text.DecimalFormat;
import java.math.RoundingMode;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class TestPathPartition {
    
    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
		{1,IpPrefix.valueOf("192.168.1.0/24"),new double[] {0.1,0.9}},
		{2,IpPrefix.valueOf("192.168.1.0/22"), new double[] {0.1,0.2,0.7}},
		{3,IpPrefix.valueOf("192.168.1.0/20"),new double[] {0.1,0.2,0.3,0.4}},
		{4,IpPrefix.valueOf("192.168.1.0/18"), new double[] {0.1,0.5,0.4}},
		{5,IpPrefix.valueOf("192.168.1.0/17"),new double[] {0.2,0.2,0.2,0.2,0.2}},
		{6,IpPrefix.valueOf("192.168.1.0/15"), new double[] {0.1,0.2,0.35,0.35}},
		{7,IpPrefix.valueOf("192.168.1.0/14"),new double[] {0.55,0.45}},
		{8,IpPrefix.valueOf("192.168.1.0/12"), new double[] {0.105,0.895}},
		{9,IpPrefix.valueOf("192.168.1.0/10"),new double[] {0.7,0.3}},
		{10,IpPrefix.valueOf("192.168.1.0/8"), new double[] {0.15,0.25,0.6}},
		{11,IpPrefix.valueOf("192.168.1.0/12"), new double[] {0.655,0.345}}
	    });
    }

    @Parameterized.Parameter
    public int test_num;
    @Parameterized.Parameter(value = 1)
    public IpPrefix prefix;
    @Parameterized.Parameter(value = 2)
    public double[] f;


    //function to sort our custom arr we use in computeIntents
    private long[][] sort_weights(long[][] arr) {
	//copy argument
	long[][] new_arr = new long[arr.length][2];
	for (int i = 0; i < arr.length; i++) {
	    new_arr[i][0] = arr[i][0];
	    new_arr[i][1] = arr[i][1];
	}

	//in place selection sort
	for (int j = 0; j < arr.length; j++) {
	    long curr_max = -1;
	    int max_i = -1;
	    for (int i = j; i < arr.length; i++) {
		if (new_arr[i][1] > curr_max) {
		    curr_max = new_arr[i][1];
		    max_i = i;
		}
	    }
	    new_arr[j][0] = new_arr[max_i][0];
	    new_arr[j][1] = new_arr[max_i][1];
	}
	return new_arr;
    }
    
    
    @Test
    public void test() throws InterruptedException{
	
	System.out.println("Starting Test " + test_num + "...");
	
	int length = f.length;

	//precision is to 10 decimals for comparisons
	DecimalFormat df = new DecimalFormat("#.##########");
	df.setRoundingMode(RoundingMode.CEILING);
	
	//FRACTION CALCULATION PROCESS:
	//1. calculate normalized load for each leaf (create path id, curr_load)
	//2. sort by curr_load
	//3. find highest power of 2 for each curr_load
	//   add prefix rule with its fraction to index in arr
	//4. subtract this highest power of 2 for curr_load
	//5. keep repeating until all curr_load = 0;
	
	//new method
	int precision = 32 - prefix.prefixLength(); //depth of the tree
	long power = precision;
	double fraction_weight = Math.pow(2.0, precision);
	long new_total = (long) fraction_weight;

	//Normalize the Weights for each of the paths with this new pow of 2
	//index 0 is the path the weight is for when we sort the array
	//index 1 is the curr_load for the path
	long[][] normalized_weights = new long[length][2];
	long curr_new_total = new_total;
	int weight_index = 0;
	for (int i = 0; i < length; i++) {
	    double fraction = f[i];

	    //TODO: rounding here may be the right choice rather than rounddown
	    long weighted = (long) (fraction*fraction_weight);
	    if (weight_index == (length-1)) {
		normalized_weights[weight_index][0] = weight_index;
		normalized_weights[weight_index][1] = curr_new_total;
	    }
	    else {
		normalized_weights[weight_index][0] = weight_index;
		normalized_weights[weight_index][1] = weighted;
		curr_new_total -= weighted;
	    }
	    weight_index += 1;
	}

	ArrayList<ArrayList<String>> prefixes =
	    new ArrayList<ArrayList<String>>();;
	ArrayList<ArrayList<Double>> fractions =
	    new ArrayList<ArrayList<Double>>();
	
	for (int i = 0; i < length; i++) {
	    prefixes.add(new ArrayList<String>());
	    fractions.add(new ArrayList<Double>());
	}

	
	boolean has_non_zero = true;

	//keep creating prefix rules, along with fractions for largest load
	long cumulative_load = 0;
	while (has_non_zero) {
	    long[][] sorted_weights = sort_weights(normalized_weights);
	    int i = 0; //always create a rule for the max load after sorting
	    long curr_load = sorted_weights[i][1];
	    
	    if (curr_load == 0) {
		has_non_zero = false;
		continue;
	    }
		
	    long highest_power_two = (long)
		(Math.log(curr_load) / Math.log(2.0));

	    
	    String binary = Integer.toBinaryString((int)cumulative_load);
	    
	    //take the rightmost 'power' bits
	    String proper_binary = "";
	    if (power > binary.length()) {
		for (int k = 0; k < power-binary.length(); k++) {
		    proper_binary = proper_binary + "0";
		}
		proper_binary = proper_binary + binary;
	    }
	    else {
		proper_binary = binary.substring(binary.length()-(int)power, binary.length());
	    }
	    
	    //add the first ('power' - 'highest_power_two') bits to the prefix
	    String prefix_add_bits =
		proper_binary.substring(0, (int)(power-highest_power_two));
	    Double prefix_fraction =
		new Double(Math.pow(2.0, highest_power_two)/(new_total * 1.0));
		
	    prefixes.get((int)sorted_weights[i][0]).add(prefix_add_bits);
	    fractions.get((int)sorted_weights[i][0]).add(prefix_fraction);

	    normalized_weights[(int)(sorted_weights[i][0])][1] -= (long) Math.pow(2.0, highest_power_two);
	    cumulative_load += (int) Math.pow(2.0, highest_power_two);

	    TimeUnit.SECONDS.sleep(3);
	}

	int original_length = prefix.prefixLength();
	double total_cumulative = 0.0;
	for (int i = 0; i < length; i++) {
	    ArrayList<String> curr_prefixes = prefixes.get(i);
	    double curr_fraction = f[i];
	    double cumulative_proportion = 0.0;
	    for (int j = 0; j < curr_prefixes.size(); j++) {
		String extra_bits = curr_prefixes.get(j);
		int curr_prefix_len = prefix.prefixLength();
		int shift = 31 - curr_prefix_len;
		Ip4Address curr_ip = prefix.address().getIp4Address();
		int curr_ip_int = curr_ip.toInt();
		int curr_mask = 0;
		for (int ind = 0; ind < extra_bits.length(); ind++){
		    String curr_char =
			extra_bits.substring(ind,ind+1);
		    int curr_bit =
			Integer.valueOf(curr_char).intValue();
		    curr_mask =
			curr_mask | (curr_bit << (shift-ind));
		}
		int new_ip_int = curr_ip_int | curr_mask;
		IpAddress new_ip = IpAddress.valueOf(new_ip_int);
		int new_prefix_len =
		    curr_prefix_len + extra_bits.length();
		IpPrefix new_prefix =
		    IpPrefix.valueOf(new_ip, new_prefix_len);
		int curr_length = new_prefix.prefixLength();
		double proportion = Math.pow(2.0, (32-curr_length)*1.0) / Math.pow(2.0, (32-original_length)*1.0);
		cumulative_proportion += proportion;
	    }
	    System.out.println("Target Fraction: " + f[i] + " ||| Calculated Fraction: " + cumulative_proportion);
	    //assert each cumulative proprtion is within 10% of what we want
	    Assert.assertEquals(Double.compare(cumulative_proportion, Double.valueOf(df.format((f[i]-0.1))).doubleValue()) >= 0, true);
	    Assert.assertEquals(Double.compare(cumulative_proportion, Double.valueOf(df.format((f[i]+0.1))).doubleValue()) <= 0, true);
	    total_cumulative += cumulative_proportion;
	}
	//assert the total proportion of load is 0.98 < x < 1.02
	Assert.assertEquals(Double.compare(total_cumulative,0.99) >= 0,true);
	Assert.assertEquals(Double.compare(total_cumulative,1.01) <= 0,true);
    }
}
