package TSM_Statistics;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ScoreFunctionsTest {

    private static final double TOLERANCE = 1e-9;

    private Neo4j embeddedDatabaseServer;

    @BeforeAll
    void initializeNeo4j() {
        this.embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
                .withDisabledServer()
                .withFunction(ScoreFunctions.class)
                .build();
    }

    @AfterAll
    void closeNeo4j() {
        this.embeddedDatabaseServer.close();
    }

    /*
     * Reference dataset: values {1, 3}, one occurrence each, range [0, 4], linear scale.
     * The default distanceExponent is 2, so the distance is |x|^2 and a curb with slope a
     * and vertex b contributes a^2 * (x - b)^2. The envelope segments are the same for
     * every exponent (a|x-b|^n = c|x-d|^n reduces to a|x-b| = c|x-d|):
     *   [0, 2] -> (x-1)^2, [2, 4] -> (x-3)^2, total integral = 2/3 + 2/3 = 4/3.
     * A new value has slope 1; an existing value is scored as one more occurrence (slope 1/2):
     *   new value 2                  -> [0,1.5], [1.5,2.5], [2.5,4] -> 3/8 + 1/12 + 3/8 = 5/6  -> score 5/8
     *   new value 0 or 4             -> carve [0,0.5] or [3.5,4]    -> 1/24 + 9/24 + 16/24 = 13/12 -> score 13/16
     *   one more occurrence at 1 / 3 -> [0,7/3] -> (x-1)^2/4, [7/3,4] -> (x-3)^2
     *                                   -> 91/324 + 140/324 = 77/108                   -> score 77/144
     */
    @Test
    void scoreOfNewValueOnLinearScale() {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            String query = """
WITH [[1.0, 1.0], [3.0, 1.0]] AS data, [0.0, 4.0] AS rng
WITH data, rng,
     TSM_Statistics.scoreOfNewValue(2.0, data, rng) AS newInside,
     TSM_Statistics.scoreOfNewValue(0.0, data, rng) AS newAtStart,
     TSM_Statistics.scoreOfNewValue(4.0, data, rng) AS newAtEnd,
     TSM_Statistics.scoreOfNewValue(1.0, data, rng) AS existingValueOneMore,
     TSM_Statistics.scoreOfNewValue(3.0, data, rng) AS existingValueOneMoreToo,
     TSM_Statistics.scoreOfNewValue(2.0, data, rng, 'linear') AS explicitLinear
RETURN newInside, newAtStart, newAtEnd, existingValueOneMore, existingValueOneMoreToo, explicitLinear""";
            double[] expected = {5. / 8., 13. / 16., 13. / 16., 77. / 144., 77. / 144., 5. / 8.};

            Record result = session.run(query).single();

            for (int i = 0; i < expected.length; i++) {
                assertThat(result.get(i).asDouble()).isCloseTo(expected[i], within(TOLERANCE));
            }
        }
    }

    @Test
    void scoreOfNewValueOnLogScale() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            // In log scale the hull lives in log10 space: intercepts 0 and 2 on range [0, 2].
            // The new value 10 lands at log10(10) = 1, exactly between them. With the squared
            // distance (default) the integral drops from 2/3 to 1/6 -> score 1/4; with the
            // absolute distance (exponent 1) it drops from 1.0 to 1/2 -> score 1/2.
            String query = """
WITH [[1.0, 1.0], [100.0, 1.0]] AS data, [1.0, 100.0] AS rng
WITH data, rng,
     TSM_Statistics.scoreOfNewValue(10.0, data, rng, 'log') AS squaredDistance,
     TSM_Statistics.scoreOfNewValue(10.0, data, rng, 'log', 1) AS absoluteDistance
RETURN squaredDistance, absoluteDistance""";
            double[] expected = {0.25, 0.5};

            Record result = session.run(query).single();

            for (int i = 0; i < expected.length; i++) {
                assertThat(result.get(i).asDouble()).isCloseTo(expected[i], within(TOLERANCE));
            }
        }
    }

    /*
     * distanceExponent = 1 gives the absolute-value distance;
     * distanceExponent = 3 uses |x|^3: old integral 1/2 + 1/2 = 1, new envelope
     * [0,1.5] + [1.5,2.5] + [2.5,4] -> 17/64 + 1/32 + 17/64 = 9/16 -> score 9/16.
     */
    @Test
    void distanceExponentParameterizesTheDistance() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            String query = """
WITH [[1.0, 1.0], [3.0, 1.0]] AS data, [0.0, 4.0] AS rng
WITH data, rng,
     TSM_Statistics.scoreOfNewValue(2.0, data, rng, 'linear', 1) AS absoluteInside,
     TSM_Statistics.scoreOfNewValue(1.0, data, rng, 'linear', 1) AS absoluteExisting,
     TSM_Statistics.scoreOfNewValue(2.0, data, rng, 'linear', 3) AS cubicInside
RETURN absoluteInside, absoluteExisting, cubicInside""";
            double[] expected = {0.75, 17. / 24., 9. / 16.};

            Record result = session.run(query).single();

            for (int i = 0; i < expected.length; i++) {
                assertThat(result.get(i).asDouble()).isCloseTo(expected[i], within(TOLERANCE));
            }
        }
    }

    @Test
    void generateScoresOnLinearScale() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            // 5 sampled points [0, 1, 2, 3, 4]; 1 and 3 already exist, so they are scored as
            // "one more occurrence" instead. Raw scores {0: 13/16, 2: 5/8, 4: 13/16,
            // 1: 77/144, 3: 77/144} normalized with min -> 0, max -> 1: 5/8 lands at 13/40.
            String query = """
WITH [[1.0, 1.0], [3.0, 1.0]] AS data, [0.0, 4.0] AS rng
RETURN TSM_Statistics.generateScores(5, data, rng) AS curve""";

            assertCurve(session, query, new double[][]{
                    {0.0, 1.0}, {1.0, 0.0}, {2.0, 0.325}, {3.0, 0.0}, {4.0, 1.0}});
        }
    }

    @Test
    void generateScoresOnLogScale() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            // Sampled points in log10 space are [0, 1, 2]; 0 and 2 are the existing values
            // (1 and 100), so only log10(10) = 1 is a genuinely new value.
            // Raw scores {10: 1/4, 1: 4/9, 100: 4/9} -> normalized {x=1: 0, x=0: 1, x=2: 1}.
            String query = """
WITH [[1.0, 1.0], [100.0, 1.0]] AS data, [1.0, 100.0] AS rng
RETURN TSM_Statistics.generateScores(3, data, rng, 'log') AS curve""";

            assertCurve(session, query, new double[][]{
                    {0.0, 1.0}, {1.0, 0.0}, {2.0, 1.0}});
        }
    }

    @Test
    void generateScoresWithSinglePoint() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            // nbpoints = 1 samples only the start of the range; existing values are still scored
            String query = """
WITH [[1.0, 1.0], [3.0, 1.0]] AS data, [0.0, 4.0] AS rng
RETURN TSM_Statistics.generateScores(1, data, rng) AS curve""";

            assertCurve(session, query, new double[][]{
                    {0.0, 1.0}, {1.0, 0.0}, {3.0, 0.0}});
        }
    }

    @Test
    void generateScoresWithAbsoluteDistance() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            // distanceExponent = 1 restores the original absolute-distance scores:
            // raw {0: 0.875, 2: 0.75, 4: 0.875, 1: 17/24, 3: 17/24} -> 2 normalizes to 0.25
            String query = """
WITH [[1.0, 1.0], [3.0, 1.0]] AS data, [0.0, 4.0] AS rng
RETURN TSM_Statistics.generateScores(5, data, rng, 'linear', 1) AS curve""";

            assertCurve(session, query, new double[][]{
                    {0.0, 1.0}, {1.0, 0.0}, {2.0, 0.25}, {3.0, 0.0}, {4.0, 1.0}});
        }
    }

    @Test
    void coercesIntegerInputs() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            // INTEGER literals are coerced to the DOUBLE parameters, like in MeanTest
            String query = """
RETURN TSM_Statistics.scoreOfNewValue(2, [[1, 1], [3, 1]], [0, 4]) AS score""";

            assertThat(session.run(query).single().get("score").asDouble())
                    .isCloseTo(5. / 8., within(TOLERANCE));
        }
    }

    @Test
    void rejectsValueOutsideRange() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            assertThatThrownBy(() -> session.run("""
RETURN TSM_Statistics.scoreOfNewValue(5.0, [[1.0, 1.0], [3.0, 1.0]], [0.0, 4.0]) AS score""").single())
                    .isInstanceOf(Neo4jException.class)
                    .hasMessageContaining("must lie inside the range");
        }
    }

    @Test
    void rejectsNonPositiveOccurrences() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            assertThatThrownBy(() -> session.run("""
RETURN TSM_Statistics.scoreOfNewValue(2.0, [[1.0, 0.0], [3.0, 1.0]], [0.0, 4.0]) AS score""").single())
                    .isInstanceOf(Neo4jException.class)
                    .hasMessageContaining("occurrences must be positive");
        }
    }

    @Test
    void rejectsMalformedDataPair() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            assertThatThrownBy(() -> session.run("""
RETURN TSM_Statistics.scoreOfNewValue(2.0, [[1.0], [3.0, 1.0]], [0.0, 4.0]) AS score""").single())
                    .isInstanceOf(Neo4jException.class)
                    .hasMessageContaining("[value, occurrences] pair");
        }
    }

    @Test
    void rejectsEmptyData() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            assertThatThrownBy(() -> session.run("""
RETURN TSM_Statistics.generateScores(5, [], [0.0, 4.0]) AS curve""").single())
                    .isInstanceOf(Neo4jException.class)
                    .hasMessageContaining("at least one [value, occurrences] pair");
        }
    }

    @Test
    void rejectsInvalidNbpoints() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            assertThatThrownBy(() -> session.run("""
WITH [[1.0, 1.0], [3.0, 1.0]] AS data, [0.0, 4.0] AS rng
RETURN TSM_Statistics.generateScores(0, data, rng) AS curve""").single())
                    .isInstanceOf(Neo4jException.class)
                    .hasMessageContaining("must be a positive integer");
        }
    }

    @Test
    void rejectsInvalidDistanceExponent() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            assertThatThrownBy(() -> session.run("""
RETURN TSM_Statistics.scoreOfNewValue(2.0, [[1.0, 1.0], [3.0, 1.0]], [0.0, 4.0], 'linear', 0) AS score""").single())
                    .isInstanceOf(Neo4jException.class)
                    .hasMessageContaining("'distanceExponent' must be a strictly positive integer");

            assertThatThrownBy(() -> session.run("""
RETURN TSM_Statistics.generateScores(5, [[1.0, 1.0], [3.0, 1.0]], [0.0, 4.0], 'linear', -3) AS curve""").single())
                    .isInstanceOf(Neo4jException.class)
                    .hasMessageContaining("'distanceExponent' must be a strictly positive integer");
        }
    }

    /*
     * The pairs come back as [x, score] with the existing values in HashMap order, so sort
     * by x before comparing; scores are compared with a tolerance.
     * The driver's Value.asList() returns an IMMUTABLE list, so copy it into an ArrayList
     * before calling sort() — sorting it in place throws UnsupportedOperationException.
     */
    private static void assertCurve(Session session, String query, double[][] expected) {
        List<List<Double>> curve = new ArrayList<>(session.run(query).single().get("curve")
                .asList(point -> point.asList(value -> value.asDouble())));
        curve.sort(Comparator.comparingDouble(point -> point.get(0)));

        assertThat(curve).hasSize(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(curve.get(i).get(0)).isCloseTo(expected[i][0], within(TOLERANCE));
            assertThat(curve.get(i).get(1)).isCloseTo(expected[i][1], within(TOLERANCE));
        }
    }
}
