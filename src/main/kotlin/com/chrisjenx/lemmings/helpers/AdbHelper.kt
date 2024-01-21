package com.chrisjenx.lemmings.helpers

import com.chrisjenx.lemmings.DeviceOutputParser
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Logger


object AdbHelper {

    private val logger: Logger = Logger.getLogger(AdbHelper::class.java.name)

    /**
     * Finds the device serial matching the given device (avd) name.
     *
     * IDEA: We could probably just loop and delay until we get a serial
     */
    suspend fun getEmulatorSerial(deviceName: String): String {
        println("~~~ Find the emulator serial for $deviceName")
        var waited = 0
        var serials: Set<String>
        // Get running serial numbers
        while (waited < 60) {
            serials = findSerials()
            logger.config("Found emulators: $serials")
            // Run emu avd name against each serial number
            val serial = serials.firstOrNull { serial ->
                val name = runCommand(listOf("adb", "-s", serial, "emu", "avd", "name")) { lines ->
                    lines.firstOrNull()?.removeSuffix("OK\r\n") ?: ""
                }
                logger.config("Found emulator: $serial, avd: $name")
                deviceName.equals(name, ignoreCase = true)
            }
            if (serial != null) return serial
            delay(1000)
            waited += 1
        }
        throw IllegalStateException("Could not find emulator serial for $deviceName")
    }

    /**
     * Will return all running emulator serials from adb devices:
     * ```
     * > adb devices
     * List of devices attached
     * emulator-5554
     * emulator-5556
     * ```
     */
    fun findSerials(): Set<String> {
        val serials = runCommand(listOf("adb", "devices")) { lines ->
            lines.filter { it.contains("emulator") }.map { it.split('\t').first() }.toSet()
        }
        return serials
    }

    /**
     * Waits for the emulator to be ready using `adb wait-for-device`
     *
     * @param serial The serial number of the emulator to wait for. If null, waits for any emulator.
     */
    fun waitForEmulator(serial: String?) {
        if (serial.isNullOrBlank()) runCommand(listOf("adb", "wait-for-device")) {}
        else runCommand(listOf("adb", "-s", serial, "wait-for-device")) {}
    }

    /**
     * Waits for the emulator to be ready using `adb shell getprop sys.boot_completed`
     *
     * [waitForEmulator] will wait for the emulator to start it's process, but not necessarily be ready to run tests.
     *
     * @see waitForEmulator
     */
    suspend fun waitForEmulatorBoot(serial: String) {
        val cmd = listOf("adb", "-s", serial, "shell", "getprop", "sys.boot_completed")
        var bootCompleted = false
        var waited = 0
        // only try for 60 seconds
        logger.config("Waiting for emulator to boot")
        while (!bootCompleted && waited <= 60) {
            print(".")
            runCommand(cmd, ignoreError = true) { lines ->
                bootCompleted = lines.firstOrNull()?.contains("1") ?: false
            }
            delay(1000) // sleep 2 seconds than try again
            waited += 1
        }
        print("booted\n")
    }

    /**
     * Returns the list of instrumentation available on the device using `adb shell pm list instrumentation`
     */
    fun getTargetInstrumentation(serial: String): String? {
        val cmds = listOf("adb", "-s", serial, "shell", "pm", "list", "instrumentation")
        return runCommand(cmds, ignoreError = true) { lines ->
            lines.filter { !it.contains("androidx.test.orchestrator/.AndroidTestOrchestrator") }
                .map { it.removePrefix("instrumentation:").split(" ").first() }
                .firstOrNull().also { logger.config("Found target instrumentation [$it]") }
        }
    }

    fun installApk(serial: String, apk: String) {
        removeExistingTestTarget(serial)
        logger.config("Installing apk: $apk")
        runCommand(listOf("adb", "-s", serial, "install", "--force-queryable", "-r", apk)) {}
    }

    fun removeExistingTestTarget(serial: String) {
        fun uninstall(serial: String, instrumentation: String) {
            val target = instrumentation.split("/").first()
            logger.config("Removing installed test target: $target")
            // adb uninstall com.package.name.test
            runCommand(listOf("adb", "-s", serial, "uninstall", target), ignoreError = true) {}
        }
        // We loop incase for what ever reason the emulator has multiple test targets installed
        do {
            val instrumentation = getTargetInstrumentation(serial)
            if (instrumentation.isNullOrBlank()) break
            uninstall(serial, instrumentation)
        } while (true)
    }

