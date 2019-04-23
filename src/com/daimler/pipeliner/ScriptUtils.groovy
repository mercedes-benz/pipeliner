// SPDX-License-Identifier: MIT

/*
 *  Copyright (c) 2019 MBition GmbH
 */

package com.daimler.pipeliner
/**
 * This class provides helper functions to improve Jenkins scripting
 *
 * @author Toni Kauppinen
 */

import com.daimler.pipeliner.Logger

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
    private def artifactory
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
     */
    def setArtifactory(){
        String artifactoryUrl

        if(!this.artifactory)
            artifactoryUrl= this.env.ARTIFACTORY_URL ? this.env.ARTIFACTORY_URL : ""
            def server = this.script.Artifactory.newServer(url: artifactoryUrl)
            server.credentialsId = getArtifactoryCredentialsFromEnvironment()
            server.bypassProxy = true
            this.artifactory = server
    }

    /**
     * Upload files to artifactory server
     * @param target Upload location in Artifactory
     * @param pattern A pattern that defines the files to upload
     */
    def uploadToArtifactory(String target, String pattern){
        setArtifactory()
        def uploadSpec = """{
            "files":[
                        {
                            "pattern": \"${pattern}\",
                            "target": \"${target}\"
                        }
                    ]
            }"""

        def buildInfo = this.artifactory.upload(uploadSpec)
        this.artifactory.publishBuildInfo(buildInfo)
    }

    /**
     * Download files from artifactory server with defined downloadSpec
     * @param target Download location in Artifactory
     * @param pattern A pattern that defines the files to download
     */
    def downloadFromArtifactory(String target, String pattern){
        setArtifactory()
        def downloadSpec = """{
            "files":[
                        {
                            "pattern": \"${pattern}\",
                            "target": \"${target}\"
                        }
                    ]
            }"""

        def buildInfo = this.artifactory.download(downloadSpec)
        this.artifactory.publishBuildInfo(buildInfo)
    }

    /**
     * Initialize internal Artifactory credential id from environment
     * @return artifactoryCredentialsId
     */
    def private getArtifactoryCredentialsFromEnvironment(){
        String artifactoryCredentialsId
        try {
            artifactoryCredentialsId = this.env.ARTIFACTORY_CREDENTIALS_ID ? this.env.ARTIFACTORY_CREDENTIALS_ID : ""
            if (artifactoryCredentialsId.isEmpty()) {
                def split = this.env.JOB_NAME.tokenize("./")
                artifactoryCredentialsId = split[0] + "-artifactory"
            }
            Logger.info("Checkout credentials: " + artifactoryCredentialsId)
        } catch (NullPointerException) {
            artifactoryCredentialsId = ""
        }

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
            cmd += args.join(" ")
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
