
pipeline {
    agent any

    environment {
        IMAGE_NAME = 'mydockerimage'
        SONARQUBE_SERVER = 'SonarQubeServer'
        PATH = "/opt/sonar-scanner/bin:${PATH}"
        ECR_REPO = '822426641221.dkr.ecr.ap-south-1.amazonaws.com/etc'
        AWS_REGION = 'ap-south-1'
        DEPLOYMENT_REPO = 'https://github.com/Naziya-2910/employee-directory.git'
        // Kubernetes manifest that Argo CD watches; its image line is updated on every build
        MANIFEST_FILE = 'k8s/deployment.yaml'
    }

    stages {

        // Code checkout
        stage('Checkout') {
            steps {
                git branch: 'main',
                    credentialsId: 'git-cred',
                    url: 'https://github.com/Naziya-2910/employee-directory.git'
            }
        }

        // Set the image tag for this build
        stage('Set Image Tag') {
            steps {
                script {
                    def shortHash = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()

                    env.IMAGE_TAG = "${BUILD_NUMBER}-${shortHash}"
                    echo "Using image tag: ${env.IMAGE_TAG}"
                }
            }
        }

        // Maven build
        stage('Maven Build') {
            steps {
                sh '''
                    docker run --rm \
                      -u $(id -u):$(id -g) \
                      -e HOME=/tmp \
                      -v "$PWD":/build -w /build \
                      maven:3.9-eclipse-temurin-21 \
                      mvn -q clean package -DskipTests -Dmaven.repo.local=/tmp/.m2
                '''
            }
        }

        // SonarQube code analysis
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv("${SONARQUBE_SERVER}") {
                    sh '''
                        sonar-scanner \
                          -Dsonar.projectKey=employee-directory \
                          -Dsonar.projectName=employee-directory \
                          -Dsonar.sources=src \
                          -Dsonar.java.binaries=target/classes
                    '''
                }
            }
        }

        // Build the Docker image
        stage('Build Docker Image') {
            steps {
                echo "Building image: ${IMAGE_NAME}:${IMAGE_TAG}"

                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
                sh "docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:latest"
            }
        }

        // Trivy image scan
        stage('Image Scan') {
            steps {
                sh "trivy image ${IMAGE_NAME}:${IMAGE_TAG} > report.txt"
            }
        }

        // Push image to ECR
        stage('Push to ECR') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-cred'
                ]]) {
                    sh '''
                        set -eu

                        aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin 822426641221.dkr.ecr.ap-south-1.amazonaws.com
                        docker tag "$IMAGE_NAME:$IMAGE_TAG" "$ECR_REPO:$IMAGE_TAG"
                        docker push "$ECR_REPO:$IMAGE_TAG"
                        docker tag "$IMAGE_NAME:$IMAGE_TAG" "$ECR_REPO:latest"
                        docker push "$ECR_REPO:latest"
                    '''
                }
            }
        }

        // Update the image tag in the GitOps repo so Argo CD deploys it
        stage('Update GitOps Repo') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'git-cred',
                    usernameVariable: 'GIT_USERNAME',
                    passwordVariable: 'GIT_PASSWORD'
                )]) {
                    sh '''
                        set -eu

                        ASKPASS="$WORKSPACE/git-askpass.sh"

                        cat > "$ASKPASS" <<'EOF'
#!/bin/sh
case "$1" in
    *Username*) printf '%s\\n' "$GIT_USERNAME" ;;
    *Password*) printf '%s\\n' "$GIT_PASSWORD" ;;
esac
EOF

                        chmod 700 "$ASKPASS"

                        export GIT_ASKPASS="$ASKPASS"
                        export GIT_TERMINAL_PROMPT=0

                        rm -rf gitops-repo

                        git clone "$DEPLOYMENT_REPO" gitops-repo

                        cd gitops-repo

                        echo "Before update:"
                        grep -n 'image:' "$MANIFEST_FILE"

                        sed -i -E \
                            "s|^([[:space:]]*image:[[:space:]]*).*|\\1${ECR_REPO}:${IMAGE_TAG}|" \
                            "$MANIFEST_FILE"

                        echo "After update:"
                        grep -n 'image:' "$MANIFEST_FILE"

                        git config user.email "jenkins@local"
                        git config user.name "Jenkins"

                        git add "$MANIFEST_FILE"

                        git commit \
                            -m "Update image tag to ${IMAGE_TAG} [skip ci]"

                        git push origin HEAD:main

                        rm -f "$ASKPASS"
                    '''
                }
            }
        }
    }
}
