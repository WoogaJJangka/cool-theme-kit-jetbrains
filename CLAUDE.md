# cool-theme-kit-jetbrains — Claude Code 작업 메모

Cool Theme Kit(VS Code, `D:\dev\projects\cool-theme-kit`)의 **Cool Icons**(유리 스타일 파일·폴더 아이콘, 역할 표시, 흔한 이름)를
PyCharm·JetBrains IDE 에서 쓰게 하는 플러그인. 2026-11-26 만듦.

## 방향
- **목표는 Cool Theme Kit(VS Code)과 같은 화면(2026-11-26 사용자 결정)**: 두 저장소는 방향이 같고 서로 참고해서, VS Code 와
  PyCharm 을 번갈아 써도 같은 화면처럼 보이게 한다. 부분마다 원본은 한 곳(Cool Icons = Cool Theme Kit, 색·UI 아이콘·글꼴 =
  JetBrains 자체)이고 다른 쪽이 따라간다. 여기서 무엇을 바꾸든 VS Code 쪽 모양과 비교하고, 새 기능은 Cool Theme Kit 에 이미 있는
  것을 같은 모양으로 옮기는 일부터 한다. 부분별 표·일부러 다른 것·아직 다른 것(강조색, 아이콘 설정, 모르는 이름, 한글 대체 글꼴,
  언어 id)·나란히 비교하는 방법은 `../cool-theme-kit/docs/SAME_SCREEN.md`(두 저장소의 공통 문서, 바꾸면 거기서 고친다).
- 아이콘과 "어떤 이름에 어떤 아이콘"은 **여기서 만들지 않는다**. Cool Theme Kit 이 VS Code 용으로 만든
  `dist/a-file-icon-vscode-icon-theme.json` + SVG 를 `gradlew syncIcons` 로 복사한다(`src/main/resources/coolicons/`, 커밋함).
  아이콘을 바꾸려면 Cool Theme Kit 에서 고치고 `bun run build` → 여기서 `syncIcons`.
- 이름 맞추기는 VS Code 규칙 그대로(`CoolIcons`): 파일 이름 → 긴 확장자부터 → 언어 id → 기본 파일 아이콘. NFC + 소문자.
- 소스·테스트·리소스 루트와 제외 폴더는 IDE 아이콘을 남긴다(폴더의 역할을 보여 주는 유일한 표시). 콘텐츠 루트 = root folder 아이콘.
- 다크/라이트: VS Code 테마의 light 아이콘 = `<x>.svg`, dark = `<x>.svg` 또는 `<x>_dark.svg`. JetBrains `IconLoader` 가 다크 테마에서
  `_dark` 를 알아서 고른다. `syncIcons` 가 이 규칙이 깨지면 실패한다.

## 환경
- conda env `cool-theme-kit-jetbrains` = OpenJDK 25 + Gradle 9.3(wrapper 생성용). 빌드는 `gradlew`(Gradle 9.3.1).
- 프로필 없는 셸(Claude Code, Git Bash): `E=/c/Users/siwon/miniconda3/envs/cool-theme-kit-jetbrains; export JAVA_HOME="$(cygpath -w $E/Library)"`
  (`gradle` 은 `gradle.bat` 만 있다).
- 대상 IDE: `gradle.properties` `localIdePath`(이 PC 의 PyCharm 2026.3, 받지 않음). 폴더가 없으면 `pycharm(platformVersion)` 을 받는다.
  PyCharm 2026.3 은 Java 25 필요, 바이트코드는 `release = 21`. 플러그인 `org.jetbrains.intellij.platform` 2.20.1.

## 명령
| 목적 | 명령 |
|---|---|
| 플러그인 zip (`build/distributions/`) | `./gradlew buildPlugin` |
| 단위 테스트 | `./gradlew test` |
| 샌드박스 IDE (폴더 열기) | `./gradlew runIde -PrunIdeProject=<폴더>` (샌드박스는 `java.exe` 창, 끄면 gradle 도 끝남) |
| 아이콘 다시 복사 | `./gradlew syncIcons` (먼저 `../cool-theme-kit` 에서 `bun run build` → `bun run export:jetbrains` → `bun run export:jetbrains-icons`) |

## 구조
- `CoolIcons`: `mapping.tsv` 읽기 + 이름 맞추기(플랫폼 서비스 없이 도는 순수 조회, 단위 테스트 대상).
  `CoolIconResolver`: 파일/폴더 → 아이콘(루트 규칙, 언어 체인, 역할 표시 설정). `CoolFolderIcon`: 닫힌/열린 폴더.
- 세 곳에서 아이콘을 준다: `CoolPsiIconProvider`(PSI, `order="first"`), `CoolFileIconProvider`(에디터 탭·최근 파일),
  `CoolProjectViewDecorator`(Project 뷰 노드). PyCharm 의 `.py` 는 아이콘 제공자를 묻지 않고 자기 아이콘을 그려서
  Project 뷰는 데코레이터가 있어야 바뀐다(2026-11-26 샌드박스에서 확인).
