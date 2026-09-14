package TSM_Statistics;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;


public class Hull {
    private Map<Parameters, List<Segment>> subfunctionsToSegments;
    private Segment functionRange;
    private Function<Double, Double> referenceFunction;
    private Function<Double, Function<Double, Double>> primitiveFunction;
    private Function<Double, Double> dataScale;

    public Hull(List<Parameters> curvesToAdd, Segment range, Function<Double, Double> referenceFunction, 
        Function<Double, Function<Double, Double>> primitivFunction, Function<Double, Double> dataScale) throws Exception{
            
            this.subfunctionsToSegments = Collections.emptyMap();
            this.functionRange = range;
            this.referenceFunction = referenceFunction;
            this.primitiveFunction = primitivFunction;
            this.dataScale = dataScale;

            if(this.getRange() == null){
                if(curvesToAdd.size() > 1){
                    Stream<Double> valuesToAdd = curvesToAdd.stream().map(x -> x.xIntercept);
                    this.setRange(new Segment(this.scale(valuesToAdd.min(Double::compareTo).orElseThrow()), this.scale(valuesToAdd.max(Double::compareTo).orElseThrow())));
                }else if(curvesToAdd.size() == 1){
                    double start = 0.;
                    double end = 2*this.scale(curvesToAdd.get(0).xIntercept);
                    if(end < start){
                        this.setRange(new Segment(end, start));
                    }else{
                        this.setRange(new Segment(start, end));
                    }
                }
            }
            
            for(Parameters params: curvesToAdd){
                this.addSubfunction(params);
            }

            this.verifyValidity();
    }

    /* Getters and Setters */

    public Function<Double, Double> getReferenceFunction(){return this.referenceFunction;}
    public void setReferenceFunction(Function<Double, Double> f){this.referenceFunction = f;}

    public Function<Double, Function<Double, Double>> getPrimitive(){return this.primitiveFunction;}
    public void setPrimitive(Function<Double, Function<Double, Double>> f){this.primitiveFunction = f;}
        
    public Segment getRange(){return this.functionRange;}
    public void setRange(Segment segment){this.functionRange = segment;}
        
    public Map<Parameters, List<Segment>> getSubfunctionSegments(){return this.subfunctionsToSegments;}
    public void setSubfunctionSegments(Map<Parameters, List<Segment>> dic){this.subfunctionsToSegments = dic;}
        
    public List<Double> getValues(){return this.getSubfunctionSegments().keySet().stream().map(x -> x.xIntercept).toList();}
    public List<Segment> getAllSegments(){return this.getSubfunctionSegments().values().stream().flatMap(List::stream).toList();}
    
    public void addSubfunctionSegment(Parameters params, Segment segment){
        this.subfunctionsToSegments.computeIfAbsent(params, x -> new ArrayList<Segment>()).add(segment);
    }
    public void addSubfunctionSegments(Parameters params, List<Segment> segmentList){
        this.subfunctionsToSegments.computeIfAbsent(params, x -> new ArrayList<Segment>()).addAll(segmentList);
    }

    public Function<Double, Double> getScale(){return this.dataScale;}
    public Double scale(Double value){return this.getScale().apply(value);}


    /* printing and representation */ 

    @Override
    public String toString(){
        String res = "Defined by :\n";
        for (Map.Entry<Parameters, List<Segment>> entry : this.getSubfunctionSegments().entrySet()) {
            res += entry.getKey().toString() + " -> " + entry.getValue().toString() + "\n";
        }
        res += "on: " + this.getRange().toString();
        return res;
    }

    public void printDict(){
        System.out.println("{");
        for (Map.Entry<Parameters, List<Segment>> entry : this.getSubfunctionSegments().entrySet()) {
            System.out.println("  " + entry.getKey() + "=" + entry.getValue());
        }
        System.out.println("{");
    }

    /* Callers */

