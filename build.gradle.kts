plugins {
    id("java")
    kotlin("jvm")
    id("application")
    id("io.freefair.lombok") version "9.2.0"
}

application {
    mainClass.set("com.boris.fundingarbitrage.App")
}

tasks.named<JavaExec>("run") {
    systemProperty("logback.configurationFile", file("src/main/resources/logback.xml").absolutePath)
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

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.hamcrest)
    testImplementation(libs.mockito.core)
    mockitoAgent(libs.mockito.core) { isTransitive = false }
    implementation(libs.commons.numbers.core)
    implementation(libs.commons.lang3)
    implementation(libs.httpclient5)
    implementation(libs.tyrus.standalone.client)
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.bcpkix.jdk18on)
    implementation(libs.jsoup)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("logback.configurationFile", file("src/test/resources/logback-test.xml").absolutePath)
    jvmArgs.add("-javaagent:${mockitoAgent.asPath}")
    useJUnitPlatform {
        excludeTags("manual")
    }
}

kotlin {
    jvmToolchain(25)
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
