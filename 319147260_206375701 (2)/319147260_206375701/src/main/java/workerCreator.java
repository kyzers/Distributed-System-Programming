import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class workerCreator {
    public static String ami = "ami-00e95a9222311e8ed";
    public static Region region = Region.US_EAST_1;
    private static final workerCreator instance = new workerCreator();
    private Ec2Client ec2;
    private List<String> runningWorkerInstanceIds;

    private workerCreator() {
        this.ec2 = Ec2Client.builder().region(region).build();
        this.runningWorkerInstanceIds = new ArrayList<>();
        discoverExistingWorkers();
    }

    public static workerCreator getInstance() {
        return instance;
    }

    public void createOrDeleteWorkers(int hashSize, int n) {
        String tagName = "worker";

        String ec2Script = "#!/bin/bash\n" +
                "aws s3 cp s3://tamerandshirazbucketbgu/project-1-jar-with-dependencies.jar /home/ec2-user/project-1-jar-with-dependencies.jar\n"
                +
                "java -cp /home/ec2-user/project-1-jar-with-dependencies.jar worker.Worker\n";

        int numWorkersNeeded = Math.min(hashSize / n, 7);

        if (hashSize == 0) {
            deleteWorkers(runningWorkerInstanceIds.size());
        }

        if (runningWorkerInstanceIds.size() >= numWorkersNeeded) {
            System.out.println("There are already enough running worker instances for hashSize " + hashSize
                    + ". Not launching more.");
        } else {
            int workersToLaunch = numWorkersNeeded - runningWorkerInstanceIds.size();
            for (int i = 0; i < workersToLaunch; i++) {
                String workerInstanceId = createEC2(ec2Script, tagName, 1);
                runningWorkerInstanceIds.add(workerInstanceId);
            }
        }

        if (hashSize / n < runningWorkerInstanceIds.size()) {
            int numWorkersToDelete = (runningWorkerInstanceIds.size() - hashSize / n);
            deleteWorkers(numWorkersToDelete);
        }
    }

    public int getSumOfRunningWorker() {
        Filter tagFilter = Filter.builder()
                .name("tag:Name")
                .values("Worker")
                .build();

        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(tagFilter)
                .build();

        int count = 0;
        DescribeInstancesResponse response = ec2.describeInstances(request);
        for (Reservation reservation : response.reservations()) {
            count += reservation.instances().size();
        }
        return count;
    }

    public String createEC2(String script, String tagName, int numberOfInstances) {
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.M4_LARGE)
                .imageId(ami)
                .maxCount(numberOfInstances)
                .minCount(1)
                .keyName("vockey")
                .iamInstanceProfile(IamInstanceProfileSpecification.builder().name("LabInstanceProfile").build())
                .userData(Base64.getEncoder().encodeToString(script.getBytes()))
                .build();

        RunInstancesResponse response = ec2.runInstances(runRequest);

        String instanceId = response.instances().get(0).instanceId();

        Tag tag = Tag.builder()
                .key("Name")
                .value(tagName)
                .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();

        ec2.createTags(tagRequest);
        System.out.printf(
                "[DEBUG] Successfully started EC2 instance %s with tag %s\n",
                instanceId, tagName);

        return instanceId;
    }

    public void deleteWorkers(int numWorkersToDelete) {
        if (runningWorkerInstanceIds.isEmpty()) {
            System.out.println("There are no more workers to delete.");
            return;
        }
        numWorkersToDelete = Math.min(numWorkersToDelete, runningWorkerInstanceIds.size());
        for (int i = 0; i < numWorkersToDelete; i++) {
            String workerInstanceId = runningWorkerInstanceIds.get(0);
            terminateEC2(workerInstanceId);
            runningWorkerInstanceIds.remove(workerInstanceId);
        }
    }

    private void discoverExistingWorkers() {
        Filter tagFilter = Filter.builder()
                .name("tag:Name")
                .values("worker") 
                .build();
        
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(tagFilter)
                .build();
        
        DescribeInstancesResponse response = ec2.describeInstances(request);
        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                if (InstanceStateName.RUNNING.equals(instance.state().name())) {
                    runningWorkerInstanceIds.add(instance.instanceId());
                }
            }
        }
    }

    public void terminateEC2(String instanceId) {
        TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
                .instanceIds(instanceId)
                .build();

        ec2.terminateInstances(terminateRequest);
        System.out.printf(
                "[DEBUG] Terminated EC2 instance %s\n", instanceId);
    }
}
