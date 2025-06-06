plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

buildscript {
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.5.0")
    }
}

dependencies {
    // 依赖core模块
    api(project(":core"))
//    反射库
//    compileOnly(kotlin("reflect"))

//    协程库
//    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")

    // 本地依赖放在libs文件夹内
    compileOnly(fileTree("libs") { include("*.jar") })
    compileOnly("fr.xephi:authme:5.6.0-SNAPSHOT") { isTransitive = false }
    implementation("org.bstats:bstats-bukkit:3.0.2")
    
    // PlaceholderAPI
    compileOnly("me.clip:placeholderapi:2.11.5")
}

// 插件名称，请在gradle.properties 修改
val pluginName: String by rootProject
//包名，请在gradle.properties 修改
//val group: String by rootProject
val groupS = project.group as String
// 作者，请在gradle.properties 修改
val author: String by rootProject
// jar包输出路径，请在gradle.properties 修改
val jarOutputFile: String by rootProject
val obfuscated: String by rootProject
val obfuscatedDictionary: String by rootProject
val obfuscationDictionaryFile: File? = if (obfuscatedDictionary.isEmpty()) null
else
    File(obfuscatedDictionary).absoluteFile
val obfuscatedMainClass =
    if (obfuscationDictionaryFile?.exists() == true) {
        obfuscationDictionaryFile.readLines().firstOrNull() ?: "a"
    } else "a"
val isObfuscated = obfuscated == "true"
val shrink: String by rootProject
val formatJarOutput = jarOutputFile.replace("\${root}", rootProject.projectDir.absolutePath)
val output: File =
    if (isObfuscated)
        File(formatJarOutput, "${rootProject.name}-${rootProject.version}-obfuscated.jar").absoluteFile
    else
        File(formatJarOutput, "${rootProject.name}-${rootProject.version}.jar").absoluteFile

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
    }
    kotlin {
        jvmToolchain(8)
    }

    shadowJar {
        if (isObfuscated) {
            relocate("top.iseason.bukkittemplate.BukkitTemplate", obfuscatedMainClass)
        }
        relocate("top.iseason.bukkittemplate", "$groupS.libs.core")
        relocate("org.bstats", "$groupS.libs.bstats")
    }
    build {
        dependsOn("buildPlugin")
    }
    processResources {
        filesMatching("plugin.yml") {
            // 删除注释,你可以返回null以删除整行，但是IDEA有bug会报错，故而返回了""
            filter {
                if (it.trim().startsWith("#")) null else it
            }
            expand(
                "main" to if (isObfuscated) obfuscatedMainClass else "$groupS.libs.core.BukkitTemplate",
                "name" to pluginName,
                "version" to project.version,
                "author" to author,
                "kotlinVersion" to getProperties("kotlinVersion"),
                "exposedVersion" to getProperties("exposedVersion"),
            )
        }
    }
}
tasks.register<proguard.gradle.ProGuardTask>("buildPlugin") {
    group = "minecraft"
    verbose()
    injars(tasks.named("shadowJar"))
    if (!isObfuscated) {
        dontobfuscate()
    } else if (obfuscationDictionaryFile?.exists() == true) {
        //混淆词典
        classobfuscationdictionary(obfuscationDictionaryFile)
        obfuscationdictionary(obfuscationDictionaryFile)
    }
    if (shrink != "true") {
        dontshrink()
    }
    allowaccessmodification() //优化时允许访问并修改有修饰符的类和类的成员
    dontusemixedcaseclassnames() // 混淆时不要大小写混合
    optimizationpasses(5)
    dontwarn()
    //添加运行环境
    val javaHome = System.getProperty("java.home")
    if (JavaVersion.current() < JavaVersion.toVersion(9)) {
        libraryjars("$javaHome/lib/rt.jar")
    } else {
        libraryjars(
            mapOf(
                "jarfilter" to "!**.jar",
                "filter" to "!module-info.class"
            ),
            "$javaHome/jmods/java.base.jmod"
        )
    }
    libraryjars(configurations.compileClasspath.get().files)
    //启用混淆的选项
    val allowObf = mapOf("allowobfuscation" to true)
    //class规则
    if (isObfuscated) keep(allowObf, "class $obfuscatedMainClass {}")
    else keep("class $groupS.libs.core.BukkitTemplate {}")
    keepkotlinmetadata()
    keep(allowObf, "class * implements $groupS.libs.core.BukkitPlugin {*;}")
    keepclassmembers("class * extends $groupS.libs.core.config.SimpleYAMLConfig {*;}")
    keepclassmembers("class * implements $groupS.libs.core.ui.container.BaseUI {*;}")
    keepclassmembers(allowObf, "class * implements org.bukkit.event.Listener {*;}")
    keepclassmembers(allowObf, "class * extends org.bukkit.event.Event {*;}")
    keepclassmembers(allowObf, "class * extends org.jetbrains.exposed.dao.id.IdTable {*;}")
    keepclassmembers(allowObf, "class * extends org.jetbrains.exposed.dao.Entity {*;}")
    keepattributes("Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*")
    keepclassmembers("enum * {public static **[] values();public static ** valueOf(java.lang.String);}")
    repackageclasses()
    outjars(output)
}

fun getProperties(properties: String) = rootProject.properties[properties].toString()
