package edu.unc.sol.app;


import java.util.UnknownFormatConversionException;

/**
 * Types of constraints we support
 */
public enum Constraint {

    //TODO: define other constraints from SOL
    ROUTE_ALL,
    CAP_LINKS,
    CAP_NODES;

    @Override
    public String toString() {
        switch (this) {
            case ROUTE_ALL:
                return "routeall";
            case CAP_LINKS:
                return "caplinks";
            case CAP_NODES:
                return "capnodes";
            default:
                throw new UnknownFormatConversionException("Unknown constraint value");
        }
    }
}
