package edu.unc.sol.app;

import org.json.JSONArray;
import org.json.JSONObject;

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

    /**
     * Creates an optimization container
     */
    public Optimization(Set<Constraint> constraints, Objective obj) {
        objective = obj;
        this.constraints = constraints;
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
	JSONObject obj = new JSONObject();
	o.put("objective", obj);
	//        obj.setAll(this.objective.toJSONnode());
	JSONObject objnode = this.objective.toJSONnode();
	for (String key : JSONObject.getNames(objnode)) {
	    obj.put(key, objnode.get(key));
	}
        // The constraints will be array of strings
	JSONArray constr = new JSONArray();
	o.put("constraints", constr);
        for (Constraint c : this.constraints) {
            constr.put(c.toString());
        }
        // Return the container
        return o;
    }
}
