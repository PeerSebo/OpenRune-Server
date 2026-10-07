plugins {
    id("base-conventions")
}

dependencies {
    testImplementation(libs.fastutil)
    implementation(projects.api.pluginCommons)

    implementation(projects.content.skills.utils)

    implementation(projects.api.attr)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
