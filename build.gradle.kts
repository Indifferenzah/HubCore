plugins {
    java
}

allprojects {
    group = "com.indifferenzah.hubcore"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io")
    }
}

subprojects {
    apply(plugin = "java")
    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        // Disabilita invokedynamic per la string concatenation (Java 9+):
        // Shadow plugin non supporta l'Handle ASM generato da StringConcatFactory
        options.compilerArgs.add("-XDstringConcat=inline")
    }
}
