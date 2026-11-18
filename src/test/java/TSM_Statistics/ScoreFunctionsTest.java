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

    /*
     * Log scale with very small values: 1e-60, 1e-59 and 1e-58 are exact decades, so
     * log10 maps them to exactly -60.0, -59.0, -58.0 (the ~1e-16 representation error of
     * the literal is ~5e-17 in the log, far below one ulp of 60). The hull is shift-
     * invariant in scaled space, so this is exactly the [0, 2] log example translated by
     * -60: the middle value scores 1/4, the existing ones 4/9.
     */
    @Test
    void logScaleWorksWithVerySmallValues() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            String scoreQuery = """
WITH [[1e-60, 1.0], [1e-58, 1.0]] AS data, [1e-60, 1e-58] AS rng
RETURN TSM_Statistics.scoreOfNewValue(1e-59, data, rng, 'log') AS score""";

            assertThat(session.run(scoreQuery).single().get("score").asDouble())
                    .isCloseTo(0.25, within(TOLERANCE));

            String curveQuery = """
WITH [[1e-60, 1.0], [1e-58, 1.0]] AS data, [1e-60, 1e-58] AS rng
RETURN TSM_Statistics.generateScores(3, data, rng, 'log') AS curve""";

            assertCurve(session, curveQuery, new double[][]{
                    {-60.0, 1.0}, {-59.0, 0.0}, {-58.0, 1.0}});
        }
    }

    /*
     * A new curb crossed TWICE by the same old curb gets a DISCONNECTED envelope region:
     * in log10 space the data hull is (1, -60) on (-110, -36) and (1, -12) on (-36, 0),
     * and scoring 1e-12 a second time adds the shallower curb (0.5, -12), which wins far
     * from both vertices. It crosses (1, -60) at -44 (opposite arms) and -108 (same-side
     * arms), collected by allIntersections as [-44, -108]: processNewSegments pairs
     * consecutive crossings, so they must be sorted — this pins the fix (unsorted, the
     * Segment(-44, -108) construction throws "start must be smaller than end").
     *   old integral: (24^3 + 50^3)/3 + (12^3 + 24^3)/3                      = 154376/3
     *   new hull: (0.5, -12) on (-110, -108) and (-44, 0), (1, -60) on (-108, -44)
     *   new integral: (98^3 - 96^3)/12 + (48^3 + 16^3)/3 + (32^3 + 12^3)/12 = 137426/3
     */
    @Test
    void handlesDisconnectedEnvelopeRegions() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            String query = """
WITH [[1e-60, 1.0], [1e-12, 1.0]] AS data, [1e-110, 1.0] AS rng
RETURN TSM_Statistics.scoreOfNewValue(1e-12, data, rng, 'log') AS score""";

            assertThat(session.run(query).single().get("score").asDouble())
                    .isCloseTo(137426. / 154376., within(TOLERANCE));
        }
    }

    /*
     * Mixed occurrences (1e-12 counted twice -> slope 1/2) make the hull itself hit the
     * same two-crossings-from-one-curb pairing (this is the data2 call of the main()
     * harness, which used to throw "start = -44 must be smaller than end = -108"). The
     * built hull is (0.5, -12) on (-110, -108) and (-44, 0), (1, -60) on (-108, -44),
     * with integral 137426/3. Scoring the new value 1e-30 (at -30, slope 1) crosses
     * (1, -60) at -45 and (0.5, -12) at -24; its -48 crossing with (0.5, -12) falls
     * outside that curb's segments and is correctly ignored. New hull:
     *   (0.5, -12) on (-110, -108) and (-24, 0), (1, -60) on (-108, -45), (1, -30) on (-45, -24)
     *   -> (98^3 - 96^3)/12 + (48^3 + 15^3)/3 + (15^3 + 6^3)/3 + (12^3 + 12^3)/12 = 132536/3.
     */
    @Test
    void buildsMixedOccurrenceHullWithThreeCurbs() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            String query = """
WITH [[1e-60, 1.0], [1e-12, 2.0]] AS data, [1e-110, 1.0] AS rng
RETURN TSM_Statistics.scoreOfNewValue(1e-30, data, rng, 'log') AS score""";

            assertThat(session.run(query).single().get("score").asDouble())
                    .isCloseTo(132536. / 137426., within(TOLERANCE));
        }
    }

    /*
     * Triple point, from the user's main() repro: scoring 0.2 a 15th time adds the curb
     * (1/15, 0.2) to the curbs (1, 0.001), (1/14, 0.2 -> replaced), (1/9, 0.5), (1, 1) and
     * (1, 3) on [0.0005, 6]. The new curb crosses (1/9, 0.5) at 31/80 AND at 19/20 — exactly
     * the vertex of (1, 1): all three curbs meet there with value 1/20 (0.05), and the
     * envelope switches directly from (1/9, 0.5) to (1, 1). So 19/20 must be skipped as a
     * boundary-coincident crossing for BOTH old curbs — it is (1/9, 0.5)'s segment end and
     * (1, 1)'s segment start (in doubles the crossing computes to 0.9500000000000001, one
     * ulp off that boundary, so the skip needs ulp tolerance — see Hull.floatEqual). If it
     * is kept even once, the pairing steals the neighbors' segments, they are deleted as
     * superfluous, the tail piece [16/5, 6] is lost, and the hull fails verification with
     * "ends do not match". The new curb's correct region is
     * [43/3200, 31/80] U [37/35, 113/40] U [16/5, 6] — three disjoint pieces, the shallower
     * curb winning far from both vertices.
     * Exact-rational integrals: old = 0.32048227332781887, new = 0.279876619420443
     * -> score = 0.87329828422104139 (verified with Fraction arithmetic).
     */
    @Test
    void handlesTriplePointCrossing() {
        try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
             Session session = driver.session()) {
            String query = """
WITH [[0.001, 1.0], [0.2, 14.0], [0.5, 9.0], [1.0, 1.0], [3.0, 1.0]] AS data, [0.0005, 6.0] AS rng
RETURN TSM_Statistics.scoreOfNewValue(0.2, data, rng) AS score""";

            assertThat(session.run(query).single().get("score").asDouble())
                    .isCloseTo(0.87329828422104139, within(TOLERANCE));
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
RETURN TSM_Statistics.scoreOfNewValue(2.0, [[1.0, -1.0], [3.0, 1.0]], [0.0, 4.0]) AS score""").single())
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
