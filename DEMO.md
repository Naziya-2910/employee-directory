# Employee Directory CI/CD Demo

Use an existing GitHub repository, Jenkins agent, ECR repository, and EKS cluster. This project does not create AWS resources.

1. **Push the project to GitHub.** Confirm the repository contains the Maven Wrapper, `pom.xml`, `Dockerfile`, `Jenkinsfile`, and `k8s/` manifests.
2. **Run Jenkins.** Trigger a webhook push build or select **Build with Parameters** and provide the AWS account ID, region, existing ECR repository, EKS cluster, and Kubernetes namespace.
3. **Show Maven tests and the generated JAR.** In Jenkins show the Maven build, published test report, and archived `target/employee-directory.jar`. The local equivalent is `./mvnw clean verify`.
4. **Show Docker image creation and ECR push.** Show the Docker build and the pushed tag. The tag includes the Git commit prefix and Jenkins build number, making each release traceable and immutable by convention.
5. **Show the EKS deployment.** Show the exact ECR URI in the Deployment, successful rollout, ready pod, `LoadBalancer` Service, and the application in a browser.
6. **Change a visible heading and application version.** For example, update the dashboard heading in `src/main/resources/templates/dashboard.html` and change the Maven `<version>` in `pom.xml`.
7. **Commit and push the change.** The committed Git SHA becomes part of the build metadata and image release tag.
8. **Show Jenkins deploying the new release.** Follow checkout, Maven test/build, report/archive, Docker build, ECR login/push, EKS update, rollout wait, and image verification.
9. **Verify the release in the browser.** Confirm the changed heading and the updated application version/Git commit in the footer.
10. **Delete the application pod.** Run `kubectl delete pod -l app=employee-directory -n <namespace>` and show Kubernetes creating a replacement to satisfy the Deployment.
11. **Demonstrate rollback.** Show `kubectl rollout history deployment/employee-directory -n <namespace>`, run `kubectl rollout undo deployment/employee-directory -n <namespace>`, wait for the rollout, and refresh the browser.
12. **Explain in-memory data loss.** Add or edit an employee, then show that deleting/replacing its pod starts a new process with sample data: user-entered changes are gone. A pod restart cannot preserve in-memory state, and multiple replicas would each have independent copies.

An EKS rolling update can temporarily run two application pods with `maxSurge: 1`, even though the steady-state replica count is one. The directory is a teaching example; persistent storage and high availability are not implemented.
