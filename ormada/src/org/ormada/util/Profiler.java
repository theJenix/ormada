package org.ormada.util;

/**
 * A basic method profiler, that will log accumulated method time and print out total time and time per n number of calls,
 * where n is a definible threshold.
 * 
 * @author Jesse Rosalia
 *
 */
public class Profiler {

    private long enterTime         = 0;
    private long accumulatedTime   = 0;
    private int  counter           = 0;
    private int  counterThresholds = 0;
    private String prefix;
    
    public Profiler(String prefix, int counterThreshold) {
        this.prefix = prefix;
        this.counterThresholds = counterThreshold;
    }

    public void enter() {
        enterTime = System.currentTimeMillis();
    }
    
    public void exit() {
        long exitTime = System.currentTimeMillis();
        accumulatedTime += exitTime - enterTime;
        
        if (++this.counter == this.counterThresholds) {
            System.out.println(this.prefix + ": Elapsed time in ms: " + accumulatedTime + ", " + ((float)accumulatedTime)/this.counterThresholds + "/" + this.counterThresholds + " calls");
            this.counter = 0;
            this.accumulatedTime = 0;
        }    
    }
}
