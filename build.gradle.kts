import groovy.json.JsonSlurper
import java.text.Normalizer

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "com.woogajjangka"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localIde = providers.gradleProperty("localIdePath").orNull
        if (localIde != null && file(localIde).isDirectory) {
            local(localIde)
        } else {
            pycharm(providers.gradleProperty("platformVersion"))
        }
    }
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

// ./gradlew runIde -PrunIdeProject=<folder> opens that folder in the sandbox IDE.
tasks.runIde {
    providers.gradleProperty("runIdeProject").orNull?.let { args(it) }
}

intellijPlatform {
    instrumentCode = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("sinceBuild")
            untilBuild = provider { null }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// syncIcons: copies the Cool Icons of Cool Theme Kit (VS Code) into the plugin resources.
//
// The input is the file icon theme Cool Theme Kit generates (dist/a-file-icon-vscode-icon-theme.json and the SVGs next
// to it), so which icon a name gets is exactly what Cool Theme Kit gives it in VS Code (the PyCharm + Atom Material Icons
// choice, with the role marks, the common names and the exceptions of the fork).
//
// The output is committed (src/main/resources/coolicons). Run it again when Cool Theme Kit changes:
//   (in ../cool-theme-kit) bun run build
//   ./gradlew syncIcons
//
// Dark and light: the VS Code theme gives the dark icon by default and the light one in its "light" section. The light
// icon of a name is always <x>.svg and the dark one <x>.svg or <x>_dark.svg, the convention of the JetBrains IDEs, so a
// name refers to <x>.svg and the IDE picks <x>_dark.svg in a dark theme by itself. The task fails if that ever stops
// being true.
// ---------------------------------------------------------------------------------------------------------------------
abstract class SyncCoolIcons : DefaultTask() {
    @get:InputDirectory
    abstract val distDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun sync() {
        val dist = distDir.get().asFile
        val themeFile = dist.resolve("a-file-icon-vscode-icon-theme.json")
        check(themeFile.isFile) { "$themeFile not found: run `bun run build` in Cool Theme Kit first" }

        @Suppress("UNCHECKED_CAST")
        val theme = JsonSlurper().parse(themeFile, "UTF-8") as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        fun section(map: Map<String, Any?>?, key: String) = (map?.get(key) as Map<String, String>?) ?: emptyMap()

        @Suppress("UNCHECKED_CAST")
        val definitions = theme["iconDefinitions"] as Map<String, Map<String, String>>

        @Suppress("UNCHECKED_CAST")
        val light = theme["light"] as Map<String, Any?>?

        // icon id -> dist/<dir>/<stem>.svg
        fun pathOf(id: String): Pair<String, String> {
            val iconPath = definitions[id]?.get("iconPath") ?: error("no iconDefinition for $id")
            val parts = iconPath.split('/')
            return parts[parts.size - 2] to parts.last().removeSuffix(".svg")
        }

        val out = outputDir.get().asFile
        out.deleteRecursively()
        val copied = mutableSetOf<String>()
        fun copy(dir: String, stem: String) {
            val key = "$dir/$stem"
            if (!copied.add(key)) return
            val src = dist.resolve("$dir/$stem.svg")
            check(src.isFile) { "$src not found" }
            src.copyTo(out.resolve("$dir/$stem.svg"), overwrite = true)
        }

        // Resolves the icon of one key for both themes and returns "<dir>/<stem>" (the light/base name).
        fun resolve(darkId: String, lightId: String): String {
            val (darkDir, darkStem) = pathOf(darkId)
            val (lightDir, lightStem) = pathOf(lightId)
            check(darkDir == lightDir) { "$darkId and $lightId are in different folders" }
            copy(lightDir, lightStem)
            when (darkStem) {
                lightStem -> Unit
                "${lightStem}_dark" -> copy(darkDir, darkStem)
                else -> error("dark icon $darkStem is neither $lightStem nor ${lightStem}_dark")
            }
            return "$lightDir/$lightStem"
        }

        fun norm(name: String) = Normalizer.normalize(name, Normalizer.Form.NFC).lowercase()

        val lines = sortedSetOf<String>()
        val withoutDark = mutableSetOf<String>() // "<dir>/<stem>" used with the same icon in both themes
        val withDark = mutableSetOf<String>()

        fun add(kind: String, key: String, darkId: String, lightId: String) {
            val darkStem = pathOf(darkId).second
            val ref = resolve(darkId, lightId)
            if (darkStem == ref.substringAfter('/')) withoutDark += ref else withDark += ref
            lines += "$kind\t${norm(key)}\t$ref"
        }

        // VS Code shows the "expanded" icon of a folder while it is open in the tree (CoolFolderIcon does the same).
        for (kind in listOf("fileNames", "fileExtensions", "languageIds", "folderNames", "folderNamesExpanded")) {
            val lightSection = section(light, kind)
            for ((key, id) in section(theme, kind)) add(kind, key, id, lightSection[key] ?: id)
        }
        for (kind in listOf("file", "folder", "folderExpanded", "rootFolder", "rootFolderExpanded")) {
            val id = theme[kind] as String
            val lightId = light?.get(kind) as String? ?: id
            add("default", kind, id, lightId)
        }

        // An icon used without a dark variant must not get one by accident (the IDE would pick it in a dark theme).
        val clash = withoutDark.intersect(withDark)
        check(clash.isEmpty()) { "icons used both with and without a dark variant: $clash" }

        out.resolve("mapping.tsv").writeText(
            "# Generated by `gradlew syncIcons` from Cool Theme Kit. Do not edit.\n" +
                "# kind\tname (NFC, lower case)\ticon (<dir>/<stem>, the IDE adds _dark in dark themes)\n" +
                lines.joinToString("\n", postfix = "\n"),
            Charsets.UTF_8,
        )
        logger.lifecycle("Cool Icons: ${lines.size} names, ${copied.size} SVG files -> $out")
    }
}

tasks.register<SyncCoolIcons>("syncIcons") {
    group = "cool theme kit"
    description = "Copies the Cool Icons generated by Cool Theme Kit (VS Code) into src/main/resources/coolicons."
    distDir = layout.projectDirectory.dir(providers.gradleProperty("coolThemeKitDir").map { "$it/dist" })
    outputDir = layout.projectDirectory.dir("src/main/resources/coolicons")
}
