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
public class ScoreTest {

    private Neo4j embeddedDatabaseServer;

    @BeforeAll
    void initializeNeo4j() {
        this.embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
                .withDisabledServer()
                .withFunction(Score.class)
                .build();
    }

    @AfterAll
    void closeNeo4j() {
        this.embeddedDatabaseServer.close();
    }

    @Test
    void computeScore() {
        // This is in a try-block, to make sure we close the driver after the test
        try(Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI()); Session session = driver.session()) {
            String query ="""
                WITH TSM_Statistics.score(0, [-1, 1], [-2, 2]) as easy_test,
                TSM_Statistics.score(0.5, [-1, 1], [-2, 2]) as easy_test2,
                TSM_Statistics.score(.5 , [-1.5, 1], [-2, 2]) as easy_test3
                RETURN easy_test, easy_test2, easy_test3
                """;
            
            List<Double> expected_results = Arrays.asList(1., .25, .16);

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