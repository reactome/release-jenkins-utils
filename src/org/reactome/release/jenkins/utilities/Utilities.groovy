package org.reactome.release.jenkins.utilities

import groovy.json.JsonSlurper

import java.nio.file.Files
import java.nio.file.Paths


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
 * Returns the current release version, decremented by 1.
 * @return - Previous release number, as determined by the directory (eg: 74 - 1 = 73)
 */
def getPreviousReleaseVersion() {
    return getReleaseVersion().toInteger() - 1;
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
    def timestamp = getTimestamp()
    def releaseVersion = getReleaseVersion()
    def databaseFilename = "${databaseName}_${releaseVersion}_${beforeOrAfter}_${stepName}_${timestamp}.dump"
    // The user and pass values come from a MySQL credentials 'secret' in Jenkinsfile calling the method.
    // When using this method, be sure to use the Jenkins 'withCredentials{}' block to instantiate the user/pass variables.
    takeDatabaseDump("${databaseName}", "${databaseFilename}", "${databaseHost}")
    sh "gzip -f ${databaseFilename}"
    return "${databaseFilename}.gz"
}

/**
 * Method for taking a MySQL database dump.
 * @param databaseName - String, name of database.
 * @param databaseFilename - String, name of dump file that will be made.
 * @param databaseHost - String, host of MySQL server.
 */
def takeDatabaseDump(String databaseName, String databaseFilename, String databaseHost) {
    def hostname = sh(script: 'hostname -f', returnStdout: true)
    def columnStatisticsParameter = ""
    if (databaseHost == 'curator.reactome.org' && databaseHost != hostname) {
        // Needed for interacting with a MySQL remote host less than MySQL 8 (curator.reactome.org is on MySQL 5)
        // https://www.mydatahack.com/mysqldump-error-unknown-table-column_statistics-in-information_schema-1109/
        columnStatisticsParameter = "--column-statistics=0"
    }
    sh "mysqldump -u${user} -p${pass} -h${databaseHost} ${columnStatisticsParameter} ${databaseName} > ${databaseFilename}"
}

/**
 * Method for storing a graph database folder into a tar archive.
 * @param graphDbFolder - String, name of graph db folder being archived
 * @param stepName - String, name of step that is being run currently. The archive will use it in its name.
 */
def createGraphDatabaseTarFile(String graphDbFolder, String stepName) {
    def timestamp = getTimestamp()
    sh "tar -zcvf ${stepName}_graph_database.dump_${timestamp}.tgz ${graphDbFolder}"
    sh "rm -r ${graphDbFolder}"
}

/**
 * Helper method for getting current time stamp. It will include the date and time in its name.
 * @return, String, date and time in yyyy-MM-dd-HHmmss format, where MM indicates month and mm indicates minute.
 */
def getTimestamp(){
    return new Date().format("yyyy-MM-dd-HHmmss")
}

/**
 * Helper method for replacing a database in MySQL. Uses command-line MySQL to drop/create a new database before
 * reconstituting the new database using a provided dump filename.
 * @param databaseToBeReplaced - String, name of MySQL database that will be replaced by replacingDatabaseFilename.
 * @param replacingDatabaseFilename - String, name of MySQL database dump file that will replace databaseToBeReplaced.
 */
def replaceDatabase(String databaseToBeReplaced, String replacingDatabaseFilename) {
    sh "mysql -u${user} -p${pass} -e \'drop database if exists ${databaseToBeReplaced}; create database ${databaseToBeReplaced}\'"
    sh "zcat  ${replacingDatabaseFilename} | mysql -u${user} -p${pass} ${databaseToBeReplaced}"
}

/**
 * Helper method for sending emails that contain attachments.
 * @param emailSubject - Subject header for email that will be sent.
 * @param emailBody - Text that will make up the body of the email that will be sent.
 * @param emailAttachmentFilename - Name of file that will be sent as an attachment in the email.
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
 * Helper method for sending emails.
 * @param emailSubject - Subject header for email that will be sent.
 * @param emailBody - Text that will make up the body of the email that will be sent.
 */
