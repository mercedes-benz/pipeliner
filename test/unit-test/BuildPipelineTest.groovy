// SPDX-License-Identifier: MIT

/*
 *  Copyright (c) 2019 MBition GmbH
 */

import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.Assert
import utils.ArtifactoryMock
import utils.TestSourceRetriver
import com.daimler.pipeliner.Logger
import com.lesfurets.jenkins.unit.BasePipelineTest
import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library
import static com.lesfurets.jenkins.unit.global.lib.LocalSource.localSource

/**
 * Defines unit tests for BuildPipeline using Jenkins Pipeline testing framework
 * Also verifies the BasePipeline run behaviour
 */
class BuildPipelineTest extends BasePipelineTest {

    ArtifactoryMock am = new ArtifactoryMock()

    @Override
        @Before
        void setUp() throws Exception {
            def library = library()
                .name('pipeliner-library')
                .allowOverride(true)
                .defaultVersion("master")
                .implicit(false)
                .targetPath('somePath')
                .retriever(new TestSourceRetriver())
                .build()
            helper.registerSharedLibrary(library)
            helper.registerAllowedMethod("node", [String.class, Closure.class], null)
            helper.registerAllowedMethod("stage", [String.class, Closure.class], null)
            helper.registerAllowedMethod("lock", [String.class, Closure.class], null)
            binding.setProperty("Artifactory", am)

            Logger.init(this)
            super.setUp()
        }

    @After
    void tearDown(){
        Logger.clear()
    }

    @Test
        void should_execute_without_errors() throws Exception {
            def script = loadScript("test/unit-test/resources/build.jenkins")
            script.execute()
            printCallStack()
        }

    @Test
        void verify_stage_invocation_and_count() throws Exception {
            def script = loadScript("test/unit-test/resources/build.jenkins")
            script.execute()

            //Build.jenkins has 3 values in list keys, but four values in merge request
            //override, thus we expect to find 4 runs
            def stages = helper.callStack.findAll { it.methodName == "stage"}
            Assert.assertEquals(4, stages.size)
        }
}
