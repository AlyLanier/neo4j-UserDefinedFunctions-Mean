package TSM_Statistics;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

/**
 * This is an example how you can create a simple user-defined function for Neo4j.
 */
public class Score {

    @UserFunction
    @Description("TSM_Statistics.score([1, 3, 5, ...], 'pow', 2) returns the score function of a value depending on the distance function to the nearest value.")
    public Function<Double, Double> score(
            @Name("values_taken, must be sorted") List<Double> values_sorted,
            @Name("if given, the range to normalize the score on") List<Double> range,
            @Name(value = "distance function name : 'affine', 'power', 'log', 'exp'", defaultValue = "x^2") String function_name,
            @Name("functions additional parameters :\naffine -> param[0]*x + param[1]\npower -> x^param[0]\nlog -> log_{param[0]}(x)") double ...parameters
        ) {
        Function<Double, Double> func = basic_functions(function_name, parameters);
        return x -> normalize_score_output(func, range, values_sorted).apply(score_function(values_sorted, func).apply(x));
    }

    @UserFunction
    @Description("TSM_Statistics.score([1, 3, 5, ...], 'pow', 2) returns the score function of a value depending on the distance function to the nearest value.")
    public Function<Double, Double> score(
            @Name("values_taken, must be sorted") List<Double> values_sorted,
            @Name(value = "distance function name : 'affine', 'power', 'log', 'exp'", defaultValue = "x^2") String function_name,
            @Name("functions additional parameters :\naffine -> param[0]*x + param[1]\npower -> x^param[0]\nlog -> log_{param[0]}(x)") double ...parameters
        ) {
        return score_function(values_sorted, basic_functions(function_name, parameters));
    }

    @UserFunction
    @Description("TSM_Statistics.score([1, 3, 5, ...], 'pow', 2) returns the score function of a value depending on the distance function to the nearest value.")
    public Function<Double, Double> score(
            @Name("values_taken, must be sorted") List<Double> values_sorted,
            @Name("if given, the range on which to normalize the score") List<Double> range
        ) {
        Function<Double, Double> func = basic_functions();
        return x -> normalize_score_output(func, range, values_sorted).apply(score_function(values_sorted, func).apply(x));
    }

    @UserFunction
    @Description("TSM_Statistics.score([1, 3, 5, ...], 'pow', 2) returns the score function of a value depending on the distance function to the nearest value.")
    public Function<Double, Double> score(
            @Name("values_taken, must be sorted") List<Double> values_sorted
        ) {
        return score_function(values_sorted, basic_functions());
    }





    private Function<Double, Double> score_function(List<Double> values_sorted, Function<Double, Double> func) {
        return x -> func.apply(distance_from_closest_value(x, values_sorted));
    }

    private double distance_from_closest_value(double x, List<Double> values_taken){
        return values_taken.stream().map(val -> Math.abs(val - x)).min(Double::compareTo).orElseThrow();
    }

    private Function<Double, Double> basic_functions(String name, double... params){
        switch (name) {
            case "affine":
                return x -> params[0] * x + params[1];
            case "power":
                return x -> Math.pow(x, params[0]);
            case "log":
                return x -> Math.log(x)/Math.log(params[0]);
            case "exp":
                return x -> Math.exp(x);
        
            default:
                return x -> Math.pow(x, 2);
        }
    }
    private Function<Double, Double> basic_functions(){
        return x -> Math.pow(x, 2);
    }


    private List<Double> get_distances(List<Double> range, List<Double> values_taken){
        if(values_taken.isEmpty()) return Collections.emptyList();

        List<Double> distances = Collections.singletonList(2*(values_taken.getFirst() - range.getFirst()));
        for(int i = 0; i < values_taken.size() - 1; i++){
            distances.add(values_taken.get(i+1) - values_taken.get(i));
        }
        distances.add(2*(range.getLast() - values_taken.getLast()));

        return distances;
    }

    private Function<Double, Double> normalize_score_output(Function<Double, Double> func, List<Double> range, List<Double> values_taken){
        double min_value = func.apply(0.);
        double max_val = func.apply(get_distances(range, values_taken).stream().map(x -> x/2).max(Double::compareTo).orElse(0.));

        return x -> (x - min_value)/(max_val - min_value);
    }


}
