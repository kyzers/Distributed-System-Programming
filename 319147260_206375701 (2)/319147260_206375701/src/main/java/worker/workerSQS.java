package worker;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

public class workerSQS {
    // finding by name the sqs
    public static String getQueueByName(SqsClient sqsClient, String queueName) { // returns a queueURL
        ListQueuesRequest filterListRequest = ListQueuesRequest.builder().queueNamePrefix(queueName).build();
        ListQueuesResponse listQueuesFilteredResponse = sqsClient.listQueues(filterListRequest);
        if (listQueuesFilteredResponse.hasQueueUrls()) {
            return listQueuesFilteredResponse.queueUrls().get(0);
        } else {
            return null;
        }
    }
    // receive messages from sqs
    public static ReceiveMessageResponse receiveMessage(SqsClient sqsClient, String queueUrl) {
        return sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10) // Process 10 messages at a time
                .visibilityTimeout(100) // Adjust based on expected processing time
                .waitTimeSeconds(6) // Enable long polling
                .build());
    }
    //delete message from sqs
public static void deleteMessage(SqsClient sqsClient, String queueUrl, String receiptHandle) {
    try {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build());
        System.out.println("Message deleted: " + receiptHandle); // Optional: log the deletion
    } catch (SqsException e) {
        System.err.println("Error deleting message: " + e.awsErrorDetails().errorMessage());
    }
}

    //makes massage visible again in the sqs
    public static void changeVisibility(SqsClient sqsClient, String queueUrl, String receiptHandle, int timeout) {
        try {
            ChangeMessageVisibilityRequest visibilityRequest = ChangeMessageVisibilityRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .visibilityTimeout(timeout)
                    .build();
            sqsClient.changeMessageVisibility(visibilityRequest);
        } catch (SqsException e) {
            System.err.println("Error changing message visibility: " + e.awsErrorDetails().errorMessage());
        }
    }
    //send massage to sqs
    public static void sendMessage(SqsClient sqsClient, String queueUrl, String messageBody) {
        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build());
            System.out.println("Message sent to " + queueUrl + ": " + messageBody);
        } catch (Exception e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

}
