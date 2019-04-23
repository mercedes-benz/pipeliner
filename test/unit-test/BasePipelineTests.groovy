// SPDX-License-Identifier: MIT

/*
 *  Copyright (c) 2019 MBition GmbH
 */

import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.Assert
import com.daimler.pipeliner.BasePipeline
import com.daimler.pipeliner.Logger
import utils.ArtifactoryMock

// Create a class that provides dummy implementation for abstract methods
class ConcreteBasePipeline extends BasePipeline{
    ConcreteBasePipeline(script, msg, env, ioMap) {
        super(script, [
            defaultInputs: '''
                key1 = value1
                key2 = value2
                listKeys = one two
                listKey2s = 1 | 2
                listKey3s = x
                unitTests = True
                labels = test | stage
                cmakeArgs =
            ''',
            exposed: ['key1', 'listKeys', 'listKey2s', 'cmakeArgs'],
            parallel: ['listKeys']
        ], msg, env, ioMap)
    }
    // Dummy implementation for abstract methods
    void stages(Map testMap) { };
    void postParallel(Map stageInput) { };
    void postPipeline(Map results) { };
};

/**
 * Defines unit test for BasePipeline
 *
 */
class BasePipelineTests {
    ConcreteBasePipeline pip, pip2
    def Artifactory

    @Before
    void setUp() {
        Logger.init(this)
        String mergeRequestMsg = '''--
            key1 = value4
            listKeys = five six seven
            listKey2s = 4 | 5
            key2 = value6
            key3 = value3
            cmakeArgs = -DBUILD_TESTING=ON -DBUILD_EXAMPLES=ON
        '''

        Map env = [ 'pip_key1': 'value4',
                    'pip_listKeys': 'five',
                    'pip_listKey2s': "4 | 5",
                    'pip_key2': 'value6',
                    'pip_key3':'value3',
                    'pip_cmakeArgs': "-DBUILD_TESTING=ON -DBUILD_EXAMPLES=ON" ]
        this.Artifactory = new ArtifactoryMock(this)
        //Since key1 and listKeys were exposed, they will get overwritten by
        //inputData above. key2 and listKey2 will keep the default, key3 not available.
        //And the parallel listKeys and listKey2s will get the 's' removed
        this.pip = new ConcreteBasePipeline(this, mergeRequestMsg, null, [:])
        this.pip2 = new ConcreteBasePipeline(this, null, env, [:])
    }

    @After
    void tearDown(){
        Logger.clear()
    }

    // Tests the processUserInput method
    @Test
    void testProcessUserInput() {
        Map expectedData = [
            'key1': 'value4',
            'key2': 'value2',
            'listkeys': ['five six seven'],
            'listkey2s': ['4', '5'],
            'listkey3s': 'x',
            'unittests': 'True',
            'labels': ['test', 'stage'],
            'cmakeargs': "-DBUILD_TESTING=ON -DBUILD_EXAMPLES=ON"
        ]
        Map expectedData2 = [
            'key1': 'value4',
            'key2': 'value2',
            'listkeys': ['five'],
            'listkey2s': ['4', '5'],
            'listkey3s': 'x',
            'unittests': 'True',
            'labels': ['test', 'stage'],
            'cmakeargs': "-DBUILD_TESTING=ON -DBUILD_EXAMPLES=ON"
        ]
        this.pip.processUserInput()
        this.pip2.processUserInput()
        Assert.assertEquals(expectedData, this.pip.inputs);
        Assert.assertEquals(expectedData2, this.pip2.inputs);
    }

    // Tests the generateStageInputs method
    @Test
    void testGenerateStageInputs () {
        this.pip.processUserInput()
        this.pip2.processUserInput()
        List stageInputs = this.pip.generateStageInputs()
        List stageInputs2 = this.pip2.generateStageInputs()
        Assert.assertEquals(3, stageInputs.size());     //with merge request description
        Assert.assertEquals(1, stageInputs2.size());    //without description
    }

    // Tests the getStageInputs method
    @Test
    void testGetStageInputs() {
        this.pip.processUserInput()
        List stageInputs = this.pip.getStageInputs()
        Assert.assertEquals(3, stageInputs.size());
    }
}
