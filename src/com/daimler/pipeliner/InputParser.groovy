// SPDX-License-Identifier: MIT

/*
 *  Copyright (c) 2019 MBition GmbH
 */

package com.daimler.pipeliner

import java.util.regex.Matcher

/**
 * Parses input data in two forms: merge request, Jenkins env variables.
 *
 * @author Oleksandr Pozniak
 */

class InputParser {

    private static final String DELIMITER = "\\|"

    /**
     * The keys exposed to the user for modification
     */
    List exposed = []

    /**
     * The keys for which pipeline should be parallelized
     */
    List parallel = []

    /**
     * The rawCombinations hold comma separated keys values pairs as String
     */
    Map<String, String> rawCombinations = [:]

    List kCombs = []
    List vCombs = []


    /**
     * Constructor
     *
     * @param exposed. The keys exposed to the user for modification
     */
    InputParser(List exposed, List parallel=[]) {
        this.exposed = exposed.collect{it.trim().toLowerCase()}
        this.parallel = parallel
    }


    /**
     * @param stringifiedList. A String representing a list of strings separated
     * by |.
     * @return List<String> list of strings.
     */
    private List<String> unStringifyList(final String stringifiedList) {
        ArrayList<String> resultingList = []

        for (str in stringifiedList.split(DELIMITER)) {
            resultingList.add(str.trim())
        }

        return resultingList
    }


    /**
     * Remove delimiter and return the rest as a string
     *
     * @param message Merge request message.
     * @return String Relevant part of message
     */
    private String removeDelimiterAndComments(final String message) {
        if (message == null) {
            return null
        }
        String cleanedMessage = ""
        boolean foundDelimiter = false

        for (line in message.split('\n')) {
            final boolean lineMatchesADelimiter = (line ==~ /^-[-]+\s*$/)

            if (foundDelimiter && line) {
                cleanedMessage += line + "\n"
            }
            if (lineMatchesADelimiter) {
                foundDelimiter = true
            }
        }
        return cleanedMessage
    }


    /**
     * Convert environment to message
     *
     * @param Map<String, String> env
     * @return String converted message
     */
    private String envToMessage(final Map<String, String> env) {

        String message = "";
        String keyValuePair = "";
        env.each {
            // we should convert the key to lower case to be case insensitive
            String keyLowerCase = it.key.trim().toLowerCase()
            if (keyLowerCase.contains(',')) {
                List keys = keyLowerCase.split(',')
                List newKeys = []
                Boolean withPip = true
                for (key in keys) {
                    if (!key.trim().startsWith('pip_')) {
                        withPip = false
                        break
                    }
                    newKeys << key.trim() - "pip_"
                }
                if (withPip) {
                    keyValuePair = newKeys.join(',') + ' = ' + it.value
                }
            } else {
                if (keyLowerCase.startsWith('pip_')) {
                    keyLowerCase = keyLowerCase - "pip_"
                    keyValuePair = keyLowerCase + " = " + it.value
                }
            }
            if (keyValuePair) {
                message = [message, keyValuePair].join('\n')
            }
        }
        return message;
    }


    /**
     * Parses merge request or any String message. Find and remove delimiter,
     * splits merge request into the message itself and raw metadata, parse it.
     *
     * @param message Merge request to be parsed.
     * @return Map with the parsed result.
     */
    private Map<String, List<String>> parseFromMessage(String message) {
        Map metadata = [:]  
        if (message) {
            List<String> keyValuePairs = message.split('\n')
            keyValuePairs = keyValuePairs.collect {it.trim()}


            for (line in keyValuePairs) {
                Matcher keyValuePairMatcher = (line =~ /^(.*?)=(.*)$/)
                if (!keyValuePairMatcher || keyValuePairMatcher.size() < 1) {
                    continue
                }
                final List<String> keyValuePairAsList = (ArrayList<String>) keyValuePairMatcher[0]
                // keyValuePairAsList[0] is the entire string
                final String rawKey = keyValuePairAsList[1]
                final String rawValue = keyValuePairAsList[2]
                // Ignore when key is empty
                if (rawKey && rawKey.trim()) {
                    final String key = rawKey.trim().toLowerCase()
                    final String value = rawValue.trim()
                    if (key.contains(',')) {
                        // For combinations
                        this.rawCombinations[key] = value
                    } else {
                        // For normal key,value pairs
                        metadata[key] = unStringifyList(value)
                    }
                }
            }
        }
        return metadata
    }


    private Map updateMetadata(List<String> keys, List<String> values, Map metadata) {
        for (int i=0; i < keys.size(); i++) {
            String cleanKey = keys[i].trim().toLowerCase()
            metadata[cleanKey] = unStringifyList(values[i])
        }
        return metadata
    }


