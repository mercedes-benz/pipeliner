// SPDX-License-Identifier: MIT

/*
 *  Copyright (c) 2019 MBition GmbH
 */

package com.daimler.pipeliner

/**
 * This class defines a template for the pipelines. It defines a structure to
 * specify input data and also defines who a pipelines is run. Actual pipelines
 * should extend this class .
 *
 * @author Sumit Ahuja
 */

abstract class BasePipeline implements Serializable {
    /**
     * The input keys and their default values for the pipeline in String
     */
    protected String defaultInputs = ''
    /**
     * The keys for which pipeline should be parallelized
     */
    protected List parallel = []
    /**
     * The explicit combinations by user inputs should be parallelized, this will
     * overwrite the auto generated combinations by generateStageInputs and
     * defaultCombinations.
     */
    protected List combinations = []
    /**
     * The node label expression
     */
    protected String nodeLabelExpr = ''
    /**
     * The docker container which from private registry should be used to run this pipeline
     */
    protected String dockerImage = ''
    /**
     * Relative file path for custom docker file which intended to create custom docker image from
     * Format: <cloneurl>#branchname:<context>
     */
    protected String customDockerfileSource = ''
    /**
     * Custom arguments to pass to docker
     */
    protected String dockerArgs = ''
    /**
     * Name of Docker container to be pushed to docker registry
     */
    protected String pushDockerImage = ''
    /*
     * Custom host entries to pass to docker
     * Format '--add-host="name:ip" --add-host="name2:ip2"'
     */
    protected String dockerAddHosts = ''
    /**
     * The actual inputs at the runtime
     */
    protected Map inputs
    /**
     * Jenkins scripted environment reference
     */
    protected def script
    /**
     * mergeRequest message from Git
     */
    protected String message
    /**
     * Map of environment variables
     */
    protected Map env
    /**
     * The input parser object instance
     */
    protected def parser
    /**
     * The utils object instance
     */
    protected def utils
    /**
     * Map of input-output values for pipelines
     * This will be initialized with Maps, so the variable becomes a Map of Maps
     * Raw usage:
     *     setter: ioMap[parallelName]["fieldName"] = ["fieldValue"]
     *     getter: ioMap[parallelName]["fieldName"]["fieldValue"]
     * API usage with lock-protected functions:
     *     setter: setMapValue
     *     getter: getMapValue
     */
    protected Map ioMap
    /**
     * Checkout credentials id, used for git with sshagent
     * This is read from the environment variable CHECKOUT_CREDENTIALS_ID
     */
    protected String checkoutCredentialsId
    /**
     * Root user constant that is used to run docker
     */
    final String DOCKER_ROOT_USER = "-u 0:0"
    /**
     * Root user constant that is used to run docker
     */
    final String DOCKER_PULL_REF_FORMAT = "^(http|https|ssh|.git|git@).*"
    /**
     * Docker free memory limit in gigabytes
     */
    final int DOCKER_FREE_MEMORY_LIMIT = 3

    /**
     * Constructor
     *
     * @param script Reference to the Jenkins scripted environment
     * @param defaults contains a Map of defaults
     * @param mergeRequestMsg A String of mergeRequest message
     * @param env A Map of environment variables
     * @param ioMap A Map of input-output variables
     */
    BasePipeline(def script, Map defaults, String mergeRequestMsg, Map env, Map ioMap) {
        this.script = script
        this.message = mergeRequestMsg
        this.env = env
        this.defaultInputs = defaults.defaultInputs
        this.parallel = defaults.parallel.collect{it.toLowerCase()}
        this.parser = new InputParser(defaults.exposed, this.parallel)
        this.utils = new ScriptUtils(this.script,this.env)
        this.ioMap = ioMap
    }

    /**
     * Initialize internal variables from values provided in the environment
     */
    void initializeFromEnvironment() {
        try {
            this.checkoutCredentialsId = this.env.CHECKOUT_CREDENTIALS_ID ? this.env.CHECKOUT_CREDENTIALS_ID : ""
            if (this.checkoutCredentialsId.isEmpty()) {
                def split = this.env.JOB_NAME.tokenize("./")
                if (split.size() > 0) {
                    Logger.info(split.inspect())
                    this.checkoutCredentialsId = split[0] + "-jenkins-ssh"
                }
                else {
                    this.checkoutCredentialsId = "global-jenkins-ssh"
                }
            }
            Logger.info("Checkout credentials: " + this.checkoutCredentialsId)
        } catch (NullPointerException) {
            this.checkoutCredentialsId = ""
        }

        try {
            this.dockerAddHosts = this.env.DOCKER_ADD_HOSTS ? this.env.DOCKER_ADD_HOSTS : ""
        } catch (NullPointerException) {
            this.dockerAddHosts = ""
        }
    }

