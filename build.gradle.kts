plugins {
    java
    id("com.gradleup.shadow") version "9.4.1"
    id("com.modrinth.minotaur") version "2.9.0"
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
}

group = property("group") as String
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")

    implementation("com.zaxxer:HikariCP:${property("hikariVersion")}")
    implementation("org.xerial:sqlite-jdbc:${property("sqliteVersion")}")
    implementation("com.mysql:mysql-connector-j:${property("mysqlVersion")}")
    implementation("org.postgresql:postgresql:${property("postgresqlVersion")}")
    implementation("org.bstats:bstats-bukkit:${property("bstatsVersion")}")

    testImplementation("org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:${property("mockbukkitVersion")}")
}

tasks {
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")

        relocate("com.zaxxer.hikari", "io.github.driftn2forty.chatsentinel.lib.hikari")
        relocate("org.xerial", "io.github.driftn2forty.chatsentinel.lib.xerial")
        relocate("org.sqlite", "io.github.driftn2forty.chatsentinel.lib.sqlite")
        relocate("com.mysql", "io.github.driftn2forty.chatsentinel.lib.mysql")
        relocate("org.postgresql", "io.github.driftn2forty.chatsentinel.lib.postgresql")
        relocate("org.bstats", "io.github.driftn2forty.chatsentinel.lib.bstats")
    }

    build {
        dependsOn(shadowJar)
    }

    jar {
        archiveClassifier.set("unshaded")
    }

    test {
        useJUnitPlatform()
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }
}
