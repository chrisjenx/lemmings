package com.chrisjenx.lemmings

import com.android.ddmlib.testrunner.InstrumentationProtoResultParser
import com.chrisjenx.lemmings.helpers.runCommand
import com.chrisjenx.lemmings.listener.CustomTestRunListener
import java.io.File
import java.io.InputStream
import java.util.logging.Logger

class DeviceOutputParser(deviceName: String, projectName: String, flavorName: String) {

    private val log = Logger.getLogger("TestParser")
    private val customRunListener = CustomTestRunListener(deviceName, projectName, flavorName, log)
    private val instrumentationParser by lazy {
        InstrumentationProtoResultParser("", listOf(customRunListener))
    }

    /**
     * Takes the log from `adb shell am instrument -m` and will turn it into XML Junit report
     *
     * @return true if any test fails
     */
    fun parseInstrumentationLog(inputStream: InputStream, outputDir: File): Boolean {
        println("Starting test result parsing...")
        outputDir.mkdirs()
        check(outputDir.isDirectory) { "Output dir is not a directory: $outputDir" }

        // Set the output for the test results
        if (!outputDir.exists()) outputDir.mkdirs()
        val testOutput = outputDir.resolve("testresults").also { it.mkdirs() }
        val logOutput = outputDir.resolve("logs").also { it.mkdirs() }
        customRunListener.setReportDir(testOutput)
        customRunListener.setLocatDir(logOutput)

        // read bytearray from a file into the parser
        inputStream.use {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytes = it.read(buffer)
            while (bytes >= 0) {
                instrumentationParser.addOutput(buffer, 0, bytes)
                bytes = it.read(buffer)
            }
        }
        val results = customRunListener.runResult
        log.info("Test results: ${results.textSummary}")
        outputDir.listFiles()?.forEach { f -> log.fine("Output: ${f.absolutePath}") }
        if (results.hasFailedTests()) {
            log.warning("FAILED: ${results.numAllFailedTests} tests failed")
            runCommand(
                listOf("buildkite-agent", "annotate", "Tests failed", "--style", "warning", "--context", "tests"),
                ignoreError = true
            ) {}
            return false
        }
        runCommand(
            listOf("buildkite-agent", "annotate", "Tests passed", "--style", "success", "--context", "tests"),
            ignoreError = true
        ) {}
        return true
    }

}
