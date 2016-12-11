package edu.unc.sol.app;



/**
 * Represents the objective function for an application (name + resource, if any)
 */
public class Objective {
    protected ObjectiveName name;
    protected Resource res = null;

    public Objective(ObjectiveName name, Resource resource) {
        this.name = name;
        this.res = resource;
    }

    public Objective(ObjectiveName name) {
        this.name = name;
    }

    /**
     * Create a JSON object representing this objective function
     */
//    public ObjectNode toJSONnode() {
//        ObjectNode o = new ObjectNode(JsonNodeFactory.instance);
//        o.put("name", this.name.toString());
//        // Check that we have a non-null resource
//        if (this.res != null) {
//            // Put it in we have a real resource
//            o.put("resource", this.res.toString());
//        }
//        return o;
//    }
}


