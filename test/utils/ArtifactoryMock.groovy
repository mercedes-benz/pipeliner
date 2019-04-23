// SPDX-License-Identifier: MIT

/*
 *  Copyright (c) 2019 MBition GmbH
 */

package utils

class ArtifactoryMock {
    private def script

    ArtifactoryMock(def script) {
        this.script=script
    }

    static NewServer newServer(Map url){
        return new NewServer(url)
    }
}

class NewServer {

    Map inputMap
    String url
    String credentialsId
    Boolean bypassProxy

    NewServer(Map inputMap){
        this.url=inputMap.url
        this.credentialsId= inputMap.credentialsId
    }

    void upload(String spec){}
}