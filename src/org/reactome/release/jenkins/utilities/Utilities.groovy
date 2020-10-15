package org.reactome.release.jenkins.utilities

import groovy.json.JsonSlurper

/**
 * Helper method for parsing out release number from Jenkins directory.
 * @return - Release number taken from directory (ex: 74)
 */
def getReleaseVersion()
{
    return (pwd() =~ /Releases\/(\d+)\//)[0][1];
}

def checkUpstreamBuildsSucceeded(String stepName, String currentRelease) {
    def statusUrl = httpRequest authentication: 'jenkinsKey', validResponseCodes: "${env.VALID_RESPONSE_CODES}", url: "${env.JENKINS_JOB_URL}/job/$currentRelease/job/$stepName/lastBuild/api/json"
    if (statusUrl.getStatus() == 404) {
        error("$stepName has not yet been run. Please complete a successful build.")
    } else {
        def statusJson = new JsonSlurper().parseText(statusUrl.getContent())
        if(statusJson['result'] != "SUCCESS"){
            error("Most recent $stepName build status: " + statusJson['result'] + ". Please complete a successful build.")
        }
    }
}