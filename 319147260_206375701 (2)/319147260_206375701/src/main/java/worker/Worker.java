package worker;

import java.util.concurrent.TimeUnit;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

public class Worker {

    public static String managerToworkerURL = null;
    public static String workerToManagerURL = null;


    public static void main(String[] args) throws InterruptedException {
        SqsClient sqsClient = SqsClient.builder().region(Region.US_WEST_2).build();
        String workerToManager = "workerToManagerQueuebyTamerAndShiraz";
        String managerToWorker = "managerToWorkerQueuebyTamerAndShiraz";

        while (workerToManagerURL == null
                || managerToworkerURL == null ) {
            System.out.println("Worker couldn't find queues. Sleeping for 5 secs");
            TimeUnit.SECONDS.sleep(3);
            workerToManagerURL = workerSQS.getQueueByName(sqsClient, workerToManager);
            managerToworkerURL = workerSQS.getQueueByName(sqsClient, managerToWorker);
        }
        processMessages(sqsClient);
    }


    public static void processMessages(SqsClient sqsClient) {
        while (true) {
            try {
                ReceiveMessageResponse receiveMessageResponse = workerSQS.receiveMessage(sqsClient, managerToworkerURL);
                for (Message message : receiveMessageResponse.messages()) {
                    try {
  
                        System.out.println("Processing message: " + message.body());
                        String processedMessageBody = ReviewProcessor.processReviews(message.body());
                        workerSQS.sendMessage(sqsClient, workerToManagerURL, processedMessageBody);
  
                        workerSQS.deleteMessage(sqsClient, managerToworkerURL, message.receiptHandle());
                        Thread.sleep(500); 

                    } catch (InterruptedException e) {
                        System.err.println("Processing interrupted. Making message available again.");

                        workerSQS.changeVisibility(sqsClient, managerToworkerURL, message.receiptHandle(), 0);
                    }
                }
            } catch (SqsException e) {
                System.err.println("Error receiving messages: " + e.awsErrorDetails().errorMessage());
            }
        }
    }

}
