package TSM_Statistics;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import TSM_Statistics.Hull.HullBuilder;

public class DataHull {
    public static Map<String, Function<Double, Double>> SCALER = Map.of("linear", x -> x, "log", x -> Math.log10(x));

    private Map<Double, Double> valuesToOccurrences;
    private String scale;
    private Hull hull;

    public DataHull(Map<Double, Double> options, List<Double> range, Function<Double, Double> referenceFunction, 
        Function<Double, Function<Double, Double>> primitivFunction, String dataScale) throws Exception{
        this.valuesToOccurrences = options;

        if(dataScale != null && DataHull.SCALER.containsKey(dataScale)){this.scale = dataScale;}
        else{this.scale = this.scaleDecider(options);}

        List<Parameters> curvesToAdd = options.entrySet().stream().map(x -> new Parameters(1./x.getValue(), x.getKey())).toList();
        Segment functionRange = new Segment(range.getFirst(), range.getLast());


        HullBuilder builder = new HullBuilder().curvesToAdd(curvesToAdd).functionRange(functionRange).distanceFunction(referenceFunction)
        .distancePrimitive(primitivFunction).dataScaler(DataHull.SCALER.get(this.scale));

        this.hull = builder.buildHull();
    }
        

    public Hull getHull(){return this.hull;}
    public Map<Double, Double> getOptions(){return this.valuesToOccurrences;}
    public Function<Double, Double> getFunction(){return this.getHull().getReferenceFunction();}
    public Function<Double, Function<Double, Double>> getPrimitive(){return this.getHull().getPrimitive();}
    public String getScale(){return this.scale;}
        
    public void setFunction(Function<Double, Double> func){this.getHull().setReferenceFunction(func);}
    public void setPrimitive(Function<Double, Function<Double, Double>> func){this.getHull().setPrimitive(func);}
        

    public void addOption(Double value, Double occurrences) throws Exception{
        Map<Double, Double> options = this.getOptions();
        if(options.containsKey(value)){options.put(value, options.get(value) + occurrences);} 
        else{options.put(value, occurrences);}

        this.getHull().addSubfunction(new Parameters(1./occurrences, value));
    }

    public String scaleDecider(Map<Double, Double> options){ // TODO
        for(Map.Entry<Double, Double> entry : options.entrySet()){
            if (entry.getKey() <= 0.){return "linear";} 
            if (Math.abs(Math.log10(entry.getKey())) > 6){return "log";}
        }
        return "linear";
    }
        
    public double scaling(Double v){return DataHull.SCALER.get(this.getScale()).apply(v);}
    public static Function<Double, Double> normalizeScore(List<Double> liste){
        switch (liste.size()) {
            case 0:
                return null;
            case 1:
                return x -> 1.0;
            default:
                double minValue = liste.stream().min(Double::compareTo).orElse(null);
                double maxValue = liste.stream().max(Double::compareTo).orElse(null);
                return x -> (x - minValue)/(maxValue - minValue);
        } 
    }

    private List<Double> linSpace(double start, double end, double nbpoints){
        List<Double> ret = Arrays.asList();
        Double step = (end-start)/(nbpoints-1);
        for(int i = 0; i < nbpoints; i++){
            ret.add(start + step*i);
        }
        return ret;
    }
        
    public List<List<Double>> generateData(Double nbpoints) throws Exception{
        Hull hull = this.getHull();
        List<Double> x = linSpace(hull.getRange().getStart(), hull.getRange().getEnd(), nbpoints);

        List<Double> values = hull.getValues();
        Map<Double, Double> options = this.getOptions();
        
        List<List<Double>> newValuesScore = Arrays.asList();
        for(double v : x){
            if(!values.contains(v)) newValuesScore.add(Arrays.asList(v, hull.scoreOfNewCurb(new Parameters(1., v))));
        }
            
        List<List<Double>> oldValuesScore = Arrays.asList();
        for(Map.Entry<Double, Double> entry : options.entrySet()){
            oldValuesScore.add(Arrays.asList(this.scaling(entry.getKey()), hull.scoreOfNewCurb(new Parameters(1./(entry.getValue() + 1), this.scaling(entry.getKey())))));
        }
        
        List<List<Double>> valuesScore = Stream.concat(newValuesScore.stream(), oldValuesScore.stream()).toList();
        Function<Double, Double> norm = DataHull.normalizeScore(valuesScore.stream().map(a -> a.getLast()).toList());
        
        return valuesScore.stream().map(a -> Arrays.asList(a.getFirst(), norm.apply(a.getLast()))).toList();
    }
}
