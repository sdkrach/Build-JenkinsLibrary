if (CFG.deployUpdatesiteSshName) {
    if (CFG.deployUpdatesiteArtifactsDir) {
        extendConfiguration([
            relativeArtifactsDir: CFG.deployUpdatesiteArtifactsDir
        ])
    } else {
        //TODO lookup updatesite in project
    }
    MPLModule("SSH Updatesite Deploy")
}