    public Double apply(Double x){
        for (Map.Entry<Parameters, List<Segment>> entry : this.getSubfunctionSegments().entrySet()) {
            for(Segment s : entry.getValue()){
                if(s.contains(x)){
                    Parameters params = entry.getKey();
                    return this.getReferenceFunction().apply(params.slope * ( x - params.xIntercept ));
                }
            }
        }
        throw new RuntimeException("could not find " + x + " in the definition set of the function");
    }

    public List<Double> apply(List<Double> l){
        return l.stream().map(x -> this.apply(x)).toList();
    }
        
    /* copy */
    
    private List<Segment> copyList(List<Segment> l){
        List<Segment> newList = new ArrayList<Segment>();
        l.forEach(segment -> newList.add(segment.copy()));
        return newList;
    }

    private Map<Parameters, List<Segment>> copyDict(){
        Map<Parameters, List<Segment>> newDict = new HashMap<>();
        this.getSubfunctionSegments().forEach((param, listSegment) -> newDict.put(param, this.copyList(listSegment)));
        return newDict;
    }

    public Hull copy() throws Exception{
        Hull cp = new Hull(null, this.getRange().copy(), this.getReferenceFunction(), this.getPrimitive(), this.getScale());
        cp.setSubfunctionSegments(this.copyDict());
        return cp;
    }
        
    /* Sanitizers */
    
    private void sanitizeSegments(){
        for (List<Segment> segments : this.getSubfunctionSegments().values()){
            segments.sort(Comparator.comparingDouble(Segment::getStart));
            int i = 0;
            while (i < segments.size() - 1) {
                if (segments.get(i).getEnd() == segments.get(i + 1).getStart()) {
                    segments.get(i).setEnd(segments.get(i + 1).getEnd());
                    segments.remove(i + 1);
                } else {
                    i++;
                }
            }
        }
    }

    private static Parameters sanitizeParameters(Parameters params){
        if(params.slope < 0){return new Parameters(-params.slope, -params.xIntercept);}
        else if(params.slope == 0){throw new RuntimeException("slope must not be 0, subfunctions must be bijective on R");}
        else{return params;}
    }

    /* Verifiers */

    private void verifySlope(){
        this.getSubfunctionSegments().keySet().forEach(params -> {if(params.slope == 0) throw new RuntimeException("slope must not be 0, subfunctions must be bijective on R");});
    }

    private void verifyDefinedOnAllSegment(){
        List<Segment> sorted = this.copyList(this.getAllSegments());
        sorted.sort(Comparator.comparingDouble(Segment::getStart));

        Segment range = this.getRange();
        if(!sorted.isEmpty() && range != null &&(range.getStart() != sorted.get(0).getStart() || range.getEnd() != sorted.get(sorted.size()-1).getEnd())){
            throw new RuntimeException("the function must be defined on its entire range, ends do not match");
        }

        for (int i = 0; i < sorted.size() - 1; i++) {
            if (sorted.get(i).getEnd() != sorted.get(i + 1).getStart()) {
                throw new RuntimeException("the function should be defined on the total range it takes values in");
            }
        }

    }

    private void verifyValidity(){
        this.verifySlope();
        this.verifyDefinedOnAllSegment();
    }

    /* Complex adder */

    public static class Intersection {
        public final double value;
        public final boolean isSameSide;

        public Intersection(double value, boolean isSameSide) {
            this.value = value;
            this.isSameSide = isSameSide;
        }
    }

    private static void addIntersection(
        Map<Parameters, Map<Segment, List<Intersection>>> usefulIntersections,
        Parameters params, 
        Segment segment, 
        Intersection intersect){
            usefulIntersections.computeIfAbsent(params, x -> new HashMap<>())
                           .computeIfAbsent(segment, x -> new ArrayList<>())
                           .add(intersect);
        }
        
