plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.invStorage)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.attr)
    implementation(projects.content.skills.utils)
    implementation(projects.content.skills.crafting)
    testImplementation(libs.fastutil)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
