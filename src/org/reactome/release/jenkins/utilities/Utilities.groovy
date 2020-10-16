package org.reactome.release.jenkins.utilities

import groovy.json.JsonSlurper


/**
 * @author: jcook
 * This library is used by the Reactome Jenkins process. It contains commonly used  
 * functionality that are used by the Jenkinsfiles while running the Reactome Release.
 */

/**
 * Helper method for parsing out release number from Jenkins directory.
 * @return - Release number taken from directory (eg: 74)
 */
def getReleaseVersion() {
    return (pwd() =~ /Releases\/(\d+)\//)[0][1];
}

/**
 * Helper method for verifying that prerequisite steps have been run.
 * It queries the Jenkins API and checks that the 'result' property is equal to 'SUCCESS'.
 * An error will be thrown if the 'SUCCESS' message is not found.
 * @param stepPath - Path in local Jenkins directory to requisite step (eg: Relational-Database-Updates/Orthoinference)
 */
def checkUpstreamBuildsSucceeded(String stepPath) {
    def releaseVersion = getReleaseVersion()
    def statusUrl = httpRequest authentication: 'jenkinsKey', validResponseCodes: "${env.VALID_RESPONSE_CODES}", url: "${env.JENKINS_JOB_URL}/job/$releaseVersion/job/$stepPath/lastBuild/api/json"
    if (statusUrl.getStatus() == 404) {
        error("$stepPath has not yet been run. Please complete a successful build.")
    } else {
        def statusJson = new JsonSlurper().parseText(statusUrl.getContent())
        if(statusJson['result'] != "SUCCESS"){
            error("Most recent $stepPath build status: " + statusJson['result'] + ". Please complete a successful build.")
        }
    }
}

/**
 * Helper method for taking database dumps and gzipping the resultant file.
 * @param database - Name of database to be dumped
 * @param stepName - Name of release step currently being run
 * @param beforeOrAfter - Either 'before' or 'after' strings, denoting when the dump is being taken in the process.
 * @param username - MySQL username
 * @param password - MySQL password
 * @param host - MySQL host
 */
def takeDatabaseDumpAndGzip(String databaseName, String stepName, String beforeOrAfter, String databaseHost) {
    def timestamp = new Date().format("yyyy-MM-dd-HHmmss")
    def releaseVersion = getReleaseVersion()
    def filename = "${databaseName}_${releaseVersion}_${beforeOrAfter}_${stepName}_${timestamp}.dump"
    // The user and pass values come from a MySQL credentials 'secret' in Jenkinsfile calling the method.
    sh "mysqldump -u${user} -p${pass} -h${databaseHost} ${databaseName} > ${filename}"
    sh "gzip -f ${filename}"
}

/**
 * Helper method for sending emails that contain attachments.
 * @param emailSubject - Subject header for email that will be sent
 * @param emailBody - Text that will make up the body of the email that will be sent
 * @param emailAttachmentFilename - Name of file that will be sent as an attachment in the email
 */
def sendEmailWithAttachment(String emailSubject, String emailBody, String emailAttachmentFilename) {
    emailext (
            body: "${emailBody}",
            to: '$DEFAULT_RECIPIENTS',
            from: "${env.JENKINS_RELEASE_EMAIL}",
            subject: "${emailSubject}",
            attachmentsPattern: "**/${emailAttachmentFilename}"
    )
}

/**
 * Helper method for cloning or pulling a github repository.
 * @param repoName - Base name of github repository that exists in the Reactome github project.
 */
def cloneOrUpdateLocalRepo(String repoName) {
    if(!fileExists(repoName)) {
        sh "git clone ${env.REACTOME_GITHUB_BASE_URL}/${repoName}"
    } else {
        sh "cd ${repoName}; git pull"
    }
}

/**
 * Builds jar file to be executed by Jenkins.
 */
def buildJarFile() {
    sh "mvn clean compile assembly:single"
}
