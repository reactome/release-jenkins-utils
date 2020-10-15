package org.reactome.release.jenkins.utilities

import groovy.json.JsonSlurper

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
def takeDatabaseDumpAndGzip(String database, String stepName, String beforeOrAfter, String username, String password, String host) {
    def timestamp = new Date().format("yyyy-MM-dd-HHmmss")
    def releaseVersion = getReleaseVersion()
    def filename = "${database}_${releaseVersion}_${beforeOrAfter}_${stepName}_${timestamp}.dump"
    sh "mysqldump -u${username} -p${password} -h${host} ${database} > ${filename}"
    sh "gzip -f ${filename}"
}

def sendEmailWithAttachment(String emailSubject, String emailBody, String emailAttachment) {
    emailext (
            body: "${emailBody}",
            to: '$DEFAULT_RECIPIENTS',
            from: "${env.JENKINS_RELEASE_EMAIL}",
            subject: "${emailSubject}",
            attachmentsPattern: "**/${emailAttachment}"
    )
}