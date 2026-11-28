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
//   (in ../cool-theme-kit) bun run build && bun run export:jetbrains
//   ./gradlew syncIcons
//
// The accent: out/jetbrains of Cool Theme Kit (bun run export:jetbrains) lists the icons the folder color changes
// (accentTargets: folders paints them with the accent) -> coolicons/folder-color.txt, and gives what VS Code draws for
// the accent -> the test fixtures src/test/resources/coolthemekit/ (CoolAccentTest, CoolFolderColorTest).
//
// Dark and light: the VS Code theme gives the dark icon by default and the light one in its "light" section. The light
// icon of a name is always <x>.svg and the dark one <x>.svg or <x>_dark.svg, the convention of the JetBrains IDEs, so a
// name refers to <x>.svg and the IDE picks <x>_dark.svg in a dark theme by itself. The task fails if that ever stops
// being true.
// ---------------------------------------------------------------------------------------------------------------------
abstract class SyncCoolIcons : DefaultTask() {
    @get:InputDirectory
    abstract val distDir: DirectoryProperty

    @get:InputDirectory
    abstract val exportDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val fixturesDir: DirectoryProperty

    @get:InputDirectory
    abstract val iconsDir: DirectoryProperty

    /** The icon styles besides glass, each copied into <stylesDir>/coolicons-<style> */
    @get:Input
    abstract val iconStyles: ListProperty<String>

    @get:Internal
    abstract val stylesDir: DirectoryProperty

    @get:OutputDirectories
    val styleDirs get() = iconStyles.get().map { stylesDir.get().dir("coolicons-$it") }

    /** The text with LF line ends, as git keeps the icons (.gitattributes); VS Code writes them with CRLF on Windows. */
    private fun lf(text: String) = text.replace("\r\n", "\n")

    private fun copySvg(source: File, target: File) {
        target.parentFile.mkdirs()
        target.writeText(lf(source.readText(Charsets.UTF_8)), Charsets.UTF_8)
    }

