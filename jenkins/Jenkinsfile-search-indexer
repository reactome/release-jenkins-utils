// This Jenkinsfile is used by Jenkins to run the 'SearchIndexer' step of Reactome's release.
// It requires that the 'DiagramConverter' step has been run successfully before it can be run.

import org.reactome.release.jenkins.utilities.Utilities

// Shared library maintained at 'release-jenkins-utils' repository.
def utils = new Utilities()

pipeline{
	agent any

	stages{
		// This stage checks that the upstream project 'DiagramConverter' was run successfully.
		stage('Check DiagramConverter build succeeded'){
			steps{
				script{
                			utils.checkUpstreamBuildsSucceeded("File-Generation/job/DiagramConverter/")
				}
			}
		}
		stage('Checkout'){
            		steps{
                		// Get some code from a GitHub repository
                		git branch: 'main', url: 'https://github.com/reactome/search-indexer.git'
            		}
        	}
		// This stage builds the jar file using maven.
		stage('Setup: Build jar file'){
			steps{
				script{
					sh "mvn clean package -PReactome-Server -DskipTests"
				}
			}
		}
		// Execute the jar file, producing data-export files. This step uses a bash script, 'run-indexer.sh' that is found in the 'scripts' folder.
		// This step requires both the neo4j and solr credentials.
		stage('Main: Run Search-Indexer'){
			steps{
				script{
					def releaseVersion = utils.getReleaseVersion()
					withCredentials([usernamePassword(credentialsId: 'neo4jUsernamePassword', passwordVariable: 'neo4jPass', usernameVariable: 'neo4jUser')]){
						withCredentials([usernamePassword(credentialsId: 'solrUsernamePassword', passwordVariable: 'solrPass', usernameVariable: 'solrUser')]){
							sh "./scripts/run-indexer.sh solrpass=${solrPass} neo4jpass=${neo4jPass} iconsdir=${env.ICONS_ABS_PATH} ehlddir=${env.ABS_DOWNLOAD_PATH}/${releaseVersion}/ehld/ maildest=${env.RELEASE_DEVELOPER_EMAIL}"
						}
					}
				}
			}
		}

		// Optimise indexes to reduce memory usage and boost performances
        stage('Main: Optimize SOLR cores'){
            steps{
                script{
                    withCredentials([usernamePassword(credentialsId: 'solrUsernamePassword', passwordVariable: 'solrPass', usernameVariable: 'solrUser')]){
                        sh "curl --user ${solrUser}:${solrPass} 'http://localhost:8983/solr/reactome/update?optimize=true&maxSegments=1'"
                        sh "curl --user ${solrUser}:${solrPass} 'http://localhost:8983/solr/target/update?optimize=true&maxSegments=1'"
                    }""
                }
            }
        }

		// Gzips both ebeye.xml and ebeye-covid.xml files, and then moves them, along with Icons folder, to the downloads folder.
		stage('Post: Move ebeye and Icons txt files to download'){
		    steps{
		        script{
				def releaseVersion = utils.getReleaseVersion()
				def iconsFolder = "icons/"
				// Gzip ebeye.xml and ebeye-covid.xml files before moving them to download/XX.
				sh "gzip ebeye*"
				sh "cp ebeye*gz ${env.ABS_DOWNLOAD_PATH}/${releaseVersion}/"
				sh "gunzip ebeye*gz"
				// Archives Icons folder before moving to download/XX folder.
				sh "mkdir -p ${iconsFolder}"
				sh "mv *txt ${iconsFolder}"
				sh "tar -zcvf icons-v${releaseVersion}.tar ${iconsFolder}"
				sh "mv ${iconsFolder} ${env.ABS_DOWNLOAD_PATH}/${releaseVersion}/"
		        }
		    }
		}
		// Uses 'changeSiteMapFiles.sh' script to move site_map files to proper location and set appropriate permissions.
		stage('Post: Update sitemap files and restart Solr') {
		    steps{
		        script{
				sh "sudo bash scripts/changeSiteMapFiles.sh"
				sh "sudo service solr stop"
				sh "sudo service solr start"
		        }
		    }
		}
		// Archive everything on S3.
		stage('Post: Archive Outputs'){
			steps{
				script{
					def releaseVersion = utils.getReleaseVersion()
					def dataFiles = ["ebeye.xml", "ebeyecovid.xml", "icons-v${releaseVersion}.tar"]
					def logFiles = []
					def foldersToDelete = []
					utils.cleanUpAndArchiveBuildFiles("search_indexer", dataFiles, logFiles, foldersToDelete)
				}
			}
		}
	}
}
