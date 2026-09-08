pipeline {
    agent any

    tools {
        jdk 'JDK-8'
        maven 'Maven-3.8.9'
    }
    environment {
        APP_NAME        = 'suspicious-events-detector'
        REPO_URL        = 'https://github.com/yaseen-asaliya/suspicious-events-detector.git'
        REPO_BRANCH     = 'main'
        DOCKER_REGISTRY = 'docker.io/yaseenasaliya'
        K8S_NAMESPACE   = 'suspicious-events-detector'
    }
    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    triggers {
        githubPush()
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: env.REPO_BRANCH,
                    url: env.REPO_URL
                // credentialsId: 'github-creds' // i made the repo public, so u can view it without creds, but if u want to push to it, u need to set up a personal access token and use it here
                script {
                    env.IMAGE_TAG = sh(
                        script: 'git rev-parse --short=7 HEAD',
                        returnStdout: true
                    ).trim()
                    env.IMAGE = "${env.DOCKER_REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG}"
                }
                echo "Building commit: ${env.IMAGE_TAG}"
            }
        }
        stage('Build') {
            steps {
                sh 'mvn -B -ntp clean compile'
            }
        }
        stage('Test') {
            environment {
                TEST_DB = "ci-mysql-${BUILD_NUMBER}"
            }
            steps {
                sh '''
                    docker run -d --name ${TEST_DB} \
                        -e MYSQL_ROOT_PASSWORD=root \
                        -e MYSQL_DATABASE=detector \
                        -e MYSQL_USER=detector-user \
                        -e MYSQL_PASSWORD=detector-pass \
                        -p 3306 mysql:8.0
                    echo "Waiting for MySQL..."
                    ready=0
                    for i in $(seq 1 30); do
                        docker exec ${TEST_DB} mysqladmin ping -h 127.0.0.1 -uroot -proot --silent && ready=1 && break
                        sleep 2
                    done
                    if [ "$ready" != 1 ]; then
                        echo "MySQL did not become ready in time"
                        docker logs ${TEST_DB}
                        exit 1
                    fi
                    export MYSQL_HOST=127.0.0.1
                    export MYSQL_PORT=$(docker port ${TEST_DB} 3306/tcp | head -1 | awk -F: '{print $NF}')
                    export MYSQL_DATABASE=detector
                    export MYSQL_USERNAME=detector-user
                    export MYSQL_PASSWORD=detector-pass
                    mvn -B -ntp test
                '''
            }
            post {
                always {
                    sh 'docker rm -f ${TEST_DB} || true'
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }
        stage('Package') {
            steps {
                sh 'mvn -B -ntp package -DskipTests'
                archiveArtifacts artifacts: 'target/*.jar',
                                 fingerprint: true
            }
        }
        stage('Docker Build & Push') {
            steps {
                echo "Building Docker image: ${env.IMAGE}"
                sh "docker build -t ${env.IMAGE} ."
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-creds',
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {
                    sh '''
                        echo "$DOCKER_PASSWORD" | docker login \
                            -u "$DOCKER_USERNAME" --password-stdin
                        docker push ${IMAGE}
                    '''
                }
            }
            post {
                always {
                    sh 'docker logout || true'
                    sh 'docker rmi ${IMAGE} || true'
                }
            }
        }
        stage('Deploy to Kubernetes') {
            steps {
                script {
                    sh "kubectl apply -k k8s/"
                    sh """
                        kubectl set image \
                        deployment/${env.APP_NAME} \
                        ${env.APP_NAME}=${env.IMAGE} \
                        -n ${env.K8S_NAMESPACE}
                    """
                    try {
                        sh """
                            kubectl rollout status \
                            deployment/${env.APP_NAME} \
                            -n ${env.K8S_NAMESPACE} \
                            --timeout=5m
                        """
                    } catch (err) {
                        echo "Rollout failed - rolling back ${env.APP_NAME}"
                        sh "kubectl rollout undo deployment/${env.APP_NAME} -n ${env.K8S_NAMESPACE}"
                        sh "kubectl rollout status deployment/${env.APP_NAME} -n ${env.K8S_NAMESPACE} --timeout=2m"
                        error("Deployment of ${env.IMAGE} failed and was rolled back")
                    }
                }
            }
        }
        stage('Post-deploy Smoke Check') {
            steps {
                script {
                    def smokePod = "smoke-check-${env.BUILD_NUMBER}"
                    try {
                        sh """
                            kubectl run ${smokePod} \
                                --image=curlimages/curl:8.10.1 \
                                --restart=Never \
                                -n ${env.K8S_NAMESPACE} \
                                --command -- curl -fsS http://${env.APP_NAME}/health/is-ready
                            kubectl wait --for=jsonpath='{.status.phase}'=Succeeded \
                                pod/${smokePod} \
                                -n ${env.K8S_NAMESPACE} \
                                --timeout=30s
                        """
                    } finally {
                        sh "kubectl logs ${smokePod} -n ${env.K8S_NAMESPACE} || true"
                        sh "kubectl delete pod ${smokePod} -n ${env.K8S_NAMESPACE} --ignore-not-found"
                    }
                }
            }
        }
    }
    post {
        success {
            echo "CI/CD pipeline completed successfully."
            echo "Commit: ${env.IMAGE_TAG}"
        }
        failure {
            echo "CI/CD pipeline failed."
        }
        always {
            cleanWs()
        }
    }
}
