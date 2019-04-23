// SPDX-License-Identifier: MIT

/*
 *  Copyright (c) 2019 MBition GmbH
 */

package utils
import com.lesfurets.jenkins.unit.global.lib.SourceRetriever

/**
 * Get shared library from local as well as remote git
 * Refer https://github.com/jenkinsci/JenkinsPipelineUnit/pull/75
 */

class TestSourceRetriver implements SourceRetriever {
	private def localDir = new File('.')
	List<URL> retrieve(String repository, String branch, String targetPath) {
		if (localDir.exists()) {
			return [localDir.toURI().toURL()]
		}
		throw new IllegalStateException("Cannot find $localDir!")
	}
}