    void processUserInput() {

        Logger.info("****** BasePipeline ********")
        Logger.info("****** processUserInput ********")

        Map parsedUserInputs = this.parser.parse(this.defaultInputs, this.env, this.message)
        this.inputs = parsedUserInputs.args

        // Filter unwanted key, value pairs
        Map tmp = [:]
        for (key in this.inputs.keySet()) {
            // Keep key value pairs as they are if they are supposed to be paralleled
            if (this.parallel.contains(key)) {
                tmp[key] = this.inputs[key]
            } else {
                // Due to the format ["key": ["one"]], we need to fetch the
                // raw value for single key value pairs, if the key is not part
                // of parallel list and the value is only one single value
                if (this.inputs[key].size() == 1) {
                    tmp[key] = this.inputs[key][0]
                } else {
                    tmp[key] = this.inputs[key]
                }
            }
        }
        this.inputs = tmp
    }

    /**
     * Defines the stages for a particular pipeline. This should be overridden in
     * the derived class.
     *
     * @param A Map with the inputs for stages
     */
    abstract void stages(Map stageInput)

    /**
     * Post function hook that is executed even in case earlier stages have failed
     *
     * NOTE: This is run inside of the scheduled node and workspace
     * NOTE: This is run for each parallel separately
     *
     * @param A Map with the inputs for stages
     */
    void postParallel(Map stageInput) {
        Logger.info("BasePipeline: postParallel")
    }

    /**
     * Post function hook that is executed even in case earlier stages have failed
     *
     * NOTE: This is run for each parallel separately outside node/workspace/docker
     *
     * @param A Map with the inputs for stages
     */
    void postParallelFinally(Map stageInput) {
        Logger.info("BasePipeline: postParallelFinally")
    }

    /**
     * Post function hook that is executed even in case earlier stages have failed
     *
     * NOTE: This is run outside of the scheduled node, workspace and docker
     * NOTE: This is run only once for the pipeline, not for each parallel
     *
     * @param A Map with the collected results from all parallel runs
     */
    void postPipeline(Map results) {
        Logger.info("BasePipeline: postPipeline")
    }

    /**
     * Defines the combined behaviour with stages and post steps
     *
     * @param A Map with the inputs for stages
     * @param A boolean specifying if postParallel is run or not.
     *        true for native
     *        false for docker (see runInDocker)
     */
    void setupStages(Map stageInput, boolean isNative=true) {
        Exception exception = null
        try {
            this.stages(stageInput)
        }
        catch (Exception ex)
        {
            exception = ex
            //Since post is called before the exception, we must set the build
            //result manually in case of an exception
            if (this.script.currentBuild.currentResult != "UNSTABLE")
                this.script.currentBuild.result = "FAILURE"
        }
        finally {
            if (isNative)
                postParallel(stageInput)
            if (exception)
                throw exception
        }
    }

    /**
     * Adds a node and runs stages
     *
     * @param A Map with the inputs for stages
     */
    void setupNodeWithStages(Map stageInput) {
        Logger.info("Parallel name: " + stageInput["parallelName"])
        this.script.node(this.nodeLabelExpr) {
            this.script.ws(createWorkspaceName()) {
                setupStages(stageInput)
            }
        }
    }

    /**
     * Helper function to check if a string is a docker remote tag reference
     *
     * @param A reference string to check
     */
    boolean isDockerTagReference(String ref) {
        if (ref.matches(DOCKER_PULL_REF_FORMAT)) {
            return false
        }
        else if (ref.contains(":")) {
            return true
        }
        else {
            return false
        }
    }

    /**
     * Function to do a full checkout. This is needed in case checkout is to
     * be performed before Dockerfile is to be built.
     * Support for integration tests includes usage of environment variables.
     */
    void earlyCheckout() {
        String url = this.env.CHECKOUT_URL
        String branch = this.env.CHECKOUT_BRANCH ? this.env.CHECKOUT_BRANCH : "master"
        utils.checkout(url, branch, checkoutCredentialsId)
    }

