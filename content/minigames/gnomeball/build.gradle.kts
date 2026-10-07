plugins { id("base-conventions") }

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.engine.utilsBits)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.registry)
    testImplementation(libs.fastutil)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.dir(rootProject.file(".data/gamevals"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