    private static List<Double> allIntersections(Map<Parameters, Map<Segment, List<Intersection>>> usefulIntersections){
        return usefulIntersections.values().stream().flatMap(segmentMap -> segmentMap.values().stream())
            .flatMap(List::stream).map(x -> x.value).toList();
    }
        
    private static List<Double> intersectionPoints(Parameters g, Parameters h){
        double gSlope = g.slope, gXInter = g.xIntercept;
        double hSlope = h.slope, hXInter = h.xIntercept;

        double oppositeIntersect = (gXInter*gSlope + hXInter*hSlope)/(gSlope + hSlope);
        if(gSlope - hSlope == 0) return Arrays.asList(oppositeIntersect);

        double concurrentIntersect = (gXInter*gSlope - hXInter*hSlope)/(gSlope - hSlope);
        return Arrays.asList(oppositeIntersect, concurrentIntersect);
    }
        

    private static boolean skipIntersectionConditionLeft(double intersection, Segment segment, boolean isSameSide, double previousCurbRoot, double newCurbRoot){
        return intersection == segment.getStart() || (intersection == segment.getEnd() && (isSameSide || previousCurbRoot > newCurbRoot));
    }
                                          
    private static boolean skipIntersectionConditionRight(double intersection, Segment segment, boolean isSameSide, double previousCurbRoot, double newCurbRoot){
        return intersection == segment.getEnd() || (intersection == segment.getStart() && (isSameSide || previousCurbRoot < newCurbRoot));
    }

    @FunctionalInterface
    private interface SkipCondition {
        boolean apply(double intersection, Segment segment, boolean isSameSide, double previousCurbRoot, double newCurbRoot);
    }

    private void processIntersections(
        Parameters parameters, 
        List<Parameters> curbsParams, 
        Map<Parameters, Map<Segment, List<Intersection>>> usefulIntersections,
        List<Parameters> consideredCurbs, 
        SkipCondition skipIntersectionCondition){
            double a = parameters.slope, b = parameters.xIntercept;
            double currentMinSlope = a;
            boolean activateSkip = false;

            double c, d;
            for (Parameters params : curbsParams){
                c = params.slope; d = params.xIntercept;
                if(c >= currentMinSlope && activateSkip) continue;

                consideredCurbs.add(params);
                if(c <= currentMinSlope){
                    currentMinSlope = c;
                    if(!activateSkip) activateSkip = true;
                }
                
                List<Segment> segments = this.getSubfunctionSegments().get(params);
                List<Double> intersections = Hull.intersectionPoints(parameters, params);

                for (int i = 0; i < intersections.size(); i++) {
                    double intersection = intersections.get(i);
                    for (Segment segment : segments) {
                        if (!segment.contains(intersection)) continue;

                        if (skipIntersectionCondition.apply(intersection, segment, i == 0, d, b)) {
                            continue;
                        } else {
                            Hull.addIntersection(usefulIntersections, params, segment, new Intersection(intersection, i == 0));
                        }
                    }
                }
            }
    }

    private List<Object> findUsefulIntersections(Parameters parameters){
        double b = parameters.xIntercept;
        Set<Parameters> getKeys = this.getSubfunctionSegments().keySet();

        List<Parameters> leftCurbsParams = getKeys.stream().filter(x -> x.xIntercept < b).sorted(Comparator.comparingDouble((Parameters params) -> params.xIntercept).reversed()).toList();
        List<Parameters> rightCurbsParams = getKeys.stream().filter(x -> x.xIntercept > b).sorted(Comparator.comparingDouble((Parameters params) -> params.xIntercept)).toList();
        
        Map<Parameters, Map<Segment, List<Intersection>>> usefulIntersections = new HashMap<>();
        List<Parameters> consideredCurbs = new ArrayList<>();

        this.processIntersections(parameters, leftCurbsParams, usefulIntersections, consideredCurbs, Hull::skipIntersectionConditionLeft);
        this.processIntersections(parameters, rightCurbsParams, usefulIntersections, consideredCurbs, Hull::skipIntersectionConditionRight);

        return Arrays.asList(usefulIntersections, consideredCurbs);
    }
        

