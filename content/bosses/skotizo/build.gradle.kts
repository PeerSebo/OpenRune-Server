plugins { id("base-conventions") }
dependencies {
    implementation(projects.api.bosses)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.instances)
    implementation(projects.content.skills.slayer)
    testImplementation(projects.api.registry)
    testImplementation(libs.fastutil)
}
tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
