plugins {
    application
    kotlin("jvm").version("1.9.22")
}

application {
    mainClass.set("MainKt")
    applicationName = "lemmings"
    applicationDistribution.from(tasks.jar)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    from(configurations.runtimeClasspath.get().files.map { file: File ->
        if (file.isDirectory) file else zipTree(file.absoluteFile)
    })
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    archiveBaseName.set("testrunner")
}

dependencies {
    // Should probably shadow this for the jar
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.android.tools.ddms:ddmlib:31.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.6")
    // Testing
    testImplementation(kotlin("test"))
}
