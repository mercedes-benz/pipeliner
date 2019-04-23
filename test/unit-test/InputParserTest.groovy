// SPDX-License-Identifier: MIT

/*
 *  Copyright (c) 2019 MBition GmbH
 */

import org.junit.Test
import org.junit.Assert
import org.junit.Before
import org.junit.After
import com.daimler.pipeliner.InputParser
import com.daimler.pipeliner.Logger


class InputParserTest {

    private static String mergeRequestMessageFromMap(final Map<String, List<String>> parametersToInclude) {

        String message = """
        This is a merge request description it can span over several lines

        Like this for example, but there needs to be a delimiter at the end which
        seperates the normal message from the data which is supposed to be parsed
        by the parser.
        --
        """

        parametersToInclude.each {
            final String key = it.key
            final List<String> value = (ArrayList<String>) it.value
            message += "\n" + key + " = " + stringifyList(value)
        }
        return "--\n" + message
        return message
    }

    private static Map<String, String> environmentFromMap(final Map<String, List<String>> expectedData) {

        TreeMap<String, String> map = new TreeMap<>();

        expectedData.each {
            String key = null
            String value = null
            if (it.key.contains(',')) {
                key = it.key.split(',').join(',PIP_')
                value = it.value[0]
            } else {
                key = it.key
                value = stringifyList(it.value)
            }
            map.put("PIP_" + key, value)
        }
        return map;
    }

    private static String stringifyList(final List<String> listToStringify) {

        String resultingString = ""
        for (int i = 0; i < listToStringify.size(); i++) {
            resultingString += listToStringify[i]
            if (i + 1 < listToStringify.size()) {
                resultingString += " | "
            }
        }

        return resultingString
    }

    @Before
    void setUp() {
        Logger.init(this)
    }

    @After
    void tearDown(){
        Logger.clear()
    }

    /**
     * Test envToMessage functioin
     */
    @Test
    void testEnvToMessage() {

        Map defaults = [
            defaultInputs: '''
                key1 = v
                key2 = v
                lists = l
                list2s = l
            ''',
            exposed: ['key1', 'key2', 'lists', 'list2s']
        ]

        final Map environment = [
                'pip_key1': 'value',
                'key2': 'v_env',
                'PIP_lists': "one | two | three",
                'pip_list2s': "a | b",
                'pip_lists,pip_list2s': "one, a | one, b",
                'pip_lists,list2s': "two, a | two, b"
        ]

        final String message = '''
                key1 = value
                lists = one | two | three
                list2s = a | b
                lists,list2s = one, a | one, b
        '''

        String defaultInputs = defaults.defaultInputs
        def parser = new InputParser(defaults.exposed)
        String fakeMessage = parser.envToMessage(environment)

        Assert.assertEquals(parser.parseFromMessage(message), parser.parseFromMessage(fakeMessage))

    }


    /**
     * Test the parser can handle input of length 1
     */
    @Test
    void testInputLengthOne() {

        Map defaults = [
            defaultInputs: '''
                key = v
            ''',
            exposed: ['key']
        ]

        final Map<String, List<String>> inputData = [
                'key': ['value']
        ]

        final Map expectedData = inputData
        final Map<String, String> environment = environmentFromMap(inputData)
        final String message = mergeRequestMessageFromMap(inputData)
        String defaultInputs = defaults.defaultInputs
        def parser = new InputParser(defaults.exposed)

        // check environment variables parse
        Map result_env = parser.parse(defaultInputs, environment, null)
        Assert.assertEquals(expectedData, result_env.args)
        // check merge request message parse
        Map result_mr = parser.parse(defaultInputs, null, message)
        Assert.assertEquals(expectedData, result_mr.args)
        // check both MR message and environment variables parse
        Map result = parser.parse(defaultInputs, environment, message)
        Assert.assertEquals(expectedData, result.args)
    }


