// This Jenkinsfile is used by Jenkins to run the 'SearchIndexer' step of Reactome's release.
// It requires that the 'DiagramConverter' step has been run successfully before it can be run.

import org.reactome.release.jenkins.utilities.Utilities

// Shared library maintained at 'release-jenkins-utils' repository.
def utils = new Utilities()

pipeline{
	agent any

        environment {
		ECR_URL = 'public.ecr.aws/reactome/search-indexer'
	        CONT_NAME = 'search_indexer_container'
        }
	
	stages{
		// This stage checks that the upstream project 'DiagramConverter' was run successfully.
		stage('Check DiagramConverter build succeeded'){
			steps{
				script{
                			utils.checkUpstreamBuildsSucceeded("File-Generation/job/DiagramConverter/")
				}
			}
		}
		
		stage('Pull diagram exporter Docker image') {
			steps{
				script {
                			sh "docker pull ${ECR_URL}:latest"
					sh """
						if docker ps -a --format '{{.Names}}' | grep -Eq '${CONT_NAME}'; then
							docker rm -f ${CONT_NAME}
						fi
					"""
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
						sh "mkdir -p output && rm -rf output/*"
						sh "mdkir -p output/icons"
						sh """
                                                      docker run \\
						         -v scripts:/opt/search-indexer/scripts
						         -v ${env.ICONS_ABS_PATH}:/data/icons:ro \\
                                                         -v ${env.ABS_DOWNLOAD_PATH}/${releaseVersion}/ehld/:/data/ehld:ro \\
							 -v output:/opt/search-indexer/output \\
						         --net=host \\
						         --name ${CONT_NAME} \\
						         ${ECR_URL}:latest \\
	                                                 /bin/bash -c "./scripts/run-indexer.sh solrpass=${solrPass} neo4jpass=${neo4jPass} iconsdir=/data/icons ehlddir=/data/ehld/ maildest=${env.RELEASE_DEVELOPER_EMAIL} && mv ebeye*gz ./output/ && mv *txt output/icons/ && mv sitemap* output/"
						   """
						}
					}
				}
			}
		}

		// Optimise indexes to reduce memory usage and boost performance
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
				dir(output) {
				    sh "gzip ebeye*"
				    sh "cp ebeye*gz ${env.ABS_DOWNLOAD_PATH}/${releaseVersion}/"
				    sh "gunzip ebeye*gz"
				}
				// Archives Icons folder before moving to download/XX folder.
				dir(output){
				    sh "tar -zcvf icons-v${releaseVersion}.tar ${iconsFolder}"
				    sh "mv ${iconsFolder} ${env.ABS_DOWNLOAD_PATH}/${releaseVersion}/"
				}
		        }
		    }
		}
		// Uses 'changeSiteMapFiles.sh' script to move site_map files to the proper location and set appropriate permissions.
		stage('Post: Update sitemap files and restart Solr') {
		    steps{
		        script{
				dir(output) {
				   sh "sudo bash ../scripts/changeSiteMapFiles.sh"
				   sh "sudo service solr stop"
				   sh "sudo service solr start"
				}
		        }
		    }
		}
		// Archive everything on S3.
		stage('Post: Archive Outputs'){
			steps{
				script{
					def releaseVersion = utils.getReleaseVersion()
					def dataFiles = ["output/ebeye.xml", "output/ebeyecovid.xml", "outuput/icons-v${releaseVersion}.tar"]
					def logFiles = []
					def foldersToDelete = []
					utils.cleanUpAndArchiveBuildFiles("search_indexer", dataFiles, logFiles, foldersToDelete)
				}
			}
		}
	}
}
