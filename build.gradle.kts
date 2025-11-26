plugins {
    id("fabric-loom") version "1.9-SNAPSHOT"
}

/* ---- Basic Metadata ---- */
base {
    archivesName = properties["archives_base_name"] as String
    version = properties["mod_version"] as String
    group = properties["maven_group"] as String
}

/* ---- Repositories ---- */
repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    mavenCentral()
}

/* ---- Dependencies ---- */
dependencies {
    minecraft("com.mojang:minecraft:${properties["minecraft_version"] as String}")
    mappings("net.fabricmc:yarn:${properties["yarn_mappings"] as String}:v2")
    modImplementation("net.fabricmc:fabric-loader:${properties["loader_version"] as String}")

    // Meteor client
    modImplementation("meteordevelopment:meteor-client:${properties["minecraft_version"] as String}-SNAPSHOT")
}

/* ---- Tasks ---- */
tasks {

    /* Expand version info into fabric.mod.json */
    processResources {
        val props = mapOf(
            "version" to project.version,
            "mc_version" to project.property("minecraft_version")
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") {
            expand(props)
        }
    }

    /* Package LICENSE (normal open-source behavior) */
    jar {
        val suffix = project.base.archivesName.get()
        from("LICENSE") {
            rename { "${it}_${suffix}" }
        }
    }

    /* Java 21 config */
    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    /* Compiler options */
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
    }
}