    /**
     * Helper function to build a custom docker image
     *
     * @param A reference to dockerfile
     * @param A reference to the stageRepo for possible checkout
     */
    String buildCustomImage(String customDockerfileSource) {
        String tag = "custom-image-" + UUID.randomUUID()

        if (customDockerfileSource.matches(DOCKER_PULL_REF_FORMAT)) {
            //Build from clone reference. Clone to folder dockerbuild and build there.
            //Current docker has issues with remote cloned submodules.
            String url = customDockerfileSource
            String branch = "master"
            String context = "."
            if (url.contains("#")) {
                url = customDockerfileSource.tokenize("#")[0]
                String extras = customDockerfileSource.tokenize("#")[1]
                branch = extras
                if (extras.contains(":")) {
                    branch = extras.tokenize(":")[0]
                    context = extras.tokenize(":")[1]
                }
            }

            utils.shWithStatus("rm -rf dockerbuild/")

            this.utils.withSshAgent({
                this.script.sh "git clone --recurse-submodules " + url + " -b " + branch + " dockerbuild"
                this.script.sh "docker build --pull -t " + tag + " dockerbuild/" + context
            }, this.checkoutCredentialsId)
        }
        else if (customDockerfileSource.contains(":")) {
            //Registry reference. No building required, but a pull is necessary!
            pullDockerTag(customDockerfileSource)
            return customDockerfileSource
        }
        else {
            //Build from local alternative file
            earlyCheckout()
            this.utils.withSshAgent({
                this.script.sh "docker build --pull -t " + tag + " -f " + customDockerfileSource + " ."
            }, this.checkoutCredentialsId)
        }

        return tag
    }

    /**
     * Helper function to pull a docker image tag
     *
     * @param A docker tag reference
     */
    void pullDockerTag(String tag) {
        this.script.sh "docker pull " + tag
    }

    /**
     * Helper function to append (build) current slave user into an image
     *
     * @param A source image that is to be appended
     * @param A user id of the current user
     * @param A group id of the current user
     * @param A username of the current user
     */
    String appendCurrentUserToImage(String image, String userId, String groupId, String currentUser) {
        Logger.info("Building current user layers")
        //Not running as root, we need to append currentUser to the image
        //This will remove most of the issues faced when running as non-root
        String tag = currentUser.toLowerCase() + "-image-" + UUID.randomUUID()

        this.script.sh "mkdir -p dockerbuild"
        String dockerFile = "dockerbuild/Dockerfile-" + currentUser.toLowerCase()

        createDockerFile(image, userId, groupId, currentUser, dockerFile)
        this.utils.withSshAgent({
            this.script.sh "docker build -t " + tag + " -f " + dockerFile + " dockerbuild"
        }, this.checkoutCredentialsId)

        return tag
    }

    /**
     * Helper function to append (build) current slave user into an image
     * Adds also passwordless sudo for the user inside the image
     *
     * @param A source image that is to be appended
     * @param A user id of the current user
     * @param A group id of the current user
     * @param A username of the current user
     * @param A dockerfile name that is created
     */
    def createDockerFile(String image, String userId, String groupId, String currentUser, String dockerFile) {
        def content = "FROM " + image + "\n"
        content += "USER root\n"
        content += "RUN groupdel " + currentUser + "||true\n"
        content += "RUN userdel -r -f " + currentUser + "||true && rm -rf /home/" + currentUser + "\n"
        content += "RUN groupadd -f -g " + groupId + " " + currentUser + "\n"
        content += "RUN useradd -l -g " + groupId + " --uid " + userId + " -ms /bin/bash " + currentUser + "\n"
        content += "RUN echo " + currentUser + ":" + currentUser + "| chpasswd\n"
        content += 'RUN echo "' + currentUser + ' ALL=(ALL) NOPASSWD:SETENV: ALL" > /etc/sudoers.d/' + currentUser + '||true\n'
        content += "USER " + currentUser + "\n"
        content += "ENV USER=" + currentUser + "\n"
        this.script.writeFile(file : dockerFile, text: content)
    }

    /**
     * Sanitize job name
     *
     * Job names created dynamically may contain unwanted characters and
     * are possibly in unwanted format. This function makes them more
     * reasonable. For example gitlab created jobs.
     *
     * @param A job name string
     */
    String sanitizeJobName(String jobName) {
        assert jobName != null
        assert jobName != ""

        String name = jobName
        name = name.replaceAll("%2F", ".")
        name = name.replaceAll("/", ".")

        if (name.contains("!")) {
            //Job has a merge request syntax
            def left = name.tokenize("!")[0]
            def right = name.tokenize("!")[1]
            name = left.replaceAll("[^a-zA-Z0-9-.]+","_")
            name += "_mr-"

            def number = right
            if (right.contains(" ")) {
                number = right.tokenize(" ")[0]
            }
            name += number.replaceAll("[^a-zA-Z0-9-.]+","_")
        }
        else {
            //Job has generic or branch build syntax
            name = name.replaceAll("[^a-zA-Z0-9-.]+","_")
        }

        //In the case of long job name, make it fit (15 chars reserved for base+executor)
        //Total of 80
        if (name.length() > 64)
            name = name.substring(0, 64)

        return name
    }

