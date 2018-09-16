package edu.unc.sol.service;

import java.util.UnknownFormatConversionException;

public enum Fairness {
  PROP_FAIR,
  WEIGHTED_FAIR;

  /**
   * Return the string version of the fairness, compatible with the python server implementation.
   *
   * @return
   */
  @Override
  public String toString() {
    switch (this) {
      case PROP_FAIR:
        return "propfair";
      case WEIGHTED_FAIR:
        return "weighed";
      default:
        throw new UnknownFormatConversionException("Unsupported fairness metric");
    }
  }
}
