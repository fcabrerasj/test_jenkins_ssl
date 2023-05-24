pipeline {
    agent any

    parameters {
        string(name: 'certificate', defaultValue: '', description: 'PEM certificate content')
        string(name: 'key', defaultValue: '', description: 'Key content')
        booleanParam(name: 'deleteExisting', defaultValue: false, description: 'Delete existing certificate secret')
        string(name: 'gcloudServiceKey', defaultValue: '', description: 'Google Cloud service account key JSON content')
        string(name: 'gcloudProject', defaultValue: '', description: 'Google Cloud project ID')
        string(name: 'kubernetesNamespace', defaultValue: 'default', description: 'Kubernetes namespace')
    }

    stages {
        stage('Delete Existing Certificate') {
            when {
                expression {
                    params.deleteExisting
                }
            }
            steps {
                sh "kubectl delete secret my-certificate -n ${params.kubernetesNamespace}"
            }
        }

        stage('Setup Environment') {
            steps {
                script {
                    withCredentials([string(credentialsId: 'gcloud-service-key', variable: 'GCLOUD_SERVICE_KEY')]) {
                        writeFile file: 'gcloud-service-key.json', text: params.gcloudServiceKey
                        sh 'gcloud auth activate-service-account --key-file=gcloud-service-key.json'
                        sh "gcloud config set project ${params.gcloudProject}"
                        sh 'gcloud container clusters get-credentials <cluster-name>'
                    }
                }
            }
        }

        stage('Upload Certificate to Kubernetes') {
            steps {
                script {
                    def certificatePath = "${env.WORKSPACE}/certificate.pem"
                    def keyPath = "${env.WORKSPACE}/key.pem"
                    writeFile file: certificatePath, text: params.certificate
                    writeFile file: keyPath, text: params.key

                    loadCertificateSecret(certificatePath, keyPath)
                }
            }
        }
    }
}

def loadCertificateSecret(String certificatePath, String keyPath) {
    script {
        sh "keytool -importcert -alias my-certificate -keystore keystore.p12 -storepass changeit -file ${certificatePath}"
        sh "keytool -importkeystore -srckeystore keystore.p12 -srcstorepass changeit -srcalias my-certificate -destkeystore keystore.jks -deststoretype JKS -deststorepass changeit"

        def certificateBase64 = sh(
            script: "base64 keystore.jks",
            returnStdout: true
        ).trim()

        sh "kubectl create secret generic my-certificate --from-file=keystore.jks=${certificatePath} -n ${params.kubernetesNamespace}"

        println "Certificate loaded into Kubernetes as a secret."
    }
}
