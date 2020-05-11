// SPDX-License-Identifier: MIT

/*
 *  Copyright (c) 2019 MBition GmbH
 */

package com.daimler.pipeliner

import com.daimler.pipeliner.Logger

/**
 * This class provides helper functions to improve Jenkins scripting
 *
 * @author Toni Kauppinen
 */

public class ScriptUtils {
    /**
     * The script object instance from Jenkins
     */
    private def script

    /**
     * The env object instance from Jenkins
     */
    private def env

    /**
     * The artifactory object instance from Jenkins
     */
    private def artifactory = null

    /**
     * Internal environment load command
     *
     * Loads environment from .env-<>.txt located in starting directory
     * HOSTNAME is used here to distinguish between native and docker environment
     *
     * NOTE: Single extra line-ending included due to occasional termination exit
     * from jenkins script. In theory it should not be needed though.
     */
    private final String ENV_LOAD_CMD = '''
    export SCRIPTUTILSPWD=$PWD
    if [ -f ".env-${HOSTNAME}.txt" ]; then
        while read line; do
            export "$line" 2>/dev/null
        done < .env-${HOSTNAME}.txt
    fi

    '''

    /**
     * Internal environment save command
     *
     * Saves environment to .env.txt in starting directory
     * HOSTNAME is used here to distinguish between native and docker environment
     *
     * NOTE: Single extra line-ending included due to occasional termination exit
     * from jenkins script. In theory it should not be needed though.
     */
    def ENV_SAVE_CMD = '''
    { set +x; } 2>/dev/null
    cd $SCRIPTUTILSPWD
    unset SCRIPTUTILSPWD
    env|grep -ve "^_=" -ve "^PWD=" -ve "^OLDPWD=" -ve "^SHLVL=" -ve "^SHELL=" -ve "^[[:lower:]]" -ve "^PIP_" > .env-${HOSTNAME}.txt

    '''

    /**
     * Constructor
     *
     * @param script Reference to the Jenkins scripted environment
     * @param env Reference to the Jenkins environment
     */
    public ScriptUtils(def script,def env) {
        this.script = script
        this.env = env
    }

    /**
     * Initializes Artifactory server instance
     *
     * @return Artifactory server instance or null in case of failure
     */
    def initArtifactory() {
        String artifactoryUrl

        if (this.artifactory == null) {
            artifactoryUrl = this.env.ARTIFACTORY_URL ? this.env.ARTIFACTORY_URL : ""
            def server = null
            if (artifactoryUrl != "") {
                Logger.info("Artifactory: " + artifactoryUrl)
                server = this.script.Artifactory.newServer(url: artifactoryUrl)
            } else {
                Logger.info("Artifactory: default-artifactory-server-id")
                server = this.script.Artifactory.server("default-artifactory-server-id")
            }

            server.credentialsId = getArtifactoryCredentialsFromEnvironment()
            server.bypassProxy = true
            this.artifactory = server
        }
        return this.artifactory
    }

    /**
     * Publishes build info to artifactory for a given set of uploaded artifacts
     * See details:
     * https://javadoc.jenkins.io/plugin/artifactory/org/jfrog/hudson/pipeline/common/types/buildInfo/BuildInfo.html
     *
     * @param buildInfo Build info data
     */
    def publishBuildInfoToArtifactory(def buildInfo) {
        initArtifactory()
        this.artifactory.publishBuildInfo(buildInfo)
    }

    /**
     * Enables interactive promotion feature for Jenkins Artifactory plugin.
     * Adds `Artifactory Release Promotion` button to Jenkins user interface for current build.
     */
    def enableInteractiveArtifactPromotion(){
        initArtifactory()
        def promotionConfig = [
                'buildName'          : this.env.JOB_NAME.replaceAll('/',' :: '),
                'buildNumber'          : this.env.BUILD_NUMBER,
        ]
        this.script.Artifactory.addInteractivePromotion(server: this.artifactory, promotionConfig: promotionConfig)
    }

