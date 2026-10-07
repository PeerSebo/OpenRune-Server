plugins { id("base-conventions") }

dependencies {
    implementation(projects.api.bosses)
    implementation(projects.api.pluginCommons)
    testImplementation(libs.fastutil)
    testImplementation(libs.rsprot.api)
    testImplementation(projects.api.invStorage)
}

tasks.test {
    workingDir = rootProject.projectDir
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.dir(rootProject.file(".data/gamevals-binary"))
}
