package edu.unc.app;

import org.onosproject.net.Path;

/**
 *
 */
public interface Predicate {
    boolean isValid(Path p);
}