def sendEmail(String emailSubject, String emailBody) {
    emailext (
            body: "${emailBody}",
            to: '$DEFAULT_RECIPIENTS',
            from: "${env.JENKINS_RELEASE_EMAIL}",
            subject: "${emailSubject}"
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

def cloneOrUpdateLocalRepoWithUserToken(String repoName) {
    withCredentials([usernamePassword(credentialsId: 'githubToken', usernameVariable: 'user', passwordVariable: 'token')]) {
        if (!fileExists(repoName)) {
            sh "git clone https://${user}:${token}@${env.REACTOME_GITHUB_BASE_URL_NO_HTTPS}/${repoName}"
        } else {
            sh "cd ${repoName}; git pull"
        }
    }
}

/**
 * Builds jar file to be executed by Jenkins.
 */
def buildJarFileWithAssemblySingle() {
    sh "mvn clean compile assembly:single"
}

def buildJarFileWithPackage() {
    sh "mvn clean package"
}

def buildJarFile() {
    buildJarFileWithAssemblySingle()
}

/**
 * Method for archiving and removing files generated by Jenkins build steps.
 * @param stepName - Name of step being archived
 * @param dataFiles - Data files to be moved to data folder
 * @param logFiles  - Log files to be moved to the logs folder
 */
def cleanUpAndArchiveBuildFiles(String stepName, List dataFiles, List logFiles, List foldersToDelete) {
    def releaseVersion = getReleaseVersion()
    def s3Path = "${env.S3_RELEASE_DIRECTORY_URL}/${releaseVersion}/${stepName}"

    sh "mkdir -p databases/ data/ logs/"
    List dbFiles = findFiles(glob: "*_${releaseVersion}_*.dump.gz")
    dbFiles.addAll(findFiles(glob: "${stepName}_graph_database.dump*tgz"))

    moveFilesToFolder("databases", dbFiles)
    moveFilesToFolder("data", dataFiles)
    moveFilesToFolder("logs", logFiles)

    gzipFolderContents("data/")
    gzipFolderContents("logs/")

    sh "aws s3 --no-progress --recursive cp databases/ ${s3Path}/databases/"
    sh "aws s3 --no-progress --recursive cp data/ ${s3Path}/data/"
    sh "aws s3 --no-progress --recursive cp logs/ ${s3Path}/logs/"


    foldersToDelete.addAll("databases", "data", "logs")
    deleteFolders(foldersToDelete)
}

/**
 * Helper method for moving a list of files to a destination, such as 'data' or 'logs'.
 * @param folder - Destination folder
 * @param files - List of files to be moved to destination folder
 */
def moveFilesToFolder(String folder, List files) {
    for (def file : files) {
        def path = (file instanceof org.jenkinsci.plugins.pipeline.utility.steps.fs.FileWrapper) ?
                       file.path :
                       file.toString()
    
        def base = new File(path).getName()
        def dest = "${folder}/${base}"
    
        sh """
            if [ "\$(readlink -f '${path}')" != "\$(readlink -f '${dest}')" ]; then
                mv --backup=numbered -f ${path} '${folder}'
            fi
        """
    }
}


/**
 * Helper method for recursively gzipping contents of a folder. Checks that file is populated before trying.
 * @param folder - String, name of folder being recursively gzipped.
 */
def gzipFolderContents(String folder) {
    // sh "if [ ! -z ${folder} ]; then gzip -rf ${folder}; fi"
    // Only zip things that are not already compressed. Blindly zipping everything will cause problems for
    // other parts of the Release that want to compare a new file to an old file and are not expecting that
    // they may need to decompress a file twice.

    sh "for f in \$(find ./${folder}/ -not -iregex \".*\\.gz\" -not -iregex \".*\\.tgz\" -not -iregex \".*\\.zip\" -not -iregex \".*\\.bz\" -not -iregex \".*\\.bz2\") ; do if [ -f \"\$f\" ] ; then gzip \$f ; fi ; done"
}

/**
 * Helper method for deleting folders.
 * @param foldersToDelete - List, folders to be deleted as part of clean-up.
 */
def deleteFolders(List foldersToDelete) {
    for (String folder : foldersToDelete) {
        sh "rm -rf ${folder}"
    }
}

/**
 * Method that takes in two folder names that have their contents compared by line count. For each file that is found
 * in both folders, it takes a line count of them and outputs it, along with the difference between them.
 * @param firstFolderName - String, name of first folder whose contents will be compared with the contents of the second folder
 * @param secondFolderName - String, name of second folder whose contents will be compared with the contents of the first folder
 * @param currentDir - String, current directories' absolute path. Necessary for getting line counts using 'Files' library.
 */
def outputLineCountsOfFilesBetweenFolders(String firstFolderName, String secondFolderName, String currentDir) {

    // Gets list of files in directories
    def firstFiles = findFiles(glob: "${firstFolderName}/*")
    def secondFiles = findFiles(glob: "${secondFolderName}/*")

    // Output number of files in each directory
    print "Total files in ${firstFolderName}: " + "\t" + firstFiles.size() + "\nTotal files in ${secondFolderName}: " + "\t" + secondFiles.size()
    print "Line count differences between ${firstFolderName} and ${secondFolderName} files:"

    for (def firstFile : firstFiles) {
        try {
            def secondFile = "${secondFolderName}" + "/" + firstFile.getName()
            // Get line counts of the file found in both the first and second folder
            long firstFileLineCount = Files.lines(Paths.get(currentDir, firstFile.toString())).count()
            long secondFileLineCount = Files.lines(Paths.get(currentDir, secondFile.toString())).count()
            // Get difference between line counts
            long lineCountDifference = firstFileLineCount - secondFileLineCount
            // Output the line counts and difference
            print firstFile.toString() + "\t" + firstFileLineCount + "\n" + secondFile.toString() + "\t" + secondFileLineCount + "\nDifference: " + lineCountDifference
        } catch (Exception e) {
            e.printStackTrace()
            echo("\nWARNING: ${firstFile} does not exist in the previous release. This might be due to the name changing or the file being removed. Please check manually.")
        }
    }
}
