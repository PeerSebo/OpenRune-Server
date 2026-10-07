plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.openrune.central.common)
    implementation(projects.api.attr)
    implementation(libs.kotlin.inline.logger)
    runtimeOnly(libs.logback.classic)
    implementation(libs.fastutil)
    implementation(libs.guice)
    implementation(libs.kotlin.coroutines.core)
    implementation(projects.api.db)
    implementation(projects.api.parsers.jackson)
    implementation(projects.api.parsers.json)
    implementation(projects.api.realm)
    implementation(projects.api.serverConfig)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.module)
    implementation(projects.server.services)
    testImplementation(kotlin("test"))
    testImplementation(libs.jackson.module.kotlin)
    testImplementation(libs.embedded.postgres)
}

tasks.test {
    doFirst {
        systemProperty("account.test.classpath", sourceSets["test"].runtimeClasspath.asPath)
    }
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
}
