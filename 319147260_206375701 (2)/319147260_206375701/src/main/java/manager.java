import java.util.concurrent.TimeUnit;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class manager {
        public static String bucketName = "tamerandshirazbucketbgu";

        public static String localToManagerURL = null;
        public static String managerToLocalURL = null;

        public static String managerToWorkerURL = null;
        public static String workerToManagerURL = null;

        public static String workerToManagerbackupURL = null;

        public static HashMap<Integer, String> messageMap = new HashMap<>();
        public static HashMap<String, Job> jobMap = new HashMap<>();

        public static int jobToPreform = -1;
        public static boolean terminate = false;
        public static Integer n = 10;

        public static workerCreator workerCreatorInstance = workerCreator.getInstance();
        private static Object lock = new Object();

        public static void main(String[] args) throws InterruptedException, IOException {
                Region awsRegion = Region.US_WEST_2;

                String localToManager = "localToManagerQueuebyTamerAndShiraz1";
                String managerToLocal = "managerToLocalQueuebyTamerAndShiraz1";

                String workerToManager = "workerToManagerQueuebyTamerAndShiraz";
                String managerToWorker = "managerToWorkerQueuebyTamerAndShiraz";

                String workerTomanagerbackup = "managerToWorkerbackupQueuebyTamerAndShiraz1";

                System.out.println("Initializing Manager!");

                SqsClient sqsClient = SqsClient.builder().region(awsRegion).build();

                localToManagerURL = SQS.getQueueUrl(sqsClient, localToManager);
                managerToLocalURL = SQS.getQueueUrl(sqsClient, managerToLocal);
                workerToManagerURL = SQS.getQueueUrl(sqsClient, workerToManager);
                managerToWorkerURL = SQS.getQueueUrl(sqsClient, managerToWorker);
                workerToManagerbackupURL = SQS.getQueueUrl(sqsClient, workerTomanagerbackup);

                while (localToManagerURL == null || managerToLocalURL == null || workerToManagerURL == null
                                || managerToWorkerURL == null || workerToManagerbackupURL == null) {
                        System.out.println("Manager couldn't find queues. Sleeping for 5 secs");
                        TimeUnit.SECONDS.sleep(5);
                        localToManagerURL = SQS.getQueueByName(sqsClient, localToManager);
                        managerToLocalURL = SQS.getQueueByName(sqsClient, managerToLocal);
                        workerToManagerURL = SQS.getQueueByName(sqsClient, workerToManager);
                        managerToWorkerURL = SQS.getQueueByName(sqsClient, managerToWorker);
                        workerToManagerbackupURL = SQS.getQueueByName(sqsClient, workerTomanagerbackup);
                }
                S3 s3 = new S3();
                restoreBackup(sqsClient, s3);
                handleJobFromLocal(sqsClient, s3);
                listener(sqsClient);
                SQS.sendMessage(sqsClient, managerToLocalURL, "Manager is ready for work");
        }

        // send massage to local completed
        public static void listener(SqsClient sqsClient) {
                Thread listenerThread = new Thread(() -> {
                        boolean running = true;
                        while (running) {
                                ReceiveMessageResponse receiveMessageResponse = SQS.receiveMessages(sqsClient,
                                                workerToManagerURL, 10, 6);

                                for (Message message : receiveMessageResponse.messages()) {
                                        // split[0]=input ;split[1]=blockName ;split[2]=after prossessing reviews
                                        String[] split = message.body().split("spliter");
                                        // job[input]
                                        Job job = jobMap.get(split[0]);
                                        if (job != null) {
                                                // send to backup SQS
                                                SQS.sendMessage(sqsClient, workerToManagerbackupURL,
                                                                job.getOutput() + "spliter" + message.body());
                                                // for debuging
                                                SQS.sendMessage(sqsClient, managerToLocalURL,
                                                                "output: " + job.getOutput() + "file: " + split[0]
                                                                                + " line in file:" + split[1] + "\n");
                                                if (job.updateHandeledReview(split[1], split[2])) {
                                                        // send ready massage to local
                                                        SQS.sendMessage(sqsClient, managerToLocalURL,
                                                                        split[0] + "spliter" + job.getOutput());
                                                        // remove worker if needed or if its the last jub kill them all
                                                        synchronized (lock) {
                                                                jobToPreform -= job.getHash().size();
                                                                workerCreatorInstance
                                                                                .createOrDeleteWorkers(jobToPreform, n);
                                                        }
                                                }
                                                // no more job to be done
                                                if (terminate == true && jobToPreform == 0) {
                                                        running = false;
                                                        try {
                                                                Runtime.getRuntime().exec(
                                                                                "sudo shutdown -h now");
                                                        } catch (IOException e) {
                                                                e.printStackTrace();
                                                        }
                                                }
                                        }
                                        SQS.deleteMessage(sqsClient, workerToManagerURL, message.receiptHandle());
                                }
                        }
                });
                listenerThread.start();
        }

        public static void handleJobFromLocal(SqsClient sqsClient, S3 s3) {
                Thread senderThread = new Thread(() -> {
                        System.out.println("upload Job Thread!");
                        boolean running = true;
                        while (running) {
                                ReceiveMessageResponse receiveMessageResponse = SQS.receiveMessages(sqsClient,
                                                localToManagerURL, 10, 10);
                                for (Message message : receiveMessageResponse.messages()) {
                                        // content[0]=input ;content[1]=output ;content[2]=n
                                        String[] content = message.body().split("spliter");
                                        if (content.length == 3) {
                                                String inputFileName = content[0];
                                                String outputFileName = content[1];
                                                // create new job
                                                Job newJob = new Job(inputFileName, outputFileName, s3, bucketName);
                                                // put new job in hashmap
                                                if (newJob != null) {
                                                        jobMap.put(inputFileName, newJob);
                                                        synchronized (lock) {
                                                                // update the job needed to be prefomed
                                                                if (jobToPreform == -1) {
                                                                        jobToPreform = newJob.getHash().size();
                                                                } else {
                                                                        jobToPreform += newJob.getHash().size();
                                                                }
                                                                n = Integer.parseInt(content[2]);
                                                                // create workers to proosses the job
                                                                workerCreatorInstance
                                                                                .createOrDeleteWorkers(jobToPreform, n);
                                                        }
                                                        // upload each line from input.txt up workers to start working
                                                        Iterator<Map.Entry<Integer, String>> iterator = newJob.getHash()
                                                                        .entrySet().iterator();
                                                        while (iterator.hasNext()) {
                                                                Map.Entry<Integer, String> entry = iterator.next();
                                                                // create message for worker
                                                                String lineAndContent = inputFileName + "spliter"
                                                                                + entry.getKey() + "spliter"
                                                                                + entry.getValue();
                                                                SQS.sendMessage(sqsClient, managerToWorkerURL,
                                                                                lineAndContent);
                                                        }
                                                }
                                        } else {
                                                if (message.body().equals("terminate")) {
                                                        SQS.sendMessage(sqsClient, workerToManagerbackupURL,
                                                                        "terminate");
                                                        terminate = true;
                                                }
                                        }
                                        SQS.deleteMessage(sqsClient, localToManagerURL,
                                                        message.receiptHandle());
                                }
                        }

                });
                senderThread.start();
        }

        public static void restoreBackup(SqsClient sqsClient, S3 s3) {
                boolean isEmpty = false;
                while (!isEmpty) {
                        ReceiveMessageResponse receiveMessageResponse = SQS.receiveMessages(sqsClient,
                                        workerToManagerbackupURL, 10, 10);
                        if (receiveMessageResponse.messages().isEmpty()) {
                                // Quit if there no massages left in backup SQS
                                isEmpty = true;
                        } else {
                                for (Message message : receiveMessageResponse.messages()) {
                                        // split[0]=output split[1]=input split[2]= block number split[3]=after
                                        // prossesing reviews
                                        String[] split = message.body().split("spliter");
                                        List<String> fileNames = S3.getFileNamesInBucket(bucketName);
                                        // output wasnt found is s3 so we need to continue the prossessing;
                                        if (split.length == 4) {
                                                if (!fileNames.contains(split[0])) {
                                                        String inputFileName = split[1];
                                                        String outputFileName = split[0];
                                                        SQS.sendMessage(sqsClient, managerToLocalURL,
                                                                        "\n\nRESTORE: file: " + split[1] + " block num:"
                                                                                        + split[2]);
                                                        // create a new job
                                                        Job newJob = new Job(inputFileName, outputFileName, s3,
                                                                        bucketName);
                                                        if (jobToPreform == -1)
                                                                jobToPreform = newJob.getHash().size();
                                                        else
                                                                jobToPreform += newJob.getHash().size();

                                                        boolean finished = newJob.updateHandeledReview(split[2],
                                                                        split[3]);
                                                        jobMap.put(inputFileName, newJob);
                                                        if (finished) {
                                                                // send ready massage to local
                                                                SQS.sendMessage(sqsClient, managerToLocalURL,
                                                                                inputFileName + "spliter"
                                                                                                + outputFileName);
                                                                synchronized (lock) {
                                                                        jobToPreform -= newJob.getHash().size();
                                                                        // update number on workers needed
                                                                        workerCreatorInstance
                                                                                        .createOrDeleteWorkers(
                                                                                                        jobToPreform,
                                                                                                        n);
                                                                }
                                                                // all backup jobs have fineshed so shutdown
                                                        }
                                                }
                                        } else {
                                                if (message.body().equals("terminate")) {
                                                        terminate = true;
                                                }

                                        }
                                        SQS.deleteMessage(sqsClient, workerToManagerbackupURL, message.receiptHandle());
                                }
                        }
                }
        }
}