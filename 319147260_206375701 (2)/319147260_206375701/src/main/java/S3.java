import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class S3 {
    private static S3Client s3Client;

    public S3() {
        Region region = Region.US_WEST_2;
        s3Client = S3Client.builder().region(region).build();
    }

    public void downloadFileAsStream(String bucketName, String key, HashMap<Integer, String> messageMap) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            ResponseInputStream<GetObjectResponse> objectResponse = s3Client.getObject(getObjectRequest);
            BufferedReader reader = new BufferedReader(new InputStreamReader(objectResponse));
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                messageMap.put(lineNumber, line);
                lineNumber++;
            }

            reader.close();
        } catch (SdkException | IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public void uploadFileToBucket(String bucketName, String key, String filePath) {
        try {
            // Create a File object
            File file = new File(filePath);

            // Create a PutObjectRequest with the file
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            // Upload the file
            s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
        } catch (SdkException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    public static List<String> getFileNamesInBucket(String bucketName) {
        ListObjectsV2Request listObjectsReq = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();
        ListObjectsV2Response listObjectsResp = s3Client.listObjectsV2(listObjectsReq);
        List<String> fileNames = new ArrayList<>();
        for (S3Object s3Object : listObjectsResp.contents()) {
            fileNames.add(s3Object.key());
        }
        return fileNames;
    }
    
    public static void main(String[] args) {
        cleanUp();
    }

        public static void cleanUp() {
        String localToManager = "localToManagerQueuebyTamerAndShiraz1";
        String managerToLocal = "managerToLocalQueuebyTamerAndShiraz1";
        String workerToManager = "workerToManagerQueuebyTamerAndShiraz";
        String managerToWorker = "managerToWorkerQueuebyTamerAndShiraz";

        String workerTomanagerbackup = "managerToWorkerbackupQueuebyTamerAndShiraz1";



        // Set up SQS client
        SqsClient sqsClient = SqsClient.builder().region(Region.US_WEST_2).build();
        S3Client s3Client = S3Client.builder().region(Region.US_WEST_2).build();
        // Purge each queue
        deleteQueue(sqsClient, localToManager);
        deleteQueue(sqsClient, managerToLocal);
        deleteQueue(sqsClient, workerToManager);
        deleteQueue(sqsClient, managerToWorker);
        deleteQueue(sqsClient, workerTomanagerbackup);
        
        cleanUpS3Bucket(s3Client,"tamerandshirazbucketbgu");
        // Close the SQS client connection
        sqsClient.close();
        s3Client.close();
    }
    
private static void deleteQueue(SqsClient sqsClient, String queueName) {
    try {
        // Get the queue URL
        String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
        
        // Delete the queue
        sqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl).build());
        
        System.out.println("Successfully deleted queue: " + queueName);
    } catch (SqsException e) {
        System.err.println("Error deleting queue " + queueName + ": " + e.awsErrorDetails().errorMessage());
    }
}
    
    public static void cleanUpS3Bucket(S3Client s3Client, String bucketName) {
        ListObjectsV2Request listReq = ListObjectsV2Request.builder().bucket(bucketName).build();
        ListObjectsV2Response listRes;
        do {
            listRes = s3Client.listObjectsV2(listReq);
            for (S3Object s3Object : listRes.contents()) {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Object.key())
                        .build());
                System.out.println("Deleted object: " + s3Object.key());
            }
            listReq = listReq.toBuilder().continuationToken(listRes.nextContinuationToken()).build();
        } while (listRes.isTruncated());
        try {
            s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
            System.out.println("Bucket deleted: " + bucketName);
        } catch (Exception e) {
            System.err.println("Error deleting bucket " + bucketName + ": " + e.getMessage());
        }
    }

}