    private void processPreviousCurbs(Parameters parameters, Map<Parameters, Map<Segment, List<Intersection>>> usefulIntersections){
        double a = parameters.slope, b = parameters.xIntercept;
        double c, d;
        List<Intersection> intersects;
        Intersection inter;
        Intersection iOpposite;
        Intersection iSameside;

        for (Parameters params : usefulIntersections.keySet()) {
            Map<Segment, List<Intersection>> segmentMap = usefulIntersections.get(params);
            c = params.slope; d = params.xIntercept;

            for (Segment segment : segmentMap.keySet()) {    
                intersects = segmentMap.get(segment);

                if (intersects.size() == 1) {
                    inter = intersects.get(0);
                    if (inter.isSameSide == (b > d)) segment.setEnd(inter.value);
                    else segment.setStart(inter.value);
                
                } else { // = 2
                    iOpposite = intersects.get(0);
                    iSameside = intersects.get(1);
                    if (c < a) {
                        if (d < b) {
                            if (segment.getEnd() != iSameside.value) {
                                this.addSubfunctionSegment(params, new Segment(iSameside.value, segment.getEnd()));
                            }
                            segment.setEnd(iOpposite.value);
                        } else {
                            if (segment.getStart() != iSameside.value) {
                                this.addSubfunctionSegment(params, new Segment(segment.getStart(), iSameside.value));
                            }
                            segment.setStart(iOpposite.value);
                        }
                    } else {
                        if (d < b) {
                            segment.setStart(iSameside.value);
                            segment.setEnd(iOpposite.value);
                        } else {
                            segment.setStart(iOpposite.value);
                            segment.setEnd(iSameside.value);
                        }
                    }
                }
            }
        }
    }
                              
                               
    private List<List<Double>> processNewSegments(Parameters parameters, List<Double> allIntersections) {
        double b = parameters.xIntercept;
        List<Segment> segments = Arrays.asList();

        Double valueToRemember;
        if(allIntersections.stream().filter(x -> x < b).toList().size() % 2 == 0){
            valueToRemember = this.getRange().getStart();
        }
        else{valueToRemember = null;}
        
        for(double value : allIntersections){
            if(valueToRemember != null){
                segments.add(new Segment(valueToRemember, value));
                valueToRemember = null;
            }else{
                valueToRemember = value;
            }
        }

        if(valueToRemember != null){
            segments.add(new Segment(valueToRemember, this.getRange().getEnd()));
        }
        
        this.addSubfunctionSegments(parameters, segments);
        return segments.stream().map(x -> x.asTuple()).toList();
    }
    
    
    private void deleteSuperfluousSegments(List<Parameters> curbs, List<List<Double>> newSegments){
        List<List<Object>> segmentsToDelete = Arrays.asList();
        double start, end;
        for(Parameters curb : curbs){
            for(Segment segment : this.getSubfunctionSegments().get(curb)){
                for(List<Double> startEnd : newSegments){
                    start = startEnd.get(0); end = startEnd.get(1);
                    if(start <= segment.getStart() && segment.getStart() < end || start < segment.getEnd() && segment.getEnd() <= end){
                        segmentsToDelete.add(Arrays.asList(curb, segment));
                    }
                }
            }
        }
        Parameters curb;
        Segment segment;
        for(List<Object> curbSegment: segmentsToDelete){
            curb = (Parameters) curbSegment.get(0);
            segment = (Segment) curbSegment.get(1);
            this.subfunctionsToSegments.get(curb).remove(segment);
        }
    }
       

    
    private void addSubfunction(Parameters parameters) throws Exception{
        Parameters parameter = Hull.sanitizeParameters(parameters);
        double a = parameter.slope, b = this.scale(parameter.xIntercept);

        parameter = new Parameters(a, b);
        double c, d;
        for(Parameters params : this.getSubfunctionSegments().keySet()){
            c = params.slope; d = params.xIntercept;
            if(b == d){
                if(a < c){
                    this.subfunctionsToSegments.remove(params);
                    break;
                }else if(a == c){
                    return;
                }else{
                    System.out.println(parameter);
                    throw new Exception("Not Implemented");
                }
            }
        }

        List<Object> temp = this.findUsefulIntersections(parameter);
        @SuppressWarnings("unchecked")
        Map<Parameters, Map<Segment, List<Intersection>>> usefullIntersections = (Map<Parameters, Map<Segment, List<Intersection>>>) temp.get(0);
        @SuppressWarnings("unchecked")
        List<Parameters> consideredCurbs = (List<Parameters>) temp.get(1);

        List<Double> allIntersections = Hull.allIntersections(usefullIntersections);
        this.processPreviousCurbs(parameter, usefullIntersections);
        List<List<Double>> newSegments = this.processNewSegments(parameter, allIntersections);

        this.deleteSuperfluousSegments(consideredCurbs, newSegments);
        this.sanitizeSegments();

        this.verifyValidity();
    }


