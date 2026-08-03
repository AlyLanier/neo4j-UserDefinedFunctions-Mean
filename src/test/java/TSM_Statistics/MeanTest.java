package TSM_Statistics;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;
import java.util.List;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MeanTest {

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
            org.neo4j.driver.Record result = session.run(query).single();

            // Then
            for(int i = 0; i < expected_results.size(); i++){
                assertThat(result.get(i).asDouble()).isExactlyInstanceOf(Double.class);
                assertThat(expected_results.get(i)).isExactlyInstanceOf(Double.class);
                assertThat(result.get(i).asDouble()).isEqualTo(expected_results.get(i));
            }
        }
    }
}