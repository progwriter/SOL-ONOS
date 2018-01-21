package edu.unc.sol.app;

import java.util.UnknownFormatConversionException;

public enum ObjectiveName {
  OBJ_MIN_LATENCY,
  OBJ_MIN_LINK_LOAD,
  OBJ_MIN_NODE_LOAD,
  OBJ_MAX_FLOW;

  /** Return the name of the objective function compatible with the Python server implementation */
  @Override
  public String toString() {
    switch (this) {
      case OBJ_MAX_FLOW:
        return "maxallflow";
      case OBJ_MIN_LINK_LOAD:
        return "minlinkload";
      case OBJ_MIN_NODE_LOAD:
        return "minnodeload";
      case OBJ_MIN_LATENCY:
        return "minlatency";
      default:
        throw new UnknownFormatConversionException("Unsupported objective name");
    }
  }
}
