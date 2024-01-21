package com.chrisjenx.lemmings.listener

import com.android.ddmlib.testrunner.IInstrumentationResultParser.StatusKeys.DDMLIB_LOGCAT
import com.android.ddmlib.testrunner.TestIdentifier
import com.android.ddmlib.testrunner.TestResult
import com.android.ddmlib.testrunner.XmlTestRunListener
import com.chrisjenx.lemmings.helpers.sanitizeFileName
import java.io.File
import java.io.IOException
import java.util.logging.Logger

class CustomTestRunListener(
    private val deviceName: String,
    private val projectName: String,
    private val flavorName: String,
    private val logger: Logger?
) : XmlTestRunListener() {

    private var logcatDir: File? = null
    private val failedTests: MutableSet<TestIdentifier> = hashSetOf()

    fun setLocatDir(logcatDir: File?) {
        this.logcatDir = logcatDir
    }

    @Throws(IOException::class)
    override fun getResultFile(reportDir: File?): File {
        return File(
            reportDir,
            sanitizeFileName("TEST-$deviceName-$projectName-$flavorName.xml")
        )
    }

    // in order for the gradle report to look good we put the test suite name as one of the
    // test class name.
    override fun getTestSuiteName(): String? {
        // in order for the gradle report to look good we put the test suite name as one of the
        // test class name.
        val testResults: Map<TestIdentifier, TestResult> = runResult.testResults
        if (testResults.isEmpty()) {
            return null
        }
        val testEntry: Map.Entry<TestIdentifier, TestResult> = testResults.entries.iterator().next()
        return testEntry.key.className
    }

    override fun getPropertiesAttributes(): Map<String, String> {
        val propertiesAttributes: MutableMap<String, String> = LinkedHashMap(super.getPropertiesAttributes())
        propertiesAttributes["device"] = deviceName
        propertiesAttributes["flavor"] = flavorName
        propertiesAttributes["project"] = projectName
        return propertiesAttributes.toMap()
    }


    override fun testRunStarted(runName: String?, testCount: Int) {
        logger?.info("Starting $testCount tests on $deviceName")
        super.testRunStarted(runName, testCount)
    }

    override fun testFailed(test: TestIdentifier, trace: String) {
        if (logger != null) {
            logger.warning("${test.className} > ${test.testName}[$deviceName] \u001b[31mFAILED \u001b[0m")
            logger.warning(getModifiedTrace(trace))
        }
        failedTests.add(test)
        super.testFailed(test, trace)
    }

    override fun testAssumptionFailure(test: TestIdentifier, trace: String) {
        logger?.warning(
            "${test.className} > ${test.testName}[$deviceName] \u001b[33mSKIPPED \u001b[0m\n${getModifiedTrace(trace)}"
        )
        super.testAssumptionFailure(test, trace)
    }

    override fun testEnded(test: TestIdentifier, testMetrics: Map<String?, String?>) {
        if (!failedTests.remove(test)) {
            // if wasn't present in the list, then the test succeeded.
            logger?.info("${test.className} > ${test.testName}[${deviceName}] \u001b[32mSUCCESS \u001b[0m")
        } else {
            // Removed a failed test
            val logcat = testMetrics[DDMLIB_LOGCAT]
            if (logcat?.isNotEmpty() == true && logcatDir != null) {
                val logcatFile = File(
                    logcatDir, sanitizeFileName("LOG-$deviceName-$projectName-$flavorName-${test.testName}.log")
                )
                logcatFile.writeText(logcat)
            }
        }
        super.testEnded(test, testMetrics)
    }

    override fun testRunFailed(errorMessage: String?) {
        logger?.warning("Tests on $deviceName failed: $errorMessage")
        super.testRunFailed(errorMessage)
    }

    override fun testIgnored(test: TestIdentifier) {
        logger?.warning("${test.className} > ${test.testName}[$deviceName] \u001b[33mSKIPPED \u001b[0m")
        super.testIgnored(test)
    }

    private fun getModifiedTrace(trace: String): String {
        // split lines
        val lines = trace.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return if (lines.size < 2) trace else
            """	
            |${lines[0]} 
            |${lines[1]}
            |""".trimMargin()

        // get the first two lines, and prepend \t on them
    }
}