plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    testImplementation(libs.fastutil)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.dir(rootProject.file(".data/gamevals-binary"))
}
