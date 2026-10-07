plugins { id("base-conventions") }

dependencies {
    implementation(projects.api.bosses)
    implementation(projects.api.attr)
    implementation(projects.api.dropTable)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.farming)
    implementation(projects.api.death)
    implementation(projects.api.dropTablePlugin)
    implementation(projects.content.interfaces.collectionLog)
    testImplementation(libs.fastutil)
    testImplementation(projects.api.registry)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.music)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
