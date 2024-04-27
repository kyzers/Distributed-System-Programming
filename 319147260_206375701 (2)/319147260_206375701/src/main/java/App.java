import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.services.sqs.model.Message;

public class App {
    final static AWS aws = AWS.getInstance();

    static String outputURL = "";
    static String inputURL = "";
    static String[] createJobsMassage;
    static String[] inputs;
    static String[] outputs;
    static int inputsSize;
    static boolean terminate;
    static String managerId;

    public static void main(String[] args) {
        int inputSize;
        String n;
        if (args.length < 3) {
            System.out.println("Not enough args!\n");
            return;
        }
        if ((args[args.length - 1]).equals("terminate")) {
            terminate = true;
            inputSize = (args.length - 2) / 2;
            n = args[args.length - 2];
        } else {
            terminate = false;
            inputSize = (args.length - 1) / 2;
            n = args[args.length - 1];
        }

        inputs = new String[inputSize];
        outputs = new String[inputSize];
        createJobsMassage = new String[inputSize];
        inputsSize = inputs.length;
        for (int i = 0; i < inputSize; i++) {
            inputs[i] = args[i];
            outputs[i] = args[i + inputSize];
            createJobsMassage[i] = inputs[i] + ".txt" + "spliter" + outputs[i] + ".txt" + "spliter" + n;

        }
        if (inputs.length != outputs.length) {
            System.out.println("Number on input files not equal to num of output files\n");
            return;
        }

        try {
            setup();
            for (int i = 0; i < inputs.length; i++) {
                String fileName = inputs[i].endsWith(".txt") ? inputs[i] : inputs[i] + ".txt";
                String filePath = getFilePathOrTerminate(fileName);
                uploadFile(fileName, filePath);
            }
            createEC2();
            createSQS();
            sendJobsAndListenForMessages();

        } catch (Exception e) {
            System.err.println("An error occurred in the main method:");
            e.printStackTrace();
        }
    }

    // Create Buckets, Create Queues, Upload JARs to S3
    private static void setup() {
        aws.createBucketIfNotExists(aws.bucketName);
    }

    private static void createEC2() {
        String ec2Script = "#!/bin/bash\n" +
                "aws s3 cp s3://tamerandshirazbucketbgu/project-1-jar-with-dependencies.jar /home/ec2-user/project-1-jar-with-dependencies.jar\n"
                +
                "java -cp /home/ec2-user/project-1-jar-with-dependencies.jar manager\n";
        aws.createEC2(ec2Script, "manager", 1);
    }

    private static void uploadFile(String s3Key, String filePath) {
        System.out.println("[DEBUG] Uploading file: "+s3Key+" to S3.\n");
        aws.uploadFileToS3(aws.bucketName, s3Key, filePath);
    }

    private static void createSQS() {
        outputURL = aws.waitForQueue("localToManagerQueuebyTamerAndShiraz1");
        inputURL = aws.waitForQueue("managerToLocalQueuebyTamerAndShiraz1");
    }

    private static void sendJobsAndListenForMessages() {
        // send jobs to manager
        for (int i = 0; i < createJobsMassage.length; i++) {
            aws.sendMessageToQueue(outputURL, createJobsMassage[i]);
        }
        // send terminate
        if (terminate) {
            aws.sendMessageToQueue(outputURL, "terminate");
        }
        boolean running = true;
        System.out.println("Listening for messages on " + inputURL + "\n");
        while (running) {
            List<Message> messages = aws.receiveMessagesFromQueue(inputURL);
            for (Message message : messages) {
                // waiting to job complete massage
                String[] parts = message.body().split("spliter");
                if (parts.length == 2) { // Check for correctly formatted messages
                    String inputPart = parts[0].replace(".txt", "");
                    String outputPart = parts[1].replace(".txt", "");
                    boolean matchFound = false;
                    // check if the complete job is for this local
                    for (int i = 0; i < inputs.length; i++) {
                        if (inputs[i].equals(inputPart) && outputs[i].equals(outputPart)) {
                            // download to massage map the txt file from s3
                            HashMap<Integer, String> messageMap = new HashMap<>();
                            aws.downloadFileAsStream(outputPart + ".txt", messageMap);
                            // create final html file
                            createHTML(messageMap, outputPart + ".txt");
                            matchFound = true; // A matching pair is found and processed
                            inputsSize -= 1;
                            break; // Exit the loop as we've found a match
                        }
                    }
                    if (!matchFound) {
                        System.out.println("No matching input-output pair found for message: " + message.body());
                        // dont delete the massage because it intended for another local
                    } else {

                        aws.deleteMessageFromQueue(inputURL, message.receiptHandle());
                        // if all job done so terminate
                        if (inputsSize == 0) {
                            running = false;
                        }
                    }
                } else {
                    System.out.println("\nmessage recived: " + message.body());
                    aws.deleteMessageFromQueue(inputURL, message.receiptHandle());
                }
            }
            // check if manager crashed
            if (aws.checkExistingInstanceByTag("manager") == "") {
                createEC2();
                if (terminate) {
                    aws.sendMessageToQueue(outputURL, "terminate");
                }
            }
        }
    }

    private static void createHTML(HashMap<Integer, String> messageMap, String outputName) {
        StringBuilder htmlContent = new StringBuilder("<html><head><title>Reviews</title></head><body>");
        Iterator<Map.Entry<Integer, String>> iterator = messageMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, String> entry = iterator.next();
            htmlContent.append(entry.getValue());
        }
        htmlContent.append("</body></html>");
        String filePath = "C:\\Users\\Tamer Abu\\Desktop\\examples\\output\\" + outputName + ".html";
        File directory = new File(filePath).getParentFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, false))) {
            writer.write(htmlContent.toString());
        } catch (IOException e) {
            System.err.println("Error writing HTML content to file: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
        System.out.println("HTML content written to file successfully.\n");
    }

    public static String getFilePathOrTerminate(String fileName) {
        String folderPath = "C:\\Users\\Tamer Abu\\Desktop\\examples\\inputs";
        String filePath = folderPath + File.separator + fileName;
        File f = new File(filePath);
        if (!f.exists()) {
            System.out.printf("%s was not found in %s!\n", fileName, folderPath);
            System.exit(0);
        }
        return filePath;
    }

}
