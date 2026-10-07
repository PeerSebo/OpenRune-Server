plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.bundles.logging)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.module.kotlin)
    implementation(projects.api.attr)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.realm)
    implementation(projects.api.realmConfig)

    testImplementation(projects.content.other.login)
    testImplementation(libs.fastutil)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