    fun disableAnimations(serial: String) {
        runCommand(
            listOf("adb", "-s", serial, "shell", "settings", "put", "global", "window_animation_scale", "0")
        ) {}
        runCommand(
            listOf("adb", "-s", serial, "shell", "settings", "put", "global", "transition_animation_scale", "0")
        ) {}
        runCommand(
            listOf("adb", "-s", serial, "shell", "settings", "put", "global", "animator_duration_scale", "0")
        ) {}
    }

    fun fetchAndInstallOrchestratorApps(serial: String) {
        val orchestratorFile = File("/tmp/orchestrator-1.4.2.apk")
        val testServicesFile = File("/tmp/test-services-1.4.2.apk")
        if (!orchestratorFile.exists() || !testServicesFile.exists()) {
            logger.config("Downloading orchestrator and test services")
            val client = OkHttpClient()
            Request.Builder().url(ORCHESTRATOR_URL).build().let { request ->
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Failed to download orchestrator: ${response.code}" }
                    response.body?.byteStream()?.use { it.copyTo(orchestratorFile.outputStream()) }
                }
            }
            Request.Builder().url(TEST_SERVICES_URL).build().let { request ->
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Failed to download test services: ${response.code}" }
                    response.body?.byteStream()?.use { it.copyTo(testServicesFile.outputStream()) }
                }
            }
        }
        logger.config("Uninstall orchestrator and test services")
        runCommand(listOf("adb", "-s", serial, "uninstall", "androidx.test.services"), ignoreError = true) {
            it.forEach { logger.config(it) }
        }
        runCommand(listOf("adb", "-s", serial, "uninstall", "androidx.test.orchestrator"), ignoreError = true) {
            it.forEach { logger.config(it) }
        }
        logger.config("Install orchestrator and test services")
        runCommand(listOf("adb", "-s", serial, "install", "--force-queryable", "-r", "/tmp/orchestrator-1.4.2.apk")) {}
        runCommand(listOf("adb", "-s", serial, "install", "--force-queryable", "-r", "/tmp/test-services-1.4.2.apk")) {}
    }

    fun clearSdcard(serial: String) {
        //adb -s serial shell 'rm -r /mnt/sdcard/Download/*'
        runCommand(listOf("adb", "-s", serial, "shell", "rm -r /sdcard/Download/*"), ignoreError = true) {}
    }

    @Suppress("SdCardPath")
    fun pullSdCard(serial: String, outputDir: String) = runCatching {
        val outputFile = File(outputDir).apply { mkdirs() }
        // Then pull each file into screenshots directory
        runCommand(
            listOf("adb", "-s", serial, "pull", "/sdcard/", outputFile.absolutePath),
            ignoreError = true
        ) { it.forEach { l -> logger.config(l) } }
        File(outputFile, "sdcard").listFiles()?.forEach {
            Files.move(it.toPath(), outputFile.resolve(it.name).toPath(), StandardCopyOption.ATOMIC_MOVE)
        }
    }

    /**
     * Will request device to stop and shutdown
     */
    fun killDevice(serial: String) {
        runCommand(listOf("adb", "-s", serial, "emu", "kill"), ignoreError = true) { lines ->
            lines.forEach { logger.config(it) }
        }
    }

    /**
     * @return true if tests succeeded, false if a test failed
     * @throws RuntimeException of process returns non 0 exit code
     */
    fun runTests(
        serial: String, deviceName: String, projectName: String, flavorName: String,
        targetInstrumentation: String, outputDir: File
    ): Boolean {
        val shellCommand = "CLASSPATH=$(pm path androidx.test.services) app_process / " +
                "androidx.test.services.shellexecutor.ShellMain am instrument -m -w -e emma true " +
                "-e clearPackageData true " +
                "-e targetInstrumentation $targetInstrumentation androidx.test.orchestrator/.AndroidTestOrchestrator"
        var result: Boolean
        val process = ProcessBuilder()
            .command("adb", "-s", serial, "shell", shellCommand)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start().also { p ->
                p.outputStream.close()
                // Start listening to the output to build results
                result = DeviceOutputParser(deviceName, projectName, flavorName)
                    .parseInstrumentationLog(p.inputStream, outputDir)
                p.waitFor()
            }
        if (process.exitValue() != 0) {
            throw RuntimeException("Failed to execute tests")
        }
        return result
    }


}

private const val ORCHESTRATOR_URL =
    "https://dl.google.com/android/maven2/androidx/test/orchestrator/1.4.2/orchestrator-1.4.2.apk"
private const val TEST_SERVICES_URL =
    "https://dl.google.com/android/maven2/androidx/test/services/test-services/1.4.2/test-services-1.4.2.apk"
