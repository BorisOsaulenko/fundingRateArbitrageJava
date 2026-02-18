plugins {
    id("java")
    kotlin("jvm")
    id("application")
    id("io.freefair.lombok") version "9.2.0"
}

application {
    mainClass.set("com.boris.fundingarbitrage.App")
}

// Apply to all Jar tasks (covers jar plus any additional Jar-producing tasks).
tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

group = "org.main"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.apache.commons:commons-numbers-core:1.1")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.3")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:2.2.1")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.fasterxml.jackson.core:jackson-core:2.21.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    implementation("io.freefair.lombok:io.freefair.lombok.gradle.plugin:9.2.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.83")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(25)
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/main/java"))
        }
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("manual")
    }
}

tasks.register<Test>("testWebsocket") {
    useJUnitPlatform {
        includeTags("websocket")
        excludeTags("manual")
    }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
}

tasks.register<Test>("testRest") {
    useJUnitPlatform {
        includeTags("rest")
        excludeTags("manual")
    }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
}