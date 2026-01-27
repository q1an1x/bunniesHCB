plugins {
    id("java")
    id("application")
}

group = "es.buni.hcb"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

application {
    mainClass.set("es.buni.hcb.BunniesHCB")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.calimero:calimero-core:3.0-M1")
    implementation("io.github.hap-java:hap:2.0.7")
}
