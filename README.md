# Cool Theme Kit for JetBrains IDEs

The **Cool Icons** of [Cool Theme Kit](https://github.com/WoogaJJangka/cool-theme-kit) (VS Code), in PyCharm and the
other JetBrains IDEs.

- Every file and folder gets the icon PyCharm gives it with the Atom Material Icons plugin, drawn in the glass style of
  Cool Theme Kit: the glyph on the same rounded tile for files, the same rounded folder for folders.
- The glyph is the JetBrains New UI icon where the IDE has one, the Atom Material Icons one elsewhere.
- Files with a role show a small mark: run (`main.py`), package (`__init__.py`), test (`conftest.py`), settings
  (`settings.py`). Common names without an icon of their own (`datasets`, `results`, `.claude`, `.env.local`) get one.
  The marks can be turned off in **Settings | Appearance & Behavior | Cool Theme Kit** (`roleMarks` in VS Code).
- An expanded folder shows the open folder icon, as in the VS Code explorer.
- The same settings as in VS Code, in **Settings | Appearance & Behavior | Cool Theme Kit**: the icon style (`iconStyle`:
  glass, or flat as the JetBrains icon plugin draws them), and your own icons for names (`filesAssociations`,
  `foldersAssociations`, `languagesAssociations`: `*.foo` → `rust`, `README.md` → `docs`, a folder `src` → `tests`, or
  the path of an SVG file). The entries of your VS Code settings can be copied as they are.
- Source, test and resource roots and excluded folders keep the icons of the IDE, which tell what the folder is in the
  project.

The icons and which name gets which icon are not made here: they are copied from the file icon theme Cool Theme Kit
generates for VS Code, so both editors show the same icon for the same name.

> Atom Material Icons draws file icons too. Turn its file and folder icons off in its settings (or disable the plugin),
> or the two plugins fight over them.

## Build

Requires the conda env `cool-theme-kit-jetbrains` (JDK 25, Gradle 9.3):

```powershell
conda env create -f environment.yml
```

In a shell without the conda profile, put the env first in `PATH`:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\miniconda3\envs\cool-theme-kit-jetbrains\Library"
```

| Goal | Command |
|---|---|
| Build the plugin zip (`build/distributions/`) | `./gradlew buildPlugin` |
| Run a sandbox IDE with the plugin | `./gradlew runIde` |
| Copy the icons again from Cool Theme Kit | (in `../cool-theme-kit`: `bun run build`, `bun run export:jetbrains`, `bun run export:jetbrains-icons`), then `./gradlew syncIcons` |
| Check compatibility with the target IDE | `./gradlew verifyPlugin` |

The target IDE is the installed PyCharm in `localIdePath` (`gradle.properties`); when that folder does not exist,
PyCharm `platformVersion` is downloaded.

Install the zip with **Settings | Plugins | ⚙ | Install Plugin from Disk...**.

## How it works

- `gradlew syncIcons` reads `dist/a-file-icon-vscode-icon-theme.json` of Cool Theme Kit and writes
  `src/main/resources/coolicons/` (SVGs and `mapping.tsv`, committed). The light icon of a name is `<x>.svg` and the
  dark one `<x>_dark.svg`, the convention of the JetBrains IDEs, so the IDE switches them with the theme.
- `CoolIcons` matches a name the way VS Code matches a file icon theme: the whole file name, then its extensions from
  the longest, then the language, then the default file icon. Names are compared in NFC and lower case (Korean names in
  NFD match).
  The language is the one of the file type, its id translated to the VS Code one where they differ (`Shell Script` →
  `shellscript`, `JSX Harmony` → `javascriptreact`), then the language it is a dialect of.
- `CoolPsiIconProvider` (Project view, Structure), `CoolFileIconProvider` (editor tabs, Recent Files, Search
  Everywhere) and `CoolProjectViewDecorator` (Project view nodes PyCharm draws itself, like `.py`) all use it.
- The IDE asks a tree node for one icon, never whether it is expanded. `CoolFolderIcon` holds the closed and the open
  folder and picks one when painted: a tree paints a row by placing the renderer at the row's bounds, so the row being
  painted is the one at the renderer's position. Outside a tree it is the closed folder.
- The flat icons (`src/main/resources/coolicons-flat/`) are the same names drawn with `iconStyle: "flat"`: Cool Theme Kit
  writes that theme with `bun run export:jetbrains-icons`, and `syncIcons` checks that it points every name to the same
  icons.
- `CoolAssociations` applies the associations as Cool Theme Kit does when it generates the theme: they win over the
  theme, a file name of the theme still wins over an extension of yours, and a name with a role (`main.py`) under an
  extension you give a bundled icon gets that icon with the mark of its role. `CoolAssociationsParityTest` checks sample
  associations against the theme VS Code generates with them. Unlike VS Code, a value that names no icon and no
  existing SVG file is left out (VS Code then shows no icon at all).

## License

MIT, as Cool Theme Kit. The icons come from Atom Material Icons (MIT) and the JetBrains New UI icons
(Apache 2.0): see the
[third-party notices of Cool Theme Kit](https://github.com/WoogaJJangka/cool-theme-kit/blob/main/THIRD-PARTY-NOTICES.md).