    /**
     * Test the parser can handle multiple inputs
     */
    @Test
    void testMultipleInputs() {

        Map defaults = [
            defaultInputs: '''
                key = v
                list = l
            ''',
            exposed: ['key', 'list']
        ]

        final Map<String, List<String>> inputData = [
                ('key'): ['value'],
                ('list'): ['one', 'two', 'three']
        ]

        final Map expectedData = inputData
        final Map<String, String> environment = environmentFromMap(inputData)
        final String message = mergeRequestMessageFromMap(inputData)
        String defaultInputs = defaults.defaultInputs
        def parser = new InputParser(defaults.exposed)

        // check environment variables parse
        Map result_env = parser.parse(defaultInputs, environment, null)
        Assert.assertEquals(expectedData, result_env.args)
        // check merge request message parse
        Map result_mr = parser.parse(defaultInputs, null, message)
        Assert.assertEquals(expectedData, result_mr.args)
        // check both MR message and environment variables parse
        Map result = parser.parse(defaultInputs, environment, message)
        Assert.assertEquals(expectedData, result.args)
    }


    /**
     * Test the parser can handle empty inputs
     */
    @Test
    void testEmptyValue() {

        Map defaults = [
            defaultInputs: '''
                key = v
                lists = l
                list2s = l
            ''',
            exposed: ['key', 'lists', 'list2s']
        ]

        final Map<String, List<String>> inputData = [
                ('key'): [],
                ('lists'): [],
                ('list2s'): ['a', 'b'],
        ]

        final Map expectedData = [key: [""], lists: [""], list2s: ['a', 'b']]
        final Map<String, String> environment = environmentFromMap(inputData)
        final String message = mergeRequestMessageFromMap(inputData)
        String defaultInputs = defaults.defaultInputs
        def parser = new InputParser(defaults.exposed)

        // check environment variables parse
        Map result_env = parser.parse(defaultInputs, environment, null)
        Assert.assertEquals(expectedData, result_env.args)
        Assert.assertEquals(null, result_env.combinations)

        // check merge request message parse
        Map result_mr = parser.parse(defaultInputs, null, message)
        Assert.assertEquals(expectedData, result_mr.args)
        Assert.assertEquals(null, result_mr.combinations)

        // check both MR message and environment variables parse
        Map result = parser.parse(defaultInputs, environment, message)
        Assert.assertEquals(expectedData, result.args)
        Assert.assertEquals(null, result.combinations)
    }


    /**
     * Test the parser can handle not exposed inputs
     */
    @Test
    void testNotExposedInputs() {

        Map defaults = [
            defaultInputs: '''
                key = v
                lists = a | b
            ''',
            exposed: []
        ]

        final Map<String, List<String>> inputData = [
                ('key'): ['value'],
                ('lists'): ['one', 'two', 'three']
        ]

        final Map expectedData = ['key':['v'], 'lists':['a', 'b']]
        final Map<String, String> environment = environmentFromMap(inputData)
        final String message = mergeRequestMessageFromMap(inputData)
        String defaultInputs = defaults.defaultInputs
        def parser = new InputParser(defaults.exposed)

        // check environment variables parse
        Map result_env = parser.parse(defaultInputs, environment, null)
        Assert.assertEquals(expectedData, result_env.args)
        // check merge request message parse
        Map result_mr = parser.parse(defaultInputs, null, message)
        Assert.assertEquals(expectedData, result_mr.args)
        // check both MR message and environment variables parse
        Map result = parser.parse(defaultInputs, environment, message)
        Assert.assertEquals(expectedData, result.args)
    }



    /**
     * Test the parser can handle not exposed combination assignment
     */
    @Test
    void testNotExposedCombAssignment() {

        Map defaults = [
            defaultInputs: '''
                key = v
                key1,key2 = a,c
            ''',
            exposed: ['key']
        ]

        final Map<String, List<String>> inputData = [
                ('key'): ['value'],
                ('key1,key2'): ["a, d"],
        ]

        Map expectedData = ['key':['value'], 'key1':['a'], 'key2': ['c']]

        final String message = mergeRequestMessageFromMap(inputData)
        String defaultInputs = defaults.defaultInputs
        def parser = new InputParser(defaults.exposed)
        Map result = parser.parse(defaultInputs, null, message)

        Assert.assertEquals(expectedData, result.args)
        Assert.assertEquals(null, result.combinations)
    }


    /**
     * Test the parser can handle not exposed combinations
     */
    @Test
    void testNotExposedCombinations() {

        Map defaults = [
            defaultInputs: '''
                key = v
                lists,list2s = a,c
            ''',
            exposed: ['key'],
            parallel: ['lists', 'list2s']
        ]

        final Map<String, List<String>> inputData = [
                ('key'): ['value'],
                ('lists,list2s'): ["a, d"],
        ]

        Map expectedData = ['key':['value'], 'lists':['a'], 'list2s': ['c']]
        List expectedComb = [[list:'a', list2:'c']]
        final String message = mergeRequestMessageFromMap(inputData)
        String defaultInputs = defaults.defaultInputs
        def parser = new InputParser(defaults.exposed, defaults.parallel)
        Map result = parser.parse(defaultInputs, null, message)

        Assert.assertEquals(expectedData, result.args)
        Assert.assertEquals(expectedComb, result.combinations)
    }


