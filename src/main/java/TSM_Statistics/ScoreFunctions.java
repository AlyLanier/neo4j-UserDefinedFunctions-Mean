package TSM_Statistics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

public class ScoreFunctions {

    private static double checkedDistanceExponent(Long distanceExponent) {
        if (distanceExponent == null || distanceExponent < 1) {
            throw new IllegalArgumentException("'distanceExponent' must be a strictly positive integer");
        }
        return distanceExponent;
    }

    private static DataHull dataHullOf(List<List<Double>> data, List<Double> range, String scale, Long distanceExponent) {
        double n = checkedDistanceExponent(distanceExponent);
        Function<Double, Double> reference = x -> Math.pow(Math.abs(x), n);
        Function<Double, Function<Double, Double>> primitive =
                a -> x -> (x < 0. ? -1. : 1.) * Math.pow(a, n) * Math.pow(Math.abs(x), n + 1.) / (n + 1.);

        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("'data' must contain at least one [value, occurrences] pair");
        }
        if (range == null || range.size() < 2) {
            throw new IllegalArgumentException("'range' must contain at least [start, end]");
        }
        Map<Double, Double> options = new HashMap<>();
        for (List<Double> pair : data) {
            if (pair == null || pair.size() < 2) {
                throw new IllegalArgumentException("each entry of 'data' must be a [value, occurrences] pair");
            }
            double value = pair.get(0);
            double occurrences = pair.get(1);
            if (occurrences <= 0.) {
                throw new IllegalArgumentException("occurrences must be positive for value " + value);
            }
            options.merge(value, occurrences, Double::sum);
        }
        try {
            return new DataHull(options, range, reference, primitive, scale);
        } catch (Exception e) {
            throw new RuntimeException("could not build the hull: " + e.getMessage(), e);
        }
    }

    @UserFunction("TSM_Statistics.scoreOfNewValue")
    @Description("Score of adding a new value to a dataset: scoreOfNewValue(value, data, range, scale = null, distanceExponent = 2). 'data' is a list of [value, occurrences] pairs, 'range' is [start, end] in the same unit as the values, 'scale' is 'linear' or 'log' (null = auto), 'distanceExponent' is a strictly positive integer n using |x|^n as the distance to the values. Returns newIntegral/oldIntegral of the hull — the smaller, the more the value improves the coverage.")
    public Double scoreOfNewValue(
            @Name("value") Double value,
            @Name("data") List<List<Double>> data,
            @Name("range") List<Double> range,
            @Name(value = "scale", defaultValue = "null") String scale,
            @Name(value = "distanceExponent", defaultValue = "2") Long distanceExponent) {
        if (value == null) {
            throw new IllegalArgumentException("'value' must not be null");
        }
        double rangeStart = range.getFirst();
        double rangeEnd = range.getLast();
        if (value < rangeStart || value > rangeEnd) {
            throw new IllegalArgumentException("'value' must lie inside the range [" + rangeStart + ", " + rangeEnd + "]");
        }

        DataHull dataHull = dataHullOf(data, range, scale, distanceExponent);
        double occurrences = dataHull.getOptions().getOrDefault(value, 0.);
        Parameters newCurb = new Parameters(1. / (occurrences + 1.), value);
        try {
            return dataHull.getHull().scoreOfNewCurb(newCurb);
        } catch (Exception e) {
            throw new RuntimeException("score computation failed: " + e.getMessage(), e);
        }
    }

    @UserFunction("TSM_Statistics.generateScores")
    @Description("Scores over the range: generateScores(nbpoints, data, range, scale = null, distanceExponent = 2). Samples 'nbpoints' linearly spaced values across 'range' (plus the values already in 'data'), builds the hull with the |x|^n distance and returns one [x, normalizedScore] pair per value, scores normalized to [0, 1]. With scale = 'log' the returned x values are log10 of the data values.")
    public List<List<Double>> generateScores(
            @Name("nbpoints") Long nbpoints,
            @Name("data") List<List<Double>> data,
            @Name("range") List<Double> range,
            @Name(value = "scale", defaultValue = "null") String scale,
            @Name(value = "distanceExponent", defaultValue = "2") Long distanceExponent) {
        if (nbpoints == null || nbpoints < 1) {
            throw new IllegalArgumentException("'nbpoints' must be a positive integer");
        }
        DataHull dataHull = dataHullOf(data, range, scale, distanceExponent);
        try {
            return dataHull.generateData(nbpoints.doubleValue());
        } catch (Exception e) {
            throw new RuntimeException("score generation failed: " + e.getMessage(), e);
        }
    }
}