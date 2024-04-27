Distributed Application System
==============================

Team Members:
- Tamer Abu-Shakra, ID: 319147260
- Shiraz kijzer ,ID: 206375701

System Architecture:
--------------------
The system consists of three main components:
1. Local Application: Initiates the processing tasks by uploading input files to S3 and sending messages to SQS.
2. Manager: Coordinates the distribution of tasks to Worker nodes and aggregates results.
3. Worker: Processes individual tasks and returns the results to the Manager.


Setup Instructions:
-------------------
Compilation:
- To compile the source code into a JAR file, navigate to the project directory and run: `mvn clean install`


Configuration:
- Update the following configurations in App.java and AWS.java before running the application:
  - In `App.java`:
    - `createHTML`: Set `outputFolderPath` to your output directory path.
    - `getFilePathOrTerminate`: Set `inputFolderPath` to your input directory path.
  - In `AWS.java`:
    - `createEC2`: Update `jarFilePath` to the location of `project-1-jar-with-dependencies.jar`.


Execution:
- Run the application with the following command:
  `java -cp "path_to_jar" App input2 input3 output2 output3 10 [terminate]`
- Example:
  `java -cp "C:\\Users\\owner\\Desktop\\examples\\target\\project-1-jar-with-dependencies.jar" App input2 input3 output2 output3 10 [terminate]`
- Run S3 to delete all SQS and bucket if need on second run.


Application Overview:
---------------------
The application is structured around three main components that communicate via Amazon Simple Queue Service (SQS). These components are the local application, the manager, and the workers.

Local Application Workflow:
- Initial Setup (First Local Application Instance):
  - Uploads input files to Amazon S3.
  - Initiates the Manager EC2 instance. This step includes acquiring a lock to ensure that if multiple local applications are initiated simultaneously, only one instance of the Manager is created.
  - Uploads the necessary JAR file to the Manager.
  - Releases the lock once the Manager setup is complete.
- Subsequent Instances (Non-First Local Applications):
  - Checks if the Manager is already operational to avoid redundant uploads of the JAR file.
- Subsequent Instances (Non-First Local Applications):
  - Checks if the Manager is already operational to avoid redundant uploads of the JAR file.

- Common Steps for All Instances:
  - Sends a message to the input_output_n queue in SQS to signal the Manager that processing can begin. This includes sending a termination message if required.
  - Waits for the Manager to set up necessary SQS queues and to complete processing.
  - Retrieves responses from the Manager via SQS. If the Manager goes down, it attempts to restart it.
  - Downloads the relevant output file from S3 and converts it into HTML format, ensuring any acquired locks are released before shutting down.

Manager Workflow:
- Initializes five SQS queues for communication with the local applications and workers.
- Processes backup SQS messages, creating new jobs as needed. This is based on whether the output file exists in S3, ensuring that unprocessed or partially processed tasks are completed.
- Monitors messages from local applications on the input_output_n SQS queue.
- Upon receiving an input_output_n message, it creates a hash of job objects and prepares the jobs by downloading the corresponding text file from S3, reading it into a string hashmap, and then uploading tasks to the worker SQS.
- Manages the worker instances based on demand, creating more if necessary and waiting for them to complete tasks.
- Collects results from workers, aggregating them into a single output file when a predefined threshold is met, and then uploads this file to S3.
- If a termination message is received, the Manager sets a termination flag, waits for all ongoing tasks to conclude, shuts down worker instances, and finally terminates itself.

Worker Workflow:
- Workers listen for messages from the Manager through SQS.
- Each received message, a block of reviews, is analyzed to determine sentiment scores and detect sarcasm.
- Results or error messages are sent back to the Manager.


To provide a clearer and more concise summary of the performance metrics you've shared, let's rephrase and structure the information:

Performance Summary:
--------------------
- Test Configuration: Execution involved running 3 instances with 5 input files in total.
- Total Running Time: Approximately 25 minutes for the complete execution.
- Parameter Detail: The 'n' value, specified in the running command, was set to 10.

Key Considerations:
-------------------
Security:
- Credentials are securely managed through AWS's IAM roles and policies, avoiding the need to send credentials in plain text.

Scalability:
Strengths:
- Workers scale dynamically based on job load, optimizing processing efficiency.
- Resource use is adaptive, with the manager adjusting worker count as needed.
Limitations:
- A single SQS queue for all results creates a bottleneck, slowing down response times.
- Having only one Manager limits job distribution speed. Multiple managers could parallelize tasks better.

Persistence:
Worker Resilience:
- Message Visibility: If a worker fails, its message reappears in the queue for reprocessing by another worker, ensuring no task is lost.
- Dynamic Worker Replacement: The manager dynamically replaces workers as needed, maintaining operational capacity.

Manager Recovery:
- Automatic Manager Restart: In case of a manager failure, a local application will automatically initiate a new manager instance (utilizing a lock to ensure only one restart), preserving system continuity.
- Data Preservation: Work is safeguarded in a backup SQS. Unprocessed messages remain in the queue, ready for the new manager, guaranteeing no loss of progress.

Threading:
- The Manager employs multiple threads for handling communications with SQS, balancing efficiency and reliability.

communication map:
local app -> ec2manager
ec2manager -> local app
ec2manager -> workers
workers -> ec2manager
ec2manager -> backup -> ec2manager

Termination Process:
Manager Actions:
- Ceasing New Jobs: Upon receiving a termination message, the manager stops accepting new job requests.
- Completion and Shutdown: It completes pending jobs, then terminates all workers and itself, ensuring a clean shutdown.

Local Application Closure:
- Self-Termination: After receiving all outputs for its jobs, a local application concludes its operations and shuts down automatically.


FAQ:
----
Q: Are all your workers working hard, or are some slacking? Why?
A: The workers in our system are designed to dynamically scale based on the workload, ensuring they process tasks efficiently. If some workers seem less busy, it could be due to system bottlenecks, like a single SQS queue for all results, which may slow down the distribution of tasks. Ensuring an even workload distribution and addressing potential system bottlenecks are essential for maintaining worker efficiency.

Q: Is your manager doing more work than he's supposed to? Have you made sure each part of your system has properly defined tasks? Did you mix their tasks?
A: The manager's role is well-defined, focusing on coordinating task distribution, aggregating results, managing SQS queues, and dynamically adjusting worker instances. The system design should avoid mixing tasks between different components to maintain clear separation of responsibilities.

Q: Lastly, are you sure you understand what "distributed" means? Is there anything in your system awaiting another?
A: Our system embodies a distributed architecture by concurrently processing data across multiple AWS resources, indicating an understanding of distributed principles. However, potential waiting points, such as a single SQS queue for all results and reliance on a single manager, could introduce delays. To enhance our distributed system's efficiency, it's essential to minimize these dependencies by possibly introducing more queues or managers for better parallel task processing and reducing wait times.
