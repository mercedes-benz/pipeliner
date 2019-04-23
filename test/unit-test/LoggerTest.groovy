// SPDX-License-Identifier: MIT

/*
 *  Copyright (c) 2019 MBition GmbH
 */

import org.junit.Test
import org.junit.After
import org.junit.Assert
import com.daimler.pipeliner.Logger
import static groovy.test.GroovyAssert.shouldFail

class LoggerTest {

    @After
    void tearDown(){
        Logger.clear()
    }

    @Test
    void logWithoutInitialization() {
        shouldFail{
            Logger.info "Hello"
        }
    }

    @Test
    void logToStandardOut() {
        // Create a stream and pass it to logger
        final ByteArrayOutputStream myOut = new ByteArrayOutputStream();
        Logger.init(new PrintStream(myOut))

        Logger.info "Hello"

        final String standardOutput = myOut.toString()
        Assert.assertEquals ("INFO: Hello\n", standardOutput)

    }
}
