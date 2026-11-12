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

# Employee Directory: CI/CD on Amazon EKS with Jenkins, Docker and Argo CD

A Java 21 / Spring Boot web application deployed to Amazon EKS through a GitOps pipeline:

```
Developer
   |  git push
   v
GitHub (this repo)
   |
   v
Jenkins pipeline: Maven build + tests -> SonarQube -> Docker build -> Trivy scan -> push to ECR
   |
   v
Jenkins commits the new image tag to k8s/deployment.yaml (GitOps)
   |
   v
Argo CD detects the commit and syncs the cluster
   |
   v
Amazon EKS  ->  LoadBalancer Service  ->  Browser
```

| Tool | Purpose |
| --- | --- |
| Jenkins | Runs the CI pipeline and writes the new image tag to Git |
| Docker | Builds the application image |
| SonarQube + SonarScanner | Code quality and security analysis |
| Trivy | Scans the image for vulnerabilities |
| Amazon ECR | Stores the container images |
| Argo CD | Deploys whatever is in the `k8s` folder to the cluster |
| Amazon EKS | Runs the application |

The app keeps employees in memory only, so records reset whenever a pod restarts.

## Repository structure

```
employee-directory/
  src/                  Spring Boot source and tests
  k8s/
    namespace.yaml      Namespace
    deployment.yaml     Deployment (image line is updated by Jenkins)
    service.yaml        LoadBalancer Service
  Dockerfile            Runtime image (copies target/employee-directory.jar)
  Jenkinsfile           CI pipeline
  pom.xml               Maven build
  README.md
```

## Values used in this guide

Set these once in every terminal session. Only the account ID needs editing; do not type the angle brackets.

```bash
export AWS_REGION=ap-south-1
export AWS_ACCOUNT_ID=<your-12-digit-account-id>
export CLUSTER_NAME=my-eks
export ECR_REPO=ecr
export NAMESPACE=employee-directory
```

Whenever a command contains text in `<angle brackets>`, replace it with your real value and remove the brackets. Never paste YAML straight into the terminal; YAML goes in files.

## 1. Prerequisites

- An AWS account and an IAM user or role allowed to use ECR, EKS and EC2.
- An Ubuntu EC2 instance for Jenkins and SonarQube, with several GB of RAM. Open inbound ports 8080 (Jenkins) and 9000 (SonarQube) only to your own IP.
- An Amazon EKS cluster (EKS Auto Mode is used here) in the same region, created in the AWS console.
- A GitHub personal access token with write access to this repo.

Create the ECR repository (the pipeline fails with `repository does not exist` without it):

```bash
aws ecr create-repository --repository-name $ECR_REPO --region $AWS_REGION
```

## 2. Install the tools on the Jenkins EC2 instance

**Jenkins.** Install it from the official guide: https://www.jenkins.io/doc/book/installing/linux/#debianubuntu

**Docker.** Jenkins needs Docker to build images:

```bash
sudo apt-get update
sudo apt install -y docker.io
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

**SonarQube** (runs as a container; generate a token after first login):

```bash
docker run -d --name sonarqube --restart unless-stopped -p 9000:9000 sonarqube:latest
```

**SonarScanner CLI:**

```bash
sudo apt update
sudo apt install -y curl unzip openjdk-21-jre
cd /tmp
curl -fSL https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-8.2.0.6438-linux-x64.zip -o sonar-scanner.zip
sudo unzip -o sonar-scanner.zip -d /opt
sudo ln -sfn /opt/sonar-scanner-8.2.0.6438-linux-x64 /opt/sonar-scanner
echo 'export PATH="/opt/sonar-scanner/bin:$PATH"' | sudo tee /etc/profile.d/sonar-scanner.sh
source /etc/profile.d/sonar-scanner.sh
sudo -u jenkins /opt/sonar-scanner/bin/sonar-scanner --version
```

**Trivy** (image vulnerability scanner):

```bash
sudo apt-get update
sudo apt-get install -y wget gnupg lsb-release
sudo mkdir -p /etc/apt/keyrings
wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | gpg --dearmor | sudo tee /etc/apt/keyrings/trivy.gpg > /dev/null
echo "deb [signed-by=/etc/apt/keyrings/trivy.gpg] https://aquasecurity.github.io/trivy-repo/deb $(lsb_release -sc) main" | sudo tee /etc/apt/sources.list.d/trivy.list
sudo apt-get update
sudo apt-get install -y trivy
```

**AWS CLI v2:**

```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
aws configure
```

**kubectl:**

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo mkdir -p -m 755 /etc/apt/keyrings
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.37/deb/Release.key | sudo gpg --dearmor --yes -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
sudo chmod 644 /etc/apt/keyrings/kubernetes-apt-keyring.gpg
echo 'deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.37/deb/ /' | sudo tee /etc/apt/sources.list.d/kubernetes.list
sudo apt-get update
sudo apt-get install -y kubectl
kubectl version --client
```

