plugins {
    id("base-conventions")
}

dependencies {
    testImplementation(libs.rsprot.api)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.attr)
    implementation(projects.api.death)
    testImplementation(projects.api.registry)
    testImplementation(projects.api.utils.utilsLogging)
    testImplementation(libs.fastutil)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
