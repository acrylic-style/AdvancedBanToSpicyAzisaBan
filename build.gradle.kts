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
        archiveFileName.set("AdvancedBanToSpicyAzisaBan-${project.version}.jar")
    }
}
