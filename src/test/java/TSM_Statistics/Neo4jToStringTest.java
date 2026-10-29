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
public class Neo4jToStringTest {

    private Neo4j embeddedDatabaseServer;

    @BeforeAll
    void initializeNeo4j() {
        this.embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
                .withDisabledServer()
                .withFunction(Neo4jToString.class)
                .build();
    }

    @AfterAll
    void closeNeo4j() {
        this.embeddedDatabaseServer.close();
    }

    @Test
    void stringTest() {
        
        // This is in a try-block, to make sure we close the driver after the test
        try(Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI()); Session session = driver.session()) {
            String query = """
CREATE (n:TestLabel:TestLabel2 {firstproperty: 'hello world', secondproperty: 0, thirdproperty: 42.69})-[rel:LAB {is_useful: FALSE}]->(m:otherLabel)
WITH n, rel, m
MATCH p = (n)-[rel]->(m)
WITH TSM_Statistics.toString(n) as n, TSM_Statistics.toString(m) as m, TSM_Statistics.toString(rel) as rel, TSM_Statistics.toString(p) as p
RETURN n, m, rel, p""";;
            List<String> expected_results = Arrays.asList(
                "(:TestLabel:TestLabel2 {firstproperty: 'hello world', secondproperty: 0, thirdproperty: 42.69})", 
                "(:otherLabel)", 
                "[:LAB {is_useful: FALSE}]", 
                "(:TestLabel:TestLabel2 {firstproperty: 'hello world', secondproperty: 0, thirdproperty: 42.69})-[:LAB {is_useful: FALSE}]->(:otherLabel)");

            // When
            org.neo4j.driver.Record result = session.run(query).single();

            // Then
            for(int i = 0; i < expected_results.size(); i++){
                assertThat(result.get(i)).isExactlyInstanceOf(String.class);
                assertThat(expected_results.get(i)).isExactlyInstanceOf(String.class);
                assertThat(result.get(i)).isEqualTo(expected_results.get(i));
            }
        }
    }
}