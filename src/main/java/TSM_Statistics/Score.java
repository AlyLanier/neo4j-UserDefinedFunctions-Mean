package TSM_Statistics;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

/**
 * This is an example how you can create a simple user-defined function for Neo4j.
 */
public class Score {

    @UserFunction
    @Description("""
        TSM_Statistics.score(4., [1., 3., 5., ...], [0., 20.], 'pow 2') returns the normalized score function of a value depending on the distance function to the nearest value.
        available functions:
            - 'affine {slope} {Y-intercept}'        -> slope*x + Y-intercept
            - 'pow {order}'                         -> x^order
            - 'exp {base (strictly positive, or e)}'-> base^x
            - 'log {base (strictly positive)}'      -> log_{base}(x)
        """)
    public double score(
            @Name("new value") double new_val,
            @Name("values, must be sorted") List<Double> values_sorted,
            @Name("The range in which the values belong, must be sorted") List<Double> range,
            @Name(value = "function to use and its parameters; as strings", defaultValue = "pow 2") String function_name_and_parameters) 
            throws Exception {
        
        if(!values_sorted.stream().sorted().toList().equals(values_sorted)){throw new InvalidParameterException("the value list must be sorted");}
        if(new_val < range.get(0) || new_val > range.get(1)){throw new InvalidParameterException("new value cannot be outside of the range");}
        if(values_sorted.get(0) < range.get(0) || values_sorted.get(values_sorted.size()-1) > range.get(1)){
            throw new InvalidParameterException("values in the list cannot be outside of the range");}
        if(range.get(0) > range.get(1)){
            throw new InvalidParameterException(
                String.format(
                    "the range of the values must start before it ends, got [%f, %f] as a range but %f > %f", 
                    range.get(0), range.get(1), range.get(0), range.get(1)
                )
            );
        }
        
        String[] params = parseString(function_name_and_parameters);
        return compute_score(new_val, values_sorted, range, params);
    }


    private String[] parseString(String parameters){
        return parameters.split(" ", 3);
    }

    private double compute_score(double new_val, List<Double> values_sorted, List<Double> range, String[] params) throws Exception{
        return normalize_score_output(score_function(new_val, values_sorted, params), range, values_sorted, params);
    }

    private double score_function(double new_val, List<Double> values_sorted, String[] params) throws Exception{
        return basic_functions(distance_from_closest_value(new_val, values_sorted), params);
    }

    private double distance_from_closest_value(double x, List<Double> values_taken){
        return values_taken.stream().map(val -> Math.abs(val - x)).min(Double::compareTo).orElseThrow();
    }

    private double basic_functions(double new_val, String ...params) throws Exception{
        if(params.length == 0){return basic_functions(new_val);}
        switch (params[0]) {
            case "affine":
                return Double.parseDouble(params[1]) * new_val + Double.parseDouble(params[2]);
            case "pow":
                return Math.pow(new_val, Double.parseDouble(params[1]));
            case "log":
                return Math.log(new_val)/Math.log(Double.parseDouble(params[1]));
            case "exp":
                if(params[1].equals("e")){return Math.exp(new_val);}
                else{return Math.pow(Double.parseDouble(params[1]), new_val);}
        
            default:
                throw new Exception("mathematical function "+params[0]+" not implemented");
        }
    }

    private double basic_functions(double new_val){
        return Math.pow(new_val, 2);
    }

    private List<Double> get_distances(List<Double> range, List<Double> values_taken){
        if(values_taken.isEmpty()) return Collections.emptyList();
        
        List<Double> distances = new ArrayList<Double>(Arrays.asList(2*(values_taken.getFirst() - range.getFirst())));
        for(int i = 0; i < values_taken.size() - 1; i++){
            distances.add(values_taken.get(i+1) - values_taken.get(i));
        }
        distances.add(2*(range.getLast() - values_taken.getLast()));
        
        return distances;
    }

    private double normalize_score_output(double new_val, List<Double> range, List<Double> values_taken, String[] params) throws Exception{
        double min_value = basic_functions(0., params);
        double max_val = basic_functions(get_distances(range, values_taken).stream().map(x -> x/2).max(Double::compareTo).orElse(0.0), params);
        return (new_val - min_value)/(max_val - min_value);
    }
}
