sshPublisher(
    alwaysPublishFromMaster: true,
    failOnError: true,
    publishers: [
        sshPublisherDesc(
            configName: CFG.deployUpdatesiteSshName,
            transfers: [
                sshTransfer(
                    sourceFiles: "${CFG.relativeArtifactsDir}/**/*",
                    cleanRemote: true,
                    removePrefix: "${CFG.relativeArtifactsDir}",
                    remoteDirectory: "${CFG.deployUpdatesiteProjectDir}/${CFG.deployUpdatesiteSubDir}"
                )
            ]
        )
    ]
)