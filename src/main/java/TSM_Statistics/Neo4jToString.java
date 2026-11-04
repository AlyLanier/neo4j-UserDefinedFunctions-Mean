package TSM_Statistics;

import java.util.Map;

import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Entity;

/**
 * This is an example how you can create a simple user-defined function for Neo4j.
 */
public class Neo4jToString {

    @UserFunction
    @Description("TSM_Statistics.toString(object) returns the full string node/relationship/path object as one would create it")
    public String toString(
            @Name("object") Object object) {
        if (object instanceof Node) {
            return nodeToString((Node) object);
        }else if(object instanceof Relationship){
            return relationToString((Relationship) object);
        }else if(object instanceof Path){
            return pathToString((Path) object);
        }else{
            return "";
        }
    }

    private String nodeToString(Node node){
        String quote;
        String ret = "(";
        for(Label label : node.getLabels()){
            ret += ":" + label.name();
        }
        Map<String, Object> properties = node.getAllProperties();
        if(properties.size() > 0){
            ret += " {";
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                quote = entry.getValue() instanceof String ? "'" : "";
                ret += entry.getKey() + ": " + quote + String.valueOf(entry.getValue()) + quote + ", ";
            }
            ret = ret.substring(0, ret.length() - 2);
            ret += "}";
        }
        ret += ")";
        return ret;
    }

    private String relationToString(Relationship relation){
        String quote;
        String ret = "[";
        ret += ":" + relation.getType().name();
        Map<String, Object> properties = relation.getAllProperties();
        if(properties.size() > 0){
            ret += " {";
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                quote = entry.getValue() instanceof String ? "'" : "";
                ret += entry.getKey() + ": " + quote + String.valueOf(entry.getValue()) + quote + ", ";
            }
            ret = ret.substring(0, ret.length() - 2);
            ret += "}";
        }
        ret += "]";
        return ret;
    }

    private String pathToString(Path path){
        String ret = "";
        for(Entity entity: path){
            if(entity instanceof Node){
                ret += nodeToString((Node) entity);
            }else if(entity instanceof Relationship){
                ret += "-" + relationToString((Relationship) entity) + "->";
            }
        }
        return ret;
    }
}