package TSM_Statistics;

import java.util.List;

import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Path;

/**
 * This is an example how you can create a simple user-defined function for Neo4j.
 */
public class Neo4jToString {

    @UserFunction
    @Description("example.join(['s1','s2',...], delimiter) - join the given strings with the given delimiter.")
    public String toString(
            @Name("object") Object object) {
        if (object instanceof Node) {
            return nodeToString((Node) object);
        }else if(object instanceof Relationship){
            return relationToString((Relationship) object);
        }else if(object instanceof Path){
            return relationToString((Relationship) object)
        }
    }

    private String nodeToString(Node node){
        
    }

    private String relationToString(Relationship relation){

    }

    private String pathToString(Path path){

    }
}