package TSM_Statistics;


import java.util.List;

import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

/**
 * This is an example how you can create a simple user-defined function for Neo4j.
 */
public class Mean {

    @UserFunction
    @Description("TSM_Statistics.mean(values, order) returns the function to compute powerMean of the input values.")
    public double mean(
        @Name("values") List<Double> values,
        @Name(value = "order", defaultValue = "-1") double order
    ) {
        if (order == 0.){ return Math.pow(values.stream().reduce(1., (a, b)->a*b), 1./values.size());}
        else{ return Math.pow(values.stream().map(x->Math.pow(x, order)).reduce(0.0, Double::sum) / values.size(), 1./order);}
    }

    @UserFunction
    @Description("TSM_Statistics.mean(values, '+inf' or '-inf') returns the function to compute powerMean of the input values.")
    public double mean(
        @Name("values") List<Double> values,
        @Name(value = "order", defaultValue = "-1") String order
    ) {
        if (order == "+inf"){ return values.stream().max(Double::compareTo).orElse(0.0);}
        else if(order == "-inf"){ return values.stream().min(Double::compareTo).orElse(0.0);}
        else{ return 0.0;}
    }

    private <T> void p(T obj){
        System.out.println(obj);
    }
}

