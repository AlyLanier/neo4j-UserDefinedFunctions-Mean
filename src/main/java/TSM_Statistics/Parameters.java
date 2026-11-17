package TSM_Statistics;

import java.util.Objects;

/**
 * Helper class to represent the tuple(float, float) used as keys in Python.
 */
public class Parameters {
    public final double slope;
    public final double xIntercept;

    public Parameters(double slope, double xIntercept) {
        this.slope = slope;
        this.xIntercept = xIntercept;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Parameters that = (Parameters) o;
        return Double.compare(that.slope, slope) == 0 && Double.compare(that.xIntercept, xIntercept) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(slope, xIntercept);
    }

    @Override
    public String toString() {
        return "(" + slope + ", " + xIntercept + ")";
    }
}
