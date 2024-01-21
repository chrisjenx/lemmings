package com.chrisjenx.lemmings.helpers

/**
 * Runs the given command and returns the output as a sequence of lines.
 *
 * @param stdin write command to stdin after process starts
 * @throws IllegalStateException if the command fails.
 */
fun <T> runCommand(
    command: List<String>,
    stdin: String? = null,
    ignoreError: Boolean = false,
    stream: (Sequence<String>) -> T
): T {
    val process = runCatching {
        Runtime.getRuntime().exec(command.toTypedArray()).also {
            if (!stdin.isNullOrBlank()) {
                it.outputStream.bufferedWriter().use { writer ->
                    writer.write(stdin); writer.flush()
                }
            }
            it.waitFor()
        }
    }.onFailure {
        if (ignoreError) return stream(emptySequence())
        else throw it
    }.getOrThrow()
    if (ignoreError && process.exitValue() != 0) {
        return process.inputReader().useLines(stream)
    }
    check(process.exitValue() == 0) {
        "exec failed:[${command.joinToString(" ")}]\n${process.errorReader().readText()}"
    }
    return process.inputReader().useLines(stream)
}