    /* Integral calculation */

    private double computeIntegral(Segment onRange) throws Exception{
        if(this.getPrimitive() == null) throw new Exception("primitive function not defined");
        if(!this.getRange().contains(onRange)){
            throw new Exception(String.format("the range on which to calculate the integral must be contained in the function definition range, %s not in %s", onRange.toString(), this.getRange().toString()));
        } 

        Function<Double, Function<Double, Double>> primitive = this.getPrimitive();
        double integral = 0.0;
        double a, b;
        Function<Double, Double> partial;
        List<Segment> segments;

        for(Map.Entry<Parameters, List<Segment>> entry : this.getSubfunctionSegments().entrySet()){
            a = entry.getKey().slope; b = entry.getKey().xIntercept;
            segments = entry.getValue();
            partial = primitive.apply(a);

            for(Segment segment : segments){
                if(onRange.contains(segment)){
                    integral += partial.apply(segment.getEnd() - b) - partial.apply(segment.getStart() - b);
                }else if(segment.contains(onRange.getStart()) && onRange.getStart() != segment.getStart()){
                    integral += partial.apply(segment.getEnd() - b) - partial.apply(onRange.getStart() - b);
                }else if(segment.contains(onRange.getEnd()) && onRange.getEnd() != segment.getEnd()){
                    integral += partial.apply(onRange.getEnd() - b) - partial.apply(segment.getStart() - b);
                }
            }
        }
        return integral;
    }

        
    private Map<Parameters, Map<Segment, Double>> computeIntegralBySegment(Segment onRange) throws Exception{
        if(this.getPrimitive() == null) throw new Exception("primitive function not defined");
        if(!this.getRange().contains(onRange)){
            throw new Exception(String.format("the range on which to calculate the integral must be contained in the function definition range, %s not in %s", onRange.toString(), this.getRange().toString()));
        } 

        Function<Double, Function<Double, Double>> primitive = this.getPrimitive();
        double a, b;
        Function<Double, Double> partial;
        List<Segment> segments;
        Map<Parameters, Map<Segment, Double>> integrals = Collections.emptyMap();


        for(Map.Entry<Parameters, List<Segment>> entry : this.getSubfunctionSegments().entrySet()){
            Parameters params = entry.getKey();
            a = entry.getKey().slope; b = entry.getKey().xIntercept;
            segments = entry.getValue();
            partial = primitive.apply(a);
            integrals.put(params, Collections.emptyMap());

            for(Segment segment : segments){
                if(onRange.contains(segment)){
                    integrals.get(params).put(segment, partial.apply(segment.getEnd() - b) - partial.apply(segment.getStart() - b));
                }else if(segment.contains(onRange.getStart()) && onRange.getStart() != segment.getStart()){
                    integrals.get(params).put(new Segment(onRange.getStart(), segment.getEnd()), partial.apply(segment.getEnd() - b) - partial.apply(onRange.getStart() - b));
                }else if(segment.contains(onRange.getEnd()) && onRange.getEnd() != segment.getEnd()){
                    integrals.get(params).put(new Segment(segment.getStart(), onRange.getEnd()), partial.apply(onRange.getEnd() - b) - partial.apply(segment.getStart() - b));
                }
            }
        }
        return integrals;
    }


