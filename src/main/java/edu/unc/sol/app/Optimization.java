package edu.unc.sol.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.Set;

public class Optimization {
    /**
     * The set of constraints that define the optimization. These
     * dictate how traffic should be routed
     */
    protected Set<Constraint> constraints;

    /**
     * The objective function of the optimization.
     */
    protected Objective objective;

    protected Map<Resource, Double> costs;

    /**
     * Creates an optimization container
     */
    public Optimization(Set<Constraint> constraints, Objective obj,
                        Map<Resource, Double> costs) {
        objective = obj;
        this.constraints = constraints;
        this.costs = costs;
    }

    /**
     * Return the set of stored constraints. The set is modifiable.
     */
    public Set<Constraint> getConstraints() {
        return constraints;
    }

    /**
     * Return the stored objective
     */
    public Objective getObjective() {
        return objective;
    }

    /**
     * Set a new objective for this optimization
     */
    public void setObjective(Objective objective) {
        this.objective = objective;
    }

    /**
     * JSON-compatible representation of this optimization
     */
    public JSONObject toJSONnode() {
        // The overall container
        JSONObject o = new JSONObject();
        // Objective node
        o.put("objective", this.objective.toJSONnode());
        // The constraints will be array of strings
        JSONArray constr = new JSONArray();
        o.put("constraints", constr);
        for (Constraint c : this.constraints) {
            constr.put(c.toString());
        }
        JSONArray rco = new JSONArray();
        for (Map.Entry<Resource, Double> e : costs.entrySet()) {
            JSONObject co = new JSONObject();
            co.put("resource", e.getKey().toString());
            co.put("cost", e.getValue());
            rco.put(co);
        }
        o.put("resource_costs", rco);
        // Return the container
        return o;
    }
}