    /**
     * Create a workspace name that is not badly mangled by Jenkins/Gitlab
     */
    String createWorkspaceName() {
        String base = "workspace/"
        String name = sanitizeJobName(this.env.JOB_NAME)
        //executor prefix e<number>, always start folders with a valid character
        String workspaceName = base + "e" + this.script.EXECUTOR_NUMBER + "_" + name

        Logger.info("Workspace name: " + workspaceName)

        return workspaceName
    }

    /**
     * Sanitize docker arguments to limit what the user is able to do
     *
     * @param A string of evaluated arguments
     */
    String sanitizeDockerArgs(String args) {
        //Make sure that docker arguments contain only allowed modifications
        //TODO: discuss and implement appropriately
        return args
    }

    /**
     * Check that docker volume directories exist
     *
     * In order to avoid magic necessary for new build slaves. This step is done
     * here. Parse docker arguments for volume parameters, create the source if
     * it does not exist.
     *
     * @param A string of evaluated arguments
     */
    void checkDockerVolumeDirectories(String args) {
        if (args.contains("-v ")) {
            boolean volume = false
            args.tokenize(" ").each {
                if (volume) {
                    def source = it.tokenize(":")[0]
                    //If a valid source does not exist assume a folder and create it
                    if (!utils.isDirectory(source) && !utils.isSocket(source)) {
                        script.sh("mkdir -p " + source)
                    }
                    volume = false
                }
                if (it == "-v")
                    volume = true
            }
        }
    }

    /**
     * Create a docker memory limit argument based on total system memory.
     */
    String dockerMemoryLimit() {
        def totalString = utils.shWithStdout('free|grep Mem:|tr -s " "|cut -d" " -f2|rev|cut -b7-|rev')
        if (totalString == "")
            return "" //No limit (unit test only)

        int totalInt = totalString.toInteger()
        //Subtract free memory limit in gigabytes from total
        return " -m " + (totalInt - DOCKER_FREE_MEMORY_LIMIT).toString() + "g"
    }

