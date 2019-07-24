
int deploymentCounter = 0
def configPrefix = "deploy${deploymentCounter}"
while (CFG."deploy${deploymentCounter}Module") {
    def deployModuleName = CFG."deploy${deploymentCounter}Module"
    def cfg = extendConfiguration(CFG
        .findAll{it.key.startsWith(configPrefix)}
        .collectEntries {[
            "deploy${deployModuleName.capitalize()}${it.key[configPrefix.length()..-1].capitalize()}",
            it.value
        ]}
    )

    CFG = updateConfiguration()
    echo CFG.toMapString()

    MPLModule("${deployModuleName.capitalize()} Deploy")

    if (CFG.deployRelease) {
        if (!CFG.deployReleaseVersion) {
            error 'Release deployment requires a properly set up release version'
        } else {
            MPLModule("${deployModuleName.capitalize()} Release Deploy")
        }
    }

    removeConfigurationExtension(cfg)

    configPrefix = "deploy${++deploymentCounter}"
}