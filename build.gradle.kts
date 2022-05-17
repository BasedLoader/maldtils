plugins {
    java
}

group = "com.basedloader"
version = "0.0.1"

repositories {
    mavenCentral()
    maven("https://maven.basedloader.com/snapshots")
}

dependencies {
    // Forge Gen
    implementation("net.minecraftforge:installertools:1.2.11")
    implementation("de.oceanlabs.mcp:mcinjector:3.8.0")
    implementation("net.minecraftforge:binarypatcher:1.1.1")
    implementation("net.minecraftforge:ForgeAutoRenamingTool:0.1.17:all") // FIXME: we can prob pull this from mcp config but whatever. Make sure its the same as the version forge wants

    // Misc Dependencies
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.3")
    implementation("net.fabricmc:mapping-io:0.3.0")
    implementation("net.fabricmc:tiny-remapper:0.8.4")

    // Tests
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}