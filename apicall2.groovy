pipeline {
    agent any
    
    parameters {
        choice(name: 'certificateOption', choices: ['RenewAll', 'RenewSelected'], description: 'Choose the certificate renewal option')
        text(name: 'certificateList', defaultValue: '', description: 'Enter the list of certificates to renew (comma-separated)')
    }
    
    stages {
        stage('Certificate Renewal') {
            steps {
                script {
                    def certificateSerialNumbers = []
                    
                    if (params.certificateOption == 'RenewAll') {
                        // Get the list of all certificates from Keyfactor API
                        certificateSerialNumbers = getKeyfactorCertificates()
                    } else if (params.certificateOption == 'RenewSelected') {
                        // Split the comma-separated certificate list into an array
                        certificateSerialNumbers = params.certificateList.split(',')
                    }
                    
                    // Renew each certificate
                    certificateSerialNumbers.each { serialNumber ->
                        renewCertificate(serialNumber)
                    }
                }
            }
        }
        
        stage('Upload as Secrets') {
            steps {
                withCredentials([
                    usernamePassword(credentialsId: 'kubernetes-login', passwordVariable: 'KUBE_PASSWORD', usernameVariable: 'KUBE_USERNAME')
                ]) {
                    // Log in to the Kubernetes cluster
                    sh "kubectl login -u ${env.KUBE_USERNAME} -p ${env.KUBE_PASSWORD}"
                    
                    // Upload each renewed certificate as a secret
                    certificateSerialNumbers.each { serialNumber ->
                        uploadCertificateAsSecret(serialNumber)
                    }
                }
            }
        }
    }
}

def getKeyfactorCertificates() {
    // Make API call to Keyfactor to get the list of certificates
    // Replace <KEYFACTOR_API_ENDPOINT> with the actual Keyfactor API endpoint
    def response = sh(
        script: "curl -u <USERNAME>:<PASSWORD> -X GET <KEYFACTOR_API_ENDPOINT>",
        returnStdout: true
    )
    
    // Extract the certificate serial numbers from the response
    def certificates = parseJson(response)
    return certificates.collect { it.serialNumber }
}

def renewCertificate(serialNumber) {
    // Make API call to renew the certificate using the serial number
    // Replace <CERT_RENEWAL_API_ENDPOINT> with the actual certificate renewal API endpoint
    sh "curl -u <USERNAME>:<PASSWORD> -X POST <CERT_RENEWAL_API_ENDPOINT> -d 'serialNumber=${serialNumber}'"
}

def uploadCertificateAsSecret(serialNumber) {
    // Load the renewed certificate file
    // Replace <RENEWED_CERT_PATH> with the actual path to the renewed certificate file
    def certificateContent = readFile("<RENEWED_CERT_PATH>")
    
    // Create or update the Kubernetes secret
    sh "kubectl create secret generic ${serialNumber} --from-literal=certificate='${certificateContent}'"
}
