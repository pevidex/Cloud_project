import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.*;

import java.net.*;

import java.lang.Math.*;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.*;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;




public class LoadBalancer {
    
    private HttpServer httpServer;
    static AmazonDynamoDB dynamoDB;
    static AmazonEC2 ec2;
	private static String ID = "";
    private static String KEY = "";
    private static final String tableName = "metrics";

    public static void init() throws Exception {

        BasicAWSCredentials bac = new BasicAWSCredentials(ID,KEY);
        dynamoDB = AmazonDynamoDBClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(bac))
            .withRegion("us-east-2")
            .build();

        ec2 = AmazonEC2ClientBuilder.standard().withRegion("us-east-2").withCredentials(new AWSStaticCredentialsProvider(bac)).build();
    }

    public static void main(String[] args) throws IOException {
        LoadBalancer lb = new LoadBalancer();
        lb.start();
    }
    
    public void start(){
        try{
            init();
        }catch(Exception e){e.printStackTrace();}
        startServer();
        
    	updateInstances();
        updateLoop();
        
    }
    
    public void startServer(){
        try {
            httpServer = HttpServer.create(new InetSocketAddress(9090), 0);
            httpServer.createContext("/mzrun.html", new LoadHandler());
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
        }catch(Exception e){e.printStackTrace();}
    }

	public class LoadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
       	
			String query = t.getRequestURI().getQuery();
			Map<String, String> queries=getQueryMap(query);
            if(query == null) return;
            String[] args = {queries.get("x0"), queries.get("y0"), queries.get("x1"), queries.get("y1"), queries.get("v")};
            
			InstanceManager manager = InstanceManager.getInstance();

	       	System.out.println("[LOAD BALANCER] Received request " + query);
	            
	        //TODO Encontrar custo do pedido mais parecido
	        Double distCost = calculateEstimasteCost(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]),Integer.parseInt(args[4]));
	        
	        ScanResult queryResult = querySimilarRequest(distCost);
	        
	        double rank = rankCalc(queryResult);
	        
	        System.out.println("Estimate cost: "+distCost + ", number of blocks: "+rank);
	        //TODO Encontrar melhor instancia para reencaminhar o pedido
	        byte[] response = null;
	        Instance chosenInstance= null;
	        try{
                chosenInstance = manager.allocateInstance(rank);
                response = sendRequest(chosenInstance.getPublicIpAddress(), query);}
            catch(Exception e){
                updateInstances(); 
                chosenInstance = manager.allocateInstance(rank);
                response = sendRequest(chosenInstance.getPublicIpAddress(), query);
            }
            
            manager.receivedAnswer(chosenInstance.getInstanceId(),rank);

	        t.sendResponseHeaders(200,response.length);
	        OutputStream os = t.getResponseBody();
	        os.write(response);
	        os.close();
        }
    }    
    public double rankCalc(ScanResult s){
        Map<String,AttributeValue> map=s.getItems().get(0);
        Long blocks = Long.parseLong(map.get("blocks").getS());
        return blocks.doubleValue()/10000;
    }
    
    
  public double calculateEstimasteCost(int x0, int y0, int x1, int y1,int v){
    	return (Math.sqrt(Math.pow(x0-x1,2)+Math.pow(y0-y1,2))*100)/v;
    }

    public void updateInstances(){
    	System.out.println("[LOAD BALANCER] Updating Instances");

    	InstanceManager manager = InstanceManager.getInstance();

        try {
            DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
            List<Reservation> reservations = describeInstancesResult.getReservations();

           	Set<Instance> instances = new HashSet<Instance>();

            for (Reservation reservation : reservations) {
                for(Instance i: reservation.getInstances()){
                    if(i.getState().getName().equals("running")||i.getState().getName().equals("pending"))
                        if(i.getImageId().equals("ami-d82b17bd"))
                            instances.add(i);
                }
            }

            manager.updateInstances(instances,ec2);

            System.out.println("[LOAD BALANCER] Number of instances running: " + instances.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateLoop() {
        while(true){
            try {
                Thread.sleep(5000);
                updateInstances();
                autoScale();
            } catch (Exception e) {e.printStackTrace();}
        }
    }

    private void autoScale(){
    	System.out.println("[LOAD BALANCER] AutoScaling");

    	InstanceManager manager = InstanceManager.getInstance();
    	manager.autoScale(ec2);
    }

    public ScanResult querySimilarRequest(double estimateCost){
    	// Scan items for movies with a year attribute greater than 1985
        double maxValue = 2* estimateCost;
        double minValue = estimateCost/2;
        double max = estimateCost;
        double min = estimateCost;
        ScanResult scanResult = null;
        while(max<maxValue || min>minValue){
            scanResult = queryDB(min, max);
            if(scanResult.getCount()>=1){
                break;
            }
            min-=1;
            if(min<0)
                min=0;
            max+=1;
            if(max>maxValue)
                max=maxValue;
        }
        return scanResult;
    }

    public ScanResult queryDB(double min, double max){
		    HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
            Condition condition = new Condition()
                .withComparisonOperator(ComparisonOperator.BETWEEN.toString())
                .withAttributeValueList(new AttributeValue().withN(String.valueOf(min)), new AttributeValue().withN(String.valueOf(max)));
            scanFilter.put("rankCost", condition);
            ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
            ScanResult scanResult = dynamoDB.scan(scanRequest);
            return scanResult;
    }

     public byte[] sendRequest(String ip, String query){
        ByteArrayOutputStream baos  = new ByteArrayOutputStream(); 
		try{
            System.out.println(query);
            URL url = new URL(String.format("http://%s:%s/mzrun.html?%s", ip, 8000, query));
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            con.setRequestMethod("GET");
            
            InputStream inputStream = con.getInputStream();
            byte[] content = new byte[ 2048 ];  
            int bytesRead = -1;  
            while( ( bytesRead = inputStream.read(content) ) != -1 ) {  
                baos.write( content, 0, bytesRead );  
            } 
            
            inputStream.close();
        }catch(Exception e){e.printStackTrace();}
		
		return baos.toByteArray();
    }
    /*
    public void sendPing(String ip){

        ByteArrayOutputStream baos  = new ByteArrayOutputStream(); 
		try{
            System.out.println(query);
            URL url = new URL(String.format("http://%s:%s/mzrun.html?%s", ip, 8000, query));
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            con.setRequestMethod("GET");
            long time = System.currentTimeMillis();
            InputStream inputStream = con.getInputStream();
            byte[] content = new byte[ 2048 ];  
            int bytesRead = -1;  
            while( ( bytesRead = inputStream.read(content) ) != -1 ) {
                if ( System.currentTimeMillis() > time + 1000) {
                    throw new IOException ();
                }
                baos.write( content, 0, bytesRead );  
            } 
            
            inputStream.close();
        }catch(Exception e){e.printStackTrace();}
		
		return baos.toByteArray();
    }*/
    
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
}
