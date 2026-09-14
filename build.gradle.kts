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
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


tasks.test {
    useJUnitPlatform()
    // Tests use fake transports only; no installation endpoints or pairing files.
    systemProperty("user.timezone", "Asia/Shanghai")
}

dependencyLocking { lockAllConfigurations() }
