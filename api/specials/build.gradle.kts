plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    testImplementation(libs.fastutil)
    testImplementation(libs.rsprot.api)
    implementation(libs.guice)
    implementation(projects.api.attr)
    implementation(projects.api.config)
    implementation(projects.api.generated)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.combat.combatWeapon)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.script)

    implementation(projects.api.utils.utilsVars)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
