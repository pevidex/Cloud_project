import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.net.*;
import java.io.*;


import java.lang.Math.*;


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.Main;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.InvalidMazeRunningStrategyException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.InvalidCoordinatesException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.CantGenerateOutputFileException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.CantReadMazeInputFileException;

import java.io.File;
import java.nio.file.*;


public class WebServer {

    public static void main(String[] args) throws Exception {
            
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/mzrun.html", new MyHandler());
        server.createContext("/ping", new PingHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        DynamoDBWriter.init();
        server.start();
    }

    public static Map<String, String> getQueryMap(String query)  {  
        String[] params = query.split("&");  
        Map<String, String> map = new HashMap<String, String>();  
        for (String param : params)  
        {  
            String name = param.split("=")[0];  
            String value = param.split("=")[1];  
            map.put(name, value);  
        }  
        return map;  
    }
    
    static class PingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
        byte[] response = "echo response".getBytes();
        t.sendResponseHeaders(200,response.length);
        OutputStream os = t.getResponseBody();
        os.write(response);
        os.close();
        }
    }
    
    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Map<String, String> queries = new HashMap<String, String>();
            System.out.println("Request received");
            
            String query = t.getRequestURI().getQuery();
            
            /*System.out.println(query);
            URL url = new URL(query);*/
            queries=getQueryMap(query);
         
            if(query == null) return;
             //http://localhost:8000/test?m=<maze-filename>&x0=76&y0=87&x1=78&y1=89&v=50&s=astar
             
             // http://LB-1040319925.us-east-2.elb.amazonaws.com/mzrun.html?m=Maze50.maze&x0=1&y0=1&x1=6&y1=6&v=75&s=bfs xs
            //  http://LB-1040319925.us-east-2.elb.amazonaws.com/mzrun.html?m=Maze100.maze&x0=1&y0=1&x1=74&y1=95&v=40&s=bfs s
            //http://LB-1040319925.us-east-2.elb.amazonaws.com/mzrun.html?m=Maze100.maze&x0=76&y0=87&x1=78&y1=89&v=50&s=astar
            //http://LB-1040319925.us-east-2.elb.amazonaws.com/mzrun.html?m=Maze250.maze&x0=124&y0=125&x1=199&y1=201&v=75&s=bfs
            Long threadID = new Long(Thread.currentThread().getId());
            
            String[] args = {queries.get("x0"), queries.get("y0"), queries.get("x1"), queries.get("y1"), queries.get("v"), queries.get("s"), queries.get("m"), queries.get("m").split("\\.")[0] + "_" + threadID + ".html" };
            
            try{
                Main game = new Main();
                game.main(args);
                System.out.println("Game Done");
                String result = ICount.printICount("ola", threadID).split("-")[1];
                System.out.println("----Result = " + result);
                saveResult(query, calculateEstimasteCost(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]),Integer.parseInt(args[4])),result);
                System.out.println("----Query: " + query);
            }catch(InvalidMazeRunningStrategyException e){
                e.printStackTrace();
            }catch(InvalidCoordinatesException e){
                e.printStackTrace();
            }
            catch(CantGenerateOutputFileException e){
                e.printStackTrace();
            }
            catch(CantReadMazeInputFileException e){
                e.printStackTrace();
            }
            
            byte[] response = Files.readAllBytes(new File(queries.get("m").split("\\.")[0]+"_" + threadID + ".html").toPath());
            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
        }
        
    public double calculateEstimasteCost(int x0, int y0, int x1, int y1,int v){
        return (Math.sqrt(Math.pow(x0-x1,2)+Math.pow(y0-y1,2))*100)/v;
    }

    private void saveResult(String query, double estcost,String result){
        try{
            DynamoDBWriter.putItem(DynamoDBWriter.newItem(query, estcost, result));
            }
        catch(Exception e){ e.printStackTrace();}
        }
    }

}
