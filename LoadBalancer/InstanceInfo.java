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

public class InstanceInfo {
	private double currentProcessing = 0; 
	private boolean scheduledShutdown = false;
	private int scheduledShutdownCycles = 0; 
	
	private Instance instance;

	public InstanceInfo(Instance i){
		instance = i;
	}

	public Instance getInstance(){
		return instance;
	}
	public double getCurrentProcessing(){
		return currentProcessing;
	}
	public void addCurrentProcessing(double i){
		this.currentProcessing+=i;
	}
	public void subtractCurrentProcessing(double i){
		this.currentProcessing-=i;
	}
	public boolean getScheduledShutdown(){
		return scheduledShutdown;
	}
	public void setScheduledShutdown(boolean i){
		this.scheduledShutdown=i;
	}
	public int getScheduledShutdownCycles(){
		return scheduledShutdownCycles;
	}
	public void incrScheduledShutdownCycles(){
		this.scheduledShutdownCycles++;
	}
	public void cancelShutDown(){
		scheduledShutdown=false;
		scheduledShutdownCycles=0;
	}
	
	public void setCurrentProcessing(double p){
        currentProcessing = p;
	}
	
}
