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
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.0-M1")
    testImplementation("org.junit.platform:junit-platform-launcher:6.1.0-M1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.1.0-M1")
    testImplementation("org.mockito:mockito-junit-jupiter:5.18.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.hamcrest:hamcrest:3.0")
    implementation("org.apache.commons:commons-numbers-core:1.1")
    implementation("org.apache.commons:commons-lang3:3.20.0")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.3")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:2.2.1")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.fasterxml.jackson.core:jackson-core:2.21.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.83")
    implementation("org.jsoup:jsoup:1.22.1")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("logback.configurationFile", file("src/test/resources/logback-test.xml").absolutePath)
}
kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("manual")
    }
}

tasks.withType<Test>().configureEach {
    doFirst {
        val mockitoCoreJar = classpath.files.firstOrNull { it.name.startsWith("mockito-core-") }
            ?: error("mockito-core jar not found on test classpath")
        jvmArgs("-javaagent:${mockitoCoreJar.absolutePath}")
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
