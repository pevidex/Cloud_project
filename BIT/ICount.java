import BIT.highBIT.*;
import java.io.*;
import java.util.*;

import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.RobotController;

public class ICount {
    private static PrintStream out = null;
    private static Map<Long, Metrics> map = new HashMap<Long, Metrics>(); 
    /* main reads in all the files class files present in the input directory,
     * instruments them, and outputs them to the specified output directory.
     */
     
    static class Metrics {
        public long bb_count;
        public long inst_count;
        
        Metrics(long bb, long inst){
            bb_count = bb;
            inst_count = inst; 
        }
        
        public void resetMetrics(){
            bb_count = 0;
            inst_count = 0; 
        }
    }
    
    public static void main(String argv[]) {
        
        ClassInfo ci = new ClassInfo("../src/main/java/pt/ulisboa/tecnico/meic/cnv/mazerunner/maze/RobotController.class");
        
        // loop through all the routines
        // see java.util.Enumeration for more information on Enumeration class
        for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
            Routine routine = (Routine) e.nextElement();
            //routine.addBefore("ICount", "mcount", new Integer(1));
            
            for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                BasicBlock bb = (BasicBlock) b.nextElement();
                bb.addBefore("ICount", "count", new Integer(bb.size()));
            }
            
        }
        ci.write("../src/main/java/pt/ulisboa/tecnico/meic/cnv/mazerunner/maze/RobotController.class");
        
        
    }
    
    public static synchronized String printICount(String foo, Long threadID) {
        
        Metrics metrics = map.get(threadID);
        String output = "";
        if(metrics == null){
            output = "[ERROR] ThreadID NOT FOUND";
        }
        else{
            output = metrics.inst_count + "-" + metrics.bb_count;
        }
        metrics.resetMetrics();
        return output;

    }
    
    

    public static synchronized void count(int incr) {
        Long threadID = new Long(Thread.currentThread().getId());
        
        Metrics metrics = map.get(threadID);
        
        if(metrics == null){
            map.put(threadID, new Metrics(0,0));
        }
        metrics = map.get(threadID);
        metrics.inst_count += incr;
        metrics.bb_count++;
        
    }

    public static synchronized void mcount(int incr) {
        //mcount++;
    }
}

