plugins { id("base-conventions") }
dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.skills.woodcutting)
    implementation(projects.content.interfaces.collectionLog)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.registry)
    testImplementation(libs.fastutil)
}
tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.dir(rootProject.file(".data/gamevals"))
}

