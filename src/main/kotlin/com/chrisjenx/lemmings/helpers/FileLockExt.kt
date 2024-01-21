package com.chrisjenx.lemmings.helpers

import java.io.File
import java.io.RandomAccessFile
import java.time.Duration
import java.time.Instant
import java.util.logging.Logger

private val logger = Logger.getLogger("FileLockExt")

fun <T> waitForDeviceLock(block: () -> T): T = waitForLock("/tmp/device.lock", block)

fun <T> waitForEmulatorLock(block: () -> T): T = waitForLock("/tmp/emulator.lock", block)

private fun <T> waitForLock(lockFile: String, block: () -> T): T {
    return RandomAccessFile(lockFile, "rw").channel.use { lockChannel ->
        val start = Instant.now()
        // should wait until it can get a lock
        lockChannel.lock().use {
            val ms = Duration.between(start, Instant.now()).toMillis()
            logger.info("Waited ${ms}ms for ${lockFile.split("/").lastOrNull()}")
            block()
        }
    }
}

/**
 * @return false if device is in use and you need to find another emulator to lock
 */
fun createEmulatorLockFile(avdName: String): Boolean {
    val file = File("/tmp/device-$avdName.lock")
    if (file.exists()) return false // already locked
    file.createNewFile()
    file.deleteOnExit()
    return true
}

/**
 * @return false if port exists and you need to find another serial to lock
 */
fun createPortLockFile(port: Int): Boolean {
    val file = File("/tmp/emulator-$port.lock")
    if (file.exists()) return false // already locked
    file.createNewFile()
    file.deleteOnExit()
    return true
}
