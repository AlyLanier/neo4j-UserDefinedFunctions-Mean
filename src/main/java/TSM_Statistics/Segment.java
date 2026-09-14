package TSM_Statistics;

import java.util.Arrays;
import java.util.List;

public class Segment {
    private Double start;
    private Double end;

    public Segment(Double start, Double end){
        if(start >= end){throw new ExceptionInInitializerError("start must be smaller than end");}
        this.start = start;
        this.end = end;
    }

    public boolean contains(Segment s){
        return getStart() <= s.getStart() && s.getEnd() <= getEnd();
    }

    public boolean contains(Double d){
        return getStart() <= d && d <= getEnd();
    }

    @Override
    public String toString(){
        return String.format("[]%d, %d[]", getStart(), getEnd());
    }

    @Override
    public boolean equals(Object obj){
        return this == obj || obj.getClass() == this.getClass() && ((Segment) obj).getStart() == getStart() && ((Segment) obj).getEnd() == getEnd();
    }

    @Override
    public int hashCode(){
        return (int) (getStart() + getEnd());
    }

    public Segment copy(){
        return new Segment(getStart(), getEnd());
    }

    public Double getStart(){
        return start;
    }

    public Double getEnd(){
        return end;
    }

    public void setStart(Double d){
        this.start = d;
    }

    public void setEnd(Double d){
        this.end = d;
    }

    public List<Double> asTuple(){
        return Arrays.asList(getStart(), getEnd());
    }

}