- Atom Material Icons 와 같이 켜면 서로 덮어쓴다. README 에 끄라고 적어 둠.

## VS Code 와 맞춘 것 (2026-11-26)
목표는 VS Code 의 Cool Theme Kit 와 같은 화면. 일부러 다른 곳은 소스·테스트·리소스 루트·제외 폴더의 IDE 아이콘뿐.
- **펼친 폴더 = 열린 폴더 아이콘**(VS Code `folderNamesExpanded`·`folderExpanded`·`rootFolderExpanded`, `mapping.tsv` 에 함께 복사).
  IDE 트리는 노드에 아이콘 하나만 묻고 펼침 여부를 안 알려 준다(`NodeRenderer` 가 `PresentationData.getIcon(false)` 고정).
  그래서 `CoolFolderIcon` 이 그릴 때 정한다: 트리는 자기 `CellRendererPane` 에 렌더러를 행 위치로 놓고 그리므로 렌더러 y 의 행이
  펼쳐졌는지 본다(`DefaultTreeUI` 바이트코드로 확인). 트리 밖(탭·팝업)은 닫힌 폴더. `ReplaceableIcon` 이라 선택 행의 어두운
  아이콘·비활성 아이콘으로 바뀌어도 둘 다 유지. `CoolFolderIconTest` 가 Swing `JTree` 를 그려서 지킨다.
- **언어 id**: JetBrains id 를 VS Code id 로 옮긴다(`CoolIcons.VSCODE_LANGUAGE_IDS`, 소문자가 같으면 필요 없음), 없으면 기반
  언어(PostgreSQL → SQL)로. `textmate` 는 파일의 언어가 아니라 빼 둠.
- **역할 표시 켜고 끄기**: Settings | Appearance & Behavior | Cool Theme Kit(`CoolIconsSettings`, `coolThemeKit.xml`) = VS Code
  `roleMarks`(기본 켬). 끄면 `files/role-<역할>-<x>` → `files/<x>`(표시 아래 아이콘). VS Code 에서 끈 결과(확장자 아이콘)와 같은지
  `CoolIconsParityTest` 가 모든 역할 이름으로 확인. 화면 문구는 `messages/CoolThemeKitBundle(_ko).properties`(한국어 필수).
- **아이콘 스타일(2026-11-27)**: VS Code `iconStyle`(glass 기본, flat). `bun run export:jetbrains-icons`(Cool Theme Kit
  `src/scripts/jetbrains/exportIconVariants.ts`)가 `out/jetbrains-icons/flat/` 에 flat 테마를 만들고, `syncIcons` 가 플러그인이
  가진 모든 svg 의 flat 판을 `coolicons-flat/` 로 복사한다(파일 이름의 설정 해시 `~<n>` 는 뗌). 이름 → 아이콘은 두 스타일이 같아서
  (`syncIcons` 가 확인) `mapping.tsv` 는 하나. 강조색으로 다시 칠한 폴더(`CoolRecoloredIcons`)는 유리 아이콘에서만 칠한다.
- **아이콘 연결(2026-11-27)**: VS Code `filesAssociations`·`foldersAssociations`·`languagesAssociations` = `CoolAssociations`
  (설정 화면의 표 세 개). VS Code 생성기 규칙 그대로: `*.x`/`**.x` = 확장자, 그 밖 = 파일 이름, 폴더는 `.이름`·`_이름` 도, 값은
  번들 아이콘 이름(대소문자 무시, `mapping.tsv` 의 `fileIcon`·`folderIcon(Expanded)` = dist 의 모든 아이콘) 또는 SVG 경로(절대·`~`,
  폴더는 `-open.svg`/`_open.svg`). 역할 이름은 사용자 확장자 아이콘 위에 표시를 다시 얹는다(`role-<역할>-<x>`), 단 표시 없이도
  제 아이콘이 있는 이름(`roleOwn`, `__init__.py` 등)은 그 아이콘 그대로. `CoolAssociationsParityTest` 가 VS Code 생성기로 만든
  `associations.tsv` 와 비교한다. 일부러 다른 것: 없는 아이콘 이름·없는 SVG 는 무시(VS Code 는 빈 아이콘).
- 남은 차이 후보: 테마 섞기(`iconThemeLayers`, 기본 Material Icon Theme 이 Cool Icons 가 모르는 이름을 채움), 폴더 색·채도·
  불투명도·단색, 번들에 없는 역할 표시 아이콘(연결로 생긴 `role-<역할>-<x>` 가 dist 에 없으면 표시 없이).
- 샌드박스 창은 `java.exe`(PyCharm `jbr`)라 computer-use 로 보려면 따로 허락이 필요하다.
