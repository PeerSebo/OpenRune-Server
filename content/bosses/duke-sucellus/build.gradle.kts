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
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.repo)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.route)
    implementation(projects.engine.game)
    implementation(projects.engine.plugin)
    testImplementation(libs.fastutil)
    testImplementation(projects.api.registry)
    testImplementation(libs.or2.all.cache)
    testImplementation(libs.rsprot.api)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
    inputs.file(rootProject.file(".data/gamevals/synth.rscm"))
    for (module in listOf("duke-sucellus", "muspah", "vardorvis", "leviathan")) {
        inputs.files(rootProject.fileTree("content/bosses/$module/src/main/kotlin") { include("**/*.kt") })
    }
}
