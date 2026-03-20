plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

dependencies {
    implementation(project(":api"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    implementation("io.github.revxrsal:lamp.common:4.0.0-rc.16")
    implementation("io.github.revxrsal:lamp.bukkit:4.0.0-rc.16")
    implementation("com.h2database:h2:2.2.224")
    implementation("com.zaxxer:HikariCP:5.1.0")
}

tasks {
    withType<JavaCompile> {
        options.compilerArgs.add("-XDstringConcat=inline")
    }

    shadowJar {
        archiveBaseName.set("HubCore")
        archiveVersion.set("1.0.0")
        archiveClassifier.set("")
        relocate("org.h2", "com.indifferenzah.hubcore.libs.h2")
        relocate("com.zaxxer.hikari", "com.indifferenzah.hubcore.libs.hikari")
        relocate("revxrsal.commands", "com.indifferenzah.hubcore.libs.lamp")
    }

    build {
        dependsOn(shadowJar)
    }
}