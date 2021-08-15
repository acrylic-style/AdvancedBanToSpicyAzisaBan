plugins {
    kotlin("jvm") version "1.5.21"
    id("com.github.johnrengelman.shadow") version "6.0.0"
}

group = "net.azisaba"
version = "0.0.2"

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://repo2.acrylicstyle.xyz") }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("net.azisaba:SpicyAzisaBan:0.0.17")
    implementation("xyz.acrylicstyle:sequelize4j:0.5.3")
    implementation("org.mariadb.jdbc:mariadb-java-client:2.7.3")
    implementation("org.hsqldb:hsqldb:2.6.0")
    implementation("xyz.acrylicstyle:minecraft-util:0.5.3")
}

tasks {
    compileKotlin { kotlinOptions.jvmTarget = "1.8" }

    withType<Jar> {
        manifest {
            attributes(
                "Main-Class" to "net.azisaba.abToSab.ABToSABApp"
            )
        }
    }

    shadowJar {
        relocate("kotlin", "net.azisaba.abToSab.libs.kotlin")
        relocate("util", "net.azisaba.abToSab.libs.util")
        relocate("xyz.acrylicstyle.sql", "net.azisaba.abToSab.libs.xyz.acrylicstyle.sql")
        relocate("net.blueberrymc.native_util", "net.azisaba.abToSab.libs.net.blueberrymc.native_util")
        relocate("org.mariadb", "net.azisaba.abToSab.libs.org.mariadb")

        minimize()
        archiveFileName.set("AdvancedBanToSpicyAzisaBan-${project.version}.jar")
    }
}
