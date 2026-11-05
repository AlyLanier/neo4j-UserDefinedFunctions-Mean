package TSM_Statistics;

import java.util.Arrays;
import java.util.List;

public class Segment {
    private double start;
    private double end;

    public Segment(double start, double end){
        if(start >= end){throw new IllegalArgumentException("start must be smaller than end");}
        this.start = start;
        this.end = end;
    }

    public boolean contains(Segment s){
        return getStart() <= s.getStart() && s.getEnd() <= getEnd();
    }

    public boolean contains(double d){
        return (getStart() <= d) && d <= getEnd();
    }

    @Override
    public String toString(){
        return String.format("]%s, %s[", getStart(), getEnd());
    }

    @Override
    public boolean equals(Object obj){
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Segment other = (Segment) obj;
        return getStart() == other.getStart() && getEnd() == other.getEnd();
    }

    @Override
    public int hashCode(){
        return (int) (getStart() + getEnd());
    }

    public Segment copy(){
        return new Segment(getStart(), getEnd());
    }

    public double getStart(){
        return start;
    }

    public double getEnd(){
        return end;
    }

    public void setStart(double d){
        this.start = d;
    }

    public void setEnd(double d){
        this.end = d;
    }

    public List<Double> asTuple(){
        return Arrays.asList(getStart(), getEnd());
    }

}