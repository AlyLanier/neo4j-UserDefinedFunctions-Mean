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
        @Name(value = "order", defaultValue = "-1") Object order
    ) throws Exception {
        try{
            double exponent = ((Number) order).doubleValue();
            return mean(values, exponent);
        }catch(Exception e){
            if(order instanceof String){return mean(values, (String) order);}
            else{throw new Exception(String.format("'order' parameter must be a Double, '-inf' or '+inf', was %d of type %s", order, order.getClass()));}
        }
    }

    private double mean(List<Double> values, double order) {
        if (order == 0.){ return Math.pow(values.stream().reduce(1., (a, b)->a*b), 1./values.size());}
        else{ return Math.pow(values.stream().map(x->Math.pow(x, order)).reduce(0.0, Double::sum) / values.size(), 1./order);}
    }

    private double mean(List<Double> values, String order) throws Exception{
        if (order.equals("+inf")){return values.stream().max(Double::compareTo).orElse(0.0);}
        else if(order.equals("-inf")){ return values.stream().min(Double::compareTo).orElse(0.0);}
        else{throw new Exception("expected '+inf' or '-inf' for parameter 'order' but was '" + order + "'");}
    }
}

