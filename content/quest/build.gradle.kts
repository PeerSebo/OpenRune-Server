plugins {
    id("base-conventions")
}

dependencies {
    testImplementation(projects.api.invPlugin)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.mechanics.dig)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.attr)
    implementation(projects.api.serverConfig)
    implementation(projects.api.death)
    implementation(projects.api.instances)
    implementation(projects.api.music)
    implementation(projects.content.other.pets)
    implementation(projects.content.skills.sailing)
    implementation(projects.api.shops)
    implementation(projects.content.minigames.sorceressGarden)
    implementation(projects.engine.utilsBits)
    testImplementation(libs.fastutil)
    testImplementation(libs.rsprot.api)
    testImplementation(projects.api.registry)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.gameProcess)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
