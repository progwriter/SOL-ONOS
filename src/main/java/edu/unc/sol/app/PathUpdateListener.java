package edu.unc.sol.app;

import org.onosproject.net.intent.PathIntent;

import java.util.List;

public interface PathUpdateListener {
    void updatePaths(List<PathIntent> paths);
}