    /**
     * Upload files to artifactory server
     * See details:
     * https://javadoc.jenkins.io/plugin/artifactory/org/jfrog/hudson/pipeline/common/types/buildInfo/BuildInfo.html
     *
     * @param target Upload location in Artifactory
     * @param pattern A pattern that defines the files to upload
     * @return BuildInfo for artifactory
     */
    def uploadToArtifactory(String pattern, String target, boolean flat = false) {
        initArtifactory()
        def uploadSpec = """{ "files": [ { "pattern": "${pattern}", "target": "${target}", "excludePatterns": ["*.sha1","*.md5","*.sha256","*.sha512"], "flat": "${flat}" } ] }"""
        return this.artifactory.upload(uploadSpec)
    }

    /**
     * Download files from artifactory server with defined downloadSpec
     * See details:
     * https://javadoc.jenkins.io/plugin/artifactory/org/jfrog/hudson/pipeline/common/types/buildInfo/BuildInfo.html
     *
     * @param target Download location in Artifactory
     * @param pattern A pattern that defines the files to download
     * @return BuildInfo for artifactory
     */
    def downloadFromArtifactory(String pattern, String target) {
        initArtifactory()
        def downloadSpec = """{
            "files":[
                        {
                            "pattern": \"${pattern}\",
                            "target": \"${target}\"
                        }
                    ]
            }"""

        return this.artifactory.download(downloadSpec)
    }

    /**
     * Copy files in artifactory to another location
     *
     * @param from From path/file
     * @param to Destination path/file
     */
    def copyInArtifactory(String from, String to) {
        initArtifactory()

        script.withCredentials([script.usernameColonPassword(credentialsId: this.artifactory.credentialsId, variable: "ARTIFACTORY_USERPASS")]) {
            String urlCmd = "'${this.artifactory.getUrl()}/api/copy/${from}?to=${to}'"
            String curlCmd = "curl --silent --show-error -u ${script.ARTIFACTORY_USERPASS} -X POST ${urlCmd}"

            Logger.info("Copy artifact with the curl cmd '${curlCmd}'")
            shWithStdout(curlCmd)
        }
    }

    /**
     * Move files in artifactory to another location
     *
     * @param from From path/file
     * @param to Destination path/file
     */
    def moveInArtifactory(String from, String to) {
        initArtifactory()

        script.withCredentials([script.usernameColonPassword(credentialsId: this.artifactory.credentialsId, variable: "ARTIFACTORY_USERPASS")]) {
            String urlCmd = "'${this.artifactory.getUrl()}/api/move/${from}?to=${to}'"
            String curlCmd = "curl --silent --show-error -u ${script.ARTIFACTORY_USERPASS} -X POST ${urlCmd}"

            Logger.info("Move artifact with the curl cmd '${curlCmd}'")
            shWithStdout(curlCmd)
        }
    }

    /**
     * Check if location exists in artifactory
     *
     * @param url of location
     */
    Boolean checkInArtifactory(String url) {
        initArtifactory()

        Integer status = -1

        script.withCredentials([script.usernameColonPassword(credentialsId: this.artifactory.credentialsId, variable: "ARTIFACTORY_USERPASS")]) {
            String cmd =    "function check { " +
                            "if curl -u ${script.ARTIFACTORY_USERPASS} --output /dev/null --silent --head --fail \$1; " +
                            "then return 0; " +
                            "else return -1; fi; }; " +
                            "check ${url}"
            status = shWithStatus cmd
        }
        if (status == 0) {
            Logger.info("URL  " + url + " is found")
            return true
        }
        return false
    }


    /**
     * Initialize internal Artifactory credential id from environment
     * @return artifactoryCredentialsId
     */
    def getArtifactoryCredentialsFromEnvironment() {
        String artifactoryCredentialsId = ""
        try {
            artifactoryCredentialsId = this.env.ARTIFACTORY_CREDENTIALS_ID ? this.env.ARTIFACTORY_CREDENTIALS_ID : ""
            if (artifactoryCredentialsId.isEmpty()) {
                def split = this.env.JOB_NAME.tokenize("./")
                artifactoryCredentialsId = split[0] + "-artifactory"
            }
        } catch (NullPointerException) {
            Logger.warn("Artifactory credential reading failed")
        }
        Logger.info("Artifactory credentials: " + artifactoryCredentialsId)
        return artifactoryCredentialsId
    }

