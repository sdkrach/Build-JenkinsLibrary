extendConfiguration([
    dockerBuildImage: "maven:${CFG.mavenVersion}-jdk-${CFG.mavenJdkVersion}",
    dockerWithRunParameters: """\
    ${CFG.dockerWithRunParameters ?: ""} \
    -v ${CFG.slaveHome}/.m2:/.m2:ro \
    -v ${CFG.emptySlaveDir}:/root/.m2:ro \
    -v ${CFG.mavenSettingsFile}:/settings.xml:ro \
    -e MAVEN_CONFIG=/tmp/.m2 \
    -e MAVEN_OPTS=-Duser.home=/tmp \
    """])

MPLModule('Setup Container')