# Employee Directory

A single Java 21 / Spring Boot / Maven web application for demonstrating a straightforward Jenkins CI/CD release to Amazon EKS:

```text
GitHub -> Jenkins -> Maven build and tests -> Docker image -> Amazon ECR -> Amazon EKS
```

Jenkins checks out this repository, builds the executable JAR, packages that existing JAR in a single-stage Docker image, pushes an immutable release tag to an existing ECR repository, and deploys that exact image to an existing EKS cluster. The application uses server-rendered Thymeleaf pages and normal HTML forms; it has no business REST API, external service integrations, or database.

## Application and project structure

```text
src/main/java/com/example/employeedirectory/
  EmployeeDirectoryApplication.java  Spring Boot entry point
  Employee.java                      In-memory employee record
  EmployeeForm.java                  Server-side form validation
  EmployeeService.java               In-memory sample data and directory operations
  EmployeeController.java            Dashboard, search, and form routes
  PageMetadataAdvice.java             Version and Git commit for page footers
src/main/resources/
  templates/                         Thymeleaf dashboard, directory, and employee form
  static/css/app.css                 Responsive, locally served styling
  application.properties
src/test/java/com/example/employeedirectory/
  EmployeeServiceTest.java            Search, department counts, and employee operations
  EmployeeControllerTest.java         Form validation, rendered filtering, and health check
k8s/                                 Namespace, one Deployment, and LoadBalancer Service
Dockerfile                           Single-stage Java 21 runtime image
Jenkinsfile                          Declarative build, ECR push, and EKS deployment
```

Employee records are held only in process memory. They reset on application restart and are not shared between replicas. The baseline Kubernetes Deployment therefore has **one replica**. A rolling update may temporarily run two pods (`maxSurge: 1`); each pod has independent sample and user-entered data. This is a deployment exercise, not a persistent-data or high-availability design.

The `GET /healthz` endpoint is a lightweight infrastructure health check (`{"status":"UP"}`), not a business API. The page footer displays the Maven application version and the Git commit used to build that release.

## Run locally with Maven

Java 21 is required. From the repository root:

```sh
./mvnw clean verify
java -jar target/employee-directory.jar
```

On Windows PowerShell, use `.\mvnw.cmd clean verify` and then `java -jar target\employee-directory.jar`. Open <http://localhost:8080>. The test suite covers employee validation, search and filtering, department counts, and create/update/delete operations.

The application version is the Maven project version in `pom.xml` (`1.0.0`). CI injects the checked-out Git commit with `-Dgit.commit.id=...`; a local build without that property shows `unknown`.

## Build and run the Docker image

Build the JAR first; Docker intentionally does not run Maven and does not use a multi-stage build.

```sh
./mvnw clean verify
docker build -t employee-directory:local .
docker run --rm -p 8080:8080 employee-directory:local
```

The Dockerfile has exactly one `FROM`, uses the Java 21 Eclipse Temurin JRE runtime, copies `target/employee-directory.jar`, runs as UID/GID `10001`, and listens on port `8080`. `.dockerignore` does not exclude the packaged JAR. The `--rm` option above uses two normal ASCII hyphens.

## ECR and EKS prerequisites

The AWS resources are **not** created by this project or pipeline. Before running a deployment, an administrator must provide:

1. An existing ECR repository in the selected account and region.
2. An existing EKS cluster, worker nodes, and network access to ECR.
3. A cluster version/provider setup that supports `Service` type `LoadBalancer`, with correctly tagged subnets and the required AWS load-balancer integration. Public subnets and an internet gateway are needed for an internet-facing endpoint.
4. The Jenkins EC2 instance and the EKS worker/node IAM role configured as described below.
5. A reachable Jenkins URL and an agent with the prerequisites in the Jenkins section.

### Jenkins EC2 IAM role: AWS API permissions

Attach an instance profile to the Jenkins EC2 instance; do not put AWS access keys in Jenkins credentials, the Jenkinsfile, or repository files. Use least privilege and scope ECR actions to the selected repository:

