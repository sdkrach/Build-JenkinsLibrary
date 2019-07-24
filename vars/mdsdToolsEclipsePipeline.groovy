
@groovy.transform.Field
def legacyConfig = [:]

def call(body) {
	echo 'This is a legancy pipeline command. Adapt to "MDSDToolsPipeline".'

	if (body instanceof Closure) {
		body.delegate = this.legacyConfig
		body.resolveStrategy = Closure.DELEGATE_FIRST
		body() 
	}

	MDSDToolsPipeline {
		skipQualityMetrics (this.legacyConfig.skipCodeQuality == null ? false : this.legacyConfig.skipCodeQuality)
		skipNotification (this.legacyConfig.skipNotification == null ? false : this.legacyConfig.skipNotification)
		skipCleanup (this.legacyConfig.skipCleanup == null ? false : this.legacyConfig.skipCleanup)
		
		if (this.legacyConfig.updateSiteLocation) {
			deployUpdatesite this.legacyConfig.updateSiteLocation
		}

		if (this.legacyConfig.webserverDir) {
			deployUpdatesiteProjectDir = this.legacyConfig.webserverDir
		}

		if (this.legacyConfig.defaultRecipient) {
			notifyDefault this.legacyConfig.defaultRecipient
		}

	}
}
