plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.instances)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.repo)
    implementation(projects.api.script)
    implementation(projects.content.other.pets)
    implementation(projects.content.quest)
    implementation(projects.content.skills.runecrafting)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    testImplementation(libs.fastutil)
    testImplementation(libs.rsprot.api)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.registry)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/LIVE"))
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.dir(rootProject.file(".data/gamevals"))
    inputs.dir(rootProject.file(".data/gamevals-binary"))
}
