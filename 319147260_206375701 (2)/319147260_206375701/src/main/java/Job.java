import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

public class Job {
    String inputFileName;
    String outputFileName;
    HashMap<Integer, String> messageMap;
    String[] reviews;
    int counter;
    String bucket;
    S3 s3;
    int jobNum;

    public Job(String inputFileName, String outputFileName, S3 s3real, String bucketName){
        this.inputFileName = inputFileName;
        this.outputFileName = outputFileName;
        this.s3 = s3real;
        this.messageMap = new HashMap<>();
        this.s3.downloadFileAsStream(bucketName, inputFileName, messageMap);
        this.reviews = new String[messageMap.size()];
        this.counter = 0;
        this.bucket = bucketName;
    }

    public HashMap<Integer, String> getHash() {
        return messageMap;
    }

    public String getOutput() {
        return outputFileName != null ? outputFileName : "";
    }

    public String getArr(String blockNumber) {
        int spot = Integer.parseInt(blockNumber);
        return reviews[spot];
    }

    public boolean updateHandeledReview(String blockNumber, String text) {
        int spot = Integer.parseInt(blockNumber);
        if (spot >= 0 && spot < reviews.length) {
            reviews[spot] = text;
            counter++;
            if (checkIfJobIsDone()) {
                try {
                    String end = writeToFile();
                    s3.uploadFileToBucket(bucket, outputFileName, end);
                } catch (IOException e) {
                    System.out.println("Upload failed");
                }
                return true;
            }
        } else {
            System.out.println("Invalid block number: " + blockNumber);
        }
        return false;
    }

    public boolean checkIfJobIsDone() {
        return counter == reviews.length;
    }

    public String writeToFile() throws IOException {
        StringBuilder builder = new StringBuilder();
        for (String review : reviews) {
            builder.append(review);
        }
        String filePath = outputFileName + ".txt";
        File file = new File(filePath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(builder.toString());
        }
        return filePath;
    }
}
