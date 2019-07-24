extendConfiguration([
    dockerWithRunParameters: """\
        ${CFG.dockerWithRunParameters ?: ""} \
        -m ${CFG.constraintBuildRamLimit} \
        --storage-opt size=${CFG.constraintBuildHddLimit} \
        """])
MPLModule('Setup Container')