    /* score computation */

    private static Map<Segment, Double> segmentToIntegral(Map<Parameters, Map<Segment, Double>> previousIntegralSegments){
        Map<Segment, Double> result = Collections.emptyMap();
        for(Map<Segment, Double> segToIntegral: previousIntegralSegments.values()){
            for(Map.Entry<Segment, Double> entry : segToIntegral.entrySet()){
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public double scoreOfNewCurbTest(Parameters parameters) throws Exception{
        Map<Parameters, Map<Segment, Double>> previousIntegralBySegment = this.computeIntegralBySegment(this.getRange());
        Map<Segment, Double> segmentIntegrals = Hull.segmentToIntegral(previousIntegralBySegment);

        Hull hull = this.copy();
        hull.addSubfunction(parameters);

        List<Segment> segmentsToCompute = Arrays.asList();
        List<Segment> segmentsComputed = Arrays.asList();
        Parameters params;
        List<Segment> segments;

        for(Map.Entry<Parameters, List<Segment>> entry: hull.getSubfunctionSegments().entrySet()){
            params = entry.getKey();
            segments = entry.getValue();
            if(!previousIntegralBySegment.containsKey(params)){
                segmentsToCompute.addAll(segments);
                continue;
            }

            for(Segment segment : segments){
                if(!previousIntegralBySegment.get(params).containsKey(segment)){
                    segmentsToCompute.add(segment);
                }else{
                    segmentsComputed.add(segment);
                }
            }
        }


        List<Double> oldComputedSegments = segmentsComputed.stream().map(x -> segmentIntegrals.get(x)).toList();
        List<Double> newComputedSegments = Arrays.asList();
        for(Segment segment: segmentsToCompute){
            newComputedSegments.add(hull.computeIntegral(segment));
        }

        double oldIntegral = segmentIntegrals.values().stream().mapToDouble(Double::doubleValue).sum();
        double newIntegral = oldComputedSegments.stream().mapToDouble(Double::doubleValue).sum() + newComputedSegments.stream().mapToDouble(Double::doubleValue).sum();

        try{
            return newIntegral/oldIntegral;
        }catch(Exception e){
            System.out.println(oldIntegral);
            System.out.println(newIntegral);
            System.out.println("too small");
            throw e;
        }   

    }
        
    public static class HullBuilder {
        private List<Parameters> curves = Arrays.asList();
        private Segment range = null;
        private Function<Double, Double> referenceFunction = null;
        private Function<Double, Function<Double, Double>> primitivFunction = null;
        private Function<Double, Double> dataScale = x -> x;

        public HullBuilder(){}

        public Hull buildHull() throws Exception{
            return new Hull(curves, range, referenceFunction, primitivFunction, dataScale);
        }

        public HullBuilder curvesToAdd(List<Parameters> curves){
            this.curves = curves;
            return this;
        }

        public HullBuilder functionRange(Segment range){
            this.range = range;
            return this;
        }

        public HullBuilder distanceFunction(Function<Double, Double> referenceFunction){
            this.referenceFunction = referenceFunction;
            return this;
        }

        public HullBuilder distancePrimitive(Function<Double, Function<Double, Double>> primitivFunction){
            this.primitivFunction = primitivFunction;
            return this;
        }

        public HullBuilder dataScaler(Function<Double, Double> dataScale){
            this.dataScale = dataScale;
            return this;
        }

    }
}
