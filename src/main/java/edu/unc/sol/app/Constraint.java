package edu.unc.sol.app;


import java.util.UnknownFormatConversionException;

/**
 * Types of constraints we support
 */
public enum Constraint {

    //TODO: define other constraints from SOL
    ROUTE_ALL,
    CAP_LINKS,
    CAP_NODES,
    ALLOCATE_FLOW;

    @Override
    public String toString() {
        switch (this) {
            case ALLOCATE_FLOW:
                return "allocate_flow";
            case ROUTE_ALL:
                return "route_all";
            case CAP_LINKS:
                return "capLinks";
            case CAP_NODES:
                return "capNodes";
            default:
                throw new UnknownFormatConversionException("Unknown constraint value");
        }
    }
}