    private Boolean isKeysExposed(List<String> keys) {
        Boolean exposed = true
        for (int i=0; i < keys.size(); i++) {
            String cleanKey = keys[i].trim().toLowerCase()
            if (!this.exposed.contains(cleanKey)) {
                exposed = false
                break
            }
        }
        return exposed
    }


    /**
     * Magnage parallel metadata and do coresponding update accordingly
     *
     * @param keys list of keys.
     * @param valueStr the value in type String.
     * @param metadata Map with the current parsed metadata.
     *
     */
    public void manageParallel(List<String> keys, String valueStr, Map metadata) {
        List<String> values = valueStr.split(',')
        if (valueStr.contains("|")) {
            for (str in valueStr.split(DELIMITER)) {
                values = str.split(',')
                for (int i=0; i < keys.size(); i++) {
                    String cleanKey = keys[i].trim().toLowerCase()
                    if (metadata[cleanKey]) {
                        if (!metadata[cleanKey].contains(values[i].trim())) {
                            metadata[cleanKey] = metadata[cleanKey] + values[i].trim()
                        }
                    } else {
                        metadata[cleanKey] = unStringifyList(values[i])
                    }
                }
            }
        } else {
            updateMetadata(keys, values, metadata)
        }
    }


    /**
     * Handle combinations and return the proper Map with format like [args:
     * metadata, combinations: combinations].
     *
     * @param metadata Map with the current parsed metadata.
     * @param fromDefaults The Boolean variable states it's going
     * handle combinations from default or not (MR or env).
     * @return Map with the parsed result.
     */
    public Map handleCombinations(Map metadata, boolean fromDefaults) {
        List combinations = []

        this.rawCombinations.each{ k,v ->
            List<String> keys = k.split(',')
            List<String> values = v.split(',')
            String keyToCheck = keys[0]
            if (fromDefaults) {
                if (this.parallel.contains(keyToCheck)) {
                    this.kCombs << keys
                    this.vCombs << unStringifyList(v)
                    manageParallel(keys, v, metadata)
                } else {
                    updateMetadata(keys, values, metadata)
                }
            } else {
                if (isKeysExposed(keys)) {
                    if (this.parallel.contains(keyToCheck)) {
                        this.kCombs << keys
                        this.vCombs << unStringifyList(v)
                        manageParallel(keys, v, metadata)
                    } else {
                        updateMetadata(keys, values, metadata)
                    }
                }
            }
        }
        return metadata
    }


    /**
     * Parse and get the actual combinations.
     */
    private List getCombinations() {
        List combinations = []
        List rawKeys = this.kCombs.flatten()
        List modKeys = []
        for (int i=0; i < rawKeys.size(); i++) {
            // Remove the ending s from the chars
            // TODO: Keys should be at least > 1 char and maybe end with 's' too
            String modKey = rawKeys[i].substring(0, rawKeys[i].length() - 1)
            modKeys += modKey.trim().toLowerCase()
        }
        for(vComb in this.vCombs.combinations()) {
            int i = 0
            Map kvComb = [:]
            List pureValues = []
            for (int j=0; j < vComb.size(); j++) {
                pureValues += vComb[j].split(',')
            }
            for (value in pureValues.flatten()) {
                kvComb[modKeys[i]] = value.trim()
                i = i +1
            }
            if (!combinations.contains(kvComb)) {
                combinations << kvComb
            }
        }
        return combinations
    }


    /**
     * The main parse function which parses defaults, environment and MR message
     *
     * @return Map with the parsed metadata and combinations if any.
     *
     *        -- ex:
     *
     *        Map parsedMetadata = [key:value, list:[one, two, three]]
     *        List parsedComb = [[listKey: a, listKey2: b], [listKey: c, listKey2: d]]
     *        Map result = [args: parsedMetadata, combinations: parsedComb]
     */
    public Map<String, List<String>> parse(String defaultInputs, Map env, String message) {
        Logger.info("****** InputParser ********")
        Logger.info("defaultsInputs: " + defaultInputs)
        Logger.info("mergeRequestMsg: " + message)
        Logger.info("Jenkins environments: " + env)

        Map metadata = parseFromMessage(defaultInputs)
        Map inputs = handleCombinations(metadata, true)
        List combinations = getCombinations()
        Map parsedInputs = inputs

        String messageFromEnv = envToMessage(env)
        message = removeDelimiterAndComments(message)
        String combinedMsg = [messageFromEnv, message].join('\n')
        Map userInputs = parseFromMessage(combinedMsg)
        if (combinedMsg) {
            for (exposedKey in this.exposed) {
                if (userInputs[exposedKey]) {
                    inputs[exposedKey] = userInputs[exposedKey]
                }
            }
            parsedInputs = handleCombinations(inputs, false)
            combinations = getCombinations()
        }

        if (combinations) {
            return [args: parsedInputs, combinations: combinations]
        }
        return [args: parsedInputs]
    }

}

