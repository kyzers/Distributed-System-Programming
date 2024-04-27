import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;


public class SQS {
    public static String getQueueUrl(SqsClient sqsClient, String queueName) {
        String queueUrl = getQueueByName(sqsClient, queueName);
        if (queueUrl == null) {
            queueUrl = createQueue(sqsClient, queueName);
        }
        return queueUrl;
    }

    public static String getQueueByName(SqsClient sqsClient, String queueName) { // returns a queueURL
        ListQueuesRequest filterListRequest = ListQueuesRequest.builder().queueNamePrefix(queueName).build();
        ListQueuesResponse listQueuesFilteredResponse = sqsClient.listQueues(filterListRequest);
        if (listQueuesFilteredResponse.hasQueueUrls()) {
            return listQueuesFilteredResponse.queueUrls().get(0);
        } else {
            return null;
        }
    }

    public static String createQueue(SqsClient sqsClient, String queueName) {
        try {
            System.out.println("\nCreate Queue");
            CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                    .queueName(queueName)
                    .build();
            sqsClient.createQueue(createQueueRequest);
            System.out.println("\nGet queue url");
            GetQueueUrlResponse getQueueUrlResponse = sqsClient
                    .getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());
            String queueUrl = getQueueUrlResponse.queueUrl();
            return queueUrl;
        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return "";
    }


    public static void sendMessage(SqsClient sqsClient, String queueUrl, String messageBody) {
        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build());
        } catch (SqsException e) {
            System.err.println("Error sending message: " + e.awsErrorDetails().errorMessage());
        }
    }

    public static ReceiveMessageResponse receiveMessages(SqsClient sqsClient, String queueUrl, int maxMessages,
            int waitTimeSeconds) {
        try {
            ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(maxMessages)
                    .waitTimeSeconds(waitTimeSeconds)
                    .build());
            return response;
        } catch (SqsException e) {
            System.err.println("Error receiving messages: " + e.awsErrorDetails().errorMessage());
            return null;
        }
    }

    public static void deleteMessage(SqsClient sqsClient, String queueUrl, String receiptHandle) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build();
        sqsClient.deleteMessage(deleteRequest);
    }

}
