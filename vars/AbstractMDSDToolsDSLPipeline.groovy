import tools.mdsd.devops.MDSDToolsDSL

def call(body) {
    def dsl = new MDSDToolsDSL()
    
    body.delegate = dsl
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body()

    MPLInit(dsl.usingDocker, dsl.buildTool, dsl.deploymentModules, dsl.constraintBuild)
    
    echo "Pipeline configuration: ${dsl.configs.toMapString()}"

    AbstractMDSDToolsPipeline({}, dsl.configs)
}