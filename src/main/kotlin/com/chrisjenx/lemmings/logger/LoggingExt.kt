package com.chrisjenx.lemmings.logger

import java.util.logging.LogManager

object LoggingExt {

    fun configureLogging() {
        runCatching {
            val stream = javaClass.classLoader.getResourceAsStream("logging.properties")
            LogManager.getLogManager().readConfiguration(stream)
        }.onFailure {
            println("ERROR: Failed settings logging config\n${it}")
            it.printStackTrace()
        }.getOrNull()
    }
}
