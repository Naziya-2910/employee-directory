pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
    }

    triggers {
        githubPush()
    }

    parameters {
        string(name: 'AWS_ACCOUNT_ID', defaultValue: '', description: '12-digit AWS account ID that owns the ECR repository')
        string(name: 'AWS_REGION', defaultValue: 'us-east-1', description: 'AWS region containing ECR and EKS')
        string(name: 'ECR_REPOSITORY', defaultValue: 'employee-directory', description: 'Existing ECR repository name')
        string(name: 'EKS_CLUSTER', defaultValue: 'employee-directory-eks', description: 'Existing EKS cluster name')
        string(name: 'K8S_NAMESPACE', defaultValue: 'employee-directory', description: 'Kubernetes namespace for this application')
    }

    stages {
        stage('Checkout from GitHub') {
            steps {
                checkout scm
                sh 'git rev-parse HEAD'
            }
        }

        stage('Maven tests and build') {
            steps {
                script {
                    env.MAVEN_BUILD_STATUS = sh(
                        returnStatus: true,
                        script: './mvnw clean verify -Dgit.commit.id="$GIT_COMMIT"'
                    ).toString()
                }
            }
        }

        stage('Publish reports and archive JAR') {
            steps {
                junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                archiveArtifacts artifacts: 'target/employee-directory.jar',
                                 fingerprint: true, allowEmptyArchive: true
                script {
                    if (env.MAVEN_BUILD_STATUS != '0') {
                        error("Maven clean verify failed with exit code ${env.MAVEN_BUILD_STATUS}; see the published test results and build log.")
                    }
                }
            }
        }

        stage('Build Docker image') {
            steps {
                script {
                    if (!(params.AWS_ACCOUNT_ID ==~ /[0-9]{12}/)) {
                        error('AWS_ACCOUNT_ID must contain exactly 12 digits.')
                    }
                    if (!(params.AWS_REGION ==~ /[a-z0-9-]+/)
                            || !(params.ECR_REPOSITORY ==~ /[a-z0-9][a-z0-9._\/-]*/)
                            || !(params.EKS_CLUSTER ==~ /[A-Za-z0-9_-]+/)
                            || !(params.K8S_NAMESPACE ==~ /[a-z0-9]([-a-z0-9]*[a-z0-9])?/)) {
                        error('One or more AWS or Kubernetes parameters contain invalid characters.')
                    }
                    env.IMAGE_TAG = "${env.GIT_COMMIT.take(12)}-${env.BUILD_NUMBER}"
                    env.ECR_REGISTRY = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${params.AWS_REGION}.amazonaws.com"
                    env.IMAGE_URI = "${env.ECR_REGISTRY}/${params.ECR_REPOSITORY}:${env.IMAGE_TAG}"
                }
                sh '''#!/usr/bin/env bash
                    set -euo pipefail
                    docker build --tag "$IMAGE_URI" .
                '''
            }
        }

        stage('Authenticate to Amazon ECR') {
            steps {
                sh '''#!/usr/bin/env bash
                    set -euo pipefail
                    aws sts get-caller-identity
                    aws ecr get-login-password --region "$AWS_REGION" \
                      | docker login --username AWS --password-stdin "$ECR_REGISTRY"
                '''
            }
        }

        stage('Push immutable release image') {
            steps {
                sh '''#!/usr/bin/env bash
                    set -euo pipefail
                    docker push "$IMAGE_URI"
                '''
            }
        }

        stage('Configure kubectl for EKS') {
            steps {
                sh '''#!/usr/bin/env bash
                    set -euo pipefail
                    aws eks update-kubeconfig --name "$EKS_CLUSTER" --region "$AWS_REGION"
                '''
                script {
                    env.EKS_CONFIGURED = 'true'
                }
                sh '''#!/usr/bin/env bash
                    set -euo pipefail
                    kubectl cluster-info
                '''
            }
        }

        stage('Deploy exact image to EKS') {
            steps {
                sh '''#!/usr/bin/env bash
                    set -euo pipefail
                    sed "s|name: NAMESPACE|name: ${K8S_NAMESPACE}|" k8s/namespace.yaml | kubectl apply -f -
                    kubectl wait --for=jsonpath='{.status.phase}'=Active \
                      "namespace/$K8S_NAMESPACE" --timeout=60s
                    sed "s|image: ACCOUNT_ID.dkr.ecr.AWS_REGION.amazonaws.com/ECR_REPOSITORY:IMAGE_TAG|image: ${IMAGE_URI}|" \
                      k8s/deployment.yaml \
                      | kubectl apply --namespace "$K8S_NAMESPACE" -f -
                    kubectl apply --namespace "$K8S_NAMESPACE" -f k8s/service.yaml
                '''
            }
        }

        stage('Wait for rollout') {
            steps {
                sh '''#!/usr/bin/env bash
                    set -euo pipefail
                    kubectl rollout status deployment/employee-directory \
                      --namespace "$K8S_NAMESPACE" --timeout=300s
                '''
            }
        }

        stage('Verify readiness and deployed image') {
            steps {
                sh '''#!/usr/bin/env bash
                    set -euo pipefail
                    kubectl wait --for=condition=Ready pod \
                      --selector=app=employee-directory \
                      --namespace "$K8S_NAMESPACE" --timeout=180s

                    DEPLOYED_IMAGE="$(kubectl get deployment employee-directory \
                      --namespace "$K8S_NAMESPACE" \
                      --output=jsonpath='{.spec.template.spec.containers[0].image}')"
                    if [[ "$DEPLOYED_IMAGE" != "$IMAGE_URI" ]]; then
                      echo "Deployment image mismatch: expected $IMAGE_URI, found $DEPLOYED_IMAGE"
                      exit 1
                    fi

                    POD_IMAGES="$(kubectl get pods --selector=app=employee-directory \
                      --namespace "$K8S_NAMESPACE" \
                      --output=jsonpath='{range .items[*]}{.spec.containers[0].image}{"\\n"}{end}')"
                    if [[ -z "$POD_IMAGES" ]]; then
                      echo "No employee-directory pods were found."
                      exit 1
                    fi
                    while IFS= read -r pod_image; do
                      [[ "$pod_image" == "$IMAGE_URI" ]] || {
                        echo "Pod image mismatch: expected $IMAGE_URI, found $pod_image"
                        exit 1
                      }
                    done <<< "$POD_IMAGES"

                    kubectl get deployment,pods,service \
                      --namespace "$K8S_NAMESPACE" --output=wide
                    echo "Verified running image: $IMAGE_URI"
                '''
            }
        }
    }

    post {
        failure {
            script {
                if (env.EKS_CONFIGURED == 'true') {
                    sh(returnStatus: true, script: '''#!/usr/bin/env bash
                        echo "Deployment diagnostics for namespace: $K8S_NAMESPACE"
                        kubectl get pods,deployments,services \
                          --namespace "$K8S_NAMESPACE" --output=wide || true
                        kubectl describe deployment employee-directory \
                          --namespace "$K8S_NAMESPACE" || true
                        kubectl get events --namespace "$K8S_NAMESPACE" \
                          --sort-by=.lastTimestamp || true
                        kubectl logs --selector=app=employee-directory \
                          --namespace "$K8S_NAMESPACE" --all-containers=true \
                          --tail=100 || true
                        kubectl rollout history deployment/employee-directory \
                          --namespace "$K8S_NAMESPACE" || true
                    ''')
                }
            }
        }
    }
}