    /**
     * Function to run docker stages inside already specified node and workspace
     *
     * @param A Map with the inputs for stages
     */
    void runInDocker(Map stageInput) {
        //NOTE: Running on the specified slave now

        //Some variables are not evaluated correctly since they are not in the sh's environment.
        //We will evaluate them manually.
        def evaluatedArgs = utils.shWithStdout("echo " + this.dockerArgs)
        evaluatedArgs = sanitizeDockerArgs(evaluatedArgs)

        def currentUser = utils.shWithStdout('echo $USER')
        def userId = utils.shWithStdout('id -u')
        def groupId = utils.shWithStdout('id -g')

        //Original image that is used
        String image = this.dockerImage
        //Tag that we use for building and running a container
        String tag = this.dockerImage
        String customTag = null

        //Check if we need to use a custom image, if so, build a new image with it
        if (this.customDockerfileSource) {
            Logger.info("Building custom image")
            //Replace the image that we run with the custom one.
            //We take care to use a variable in the scope of this function and not from "this"
            customTag = buildCustomImage(this.customDockerfileSource?.trim())
            tag = customTag
            image = customTag
        } else if (this.dockerImage == "Dockerfile") {
            Logger.info("Building official image from Dockerfile")
            customTag = buildCustomImage("Dockerfile")
            tag = customTag
            image = customTag
        } else if (isDockerTagReference(tag)) {
            Logger.info("Pulling tag: " + tag)
            pullDockerTag(tag)
        }

        //Check if we need to add current user to the image, if so, build a new image with it
        if (!this.dockerArgs.contains(DOCKER_ROOT_USER)) {
            tag = appendCurrentUserToImage(image, userId, groupId, currentUser)
        }

        checkDockerVolumeDirectories(evaluatedArgs)

        // Add docker socket mount if we want to push docker image to docker registry afterward
        String mountDockerSocket = ""
        if (this.pushDockerImage) {
            mountDockerSocket = "-v /var/run/docker.sock:/var/run/docker.sock"
        }

        this.utils.withSshAgent( {
            String sshAgentArgs = ""
            if (this.checkoutCredentialsId != "")
                sshAgentArgs = utils.shWithStdout('echo " -v $(dirname $SSH_AUTH_SOCK):$(dirname $SSH_AUTH_SOCK) -e SSH_AUTH_SOCK=$SSH_AUTH_SOCK"')

            //Start docker container with our specified tag
            this.script.docker.image(tag).inside(evaluatedArgs + dockerMemoryLimit() + " " + sshAgentArgs + " " + this.dockerAddHosts + " " + mountDockerSocket) {
                //Running in the docker container now

                //Disable remote host key checking to avoid interactive prompt from git
                this.utils.sh "mkdir -p ~/.ssh"
                this.utils.sh 'echo "Host *\n\tStrictHostKeyChecking no\n" >> ~/.ssh/config'

                //Setup stages, but do not run postParallel step
                setupStages(stageInput, false)

                //Take ownership back (run inside of docker that has root access)
                if (this.dockerArgs.contains(DOCKER_ROOT_USER)) {
                        this.script.echo "Changing file ownership back to user: " + currentUser
                        //The image may not have a user. Set ownership using ID values instead.
                        this.script.sh "chown " + userId +":"+ groupId + ' $PWD -R'
                }
            }
        }, this.checkoutCredentialsId)

        //Remove custom current user layers
        if (!this.dockerArgs.contains(DOCKER_ROOT_USER) && utils.shWithStatus("docker rmi " + tag) != 0) {
            //Log the problem, but don't change build status
            Logger.error("Docker rmi failed for current user layers " + tag)
        }

        //Remove custom docker layers if they were built locally and not pulled from a tag
        if (customTag && !isDockerTagReference(this.customDockerfileSource?.trim())) {
            if (utils.shWithStatus("docker rmi " + customTag) != 0) {
                //Log the problem, but don't change build status
                Logger.error("Docker rmi failed for image " + customTag)
            }
        }
    }

    /**
     * Adds a node and run stages in a docker container with specified arguments
     *
     * @param A Map with the inputs for stages
     */
    void setupDockerWithStages(Map stageInput) {
        Logger.info("Parallel name: " + stageInput["parallelName"])
        //NOTE: Running on master jenkins now
        this.script.node(this.nodeLabelExpr) {
            this.script.ws(createWorkspaceName()) {
                try {
                    this.runInDocker(stageInput)
                } finally {
                    //Run postParallel here since it was skipped in runInDocker
                    this.postParallel(stageInput)
                }
            }
        }
        //Run postParallelFinally outside of node
        this.postParallelFinally(stageInput)
    }

    /**
     * Initializes input-output Map for a single parallel run instance.
     *
     * @param A String with the name of the parallel to be initialized
     */
    void initValueMap(String parallelName) {
        this.script.lock("ioMap") {
            //Initialize empty map for this parallel, but do not reset it
            if (this.ioMap[parallelName] == null)
                this.ioMap[parallelName] = [:]
        }
    }

    /**
     * Gets a field value from the input-output Map for a single parallel run instance.
     *
     * @param A String with the name of the parallel
     * @param A String with the name of the field
     * @return A String with the value of the field
     */
    String getMapValue(String parallelName, String fieldName) {
        this.script.lock("ioMap") {
            return this.ioMap[parallelName][fieldName]
        }
    }

    /**
     * Sets a field value to the input-output Map for a single parallel run instance.
     *
     * @param A String with the name of the parallel
     * @param A String with the name of the field
     * @param A String with the value of the field
     */
    void setMapValue(String parallelName, String fieldName, String value) {
        this.script.lock("ioMap") {
            this.ioMap[parallelName][fieldName] = value
        }
    }