    /**
     * Run jenkins sh script and reuse the environment variables from other
     * runs of this command. This will allow development of stages with
     * environment setup scripts that use "export"
     *
     * @param A command that is to be executed
     * @param An optional shell to be used (default bash)
     */
    def sh(String cmd, def shell="bash") {

        def internal_cmd = "#!/bin/" + shell + "\n"
        internal_cmd += ENV_LOAD_CMD

        //Set tracing and errors on as we want to see the actual cmd
        internal_cmd += "set -xe\n"
        internal_cmd += cmd
        //Jenkins gotcha: must include a new line at the end!
        internal_cmd += "\n"

        internal_cmd += ENV_SAVE_CMD

        return this.script.sh (
            script: internal_cmd
        )
    }

    /**
     * Run jenkins sh script and return output as string
     *
     * @param A command that is to be executed
     * @param An optional shell to be used (default bash)
     */
    String shWithStdout(String cmd, def shell="bash") {
        //Jenkins gotcha: must include new line at the end!
        def internal_cmd = "#!/bin/" + shell + "\n" + cmd + "\n"
        def ret = this.script.sh (
            script: internal_cmd,
            returnStdout: true
        )
        if (ret)
            return ret.trim()
        return ""
    }

    /**
     * Run jenkins sh script and return exit status as int
     *
     * @param A command that is to be executed
     * @param An optional shell to be used (default bash)
     */
    int shWithStatus(String cmd, def shell="bash") {
        //Jenkins gotcha: must include new line at the end!
        def internal_cmd = "#!/bin/" + shell + "\n" + cmd + "\n"
        def ret = this.script.sh (
            script: internal_cmd,
            returnStatus: true
        )
        //Due to unit test mockup of sh, check for null/stdout return value
        if (ret == "stdout" || ret == null) return -1

        return ret
    }

    /*
     * Call sshagent closure with credentialsId
     *
     * @Param closure Closure to be called
     * @Param credentialsId String of credentials identifier passed to sshagent,
     *        if omitted closure is called directly
     */
    def withSshAgent(Closure closure, String credentialsId=null)
    {
        if (credentialsId) {
            this.script.sshagent([credentialsId]) {
                closure.call()
            }
        } else {
            closure.call()
        }
    }

    /**
     * Test if a directory exists in shell (slave)
     *
     * @return boolean true if the directory exists
     */
    boolean isDirectory(String path) {
        return (shWithStatus("test -d " + path) == 0) ? true : false
    }

    /**
     * Test if a file exists in shell (slave)
     *
     * @return boolean true if the file exists
     */
    boolean isFile(String path) {
        return (shWithStatus("test -f " + path) == 0) ? true : false
    }

    /**
     * Test if a socket exists in shell (slave)
     *
     * @return boolean true if the socket exists
     */
    boolean isSocket(String path) {
        return (shWithStatus("test -S " + path) == 0) ? true : false
    }

    /**
     *
     * Checks out the associated git repository and initializes and
     * updates any submodules contained in the repo.
     *
     * @return boolean Is the shell running in docker or not
     */
    boolean isDocker() {
        return isFile("/.dockerenv")
    }

    /**
     *
     * Checks out the associated git repository and initializes and
     * updates any submodules contained in the repo.
     *
     * @param url String of checkout URL
     * @param branch String of checkout branch
     * @param credentialsId String of checkout credentials identifier
     * @param args String array of optional arguments for submodule update
     */
    def checkout(String url=null, String branch=null, String credentialsId=null, String[] args=null) {
        if (url) {
            Logger.info("Running parameterized checkout")
            Logger.info("Url: " + url)
            Logger.info("Branch: " + branch)
            script.checkout([$class: 'GitSCM',
                branches: [[name: '*/' + branch]],
                doGenerateSubmoduleConfigurations: false,
                extensions: [],
                submoduleCfg: [],
                userRemoteConfigs: [[url: url, credentialsId: credentialsId]]]
            )
        } else {
            Logger.info("Running standard checkout")
            if (!isDocker()) {
                withSshAgent({ script.checkout script.scm }, credentialsId)
            } else {
                script.checkout script.scm
            }
        }

        def cmd = 'git submodule update --init'
        if (args != null)
            cmd += ' ' + args.join(" ")
        else
            cmd += "\n"

        if (!isDocker()) {
            withSshAgent({ script.sh cmd }, credentialsId)
        } else {
            script.sh cmd
        }
        Logger.info("Checkout complete")
    }
}
