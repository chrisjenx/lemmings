package com.chrisjenx.lemmings.helpers

import kotlinx.coroutines.future.await
import java.util.logging.Logger

object EmulatorHelper {

    // Technically adb only "Supports" 5554-5584, but we need more than 30 devices, we have to use even numbers
    private val PORT_RANGE = (5554..5810).filter { it % 2 == 0 }
    private val logger: Logger = Logger.getLogger(EmulatorHelper::class.java.name)

    //TODO: work out how to upgrade gracefully
    fun fetchEmulatorImage(systemImg: String) {
        println("~~~ Fetch emulator image [$systemImg]")
        val list = listOf("sdkmanager", "--list_installed")
        val installed = runCommand(list) { lines -> lines.any { it.contains(systemImg) } }
        if (installed) return // if contains system image do nothing
        val cmd = listOf("sdkmanager", systemImg)
        runCommand(cmd, stdin = "y") { lines -> lines.forEach { logger.fine(it) } }
    }

    /**
     * We want to reuse emulators to save time booting etc.
     * - We build a prefix name using the system image, e.g. "android-30-google_atd-x86"
     * - We can then search using "avdmanager list avd -c" to find any existing emulators with that prefix
     * - Each running terminal testrunner holds a lock on that device so we then need to check who is using it, and
     *   iterate until we find a free one.
     * - If we can't find a free one, we create a new one.
     * - We then put a temp lock on that device emulator so none else can use it.
     * - When the testrunner finishes, we clean up the device and release the lock.
     *
     * @param systemImg The system image to use for the emulator
     * @param clean If true, which ever emulator we find to use we clean (normally on restart of a job calls this)
     * @return the avd name of the emulator
     */
    fun getOrCreateEmulator(systemImg: String, deviceDef: String, clean: Boolean = false): String {
        val devicePrefix = "$systemImg-$deviceDef".replace("system-images;", "").replace("[;_\\s]".toRegex(), "-")
        val findCmds = listOf("avdmanager", "list", "avd", "-c")
        return waitForDeviceLock {
            // Can be empty if new run/device/agent etc
            val devices = runCommand(findCmds) { lines -> lines.filter { it.startsWith(devicePrefix) }.toList() }
            val lockedDevice = devices.firstOrNull { avdName -> createEmulatorLockFile(avdName) }
            // if we found one to use, then we return that one to use
            if (lockedDevice != null) {
                if (clean) { // Recreate if clean enabled
                    deleteEmulator(lockedDevice)
                    createEmulator(lockedDevice, systemImg, deviceDef)
                }
                return@waitForDeviceLock lockedDevice
            }
            // Otherwise we create a new device
            val newDeviceName = generateDeviceName(prefix = devicePrefix)
            createEmulator(newDeviceName, systemImg, deviceDef)
            createEmulatorLockFile(newDeviceName)
            // Return new device name to use
            newDeviceName
        }
    }

    /**
     * @param deviceName The name of the emulator to create
     * @param systemImg The system image to use for the emulator
     */
    private fun createEmulator(deviceName: String, systemImg: String, deviceDef: String) = runCatching {
        logger.config("Creating emulator [$deviceName] using [$systemImg]")
        val cmd = listOf(
            "avdmanager", "--verbose", "create", "avd", "--name", deviceName, "--package", systemImg, "-c", "2000M",
            "--device", deviceDef
        )
        runCommand(cmd, stdin = "n", ignoreError = true) { lines -> lines.forEach { logger.config(it) } }
    }.onFailure {
        deleteEmulator(deviceName)
        throw it
    }.getOrThrow()

    fun deleteEmulator(deviceName: String) {
        logger.info("Deleting emulator $deviceName")
        val cmd = listOf("avdmanager", "delete", "avd", "--name", deviceName)
        // If the emulator doesn't exist this will fail
        runCommand(cmd, ignoreError = true) { lines -> lines.forEach { logger.config(it) } }
    }

    /**
     * @param fileChannel for locking when creating the device so we generate a unique port
     */
    fun launchEmulator(deviceName: String, debug: Boolean = false): Emulator {
        fun nextPort(from: Int = 0): Int {
            val serials = AdbHelper.findSerials().mapNotNull { it.removePrefix("emulator-").toIntOrNull() }
            val nextPort = PORT_RANGE.first { it !in serials && it > from }
            // Recursive call to find next port until we find one not in use and locked
            if (!createPortLockFile(nextPort)) return nextPort(nextPort)
            return nextPort
        }

        var process: Process? = null
        return runCatching {
            waitForEmulatorLock {
                // Find unused serial port
                val port = nextPort()
                val cmds = listOf(
                    "emulator", "-port", port.toString(), "-avd", deviceName,
                    "-cores", "2", "-memory", "4096", //"-no-snapshot",
                    "-no-boot-anim", "-no-window", "-gpu", "swiftshader_indirect"
                )
                logger.info("Launching emulator: $deviceName, serial: emulator-$port")
                process = ProcessBuilder(cmds)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(if (debug) ProcessBuilder.Redirect.INHERIT else ProcessBuilder.Redirect.DISCARD)
                    .start()
                Emulator(deviceName, port, process!!)
            }
        }.onFailure {
            process?.destroy()
            deleteEmulator(deviceName)
        }.getOrThrow()
    }

}

data class Emulator(val deviceName: String, private val port: Int, private val process: Process) {
    val serial = "emulator-$port"
    suspend fun stopAndCleanup() {
        println("Stopping emulator $deviceName")
        AdbHelper.killDevice(serial)
        process.destroy()
        process.onExit().await()
    }
}