    /**
     * Executes pipeline by invoking the stages with appropriate data
     *
     * @return A Map of the input-output parameters passed to and modified by this pipeline
     */
    Map run() {
        // Map that holds job name as key and job closure
        Map joblist = [:]
        //List for parallels for this pipeline
        def parallelList = []

        initializeFromEnvironment()
        processUserInput()

        def stageInputs = getStageInputs()
        stageInputs.eachWithIndex {
            stageInput, listIndex ->
                String parallelName = generateJobName(stageInput)
                // Inject the parallelName to the stageInput for the job for backward reference
                stageInput["parallelName"] = parallelName
                parallelList.add(parallelName)

                initValueMap(parallelName)

                joblist[parallelName] = {
                    try {
                        // Setup stages with Docker or node, based on configuration
                        if (this.dockerImage?.trim()) {
                            this.setupDockerWithStages(stageInput)
                        } else if (this.nodeLabelExpr?.trim()) {
                            this.setupNodeWithStages(stageInput)
                        } else {
                            this.script.node {
                                //NOTE: execution must always be encased in node, even if label is not specified!
                                this.setupStages(stageInput)
                            }
                        }

                        setMapValue(parallelName, "result", "SUCCESS")

                        //NOTE: Currently it's not possible to detect UNSTABLE state from a parallel run instance
                    } catch (Exception ex) {
                        setMapValue(parallelName, "result", "FAILURE")

                        throw ex
                    }
                }
        }

        try {
            this.script.parallel joblist
        }
        finally {
            Map results = [:]
            parallelList.each { item ->
                results[item] = getMapValue(item, "result")
            }
            this.postPipeline(results)
        }

        return ioMap
    }


    /**
     * Check the given combinations is valid or not
     */
    Boolean isValidCombinations(List combinations, List stageInputs) {
        if (combinations == null || combinations.empty) {
            return false
        } else {
            for (e in combinations) {
                if (!stageInputs.contains(e)) {
                    return false
                }
            }
            return true
        }
    }

    List getStageInputs() {
        // Generate inputs for stages and create a map of jobs
        def stageInputs = generateStageInputs()
        if (isValidCombinations(this.combinations, stageInputs)) {
            return this.combinations
        } else {
            return stageInputs
        }
    }

    /**
     * Function to get simplified parallel key string
     *
     * @return A String with parallel key name
     */
    String parallelKeyName() {
        //Subtract "s" from the parallel key if one exists
        String parallelKey = this.parallel[0]
        if (parallelKey.endsWith("s"))
            parallelKey = parallelKey[0..-2]  //NOTE: -2 actually removes just one char!

        return parallelKey
    }

    /**
     * Generates a job name based on the parallel keys and values.
     * Eg for parallel field "targets":
     * Legacy: "target: qemu-x86-64_nogfx:core-image-minimal"
     * New: "qemu-x86-64_nogfx:core-image-minimal"
     *
     * @param A Map with the inputs for stages
     * @return A String with job name
     */
    String generateJobName(Map stageInput) {
        // If no parallel keys exist, return a default name
        def jobName = "default"
        if (!this.parallel.empty) {
            //Legacy with prefix "target: "
            //jobName = parallelKeyName() + ": " + stageInput[parallelKeyName()]

            //New without prefix, same as key used in input-output map
            jobName = stageInput[parallelKeyName()]
        }

        return jobName
    }


    /**
     * Generates list of maps based on parallel keys, and processed user inputs
     * Each entry is an input for one parallelized instance of this pipeline
     *
     * @return A list of maps to be parallelled in separated stages
     */
    List generateStageInputs() {
        List stageInputs = []

        if (parallel.empty) {
            return stageInputs << this.inputs
        }

        // Map containing only non-parallel key-value pairs
        def linearInputKeys = this.inputs.keySet() - parallel
        def linearInputMap = this.inputs.subMap(linearInputKeys)
        if (this.parallel.size() > 1)
            throw new Exception("Only one parallel key is supported")

        String parallelKey = parallelKeyName()

        def parallelInputMap = this.inputs.subMap(parallel)

        List parallelKeyValues = []

        parallelInputMap.each { k, v ->
            v[0].tokenize(" ").each { splitValue ->
                parallelKeyValues.add(splitValue)
            }
        }

        stageInputs = linearParallelComb(parallelKey, parallelKeyValues)
        return stageInputs
    }

    /**
     * To combine parallel key value pairs with linear inputs
     *
     * @param A String containing parallel key to be used as field name
     * @param A List containing parallel key values
     * @return A List containing maps for each parallel instance
     */
    List linearParallelComb(String parallelKey, List parallelKeyValues) {
        List stageInputs = []

        // Map containing only non-parallel key-value pairs
        def linearInputKeys = this.inputs.keySet() - parallel
        def linearInputMap = this.inputs.subMap(linearInputKeys)
        if (this.parallel.size() > 1)
            throw new Exception("Only one parallel key is supported")

        // Generate stage input map list with added field for parallel key
        stageInputs = parallelKeyValues.collect {
            def stageMapInput = [:]
            stageMapInput[parallelKey] = it
            stageMapInput + linearInputMap
        }

        return stageInputs
    }

}
