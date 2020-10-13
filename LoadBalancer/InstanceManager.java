import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
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

import java.util.*;
import com.sun.net.httpserver.HttpServer;
import java.util.*;


public class InstanceManager {
	private final static double maxProcessing=500000;
	private final static double minProcessing=20000;
	private final static int CYCLES=2;
	private static InstanceManager manager = null;

	private Map<String, InstanceInfo> instances = new HashMap<String, InstanceInfo>();

	private InstanceManager(){}

	public static InstanceManager getInstance(){
		if(manager == null)
			manager = new InstanceManager();
		return manager;
	}

	//Method to allocate best Instance based on cost
	//Modify this method to receive a cost and perform an algorithm 
	public Instance allocateInstance(double estimateCost){
		String id=getInstanceIdLowWork();
		if(id==null){
			System.out.println("[AUTO-SCALER] No instances running!");
			return null;
		}
		instances.get(id).addCurrentProcessing(estimateCost);
		return instances.get(id).getInstance();
	}
	
	public void receivedAnswer(String Id, double distCost){
        instances.get(Id).subtractCurrentProcessing(distCost);
        if(instances.get(Id).getCurrentProcessing() <= 0)
            instances.get(Id).setCurrentProcessing(0);
	}
	
	public void autoScale(AmazonEC2 ec2){
		Double averageProcessing = calculateAverageProcessing();
		if(averageProcessing==-1){
			System.out.println("[AUTO-SCALER] No instances running!");
            increaseInstances(ec2);
			return;
		}
		if(averageProcessing>maxProcessing){
			increaseInstances(ec2);
			System.out.println("[AUTO-SCALER] Increasing instance!");
		}
		if(averageProcessing<minProcessing){
            if(getNrActiveInstances()<=1)
                return;
			reduceInstances(ec2);
			System.out.println("[AUTO-SCALER] Reducing instance!");
		}


	}
	public int getNrActiveInstances(){
        int count = 0;
        for(String i: instances.keySet()){
            if(!instances.get(i).getScheduledShutdown())
                count++;
        }
        return count;
	}
	public void increaseInstances(AmazonEC2 ec2){
		ArrayList<String> machinesToShutDown = getMachinesScheduledToShutdown();
		if(machinesToShutDown.size()>0){
			instances.get(machinesToShutDown.get(0)).cancelShutDown();
			System.out.println("[AUTO-SCALER] Canceling shutdown machine id: "+machinesToShutDown.get(0));
			return;
		}
            RunInstancesRequest runInstancesRequest =
               new RunInstancesRequest();
		runInstancesRequest.withImageId("ami-d82b17bd")
                               .withInstanceType("t2.micro")
                               .withMinCount(1)
                               .withMaxCount(1)
                               .withKeyName("CNV-lab-AWS")
                               .withSecurityGroups("CNV-ssh+http");
            RunInstancesResult runInstancesResult =
               ec2.runInstances(runInstancesRequest);
             Instance instance = runInstancesResult.getReservation().getInstances()
                                      .get(0);
            System.out.println("[AUTO-SCALER] Machine running id: "+instance.getInstanceId());
}

	public ArrayList<String> getMachinesScheduledToShutdown(){
		ArrayList<String> ids = new ArrayList<String>();
		for(Map.Entry<String, InstanceInfo> i : instances.entrySet()){
			if(i.getValue().getScheduledShutdown()){
				ids.add(i.getKey());
			}
		}
		return ids;
	}

	public void reduceInstances(AmazonEC2 ec2){
		String id=getInstanceIdLowWork();
		instances.get(id).setScheduledShutdown(true);
        System.out.println("[AUTO-SCALER] Machine scheduled to shutdown id: "+instances.get(id));
	}
	public String getInstanceIdLowWork(){
		double min=99999999;
		String inst = null;
		for(Map.Entry<String, InstanceInfo> i : instances.entrySet()){
			if(i.getValue().getCurrentProcessing()<min && !i.getValue().getScheduledShutdown()){
				min=i.getValue().getCurrentProcessing();
				inst=i.getKey();
			}
		}
		return inst;
	}

	public double calculateAverageProcessing(){
		long count=0;
		int nrEntries=0;
		for(Map.Entry<String, InstanceInfo> i : instances.entrySet()){
			if(!i.getValue().getScheduledShutdown()){
				count+=i.getValue().getCurrentProcessing();
				nrEntries++;
			}
		}
		if(nrEntries==0)
			return -1;
        System.out.println("[AUTO-SCALER] averageProcessing: "+count/nrEntries);
		return count/nrEntries;
	}

	public void terminateInstance(String id, AmazonEC2 ec2){
		TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
            termInstanceReq.withInstanceIds(id);
            ec2.terminateInstances(termInstanceReq);
        System.out.println("[AUTO-SCALER] Machine terminated id: "+id);
	}
	public void updateInstances(Set<Instance> updatedInstances, AmazonEC2 ec2){

		Set<String> updatedInstancesIds = new HashSet<String>();
        
		for(Instance i : updatedInstances){
			updatedInstancesIds.add(i.getInstanceId());
			
		}
        ArrayList<String> idsToRemove=new ArrayList<String>();
		//Check Removed Instances
		for(String id : instances.keySet()){
			if(!updatedInstancesIds.contains(id)){
				System.out.println("[LOAD BALANCER] Instance " + id + " removed.");
				idsToRemove.add(id);
			}
		}
		for(String id: idsToRemove)
				instances.remove(id);

		//Check Added Instances
		for(Instance i : updatedInstances){
			if(!instances.containsKey(i.getInstanceId())){
				System.out.println("[LOAD BALANCER] Instance " + i.getInstanceId() + " added.");
				InstanceInfo newInstance = new InstanceInfo(i);
				instances.put(i.getInstanceId(), newInstance);
			}
		}
		for(String id : instances.keySet()){
            if(instances.get(id).getScheduledShutdown()){
                    System.out.println("Instance " + id + " will shutdown");
                    if(instances.get(id).getCurrentProcessing()<=0){
                        if(instances.get(id).getScheduledShutdownCycles()==CYCLES)
                            terminateInstance(id,ec2);
                        else
                            instances.get(id).incrScheduledShutdownCycles();
                    }
                }
			}

	}


	
}
