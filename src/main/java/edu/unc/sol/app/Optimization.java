package edu.unc.sol.app;



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
//    public ObjectNode toJSONnode() {
//        // The overall container
//        ObjectNode o = new ObjectNode(JsonNodeFactory.instance);
//        // Objective node
//        ObjectNode obj = o.putObject("objective");
//        obj.setAll(this.objective.toJSONnode());
//        // The constraints will be array of strings
//        ArrayNode constr = o.putArray("constraints");
//        for (Constraint c : this.constraints) {
//            constr.add(c.toString());
//        }
//        // Return the container
//        return o;
//    }
}
