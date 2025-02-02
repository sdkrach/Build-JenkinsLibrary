def call(body, sshName, webRoot, fallbackRecipient, buildImage = 'maven:3-jdk-11', buildLimitTime = 30, buildLimitRam = '4G', buildLimitHdd = '20G') {

	// mandatory framework stuff
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

	// evaluation of git build information
	boolean isMasterBranch = "$BRANCH_NAME" == 'master'
	echo "Is master branch: $isMasterBranch"
	boolean isPullRequest = !(env.CHANGE_TARGET == null)
	echo "Is pull request: $isPullRequest"

	// evaluation of build parameters
	String relativeArtifactsDir = "${config.updateSiteLocation}"
	final MANDATORY_PARAMS = ['webserverDir', 'updateSiteLocation']
	for (mandatoryParameter in MANDATORY_PARAMS) {
		if (!config.containsKey(mandatoryParameter) || config.get(mandatoryParameter).toString().trim().isEmpty()) {
			error "Missing mandatory parameter $mandatoryParameter"
		}
	}
	String defaultRecipient = config.containsKey('defaultRecipient') ? decodeEmailAddress(config.get('defaultRecipient')) : fallbackRecipient
	boolean skipCodeQuality = config.containsKey('skipCodeQuality') && config.get('skipCodeQuality').toString().trim().toBoolean()
	boolean skipNotification = config.containsKey('skipNotification') && config.get('skipNotification').toString().trim().toBoolean()
	boolean skipCleanup = config.containsKey('skipCleanup') && config.get('skipCleanup').toString().trim().toBoolean()
	boolean doReleaseBuild = params.Release.toString().toBoolean()
	String releaseVersion = params.ReleaseVersion
	if (doReleaseBuild && (releaseVersion == null || releaseVersion.trim().isEmpty())) {
		error 'A release build requires a proper release version.'
	}

	// archive release build
	if (doReleaseBuild) {
		currentBuild.rawBuild.keepLog(true)
	}

	try {
	
		pipeline {
		
			properties([
				// define parameters for parameterized builds
				parameters([
					booleanParam(defaultValue: false, name: 'Release', description: 'Set true for release build'),
					string(defaultValue: 'nightly', name: 'ReleaseVersion', description: 'Set version to be used for the release')
				]),
				// define log rotation
				buildDiscarder(logRotator(artifactDaysToKeepStr: '1', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '100')),
			])

			node('docker') {
				def workspace
				
				def slaveHome = "${env.SLAVE_HOME}"
				def slaveUid = "${env.SLAVE_USER_ID}"
				def slaveName = "${env.NODE_NAME}"

				stage ('Prepare') {
					deleteDir()
					workspace = pwd()
					sh "mkdir -p $slaveHome/.m2"
				}
				
				try {
				
					stage ('Checkout') {
						// scm is injected and configured by multi branch pipeline
						checkout scm
					}
					
					stage ('Build') {
						timeout(time: buildLimitTime, unit: 'MINUTES') {

							// inject maven config file
							configFileProvider(
								[configFile(fileId: 'fba2768e-c997-4043-b10b-b5ca461aff54', variable: 'MAVEN_SETTINGS')]) {
								
								def emptyDir = "/tmp/${env.BUILD_TAG}"
								try {
									sh "mkdir -p $emptyDir"
									// run maven build in docker container
									docker.image(buildImage).withRun("""\
										-u ${slaveUid} \
										-v ${workspace}:/ws:ro \
										-v ${slaveHome}/.m2:/.m2:ro \
										-v $emptyDir:/root/.m2:ro \
										-v $MAVEN_SETTINGS:/settings.xml:ro \
										-e MAVEN_CONFIG=/tmp/.m2 \
										-e MAVEN_OPTS=-Duser.home=/tmp \
										-m $buildLimitRam \
										--storage-opt size=$buildLimitHdd \
										--network proxy \
										-it \
										--entrypoint=/bin/cat \
									""") { c ->
										lock("m2-cache-$slaveName") {
											sh "docker exec ${c.id} cp -r /.m2 /tmp"
										}
										sh "docker exec ${c.id} cp -r /ws /tmp"
										sh "docker exec ${c.id} mvn -Dbnd.home.dir=/tmp -s /settings.xml -f /tmp/ws/pom.xml clean verify"
										sh "docker cp ${c.id}:/tmp/ws/. ${workspace}"
										if (!isPullRequest) {
											lock("m2-cache-$slaveName") {
												sh "docker cp ${c.id}:/tmp/.m2/. ${slaveHome}/.m2"
											}
										}
									}
								} finally {
									sh "rm -rf $emptyDir"
								}
							}

						}
					}

					stage ('Archive') {
						archiveArtifacts "${relativeArtifactsDir}/**/*"
					}

					stage ('Quality Metrics') {

						if (!skipCodeQuality) {

							if (!isPullRequest) {
								publishHTML([
									allowMissing: false,
									alwaysLinkToLastBuild: false,
									keepAll: false,
									reportDir: "${relativeArtifactsDir}/javadoc",
									reportFiles: 'overview-summary.html',
									reportName: 'JavaDoc',
									reportTitles: ''
								])
							}

							recordIssues([
								tool: checkStyle([
									pattern: '**/target/checkstyle-result.xml'
								])
							])

							junit([
								testResults: '**/surefire-reports/*.xml',
								allowEmptyResults: true
							])

							jacoco([
								execPattern: '**/target/*.exec',
								classPattern: '**/target/classes',
								sourcePattern: '**/src,**/src-gen,**/xtend-gen',
								inclusionPattern: '**/*.class',
								exclusionPattern: '**/*Test*.class'
							])
						} else {
							echo 'Skipping collection of code quality metrics'
						}
					}
					
					stage ('Deploy') {
						if (!isPullRequest && isMasterBranch) {
						
							sshPublisher(
								alwaysPublishFromMaster: true,
								failOnError: true,
								publishers: [
									sshPublisherDesc(
										configName: sshName,
										transfers: [
											sshTransfer(
												sourceFiles: "${relativeArtifactsDir}/**/*",
												cleanRemote: true,
												removePrefix: "${relativeArtifactsDir}",
												remoteDirectory: "${config.webserverDir}/nightly"
											)
										]
									)
								]
							)
						}
						
						if (doReleaseBuild) {
							configFileProvider(
								[configFile(fileId: '57dc902b-f5a7-49a9-aec3-98deabe48580', variable: 'COMPOSITE_SCRIPT')]) {
								def SCRIPTNAME = 'compositeCreate.sh'
								sh "rm -f ./$SCRIPTNAME"
								sh "cp $COMPOSITE_SCRIPT ./$SCRIPTNAME"
								try {
									sshPublisher(
										alwaysPublishFromMaster: true,
										failOnError: true,
										publishers: [
											sshPublisherDesc(
												configName: sshName,
												transfers: [
													sshTransfer(
														sourceFiles: "$SCRIPTNAME",
														remoteDirectory: "${config.webserverDir}"
													)
												]
											),
											sshPublisherDesc(
												configName: sshName,
												transfers: [
													sshTransfer(
														execCommand:
															"cd $webRoot/${config.webserverDir} && " +
															"mkdir -p releases/$releaseVersion && " +
															"cp -a nightly/* releases/$releaseVersion/ && " +
															"chmod +x $SCRIPTNAME && " +
															"./$SCRIPTNAME releases && " +
															"rm $SCRIPTNAME"
													)
												]
											)
										]
									)
								} finally {
									sh "rm ./$SCRIPTNAME"
								}
							}
						}

					}
				} finally {
					stage ('Cleanup') {
						if (!skipCleanup) {
							sh "ls -A1 | xargs -d '\n' rm -rf"
						}
					}
				}
			}
		}
	}
	catch (err) {
		currentBuild.result = 'FAILURE'
		if (err instanceof hudson.AbortException && err.getMessage().contains('script returned exit code 143')) {
			currentBuild.result = 'ABORTED'
		}
		if (err instanceof org.jenkinsci.plugins.workflow.steps.FlowInterruptedException && err.causes.size() == 0) {
			currentBuild.result = 'ABORTED'
		}
		throw err
	} finally {
		emailNotification(defaultRecipient, skipNotification)
	}

}

def decodeEmailAddress(configParameter) {
	return configParameter.contains('@') ? configParameter : new String(configParameter.decodeBase64())
}
