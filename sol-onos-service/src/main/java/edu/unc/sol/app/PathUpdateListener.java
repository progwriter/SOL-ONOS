package edu.unc.sol.app;

import org.onosproject.net.intent.PathIntent;

import java.util.Collection;

public interface PathUpdateListener {
  /**
   * Callback function for when the new paths have been computed
   *
   * @param paths a collection of path intents that can be installed.
   */
  void updatePaths(Collection<PathIntent> paths);
}
