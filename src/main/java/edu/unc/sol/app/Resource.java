package edu.unc.sol.app;

import java.util.UnknownFormatConversionException;

public enum Resource {
    BANDWIDTH,
    CPU;

    /**
     * Return the string representation compatible with the SOL python server
     * @throws UnknownFormatConversionException
     */
    @Override
    public String toString() {
        switch (this) {
            case BANDWIDTH:
                return "bw";
            case CPU:
                return "cpu";
            default:
                throw new UnknownFormatConversionException("Unsupported resource name");
        }
    }
}
