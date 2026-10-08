# Employee Directory: Interview Guide

### 1. What does Maven build?

Maven compiles the Java 21 Spring Boot application, resolves its dependencies, runs unit and MVC tests, and packages one executable JAR at `target/employee-directory.jar`. `./mvnw clean verify` performs the complete build and verification lifecycle.

### 2. What does Docker package?

Docker packages the already-built executable JAR and a Java 21 runtime into one image. The single-stage Dockerfile does not install Maven or compile source code; the image runs the JAR as a non-root user on port 8080.

### 3. How does Jenkins automate deployment?

The declarative Jenkins pipeline checks out GitHub, runs Maven tests/build, publishes test reports and archives the JAR, builds the Docker image, authenticates to ECR with the EC2 instance role, pushes a commit/build-number tag, configures `kubectl` for EKS, applies the manifests with the exact image URI, waits for rollout, and verifies ready pods and image identity.

### 4. Why use ECR?

Amazon ECR is the private container registry for the release images. It integrates with AWS IAM, and EKS worker nodes can authenticate to it using their node role to pull the image.

### 5. How does EKS pull the image?

The Deployment references a private ECR URI and a unique release tag. The worker node's IAM role needs ECR pull permissions, and kubelet on the node uses those permissions to retrieve the image. Jenkins' image-push credentials are not reused as pod credentials.

### 6. How does a Service select pods?

The Service's selector `app: employee-directory` matches the same label on the Deployment's pod template. Kubernetes routes traffic sent to the Service to matching ready pod endpoints. The `LoadBalancer` Service requests an external AWS load balancer through the cluster's configured integration.

### 7. How does a rolling update work?

The Deployment creates a new ReplicaSet when its pod template changes, waits for new pods to become ready, and then scales down the old ReplicaSet. This configuration has one steady-state replica, `maxSurge: 1`, and `maxUnavailable: 0`, so two pods may briefly exist during an update.

### 8. How do health probes differ?

The startup probe gives the Java application time to initialize and suppresses liveness/readiness checks until it succeeds. The readiness probe controls whether a pod receives Service traffic. The liveness probe restarts a container that is no longer responding as expected. They use the lightweight `/healthz` endpoint.

### 9. How do you troubleshoot a failed deployment?

Start with `kubectl rollout status` and `kubectl describe deployment`. Inspect pod status and events, then current and previous container logs. Check `ImagePullBackOff` for ECR URI, node-role permissions, and network; check `CrashLoopBackOff` for startup logs and memory; check `Pending` for capacity and scheduling; and check probe failures against `/healthz`. Jenkins prints deployment, pod, event, log, and rollout diagnostics on failure.

### 10. How do you roll back?

Use `kubectl rollout history deployment/employee-directory -n <namespace>` to inspect revisions, then `kubectl rollout undo deployment/employee-directory -n <namespace>` or specify `--to-revision`. Wait for the rollback to complete and verify the Deployment image and browser release footer.

### 11. How are AWS permissions managed?

Jenkins uses the EC2 instance profile, not static access keys. Its IAM role gets scoped ECR push and EKS `DescribeCluster` permissions. EKS access entries/identity mapping and Kubernetes RBAC separately authorize kubectl actions. Worker nodes have separate ECR pull permissions. AWS IAM API permissions alone do not grant Kubernetes object access.

### 12. How do image tags identify releases?

Every tag is `<12-character-git-commit>-<jenkins-build-number>`. It ties an image to source and a Jenkins run and avoids the ambiguity of a mutable `latest` tag. Jenkins deploys and verifies the exact full ECR image URI it pushed.

### 13. What happens when a pod is deleted?

The Deployment controller notices that the actual pod count is below its desired replica count and schedules a replacement. The replacement is a new process with the same image and starts with the sample employee data.

### 14. Why is application data lost after restart?

Employee data is stored in a Java in-memory map, not on durable storage. A new process starts with the seed data, so edits disappear on a pod replacement or application restart. Separate replicas would each have independent maps and could show different data.

### 15. What changes would be needed for persistent data and multiple replicas?

Use a durable shared data store with schema/migration management, transactions, connection pooling, secrets/network configuration, and backup/recovery. Make the application stateless with respect to employee records, ensure session/state handling works across replicas, add production-grade security/observability, and then scale the Deployment and validate availability, capacity, and rollout behavior. Those database and HA components are intentionally outside this demo's scope.