**Helm 3** (used to install Argo CD):

```bash
curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
chmod 700 get_helm.sh
./get_helm.sh
helm version
```

## 3. Connect kubectl to the EKS cluster

```bash
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME
kubectl get nodes
```

Confirm the cluster type (prints `true` for EKS Auto Mode, which changes the load balancer setup in step 8):

```bash
aws eks describe-cluster --name $CLUSTER_NAME --region $AWS_REGION --query 'cluster.computeConfig.enabled'
```

## 4. Install Argo CD

Add the Argo Helm repository first, then install:

```bash
helm repo add argo https://argoproj.github.io/argo-helm
helm repo update
helm upgrade --install argocd argo/argo-cd --namespace argocd --create-namespace
kubectl get pods -n argocd
```

Wait until every pod is `Running`. Print the first admin password (username `admin`):

```bash
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
```

Open the UI through a port-forward:

```bash
kubectl port-forward --address 0.0.0.0 \
  svc/argocd-server -n argocd 8081:443
```

The port-forward listens on all interfaces of the instance, so open `https://<ec2-public-ip>:8081` in your browser (or https://localhost:8081 from the instance itself). Allow inbound TCP 8081 in the instance's security group for your own IP only, and keep the command running in its terminal (`Ctrl+C` stops it). Chrome warns "Your connection is not private" because Argo CD uses a self-signed certificate; click **Advanced**, then **Proceed**.

## 5. Configure Jenkins

1. **Plugins** (Manage Jenkins, Plugins): Pipeline, Git, SonarQube Scanner, CloudBees AWS Credentials, JUnit.
2. **SonarQube token:** log in to SonarQube at `http://<sonarqube-host>:9000`, open your avatar, **My Account, Security**, and generate a token. In Jenkins add it as a **Secret text** credential with ID `sonar-token`.
3. **SonarQube server:** Manage Jenkins, System, **SonarQube servers**. Name it exactly `SonarQubeServer`, set the URL, and select `sonar-token`. A `401 Unauthorized` in the Sonar stage means this token is missing or wrong.
4. **Credentials** (Manage Jenkins, Credentials):
   - `git-cred`: Username with password. Username is your GitHub user; the password is the personal access token (needs write access).
   - `aws-cred`: AWS credentials (access key ID and secret) for the account that owns the ECR repository.
5. **Pipeline job:** create a Pipeline job, set **Definition** to **Pipeline script from SCM**, SCM Git, repository `https://github.com/Naziya-2910/employee-directory.git`, branch `main`, credentials `git-cred`, script path `Jenkinsfile`.

## 6. The pipeline

The `Jenkinsfile` runs these stages:

| Stage | What it does |
| --- | --- |
| Checkout | Clones `main` from GitHub |
| Set Image Tag | Builds the tag `<build number>-<short commit hash>` |
| Maven Build and Test | Runs `mvn clean verify` in a Maven Docker container; a failing test stops the build |
| SonarQube Analysis | Runs `sonar-scanner` against `src` |
| Build Docker Image | Builds the image from the `Dockerfile` |
| Image Scan | Saves a Trivy report, then fails on fixable CRITICAL vulnerabilities |
| Push to ECR | Logs in to ECR and pushes the unique tag |
| Update GitOps Repo | Writes the new image into `k8s/deployment.yaml`, commits as Jenkins with `[skip ci]`, and pushes to `main` |

Why the last stage exists: Argo CD only deploys what is in Git. The commit that changes the image tag is what triggers each deployment, and it also gives you a Git history of releases and a rollback by reverting a commit.

Run it once now with **Build Now**, before creating the Argo CD application, so `k8s/deployment.yaml` holds a real image tag. Check ECR for the pushed tag:

```bash
aws ecr describe-images --repository-name $ECR_REPO --region $AWS_REGION --query 'imageDetails[].imageTags' --output text
```

## 7. Fix the namespace manifest

`k8s/namespace.yaml` must contain the real name, not a placeholder. Kubernetes rejects uppercase names, and Argo CD shows `OutOfSync` otherwise:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: employee-directory
  labels:
    app.kubernetes.io/part-of: employee-directory
```

## 8. Expose the app: attach a load balancer (EKS Auto Mode)

The Service of type `LoadBalancer` needs tagged public subnets and two extra settings.

**8.1 Tag the cluster's public subnets** so the load balancer can be placed in them:

```bash
SUBNETS=$(aws eks describe-cluster --name $CLUSTER_NAME --region $AWS_REGION --query 'cluster.resourcesVpcConfig.subnetIds' --output text)
aws ec2 create-tags --region $AWS_REGION --resources $SUBNETS \
  --tags Key=kubernetes.io/role/elb,Value=1 Key=kubernetes.io/cluster/$CLUSTER_NAME,Value=shared
aws ec2 describe-subnets --region $AWS_REGION --subnet-ids $SUBNETS --query 'Subnets[].{ID:SubnetId,Tags:Tags}' --output json | cat
```

The tags are per cluster, so repeat this for any new cluster. A public subnet has a `0.0.0.0/0` route to an internet gateway (`igw-...`).

**8.2 Set `k8s/service.yaml`.** `loadBalancerClass` hands the Service to EKS Auto Mode, and the annotation makes the load balancer internet-facing (the default is internal):

```yaml
apiVersion: v1
kind: Service
metadata:
  name: employee-directory
  labels:
    app: employee-directory
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-scheme: internet-facing
spec:
  type: LoadBalancer
  loadBalancerClass: eks.amazonaws.com/nlb
  selector:
    app: employee-directory
  ports:
    - name: http
      port: 80
      targetPort: http
      protocol: TCP
```

Commit it to `main`. `loadBalancerClass` cannot be changed on an existing Service, so if a Service already exists, delete it after the commit and let Argo CD recreate it (see step 9).

## 9. Create the Argo CD application

The Application tells Argo CD which repo, branch and folder to watch and where to deploy. Without it Argo CD does nothing.

```bash
cat > employee-app.yaml <<'EOF'
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: employee-directory
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/Naziya-2910/employee-directory.git
    targetRevision: main
    path: k8s
  destination:
    server: https://kubernetes.default.svc
    namespace: employee-directory
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
EOF
kubectl apply -f employee-app.yaml
```

Check synchronization:

```bash
kubectl get application employee-directory -n argocd
kubectl get pods -n employee-directory
```

The goal is `Synced` and `Healthy` with the pod `1/1 Running`. Right after creation `OutOfSync` and `Progressing` is normal for a minute.

If an old Service exists without the load balancer settings, recreate it:

```bash
kubectl delete svc employee-directory -n employee-directory
kubectl annotate application employee-directory -n argocd argocd.argoproj.io/refresh=hard --overwrite
```

## 10. Open the application

**Option A: LoadBalancer (default).** Wait a few minutes for the address:

```bash
kubectl get svc -n employee-directory
kubectl describe svc employee-directory -n employee-directory | tail -5
```

You want a `SuccessfullyReconciled` event and an `...elb.amazonaws.com` hostname under `EXTERNAL-IP`. Open `http://<hostname>/`. Use `http`, not `https`, because the Service only listens on port 80. Allow a couple more minutes for the targets to become healthy.

**Option B: NodePort (no load balancer, no extra AWS cost).** Change `k8s/service.yaml` to `type: NodePort`, remove the `loadBalancerClass` line and the annotation, and commit. Then:

```bash
kubectl get svc -n employee-directory
kubectl get nodes -o wide
```

The Service shows a port like `80:30088/TCP`; the nodes show an `EXTERNAL-IP` (public IP). Open `http://<node-public-ip>:<nodeport>/` after allowing that port (range 30000-32767) in the worker nodes' security group for your IP.

**Option C: port-forward from the instance** (quick check):

```bash
kubectl port-forward svc/employee-directory -n employee-directory 8082:80 --address 0.0.0.0
curl http://localhost:8082/healthz
```

The health check returns `{"status":"UP"}`.

## 11. Test the whole flow

1. On GitHub, edit one visible line under `src/main/resources/templates/` (for example the page `<title>`) and commit to `main`. Do not edit `k8s/deployment.yaml`; Jenkins changes it.
2. In Jenkins click **Build Now**.
3. When it finishes, `k8s/deployment.yaml` shows a new Jenkins commit such as `Update image tag to 3-abc1234 [skip ci]`.
4. Argo CD rolls out the new pod within about 3 minutes. To speed it up:
   ```bash
   kubectl annotate application employee-directory -n argocd argocd.argoproj.io/refresh=hard --overwrite
   ```
5. Confirm the new image is running:
   ```bash
   kubectl get pods -n employee-directory
   kubectl get deployment employee-directory -n employee-directory -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
   ```
6. Reload the page with `Ctrl+Shift+R`. The footer also shows the Git commit of the running build.

## 12. Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `bash: syntax error near unexpected token 'newline'` or `No such file or directory` | A `<placeholder>` was typed literally | Replace the placeholder, brackets included, with the real value |
| `apiVersion:: command not found` | YAML pasted into the terminal | Put YAML in a file or on GitHub, not in the shell |
| Chrome: `ERR_CERT_AUTHORITY_INVALID` on the Argo CD page | Self-signed certificate | **Advanced**, then **Proceed**; use a real certificate in production |
| Jenkins Sonar stage: `HTTP 401 Unauthorized` | No valid SonarQube token in Jenkins | Create a token, store it as `sonar-token`, set it on `SonarQubeServer` (step 5) |
| Jenkins push fails: `repository does not exist` | ECR repository is missing in this account or region | Create it (step 1), or fix the registry values in the `Jenkinsfile` |
| Pod `ImagePullBackOff`, event `not found` | The image tag is not in ECR, or `deployment.yaml` has no tag | Run the pipeline so it pushes the image and writes the tag |
| Pod `ImagePullBackOff`, event `denied` | Node role cannot pull from ECR | Attach `AmazonEC2ContainerRegistryPullOnly` to the node role |
| Argo CD `OutOfSync`: `Namespace "NAMESPACE" is invalid` | Placeholder name in `k8s/namespace.yaml` | Set `name: employee-directory` (step 7) |
| App `Degraded` for the wrong application | The Application points at another repo or path | Delete it and create one for this repo and the `k8s` folder |
| Service `EXTERNAL-IP` `<pending>`, `Evaluated 0 subnets` | Subnets lack load balancer tags | Tag them (step 8.1) |
| Still `<pending>` on EKS Auto Mode | Service lacks `loadBalancerClass` and the internet-facing annotation | Fix `service.yaml`, delete the Service, refresh (steps 8.2 and 9) |
| Browser: `ERR_CONNECTION_REFUSED` | `https://` used, load balancer still starting, or no healthy pod | Use `http://`, wait, and check `kubectl get pods -n employee-directory` |
| Terminal stuck on a `:` prompt | Output opened in a pager | Press `q` |

To find out why a sync fails:

```bash
kubectl get application employee-directory -n argocd -o jsonpath='{range .status.resources[*]}{.kind}{"\t"}{.name}{"\t"}{.status}{"\t"}{.health.status}{"\n"}{end}'
kubectl get application employee-directory -n argocd -o jsonpath='{.status.operationState.phase}{"\n"}{.status.operationState.message}{"\n"}'
kubectl describe pod -n employee-directory -l app=employee-directory
```

## 13. Security notes

- Never commit tokens, AWS keys or passwords. Keep them in Jenkins credentials.
- Restrict ports 8080 (Jenkins), 8081 (Argo CD port-forward), 9000 (SonarQube) and any NodePort to your own IP.
- Remember that the Jenkins pipeline pushes to `main`. Use a token limited to this repository.
- If SonarQube's public IP changes after a restart, update the server URL in Jenkins, or give the instance an Elastic IP.

## 14. Cleanup

EKS, EC2 nodes, load balancers and ECR images all cost money while they exist.

```bash
kubectl delete application employee-directory -n argocd
```

With `prune: true` this removes the pods and the Service, which releases the load balancer. Delete the rest by hand in AWS when you are finished: the EKS cluster, the Jenkins EC2 instance, the ECR repository and the subnet tags.
