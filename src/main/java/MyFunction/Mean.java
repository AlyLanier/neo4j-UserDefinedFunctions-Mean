package MyFunction;

import java.util.List;
import java.util.function.Function;

import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

/**
 * This is an example how you can create a simple user-defined function for Neo4j.
 */
public class Mean {

    @UserFunction
    @Description("MyFunction.mean([1, 3, 5, ...], 5) returns the powerMean of the input values.")
    public double mean(
            @Name("values") List<Double> values,
            @Name(value = "order", defaultValue = "-1") int order) {
        if (order == 0){ return Math.pow(values.stream().reduce(1., (a, b)->a*b), 1/values.size());}
        else{
            return Math.pow(values.stream().map(x->Math.pow(x, order)).reduce(0.0, Double::sum) /values.size(), 1/order);
        }
    }

    @UserFunction
    @Description("MyFunction.mean([1, 3, 5, ...], +inf or -inf) returns the powerMean of the input values.")
    public double mean(
            @Name("values") List<Double> values,
            @Name(value = "order", defaultValue = "-1") String order) {
        if (order == "+inf"){ return values.stream().max(Double::compareTo).orElse(0.0);}
        else{ return values.stream().min(Double::compareTo).orElse(0.0);
        }
    }

    @UserFunction
    @Description("MyFunction.meanFunction(Interger or +inf or -inf) returns the function to compute powerMean of the input values.")
    public Function<List<Double>, Double> mean(@Name(value = "order", defaultValue = "-1") String order) {
        if (order == "+inf"){ return values -> values.stream().max(Double::compareTo).orElse(0.0);}
        else{ return values -> values.stream().min(Double::compareTo).orElse(0.0);
        }
    }

    @UserFunction
    @Description("MyFunction.meanFunction(Interger or +inf or -inf) returns the function to compute powerMean of the input values.")
    public Function<List<Double>, Double> mean(@Name(value = "order", defaultValue = "-1") int order) {
        if (order == 0){ return values -> Math.pow(values.stream().reduce(1., (a, b)->a*b), 1/values.size());}
        else{ 
            return values -> Math.pow(values.stream().map(x->Math.pow(x, order)).reduce(0.0, Double::sum) / values.size(), 1/order);
        }
    }
}