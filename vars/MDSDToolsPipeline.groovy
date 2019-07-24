def call(body) {
    AbstractMDSDToolsDSLPipeline {
        agent_label = 'docker'

        buildWithMaven {
            version = '3.6.0'
            jdkVersion = 11
        }

        constraintBuild {
            timeLimitMinutes = 30
            ramLimit = '4G'
            hddLimit = '20G'
        }
        
        skipDeploy ("${this.BRANCH_NAME}" != 'master')
        skipNotification ("${this.BRANCH_NAME}" != 'master')
        
        deployUpdatesiteSshName 'web'
        deployUpdatesiteRootDir '/home/deploy/html'
        deployUpdatesiteSubDir ("${this.BRANCH_NAME}" == 'master' ? 'nightly': "branches/${this.BRANCH_NAME}")
        deployUpdatesiteProjectDir this.scm.userRemoteConfigs[0].url.replaceFirst(/^.*\/([^\/]+?).git$/, '$1').toLowerCase()
             
        notifyDefault 'bWRzZC10b29scy1idWlsZEBpcmEudWthLmRl'

        body.delegate = delegate
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body()
    }
}