@file:Suppress("CallToThreadRun")
@file:OptIn(ExperimentalTime::class)

import com.chrisjenx.lemmings.helpers.AdbHelper
import com.chrisjenx.lemmings.helpers.Emulator
import com.chrisjenx.lemmings.helpers.EmulatorHelper
import com.chrisjenx.lemmings.logger.LoggingExt
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue


fun main(args: Array<String>): Unit = runBlocking {
    LoggingExt.configureLogging()
    // Using: https://github.com/Kotlin/kotlinx-cli
    val parser = ArgParser("testrunner")
    val apk by parser.option(ArgType.String, shortName = "a", fullName = "apk", description = "Path to the apk to test")
        .required()
    val output by parser.option(
        ArgType.String, shortName = "o", fullName = "output", description = "Path for the test run outputs"
    ).required()
    val projectName by parser.option(
        ArgType.String, shortName = "p", fullName = "project", description = "Project name",
    ).default("projectName")
    val flavorName by parser.option(
        ArgType.String, shortName = "f", fullName = "flavor", description = "Flavor name"
    ).default("flavorName")
    val debug by parser.option(
        ArgType.Boolean, shortName = "D", fullName = "debug", description = "Debug mode"
    ).default(false)
    val systemImg by parser.option(
        ArgType.String, shortName = "s", fullName = "systemImg", description = "System image to use for emulator"
    ).default("system-images;android-30;google_atd;x86")
    val deviceDef by parser.option(
        ArgType.String, shortName = "e", fullName = "device", description = "Device definition to use for emulator"
    ).default("pixel_6")
    val clean by parser.option(
        ArgType.String, shortName = "c", fullName = "clean", description = "Create a clean device before running tests"
    ).default("false")
    val numShards by parser.option(ArgType.Int, fullName = "numShards", description = "Number of shards to run")
    val shardIndex by parser.option(ArgType.Int, fullName = "shardIndex", description = "Index of shard to run")
    parser.parse(args) // validate input

    // TODO: enable sharding
    /*
    adb -s DEVICE_1_SERIAL shell am instrument -w -e numShards 2 -e shardIndex 0 > device_1_results // Runs first half of the tests
    adb -s DEVICE_2_SERIAL shell am instrument -w -e numShards 2 -e shardIndex 1 > device_2_results // Runs second half of the tests
     */

    EmulatorHelper.fetchEmulatorImage(systemImg)
    val deviceName = EmulatorHelper.getOrCreateEmulator(systemImg, deviceDef, clean = clean.equals("true", ignoreCase = true))
    val emulator = EmulatorHelper.launchEmulator(deviceName, debug)
    val shutdownHook = shutdownHook(emulator)
    Runtime.getRuntime().addShutdownHook(shutdownHook)
    runCatching {
        val serial = emulator.serial
        AdbHelper.waitForEmulator(serial) // We wait again just in-case the other wait for device was premature
        AdbHelper.waitForEmulatorBoot(serial)
        AdbHelper.disableAnimations(serial)
        AdbHelper.clearSdcard(serial)
        AdbHelper.fetchAndInstallOrchestratorApps(serial)
        AdbHelper.installApk(serial, apk)
        val targetInstrumentation = AdbHelper.getTargetInstrumentation(serial)
        checkNotNull(targetInstrumentation) { "Failed to find target instrumentation to run tests on" }

        println("--- Starting instrumentation tests")
        val result = measureTimedValue {
            AdbHelper.runTests(
                serial, deviceName, projectName, flavorName,
                targetInstrumentation, File(output)
            )
        }
        println("Instrumentation tests took ${result.duration}")
        println("~~~ Pulling screenshots")
        AdbHelper.pullSdCard(serial, output)
        AdbHelper.removeExistingTestTarget(serial)
        // Exit 1 if tests failed.
        if (!result.value) exitProcess(1)
    }.onSuccess {
        shutdownHook.run()
    }.onFailure {
        println("+++ Failed to run tests")
        shutdownHook.run()
    }.getOrThrow()
}

fun shutdownHook(emulator: Emulator) = object : Thread("ShutdownHook") {
    @Volatile
    var stopCalled = false
    override fun run() {
        if (stopCalled) return
        stopCalled = true
        println("Shutdown called")
        runBlocking { emulator.stopAndCleanup() }
    }
}