- `ecr:GetAuthorizationToken` (AWS requires `Resource: "*"`)
- `ecr:BatchCheckLayerAvailability`, `ecr:CompleteLayerUpload`, `ecr:InitiateLayerUpload`, `ecr:PutImage`, and `ecr:UploadLayerPart` on the ECR repository ARN
- `eks:DescribeCluster` on the selected EKS cluster ARN
- `sts:GetCallerIdentity` for the Jenkins identity check

The Jenkins role's ECR permissions are for **pushing** releases. Separately, the EKS worker/node IAM role needs ECR pull permissions (`ecr:GetAuthorizationToken`, `ecr:BatchGetImage`, `ecr:GetDownloadUrlForLayer`, and `ecr:BatchCheckLayerAvailability`) so kubelet can pull private images. A common AWS-managed policy for the node role is `AmazonEC2ContainerRegistryPullOnly`; scope permissions according to your account policy.

### EKS access and Kubernetes RBAC are separate

AWS IAM authorization to call AWS APIs is not by itself permission to change Kubernetes objects. Configure an EKS access entry (or the cluster's existing identity mapping) for the Jenkins EC2 role, then grant Kubernetes access separately:

- Jenkins must be able to authenticate to the cluster and manage the selected namespace's Deployment and Service, inspect pods/events, and read pod logs for rollout verification/diagnostics.
- Because this pipeline applies `k8s/namespace.yaml`, the role also needs permission to create/update that Namespace at cluster scope. Alternatively, have an administrator pre-create the namespace and grant the Jenkins identity only the required namespace-scoped access, then adjust the namespace-apply stage to match that policy.
- Prefer a dedicated namespace-scoped RBAC role/group for routine release operations rather than cluster-admin. Do not confuse the EKS cluster's AWS IAM role, the EC2 instance profile, worker-node image-pull permissions, and Kubernetes user RBAC; they serve different purposes.

## Jenkins setup

The Jenkins agent that runs the pipeline needs:

- Java 21 (to run the Maven Wrapper and application build)
- Git
- Docker CLI and a running Docker daemon, with permission for the Jenkins agent user to build and push images
- AWS CLI v2, using the EC2 instance profile
- `kubectl`, compatible with the EKS cluster version
- Network reachability to GitHub, ECR, the EKS API endpoint, and the Docker registry

Install/configure Jenkins Pipeline, Git, and GitHub integration plugins. Configure a Pipeline job to use this GitHub repository and the repository's `Jenkinsfile`. Do not store AWS keys in Jenkins credentials.

### Parameters

| Parameter | Purpose | Example |
| --- | --- | --- |
| `AWS_ACCOUNT_ID` | 12-digit account owning the ECR repository | `123456789012` |
| `AWS_REGION` | Shared AWS region for ECR and EKS | `us-east-1` |
| `ECR_REPOSITORY` | Existing ECR repository name | `employee-directory` |
| `EKS_CLUSTER` | Existing cluster name | `employee-directory-eks` |
| `K8S_NAMESPACE` | Namespace to deploy into | `employee-directory` |

Enter your own account, region, repository, cluster, and namespace values. The pipeline tags each image as `<12-character-git-commit>-<jenkins-build-number>`; it never deploys `latest`. Concurrent pipeline runs are disabled so two releases cannot race to update the Deployment. A test/build failure, Docker build or push failure, kubectl failure, or unsuccessful rollout fails the build. Deployment failures print current resources, Deployment details, events, pod logs, and rollout history.

### GitHub webhook and manual builds

For push-triggered builds, configure the Jenkins GitHub webhook endpoint in the GitHub repository:

```text
https://<your-jenkins-host>/github-webhook/
```

Enable push events, ensure the endpoint is reachable from GitHub, and enable the GitHub push trigger for the job. The `githubPush()` trigger is declared in the Jenkinsfile. A GitHub webhook does not replace repository/job SCM configuration.

For a manual build, open the Jenkins Pipeline job, choose **Build with Parameters**, enter the five values above, and start the build. This is also a useful fallback when webhook delivery is unavailable.

## Kubernetes resources and behavior

Only three application manifests are provided:

- `k8s/namespace.yaml`
- `k8s/deployment.yaml` (one replica, rolling update, resource bounds, security context, and startup/readiness/liveness probes)
- `k8s/service.yaml` (AWS `LoadBalancer` Service)

The Deployment has an image placeholder. Jenkins substitutes the release's exact ECR URI before applying the Deployment, then checks that both the Deployment and ready pods use the exact URI. The application process shuts down gracefully. A rolling update with `maxSurge: 1` may run two pods temporarily; they do not share in-memory employee data.

## Inspect and operate the deployment

Set the namespace to the one used by the Jenkins job:

```sh
kubectl get pods -n employee-directory -o wide
kubectl get services -n employee-directory
kubectl get service employee-directory -n employee-directory -w
kubectl logs deployment/employee-directory -n employee-directory
kubectl describe pod -l app=employee-directory -n employee-directory
kubectl get events -n employee-directory --sort-by=.lastTimestamp
kubectl rollout history deployment/employee-directory -n employee-directory
kubectl rollout status deployment/employee-directory -n employee-directory --timeout=300s
```

The `LoadBalancer` Service's external hostname/address may take a few minutes to appear. Browse to `http://<EXTERNAL-IP-or-hostname>/` once provisioned.

### Manual rollback

Review the revisions and roll back one revision, then verify rollout completion:

```sh
kubectl rollout history deployment/employee-directory -n employee-directory
kubectl rollout undo deployment/employee-directory -n employee-directory
kubectl rollout status deployment/employee-directory -n employee-directory --timeout=300s
kubectl get deployment employee-directory -n employee-directory \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

Use `kubectl rollout undo ... --to-revision=<number>` to select a specific history entry. A rollback restores the prior pod template/image, not lost in-memory edits. Any pod replacement resets that pod's employee data.

## Common failures

| Symptom | Checks |
| --- | --- |
| Maven build errors | Check Java is version 21 (`java -version`), wrapper/network access to Maven Central, compiler/test output, and `target/surefire-reports/`. Run `./mvnw clean verify` locally. |
| ECR access denied | Confirm the instance profile is attached, `aws sts get-caller-identity` shows the expected role/account, region/account/repository parameters are correct, and the role has ECR push actions on that repository. |
| `ImagePullBackOff` | Check the exact Deployment image URI/tag exists in the selected ECR repository, node role has ECR pull permissions, cluster nodes can reach ECR, and the image is compatible with the node CPU architecture. Inspect `kubectl describe pod ...` events. |
| `CrashLoopBackOff` | Check `kubectl logs ... --previous`, current logs, container exit details, memory limits, Java runtime/image, and that port `8080` is available. |
| Pods remain `Pending` | Inspect pod events, cluster capacity, CPU/memory requests, node selectors/taints, and available subnet/IP capacity. |
| Failed startup/readiness/liveness probes | Check pod logs/events, verify the app starts on port `8080`, and confirm `GET /healthz` responds successfully. The startup probe allows up to 150 seconds before restarting a slow startup. |
| EKS access denied | Check `aws eks update-kubeconfig`, the EKS access entry/identity mapping, and Kubernetes RBAC. ECR/IAM push permissions do not grant permission to patch Kubernetes Deployments. |
| External Service address stays pending | Check cloud-provider/load-balancer integration, subnet tags, subnet route tables, permissions, and events for the Service. |

## AWS charges and cleanup

EKS control planes, EC2 worker nodes, EBS volumes, ECR image storage/data transfer, public IPv4 addresses, and provisioned load balancers can incur AWS charges. Verify current pricing for your region before deploying. A `LoadBalancer` Service can provision billable networking resources.

Delete only resources belonging to this demo. The commands below remove the Service and Deployment from the chosen namespace and then the namespace; delete shared ECR repositories or clusters only if you intentionally created them for this demo and no longer need them:

```sh
kubectl delete service employee-directory -n employee-directory
kubectl delete deployment employee-directory -n employee-directory
kubectl delete namespace employee-directory
```

AWS cluster, node, ECR, and load-balancer cleanup is intentionally manual and is not performed by Jenkins. Follow your organization's AWS cleanup procedure; inspect dependencies and retained images before deleting any shared resource.
