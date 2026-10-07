plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.guice)
    implementation(projects.api.bosses)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.config)
    implementation(projects.api.death)
    implementation(projects.api.instances)
    implementation(projects.api.music)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.repo)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.route)
    implementation(projects.api.specials)
    implementation(projects.api.weapons)
    implementation(projects.engine.game)
    implementation(projects.engine.plugin)
    implementation(projects.engine.utilsBits)
    implementation(projects.content.interfaces.collectionLog)
    testImplementation(libs.fastutil)
    testImplementation(projects.api.registry)
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.combat.combatWeapon)
    testImplementation(libs.or2.all.cache)
    testImplementation(libs.rsprot.api)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.coroutines.core)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.dir(rootProject.file(".data/gamevals"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
