package com.chrisjenx.lemmings

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DeviceOutputParserTest {

    @get:Rule
    internal val folder = TemporaryFolder.builder().build()

    private lateinit var outputFile: File

    @Before
    fun setUp() {
        outputFile = folder.newFolder("test-results")
    }

    @Test
    fun `read test output`() {
        // Get example-test-output.bin from java resources
        val resource = javaClass.classLoader.getResource("example-test-output.bin")!!
        val input = File(resource.toURI())

        val deviceOutputParser = DeviceOutputParser("deviceName", "projectName", "flavorName")
        deviceOutputParser.parseInstrumentationLog(input.inputStream(), outputFile)

        assert(outputFile.listFiles()?.isNotEmpty() == true) { "No files were written to $outputFile"}
        outputFile.listFiles()?.forEach { file ->
            assert(file.length() > 0) { "File $file is empty" }
        }
    }
}