package tools.mdsd.devops

class MDSDToolsDSL implements Serializable {
    Map configs = [:]
    Set deploymentModules = []
    String buildTool = ''
    boolean usingDocker = true
    boolean constraintBuild = false
    int deploymentCounter = 0

    def deployUpdatesite(String folderName) {
        this.deployUpdatesite {
            artifactsDir = folderName
        }
    }

    def methodMissing(String name, args) {
        def config = [:]
        if (args.length == 1) {
            if (args[0] instanceof Closure) {
                args[0].delegate = config
                args[0].resolveStrategy = Closure.DELEGATE_FIRST
                args[0]()   
            
                if (name.startsWith("deploy")) {
                    def deploymentName = "deploy${deploymentCounter++}"
                    configs << ["${deploymentName}Module": name[6..-1].toLowerCase()]
                    configs += config.collectEntries { ["${deploymentName}${it.key.capitalize()}", it.value]}
                    if (config.artifactsDir && !(configs.archiveArtifactsDir)) {
                        configs.archiveArtifactsDir = config.artifactsDir
                    }
                    deploymentModules << name[6..-1].toLowerCase()
                } else if (name.startsWith("buildWith")) {
                    buildTool = name[9..-1].toLowerCase()
                    configs += config.collectEntries { ["${buildTool}${it.key.capitalize()}", it.value]}
                } else if (name == "constraintBuild") {
                    constraintBuild = true
                    configs += config.collectEntries { ["constraintBuild${it.key.capitalize()}", it.value]}
                }

            } else {
                configs[name] = args[0]
            }
        } else if (args.length == 0) {
               configs[name] = true
        } else {
            throw new MissingMethodException(name, this.class, args)
        }
    }

    @NonCPS
    def propertyMissing(String name) {
        configs[name]
    }

    @NonCPS
    def propertyMissing(String name, value) {
        configs[name] = value
    }  
}