    /**
     * Test case like k1,k2 = v1,v2
     */
    @Test
    void testParseAssignment() {

        Map defaults = [
            defaultInputs: '''
                key1 = v1
                listKeys = a | b | c
                key2,key3 = v2,v3
            ''',
            exposed: ['key2', 'key3']
        ]

        final Map<String, List<String>> inputData = [
                ('key1'): ['value'],
                ('key2,key3'): ["a, b"]
        ]

        final String message = mergeRequestMessageFromMap(inputData)
        Map expectedData = [key1: ['v1'], key2: ['a'], key3: ['b'], listkeys:['a', 'b','c']]
        String defaultInputs = defaults.defaultInputs
        def parser = new InputParser(defaults.exposed)
        Map result = parser.parse(defaultInputs, null, message)

        Assert.assertEquals(expectedData, result.args)
        Assert.assertEquals(null, result.combinations)
    }

    /**
     * Test case like
     * k1,k2 = v1,v2
     * k3,k4 = v3,v4
     */
    @Test
    void testParseTwoAssignments() {

        Map defaults = [
            defaultInputs: '''
                key1 = v1
                listKeys = a | b | c
                key2,key3 = v2,v3
                key4,key5 = v4,v5
            '''
        ]

        Map expectedData = [key1:['v1'], key2:['v2'], key3:['v3'], key4:['v4'], key5:['v5'], listkeys:['a','b','c']]
        String defaultInputs = defaults.defaultInputs
        def parser = new InputParser(defaults.exposed)
        Map result = parser.parse(defaultInputs, null, null)

        Assert.assertEquals(expectedData, result.args)
        Assert.assertEquals(null, result.combinations)
    }

    /**
     * Test case like listKey1s,listKey2s = a,1
     */
    @Test
    void testParseOneCombination() {

        Map defaults = [
            defaultInputs: '''
                key1 = v1
                listKeys, listKey2s = five,a
            ''',
            parallel: ['listkeys', 'listkey2s']
        ]

        Map<String, List<String>> expectedData = [key1: ['v1'], listkeys: ['five'], listkey2s: ['a']]
        def map1 = [listkey:'five', listkey2:'a']
        List expectedComb = [map1]
        String defaultInputs = defaults.defaultInputs
        def parser = new InputParser(defaults.exposed, defaults.parallel)
        Map result = parser.parse(defaultInputs, null, null)

        Assert.assertEquals(expectedData, result.args)
        Assert.assertEquals(expectedComb, result.combinations)
    }

    /**
     * Test case like listKey1s,listKey2s = a,1 | b, 2
     */
    @Test
    void testParseMultipleCombinations() {

        Map defaults = [
            defaultInputs: '''
                key1 = v1
                listKeys, listKey2s = five,a | five,b
            ''',
            parallel: ['listkeys', 'listkey2s']
        ]

        Map<String, List<String>> expectedData = [key1: ['v1'], listkeys: ['five'], listkey2s: ['a', 'b']]

        def map1 = [listkey:'five', listkey2:'a']
        def map2 = [listkey:'five', listkey2:'b']
        List expectedComb = [map1, map2]
        String defaultInputs = defaults.defaultInputs
        def parser = new InputParser(defaults.exposed, defaults.parallel)
        Map result = parser.parse(defaultInputs, null, null)

        Assert.assertEquals(expectedData, result.args)
        Assert.assertEquals(expectedComb, result.combinations)
    }

