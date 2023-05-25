pipeline {
    agent any
    
    parameters {
        string(name: 'PROJECT_ID', defaultValue: '', description: 'GKE Project ID')
        string(name: 'REGION', defaultValue: '', description: 'GKE Region')
        string(name: 'CLUSTER_NAME', defaultValue: '', description: 'GKE Cluster Name')
        string(name: 'K8S_API_PROXY', defaultValue: '', description: 'Kubernetes API Proxy (e.g., http://localhost:8001)')
        string(name: 'NAMESPACE', defaultValue: '', description: 'Kubernetes Namespace')
        choice(name: 'MICROSERVICE', choices: [], description: 'Microservice to restart')
    }
    
    stages {
        stage('Retrieve Microservices') {
            steps {
                script {
                    def credentials = googleComputeCredentials(projectId: PROJECT_ID)
                    withCredentials([credentials]) {
                        sh 'gcloud container clusters get-credentials $CLUSTER_NAME --region $REGION --project $PROJECT_ID'
                        sh 'kubectl config use-context $CLUSTER_NAME'
                        def microservicesOutput = sh(script: 'kubectl get pods -n $NAMESPACE -o=jsonpath="{range .items[*]}{.metadata.name}{"\\n"}{end}"', returnStdout: true)
                        def microservicesList = microservicesOutput.split("\n")
                        params.MICROSERVICE = choice(choices: microservicesList, description: 'Microservice to restart')
                    }
                }
            }
        }
        
        stage('Restart Microservice') {
            when {
                expression { params.MICROSERVICE != '' }
            }
            steps {
                script {
                    def credentials = googleComputeCredentials(projectId: PROJECT_ID)
                    withCredentials([credentials]) {
                        sh "kubectl rollout restart deployment/${params.MICROSERVICE} -n $NAMESPACE"
                    }
                }
            }
        }
    }
}