    @TaskAction
    fun sync() {
        val dist = distDir.get().asFile
        val themeFile = dist.resolve("a-file-icon-vscode-icon-theme.json")
        check(themeFile.isFile) { "$themeFile not found: run `bun run build` and `bun run export:jetbrains-icons` in Cool Theme Kit first" }

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
        // "<dir>/<stem>" copied under another name -> the icon of the theme it is a copy of (see resolve)
        val copiedFrom = mutableMapOf<String, String>()
        fun copy(dir: String, stem: String, from: String = stem) {
            val key = "$dir/$stem"
            if (!copied.add(key)) return
            if (from != stem) copiedFrom[key] = "$dir/$from"
            val src = dist.resolve("$dir/$from.svg")
            check(src.isFile) { "$src not found" }
            copySvg(src, out.resolve("$dir/$stem.svg"))
        }

        // Resolves the icon of one key for both themes and returns "<dir>/<stem>" (the light/base name).
        fun resolve(darkId: String, lightId: String): String {
            val (darkDir, darkStem) = pathOf(darkId)
            val (lightDir, lightStem) = pathOf(lightId)
            check(darkDir == lightDir) { "$darkId and $lightId are in different folders" }
            copy(lightDir, lightStem)
            when {
                darkStem == lightStem -> Unit
                darkStem == "${lightStem}_dark" -> copy(darkDir, darkStem)
                // an icon of another theme (iconThemeLayers) names its light variant otherwise (<x>_light.svg for <x>.svg):
                // its dark icon is copied as <light>_dark, where the IDE looks for it
                darkDir == "layers" -> copy(darkDir, "${lightStem}_dark", from = darkStem)
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

        // Every bundled icon by its VS Code name, for the associations of the user, which may give a name any of them:
        // files/<id> is the file icon <id>, folders/<x> and foldersOpen/<x> the folder icon <x> (folder-<x>, folder-<x>-open)
        for (id in definitions.keys.sorted()) {
            if (id.endsWith("_dark")) continue
            val darkId = if ("${id}_dark" in definitions) "${id}_dark" else id
            val dir = pathOf(id).first
            when {
                dir == "files" -> add("fileIcon", id, darkId, id)
                dir == "folders" && id.startsWith("folder-") -> add("folderIcon", id.removePrefix("folder-"), darkId, id)
                dir == "foldersOpen" && id.startsWith("folder-") && id.endsWith("-open") ->
                    add("folderIconExpanded", id.removePrefix("folder-").removeSuffix("-open"), darkId, id)
            }
        }

        // The names with a role, and the icon of those that have one of their own (without the marks): a file association
        // of the user can change the icon under the mark of the others (CoolAssociations).
        val iconsExport = iconsDir.get().asFile
        val roleNamesFile = iconsExport.resolve("role-names.json")
        val noMarksFile = iconsExport.resolve("no-role-marks/a-file-icon-vscode-icon-theme.json")
        check(roleNamesFile.isFile && noMarksFile.isFile) {
            "$iconsExport is incomplete: run `bun run export:jetbrains-icons` in Cool Theme Kit first"
        }

        @Suppress("UNCHECKED_CAST")
        val roleNames = JsonSlurper().parse(roleNamesFile, "UTF-8") as List<Map<String, String>>

        @Suppress("UNCHECKED_CAST")
        val noMarks = JsonSlurper().parse(noMarksFile, "UTF-8") as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val noMarksLight = noMarks["light"] as Map<String, Any?>?
        for (roleName in roleNames) {
            val name = roleName.getValue("name")
            lines += "roleName\t${norm(name)}\t${roleName.getValue("role")}"
            val own = section(noMarks, "fileNames")[name] ?: continue
            add("roleOwn", name, own, section(noMarksLight, "fileNames")[name] ?: own)
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

        // The license of each other theme mixed in (iconThemeLayers), next to its icons
        val licenses = iconsDir.get().asFile.resolve("licenses").listFiles()?.filter { it.name.endsWith(".txt") }.orEmpty()
        check(licenses.isNotEmpty() || copied.none { it.startsWith("layers/") }) {
            "the icons of other themes have no license: run `bun run export:jetbrains-icons` again"
        }
        for (license in licenses) license.copyTo(out.resolve("layers/LICENSE-${license.name}"), overwrite = true)

        // The accent: the icons that take the folder color, and what VS Code draws (the fixtures of the tests)
        val export = exportDir.get().asFile
        val folderColorFile = export.resolve("folder-color.json")
        val accentFile = export.resolve("accent.json")
        check(folderColorFile.isFile && accentFile.isFile) {
            "$export is incomplete: run `bun run export:jetbrains` in Cool Theme Kit first"
        }

        @Suppress("UNCHECKED_CAST")
        val folderColor = JsonSlurper().parse(folderColorFile, "UTF-8") as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val icons = folderColor["icons"] as Map<String, Map<String, String>>
        val used = icons.filterKeys { it in copied }.toSortedMap()
        for ((icon, svgs) in used) {
            check(out.resolve("$icon.svg").readText(Charsets.UTF_8) == lf(svgs.getValue("default"))) {
                "$icon differs from out/jetbrains: run `bun run build` and `bun run export:jetbrains` again"
            }
            check("${icon}_dark" !in copied) { "$icon takes the folder color but has a dark variant" }
        }
        out.resolve("folder-color.txt").writeText(
            "# Generated by `gradlew syncIcons` from Cool Theme Kit. Do not edit.\n" +
                "# The icons the folder color changes (<dir>/<stem>), drawn here with ${folderColor["defaultColor"]}\n" +
                used.keys.joinToString("\n", postfix = "\n"),
            Charsets.UTF_8,
        )
        val fixtures = fixturesDir.get().asFile
        fixtures.deleteRecursively()
        fixtures.mkdirs()
        accentFile.copyTo(fixtures.resolve("accent.json"), overwrite = true)
        logger.lifecycle("Accent: ${used.size} icons take the folder color, fixtures -> $fixtures")

        // The other icon styles (iconStyles in gradle.properties: flat, glossy): the svg of that style of every icon copied
        // above, into coolicons-<style>/. Each theme points every name to the same icons, only drawn otherwise (checked
        // here); its files carry the hash of the settings (<stem>~<n>.svg). Then the icons of the style the folder color
        // changes, as for the glass ones (folder-color.txt, and the fixtures of CoolFolderColorTest).
        val hash = Regex("~-?\\d+$")
        fun stemOf(definitions: Map<String, Map<String, String>>, id: String?): String? =
            id?.let { definitions[it]?.get("iconPath")?.split('/')?.takeLast(2)?.joinToString("/")?.removeSuffix(".svg")?.replace(hash, "") }

        val styleFixtures = mutableMapOf<String, Any>()
        val stylesOut = stylesDir.get().asFile
        for (style in iconStyles.get()) {
            val styleExport = iconsExport.resolve(style)
            val styleTheme = styleExport.resolve("a-file-icon-vscode-icon-theme.json")
            check(styleTheme.isFile) {
                "$styleTheme not found: run `bun run export:jetbrains-icons` in a Cool Theme Kit that has iconStyle $style"
            }

            @Suppress("UNCHECKED_CAST")
            val styleJson = JsonSlurper().parse(styleTheme, "UTF-8") as Map<String, Any?>

            @Suppress("UNCHECKED_CAST")
            val styleDefinitions = styleJson["iconDefinitions"] as Map<String, Map<String, String>>

            @Suppress("UNCHECKED_CAST")
            val styleLight = styleJson["light"] as Map<String, Any?>?
            for (kind in listOf("fileNames", "fileExtensions", "languageIds", "folderNames", "folderNamesExpanded")) {
                for ((sections, styleSections) in listOf(theme to styleJson, light to styleLight)) {
                    val styleSection = section(styleSections, kind)
                    for ((key, id) in section(sections, kind)) {
                        check(stemOf(definitions, id) == stemOf(styleDefinitions, styleSection[key])) {
                            "$kind $key is not the same icon in the $style theme: run `bun run export:jetbrains-icons` again"
                        }
                    }
                }
            }
            val styleFiles = mutableMapOf<String, File>()
            for (dir in listOf("files", "folders", "foldersOpen", "layers")) {
                styleExport.resolve(dir).listFiles()?.filter { it.name.endsWith(".svg") }?.forEach {
                    styleFiles["$dir/${it.name.removeSuffix(".svg").replace(hash, "")}"] = it
                }
            }
            val styleOut = stylesOut.resolve("coolicons-$style")
            styleOut.deleteRecursively()
            for (key in copied.sorted()) {
                val src = styleFiles[copiedFrom[key] ?: key] ?: error("no $style icon for $key: run `bun run export:jetbrains-icons` again")
                copySvg(src, styleOut.resolve("$key.svg"))
            }

            @Suppress("UNCHECKED_CAST")
            val styleColored = (folderColor[style] as Map<String, Map<String, String>>?)
                ?: error("$folderColorFile has no $style icons: run `bun run export:jetbrains` again")
            val usedStyle = styleColored.filterKeys { it in copied }.toSortedMap()
            for ((icon, svgs) in usedStyle) {
                check(styleOut.resolve("$icon.svg").readText(Charsets.UTF_8) == lf(svgs.getValue("default"))) {
                    "$style $icon differs from out/jetbrains: run `bun run export:jetbrains` and `bun run export:jetbrains-icons` again"
                }
            }
            styleOut.resolve("folder-color.txt").writeText(
                "# Generated by `gradlew syncIcons` from Cool Theme Kit. Do not edit.\n" +
                    "# The $style icons the folder color changes (<dir>/<stem>), drawn here with ${folderColor["defaultColor"]}\n" +
                    usedStyle.keys.joinToString("\n", postfix = "\n"),
                Charsets.UTF_8,
            )
            styleFixtures[style] = usedStyle
            logger.lifecycle("Icon style $style: ${copied.size} SVG files, ${usedStyle.size} take the folder color -> $styleOut")
        }
        val json = groovy.json.JsonOutput.toJson(folderColor + mapOf("icons" to used) + styleFixtures)
        fixtures.resolve("folder-color.json").writeText(groovy.json.JsonOutput.prettyPrint(json) + "\n", Charsets.UTF_8)

        // The associations: sample settings and the icons VS Code gives with them (CoolAssociationsParityTest)
        @Suppress("UNCHECKED_CAST")
        val associations = JsonSlurper().parse(iconsExport.resolve("associations.json"), "UTF-8") as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val settings = associations["settings"] as Map<String, Map<String, String>>
        val rows = mutableListOf(
            "# Generated by `gradlew syncIcons` from Cool Theme Kit (bun run export:jetbrains-icons). Do not edit.",
            "# setting <files|folders|languages> <key> <value> | file <name> <language> <icon> | folder <name> <closed> <open>",
        )
        for ((kind, key) in listOf("files" to "filesAssociations", "folders" to "foldersAssociations", "languages" to "languagesAssociations")) {
            for ((name, value) in settings.getValue(key)) rows += "setting\t$kind\t$name\t$value"
        }
        fun refOf(icon: Map<String, String>): String {
            val lightRef = icon.getValue("light")
            val darkRef = icon.getValue("dark")
            check(darkRef == lightRef || darkRef == "${lightRef}_dark") { "dark icon $darkRef is neither $lightRef nor ${lightRef}_dark" }
            return lightRef
        }

        @Suppress("UNCHECKED_CAST")
        for (file in associations["files"] as List<Map<String, String>>) {
            rows += "file\t${file.getValue("name")}\t${file["language"] ?: ""}\t${refOf(file)}"
        }

        @Suppress("UNCHECKED_CAST")
        for (folder in associations["folders"] as List<Map<String, Any>>) {
            rows += "folder\t${folder["name"]}\t${refOf(folder["closed"] as Map<String, String>)}\t${refOf(folder["open"] as Map<String, String>)}"
        }
        fixtures.resolve("associations.tsv").writeText(rows.joinToString("\n", postfix = "\n"), Charsets.UTF_8)

        // The filters: sample icons as VS Code draws them with folderColor, monochrome, opacity and saturation (CoolIconFiltersTest)
        val filtersFile = iconsExport.resolve("filters.json")
        check(filtersFile.isFile) { "$filtersFile not found: run `bun run export:jetbrains-icons` in Cool Theme Kit again" }
        filtersFile.copyTo(fixtures.resolve("filters.json"), overwrite = true)
    }
}

tasks.register<SyncCoolIcons>("syncIcons") {
    group = "cool theme kit"
    description = "Copies the Cool Icons generated by Cool Theme Kit (VS Code) into src/main/resources/coolicons."
    distDir = layout.projectDirectory.dir(providers.gradleProperty("coolThemeKitDir").map { "$it/out/jetbrains-icons/glass" })
    outputDir = layout.projectDirectory.dir("src/main/resources/coolicons")
    exportDir = layout.projectDirectory.dir(providers.gradleProperty("coolThemeKitDir").map { "$it/out/jetbrains" })
    fixturesDir = layout.projectDirectory.dir("src/test/resources/coolthemekit")
    iconsDir = layout.projectDirectory.dir(providers.gradleProperty("coolThemeKitDir").map { "$it/out/jetbrains-icons" })
    iconStyles = providers.gradleProperty("iconStyles").map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
    stylesDir = layout.projectDirectory.dir("src/main/resources")
}

// syncIcons writes into the sources: `./gradlew syncIcons test` copies the resources once they are written
tasks.processResources { mustRunAfter("syncIcons") }
tasks.processTestResources { mustRunAfter("syncIcons") }
