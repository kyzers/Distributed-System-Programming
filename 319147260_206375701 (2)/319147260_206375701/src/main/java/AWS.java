import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.KeyType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AWS {
    private static S3Client s3;
    final SqsClient sqs;
    private final Ec2Client ec2;

    public static String ami = "ami-00e95a9222311e8ed";

    public static Region region1 = Region.US_WEST_2;
    public static Region region2 = Region.US_EAST_1;
    private final DynamoDbClient dynamoDb;

    private static final String LOCK_TABLE_NAME = "YourLockTableName";
    private static final String LOCK_KEY = "LockKey";
    private static final String LOCK_VALUE = "ManagerCreationLock";
    private static final long LOCK_LEASE_DURATION = 600;

    private static final AWS instance = new AWS();

    private AWS() {
        s3 = S3Client.builder().region(region1).build();
        sqs = SqsClient.builder().region(region1).build();
        ec2 = Ec2Client.builder().region(region2).build();
        dynamoDb = DynamoDbClient.builder().region(region2).build();
        ensureDynamoDBTableExists();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook triggered, releasing lock if held...");
            releaseLock();
        }));
    }

    public static AWS getInstance() {
        return instance;
    }

    public String bucketName = "tamerandshirazbucketbgu";

    // S3
    public void createBucketIfNotExists(String bucketName) {
        try {
            s3.createBucket(CreateBucketRequest
                    .builder()
                    .bucket(bucketName)
                    .createBucketConfiguration(
                            CreateBucketConfiguration.builder()
                                    .locationConstraint(BucketLocationConstraint.US_WEST_2)
                                    .build())
                    .build());
            s3.waiter().waitUntilBucketExists(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
        } catch (S3Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void uploadFileToS3(String bucketName, String key, String filePath) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3.putObject(putObjectRequest, RequestBody.fromFile(Paths.get(filePath)));
            System.out.printf("[DEBUG] Successfully uploaded '%s' to S3 bucket '%s'\n", filePath, bucketName);
        } catch (Exception e) {
            System.err.println("[ERROR] Unable to upload file to S3: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    public void downloadFileAsStream(String fileName, HashMap<Integer, String> messageMap) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            ResponseInputStream<GetObjectResponse> objectResponse = s3.getObject(getObjectRequest);
            BufferedReader reader = new BufferedReader(new InputStreamReader(objectResponse));
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                messageMap.put(lineNumber, line);
                lineNumber++;
            }

            reader.close();
        } catch (SdkException | IOException e) {
            System.err.println("Error: " + e.getMessage() + "\n");
        }
    }

    // EC2
    public void createEC2(String script, String tagName, int numberOfInstances) {
        if (!acquireLock()) {
            System.out.println("Another instance is currently creating the manager. Exiting.");
            return;
        }
        try {
            // Check if an instance with the specified tag name already exists
            String existingInstanceId = checkExistingInstanceByTag(tagName);
            if (!existingInstanceId.isEmpty()) {
                System.out.printf("[DEBUG] EC2 instance with tag name '%s' already exists: %s\n", tagName,
                        existingInstanceId);
                return; // Return the existing instance ID
            }

            String s3Key = "project-1-jar-with-dependencies.jar"; // The key (path) under which the file should
            if (!checkFileExistsInBucket(bucketName, s3Key)) {
                // if jar is there no need to upload again
                String localFilePath = "C:\\Users\\Tamer Abu\\Desktop\\examples\\target\\project-1-jar-with-dependencies.jar";
                System.out.println("\n[DEBUG] Uploading Jar file to S3.");
                uploadFileToS3(bucketName, s3Key, localFilePath);
            }
            Ec2Client ec2 = Ec2Client.builder().region(region2).build();
            RunInstancesRequest runRequest = (RunInstancesRequest) RunInstancesRequest.builder()
                    .instanceType(InstanceType.M4_LARGE)
                    .imageId(ami)
                    .maxCount(numberOfInstances)
                    .minCount(1)
                    .keyName("vockey")
                    .iamInstanceProfile(IamInstanceProfileSpecification.builder().name("LabInstanceProfile").build())
                    .userData(Base64.getEncoder().encodeToString((script).getBytes()))
                    .build();

            RunInstancesResponse response = ec2.runInstances(runRequest);

            String instanceId = response.instances().get(0).instanceId();

            software.amazon.awssdk.services.ec2.model.Tag tag = Tag.builder()
                    .key("Name")
                    .value(tagName)
                    .build();

            CreateTagsRequest tagRequest = (CreateTagsRequest) CreateTagsRequest.builder()
                    .resources(instanceId)
                    .tags(tag)
                    .build();

            try {
                ec2.createTags(tagRequest);
                System.out.printf(
                        "[DEBUG] Successfully started EC2 instance %s based on AMI %s\n",
                        instanceId, ami);

            } catch (Ec2Exception e) {
                System.err.println("[ERROR] " + e.getMessage());
                System.exit(1);
            }
        } finally {
            releaseLock(); // Ensure the lock is always released
        }
    }

    public String checkExistingInstanceByTag(String tagName) {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(Filter.builder()
                        .name("tag:Name")
                        .values(tagName)
                        .build(),
                        Filter.builder()
                                .name("instance-state-name")
                                .values("running", "pending")
                                .build())
                .build();
        DescribeInstancesResponse response = ec2.describeInstances(request);

        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                return instance.instanceId(); // Return the first found instance ID
            }
        }
        return ""; // Return empty if no matching instance is found
    }

    // SQS
    public String waitForQueue(String queueName) {
        try {
            System.out.printf("Waiting for %s... This might take a while...\n", queueName);
            String name = getQueueByName(queueName);

            while (name == null) { // Loop indefinitely until the queue is found
                System.out.println("Queue not found, checking again...");
                TimeUnit.SECONDS.sleep(1); // Wait for 1 second before checking again
                name = getQueueByName(queueName);
            }

            System.out.printf("%s is on!\n", queueName);
            return name;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Interrupted while waiting for queue.");
            return null;
        }
    }

    public String getQueueByName(String queueName) { // returns a queueURL
        ListQueuesRequest filterListRequest = ListQueuesRequest.builder()
                .queueNamePrefix(queueName).build();
        ListQueuesResponse listQueuesFilteredResponse = sqs.listQueues(filterListRequest);
        if (listQueuesFilteredResponse.hasQueueUrls()) {
            return listQueuesFilteredResponse.queueUrls().get(0);
        } else {
            return null;
        }
    }

    public void sendMessageToQueue(String queueUrl, String messageBody) {
        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build();

        sqs.sendMessage(sendMsgRequest);
        System.out.println("Message sent to SQS queue: " + messageBody + "\n");
    }

    public List<Message> receiveMessagesFromQueue(String queueUrl) {
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(10) 
                .build();

        return sqs.receiveMessage(receiveRequest).messages();
    }

    public void deleteMessageFromQueue(String queueUrl, String receiptHandle) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build();

        sqs.deleteMessage(deleteRequest);
    }

    public static boolean checkFileExistsInBucket(String bucketName, String fileNameToCheck) {
        try {

            s3.headObject(HeadObjectRequest.builder().bucket(bucketName).key(fileNameToCheck).build());
            return true;
        } catch (S3Exception e) {
            return false;
        }
    }

    // DynamoDB lok ensure that only one local create manager if crashes
    public boolean acquireLock() {
        long currentTime = System.currentTimeMillis() / 1000L;
        long ttl = currentTime + LOCK_LEASE_DURATION; // LOCK_LEASE_DURATION in seconds
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(LOCK_KEY, AttributeValue.builder().s(LOCK_VALUE).build());
        item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());
    
        try {
            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(LOCK_TABLE_NAME)
                    .item(item)
                    .conditionExpression("attribute_not_exists(" + LOCK_KEY + ") OR #ttl < :current_time")
                    .expressionAttributeNames(Collections.singletonMap("#ttl", "ttl")) // Alias for reserved keyword
                    .expressionAttributeValues(Collections.singletonMap(":current_time", AttributeValue.builder().n(String.valueOf(currentTime)).build()))
                    .build());
            return true; // Lock acquired or existing lock was stale and overwritten
        } catch (ConditionalCheckFailedException e) {
            return false; // Lock already exists and is we cant reach it
        }
    }
    

    public void releaseLock() {
        dynamoDb.deleteItem(DeleteItemRequest.builder()
                .tableName(LOCK_TABLE_NAME)
                .key(Collections.singletonMap(LOCK_KEY, AttributeValue.builder().s(LOCK_VALUE).build()))
                .build());
    }

    private void ensureDynamoDBTableExists() {
        try {
            dynamoDb.describeTable(DescribeTableRequest.builder()
                    .tableName(LOCK_TABLE_NAME)
                    .build());
            System.out.println("DynamoDB table exists: " + LOCK_TABLE_NAME);
        } catch (ResourceNotFoundException e) {
            System.out.println("DynamoDB table not found, creating: " + LOCK_TABLE_NAME);
            createDynamoDBTable();
        }
    }

    private void createDynamoDBTable() {
        try {
            dynamoDb.createTable(CreateTableRequest.builder()
                    .tableName(LOCK_TABLE_NAME)
                    .keySchema(KeySchemaElement.builder()
                            .attributeName(LOCK_KEY)
                            .keyType(KeyType.HASH)
                            .build())
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName(LOCK_KEY)
                            .attributeType(ScalarAttributeType.S)
                            .build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
            // Wait for the table to become active
            dynamoDb.waiter().waitUntilTableExists(DescribeTableRequest.builder()
                    .tableName(LOCK_TABLE_NAME)
                    .build());
            System.out.println("DynamoDB table created: " + LOCK_TABLE_NAME);
        } catch (DynamoDbException e) {
            System.err.println("Failed to create DynamoDB table: " + e.getMessage());
        }
    }

}
