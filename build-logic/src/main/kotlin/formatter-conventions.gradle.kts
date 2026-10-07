plugins {
    id("com.diffplug.spotless")
}

spotless {
    if (!providers.gradleProperty("spotlessNoRatchet").orNull.toBoolean()) {
        ratchetFrom("origin/main")
    }

    kotlin {
        ktlint("1.5.0")
    }
}
