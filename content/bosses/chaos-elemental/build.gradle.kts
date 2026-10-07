plugins { id("base-conventions") }

dependencies {
    implementation(projects.api.bosses)
    implementation(projects.api.pluginCommons)
    testImplementation(projects.api.invStorage)
    testImplementation(libs.rsprot.api)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