    /**
     * Test case like
     * listKey1s,listKey2s = a,1 | b, 2
     * listKey3s,listKey4s = c,3 | d, 4
     */
    @Test
    void testParseDefaultsComplexCombinations() {

        Map defaults = [
            defaultInputs: '''
                key1 = v1
                listKeys, listKey2s = five,a | five,b
                listKey3s, listKey4s = six,c | six,d
            ''',
            parallel: ['listkeys', 'listkey2s', 'listkey3s', 'listkey4s']
        ]

        Map<String, List<String>> expectedData = [key1: ['v1'], listkeys: ['five'], listkey2s: ['a', 'b'], listkey3s: ['six'], listkey4s: ['c', 'd']]

        def map1 = [listkey:'five', listkey2:'a', listkey3:'six', listkey4:'c']
        def map2 = [listkey:'five', listkey2:'b', listkey3:'six', listkey4:'c']
        def map3 = [listkey:'five', listkey2:'a', listkey3:'six', listkey4:'d']
        def map4 = [listkey:'five', listkey2:'b', listkey3:'six', listkey4:'d']
        List expectedComb = [map1, map2, map3, map4]

        String defaultInputs = defaults.defaultInputs
        def parser = new InputParser(defaults.exposed, defaults.parallel)
        Map result = parser.parse(defaultInputs, null, null)

        Assert.assertEquals(expectedData, result.args)
        Assert.assertEquals(expectedComb, result.combinations)
    }

    /**
     * Test case like
     * listKey1s,listKey2s = a,1 | b, 2
     * listKey3s,listKey4s = c,3 | d, 4
     * and defaults should be overwritten by user inputs
     */
    @Test
    void testParseComplexCombinations() {

        Map defaults = [
            defaultInputs: '''
                key1 = v1
                listKeys, listKey2s = five,a | five,b
                listKey3s, listKey4s = six,c | six,d
            ''',
            exposed: ['listKey3s', 'listKey4s'],
            parallel: ['listkeys', 'listkey2s', 'listkey3s', 'listkey4s']
        ]

        final Map<String, List<String>> inputData = [
                ('key1'): ['value'],
                ('listKey3s,listKey4s'): ["x, y"]
        ]

        final String message = mergeRequestMessageFromMap(inputData)

        Map<String, List<String>> expectedData = [key1: ['v1'], listkeys: ['five'], listkey2s: ['a','b'], listkey3s: ['x'], listkey4s: ['y']]

        def map1 = [listkey:'five', listkey2:'a', listkey3:'x', listkey4:'y']
        def map2 = [listkey:'five', listkey2:'b', listkey3:'x', listkey4:'y']
        List expectedComb = [map1, map2]

        String defaultInputs = defaults.defaultInputs
        def parser = new InputParser(defaults.exposed, defaults.parallel)
        Map result = parser.parse(defaultInputs, null, message)

        Assert.assertEquals(expectedData, result.args)
        Assert.assertEquals(expectedComb, result.combinations)
    }


    /**
     * Test Merge Request message can overwrite environment viriables
     *
     */
    @Test
    void testMRmessageOverEnv() {

        Map defaults = [
            defaultInputs: '''
                key1 = v1
                key2 = v2
                lists = one | two | three
                list2s = l
                list3s = l
                list4s = l
            ''',
            exposed: ['key1', 'key2', 'lists', 'list2s', 'list3s', 'list4s'],
            parallel: ['lists', 'list2s', 'list3s', 'list4s']
        ]

        final Map<String, List<String>> inputDataEnv = [
                ('key1'): ['valueEnv'],
                ('key2'): ['valueEnv'],
                ('lists,list2s'): ["one, a | one, c"],
                ('list3s,list4s'): ["x, w"]
        ]

        final Map<String, List<String>> inputDataMR = [
                ('key1'): ['valueMR'],
                ('lists,list2s'): ["one, a | one, b"]
        ]

        final Map<String, List<String>> expectedData = [
                ('key1'): ['valueMR'],
                ('key2'): ['valueEnv'],
                ('lists'): ['one', 'two', 'three'],
                ('list2s'): ['l', 'a', 'b'],
                ('list3s'): ['x'],
                ('list4s'): ['w']
        ]

        def map1 = [list:'one', list2:'a', list3:'x', list4:'w']
        def map2 = [list:'one', list2:'b', list3:'x', list4:'w']
        List expectedComb = [map1, map2]

        final Map<String, String> environment = environmentFromMap(inputDataEnv)
        final String message = mergeRequestMessageFromMap(inputDataMR)
        String defaultInputs = defaults.defaultInputs
        def parser = new InputParser(defaults.exposed, defaults.parallel)
        Map result = parser.parse(defaultInputs, environment, message)

        Assert.assertEquals(expectedData, result.args)
        Assert.assertEquals(expectedComb, result.combinations)
    }

}
