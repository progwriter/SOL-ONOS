package edu.unc.app;

import org.onosproject.net.Path;

import java.util.List;

public interface PathUpdateListener {
    void updatePaths(List<Path> paths);
}
