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
import java.util.List;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;


 @TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MeanTest {

    private static final double TOLERANCE = 1e-9;

    private Neo4j embeddedDatabaseServer;

    @BeforeAll
    void initializeNeo4j() {
        this.embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
                .withDisabledServer()
                .withFunction(Mean.class)
                .build();
    }

    @AfterAll
    void closeNeo4j() {
        this.embeddedDatabaseServer.close();
    }

    @Test
    void computeMeans() {
        
        // This is in a try-block, to make sure we close the driver after the test
        try(Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI()); Session session = driver.session()) {
            String query = """
WITH TSM_Statistics.mean([7, -1, 18], "-inf") as testminf,
TSM_Statistics.mean([2, 4, 4], -1) as testm1, 
TSM_Statistics.mean([4, 9], 0) as test0, 
TSM_Statistics.mean([16, 6], 1) as test1, 
TSM_Statistics.mean([7, 1], 2) as test2,
TSM_Statistics.mean([7, -1, 18], "+inf") as testpinf

RETURN testminf, testm1, test0, test1, test2, testpinf """;;
            List<Double> expected_results = Arrays.asList(-1., 3., 6., 11., 5., 18.);

            // When
            Record result = session.run(query).single();

            // Then
            for(int i = 0; i < expected_results.size(); i++){
                assertThat(result.get(i).asDouble()).isExactlyInstanceOf(Double.class);
                assertThat(expected_results.get(i)).isExactlyInstanceOf(Double.class);
                assertThat(result.get(i).asDouble()).isEqualTo(expected_results.get(i));
            }
        }
    }

    /*
     * The power mean M_k is non-decreasing in k, and always stays within [min, max].
     * On [2, 4, 4]: min = 2, harmonic = 3, geometric = 32^(1/3),
     * arithmetic = 10/3, quadratic = sqrt(12), max = 4.
     */
    @Test
    void powerMeansAreMonotonicInOrder() {
        // This is in a try-block, to make sure we close the driver after the test
        try(Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI()); Session session = driver.session()) {
            String query = """
WITH [2.0, 4.0, 4.0] AS vals
WITH vals,
     TSM_Statistics.mean(vals, '-inf') AS mMin,
     TSM_Statistics.mean(vals, -1) AS mHarmonic,
     TSM_Statistics.mean(vals, 0) AS mGeometric,
     TSM_Statistics.mean(vals, 1) AS mArithmetic,
     TSM_Statistics.mean(vals, 2) AS mQuadratic,
     TSM_Statistics.mean(vals, '+inf') AS mMax
RETURN mMin, mHarmonic, mGeometric, mArithmetic, mQuadratic, mMax""";
            double[] expected = {2., 3., Math.pow(32., 1. / 3.), 10. / 3., Math.sqrt(12.), 4.};

            Record result = session.run(query).single();

            for(int i = 0; i < expected.length; i++){
                assertThat(result.get(i).asDouble()).isCloseTo(expected[i], within(TOLERANCE));
            }
            for(int i = 0; i + 1 < expected.length; i++){
                assertThat(result.get(i).asDouble()).isLessThanOrEqualTo(result.get(i + 1).asDouble());
            }
        }
    }

    /*
     * Any power mean of a single value is that value, whatever the order.
     */
    @Test
    void singleValueIsInvariantUnderOrder() {
        try(Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI()); Session session = driver.session()) {
            String query = """
WITH [42.0] AS vals
WITH vals,
     TSM_Statistics.mean(vals, '-inf') AS a, TSM_Statistics.mean(vals, -2) AS b,
     TSM_Statistics.mean(vals, -1) AS c, TSM_Statistics.mean(vals, 0) AS d,
     TSM_Statistics.mean(vals, 0.5) AS e, TSM_Statistics.mean(vals, 1) AS f,
     TSM_Statistics.mean(vals, 2) AS g, TSM_Statistics.mean(vals, 3) AS h,
     TSM_Statistics.mean(vals, '+inf') AS i
RETURN a, b, c, d, e, f, g, h, i""";

            Record result = session.run(query).single();

            for(int i = 0; i < 9; i++){
                assertThat(result.get(i).asDouble()).isCloseTo(42., within(TOLERANCE));
            }
        }
    }

    /*
     * Any power mean of a constant list is that constant.
     */
    @Test
    void constantListIsInvariantUnderOrder() {
        try(Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI()); Session session = driver.session()) {
            String query = """
WITH [5.0, 5.0, 5.0] AS vals
WITH vals,
     TSM_Statistics.mean(vals, '-inf') AS a, TSM_Statistics.mean(vals, -2) AS b,
     TSM_Statistics.mean(vals, -1) AS c, TSM_Statistics.mean(vals, 0) AS d,
     TSM_Statistics.mean(vals, 0.5) AS e, TSM_Statistics.mean(vals, 1) AS f,
     TSM_Statistics.mean(vals, 2) AS g, TSM_Statistics.mean(vals, 3) AS h,
     TSM_Statistics.mean(vals, '+inf') AS i
RETURN a, b, c, d, e, f, g, h, i""";

            Record result = session.run(query).single();

            for(int i = 0; i < 9; i++){
                assertThat(result.get(i).asDouble()).isCloseTo(5., within(TOLERANCE));
            }
        }
    }

    /*
     * Fractional, negative and higher orders. Even orders are well defined for
     * negative values; the last one exercises a negative entry with order 2.
     */
    @Test
    void fractionalNegativeAndHigherOrders() {
        try(Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI()); Session session = driver.session()) {
            String query = """
WITH TSM_Statistics.mean([4.0, 9.0], 0.5) AS halfOrder,
     TSM_Statistics.mean([4.0, 9.0], -1) AS harmonic,
     TSM_Statistics.mean([1.0, 2.0], -2) AS minusTwo,
     TSM_Statistics.mean([1.0, 2.0], 3) AS cubic,
     TSM_Statistics.mean([16.0, 6.0], 2) AS quadratic,
     TSM_Statistics.mean([7.0, -1.0, 18.0], 2) AS quadraticWithNegative
RETURN halfOrder, harmonic, minusTwo, cubic, quadratic, quadraticWithNegative""";
            // ((sqrt(4)+sqrt(9))/2)^2 = 6.25 ; 2/(1/4+1/9) = 72/13
            // ((1 + 2^-2)/2)^(-1/2) = 0.625^(-1/2) ; ((1 + 2^3)/2)^(1/3) = 4.5^(1/3)
            // sqrt((256+36)/2) = sqrt(146) ; sqrt((49+1+324)/3) = sqrt(374/3)
            double[] expected = {6.25, 72. / 13., Math.pow(0.625, -0.5), Math.pow(4.5, 1. / 3.), Math.sqrt(146.), Math.sqrt(374. / 3.)};

            Record result = session.run(query).single();

            for(int i = 0; i < expected.length; i++){
                assertThat(result.get(i).asDouble()).isCloseTo(expected[i], within(TOLERANCE));
            }
        }
    }

    /*
     * The default order is -1 (harmonic mean).
     */
    @Test
    void defaultOrderIsHarmonicMean() {
        try(Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI()); Session session = driver.session()) {
            String query = """
WITH TSM_Statistics.mean([2.0, 4.0, 4.0]) AS byDefault,
     TSM_Statistics.mean([2.0, 4.0, 4.0], -1) AS explicit
RETURN byDefault, explicit""";

            Record result = session.run(query).single();

            assertThat(result.get("byDefault").asDouble()).isCloseTo(3., within(TOLERANCE));
            assertThat(result.get("explicit").asDouble()).isCloseTo(3., within(TOLERANCE));
        }
    }

    @Test
    void rejectsUnknownOrderString() {
        try(Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI()); Session session = driver.session()) {
            assertThatThrownBy(() -> session.run("""
RETURN TSM_Statistics.mean([1.0, 2.0], 'median') AS m""").single())
                    .isInstanceOf(Neo4jException.class)
                    .hasMessageContaining("median");
        }
    }

    /*
     * NOTE: needs the fixed Mean.java — the old error path formatted the offending
     * value with %d, which itself threw IllegalFormatConversionException for
     * non-numeric orders and masked the intended message.
     */
    @Test
    void rejectsNonNumericOrderValue() {
        try(Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI()); Session session = driver.session()) {
            assertThatThrownBy(() -> session.run("""
RETURN TSM_Statistics.mean([1.0, 2.0], [1.0, 2.0]) AS m""").single())
                    .isInstanceOf(Neo4jException.class);
        }
    }
}