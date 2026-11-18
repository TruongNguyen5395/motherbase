# MotherBase Development Workflow

Guidance for engineering sessions working in this repository. Always follow [CONTRIBUTING.md](CONTRIBUTING.md) and [CLAUDE.md](CLAUDE.md) alongside this workflow.

## 1. Repository & Module Map

Kotlin Multiplatform codebase targeting Android, Desktop (JVM), Wear OS, and Pebble:

- `shared/core`: Gateway protocol (`ApiResult`, JSON-RPC, REST), media/file streaming ([`MediaApi.kt`](shared/core/src/commonMain/kotlin/dev/hermeskotlin/core/media/MediaApi.kt)), chat models & interactive replies ([`ReplyInteraction.kt`](shared/core/src/commonMain/kotlin/dev/hermeskotlin/core/chat/ReplyInteraction.kt)), inbox/cron state ([`Inbox.kt`](shared/core/src/commonMain/kotlin/dev/hermeskotlin/core/inbox/Inbox.kt)).
- `shared/ui`: Shared Compose UI & ViewModels split across `commonMain`, `jvmShared` (shared Android + Desktop JVM helpers such as [`TempFileStorage.jvmShared.kt`](shared/ui/src/jvmShared/kotlin/dev/hermeskotlin/ui/chat/TempFileStorage.jvmShared.kt)), `androidMain` (`PdfRenderer`, `MediaStore`), `jvmMain` (`DesktopPdf`, `PdfWorker`, FileKit dialogs), and test source sets (`commonTest`, `androidHostTest`, `jvmTest`).
- `shared/designsystem`, `shared/wearlink`: Theme/components and Wear companion sync.
- `androidApp`, `desktopApp`, `wearApp`, `pebbleApp`: Platform entry points, R8/ProGuard configs, and packaging.

## 2. Key Implemented Subsystems

1. **Non-Blocking Interactive Replies (`ReplyInteraction`)**:
   - End-of-turn interactive cards (`offer_choices` toolset `motherbase_interact`) are modeled as a closed hierarchy in [`ReplyInteraction.kt`](shared/core/src/commonMain/kotlin/dev/hermeskotlin/core/chat/ReplyInteraction.kt) and rendered by [`ReplyInteractionCard.kt`](shared/ui/src/commonMain/kotlin/dev/hermeskotlin/ui/chat/ReplyInteractionCard.kt).
   - Keep these non-blocking and separate from blocking `InputRequest` (`clarify`). Future interactive modes (`Approval`, `Form`, etc.) extend `ReplyInteraction`.
2. **Scheduled Cron Outputs vs Action Inbox**:
   - Scheduled job details open via `cron_output:<jobId>` in [`ScheduledPage.kt`](shared/ui/src/commonMain/kotlin/dev/hermeskotlin/ui/sessions/ScheduledPage.kt) and [`ScheduledViewModel.kt`](shared/ui/src/commonMain/kotlin/dev/hermeskotlin/ui/sessions/ScheduledViewModel.kt) with cross-profile routing.
   - [`InboxPage.kt`](shared/ui/src/commonMain/kotlin/dev/hermeskotlin/ui/inbox/InboxPage.kt) and [`InboxViewModel.kt`](shared/ui/src/commonMain/kotlin/dev/hermeskotlin/ui/inbox/InboxViewModel.kt) support local dismiss/clear-read (`InboxStore`) without polluting chat sessions.
3. **File Viewer, Disk Streaming & Desktop PDF Worker**:
   - **Save & Open in app / Share** stream chunks directly to disk via [`MediaApi.downloadTo`](shared/core/src/commonMain/kotlin/dev/hermeskotlin/core/media/MediaApi.kt) and [`MediaActions`](shared/ui/src/commonMain/kotlin/dev/hermeskotlin/ui/chat/MediaActions.kt) (`saveStream` / `shareStream`) without buffering full files in RAM or hitting a 64 MB ceiling.
   - **In-app PDF rendering** streams documents to a cached [`TempFileHandle`](shared/ui/src/commonMain/kotlin/dev/hermeskotlin/ui/chat/TempFileStorage.kt): Android uses `ParcelFileDescriptor` directly ([`PdfView.android.kt`](shared/ui/src/androidMain/kotlin/dev/hermeskotlin/ui/chat/PdfView.android.kt)); Desktop passes the temp path (`PdfWire.OPEN_FILE`, `512 MiB` / `2_000` pages / `-Xmx512m`) to the isolated child JVM ([`DesktopPdf.kt`](shared/ui/src/jvmMain/kotlin/dev/hermeskotlin/ui/chat/DesktopPdf.kt), [`PdfWorker.kt`](shared/ui/src/jvmMain/kotlin/dev/hermeskotlin/ui/chat/PdfWorker.kt)).
   - Worker crashes/timeouts map to `FileProblem.PdfDesktopFailed` (separate from `PdfDesktopLimit`) and append bounded stderr tails to `pdf-worker.log` in `appDataDir`.
   - **Large text/code files (`> 64 MiB`)** fall back to HTTP `Range` head preview (`MediaApi.readTextHead`, first 512 KB) when `/api/fs/read-text` returns `413`.

## 3. Standard Development & Verification Loop

### Step 1 — Verify Server Contract & Multiplatform Boundaries
- Confirm actual `hermes-agent` server/plugin behavior and schemas before adding client calls (follow [CLAUDE.md](CLAUDE.md)).
- When modifying shared file, media, PDF, or platform interfaces, check `commonMain`, `jvmShared`, `androidMain`, and `jvmMain` together to keep `expect`/`actual` and test fakes aligned.

### Step 2 — Static & Cross-Locale Pre-Push Checks
1. **7-Locale Resource Parity**: [`StringCatalogueTest.kt`](shared/ui/src/jvmTest/kotlin/dev/hermeskotlin/ui/StringCatalogueTest.kt) verifies all 7 `composeResources` folders (`values`, `values-de`, `values-es`, `values-fr`, `values-hu`, `values-pt-rBR`, `values-zh-rCN`) have matching `%1$s`/`%1$d` placeholders and no `%%`. Update all 7 XML files together when adding/editing strings.
2. **Shrinking & Packaging (`R8` / `ProGuard` / `jlink`)**: Release builds run code shrinking (`androidApp/proguard-rules.pro`, `desktopApp/proguard-rules.pro`) and trimmed JDK images (`desktopApp/build.gradle.kts` `modules(...)`). Always verify reflection/ServiceLoader/JNI dependencies (WebRTC, JLaTeXMath, PDFBox/FontBox, Commons Logging) and JDK modules (including `jdk.charsets`).
3. **Public Repo Hygiene**: Never commit personal gateway URLs, tokens, bot/profile names, real chats/cron jobs, or local machine paths into this repository.

### Step 3 — CI Verification
Push changes and verify both jobs in [`.github/workflows/ci.yml`](.github/workflows/ci.yml):
- `desktop`: `:shared:core:jvmTest`, `:shared:designsystem:jvmTest`, `:shared:ui:jvmTest`, `:shared:wearlink:jvmTest`, `:desktopApp:jvmTest`, `:desktopApp:compileKotlinJvm`.
- `build`: `:shared:core:testAndroidHostTest`, `:shared:designsystem:testAndroidHostTest`, `:shared:ui:testAndroidHostTest`, `:shared:wearlink:testAndroidHostTest`, `:androidApp:assembleDebug`, `:androidApp:assembleDev`, `:androidApp:lintDebug`, `:wearApp:testDebugUnitTest`, `:wearApp:assembleDebug`, `:wearApp:lintDebug`, plus R8 verification steps.

### Step 4 — Release & OTA Packaging
- Ensure the working tree is clean and CI on `main` is green before tagging.
- Pushing a SemVer tag (`git tag vX.Y.Z && git push origin vX.Y.Z`) runs [`.github/workflows/release.yml`](.github/workflows/release.yml):
  - `release` job (Ubuntu): runs Android/Wear tests and builds signed APKs + SHA256 checksums.
  - `windows` job (`windows-latest`): runs `:desktopApp:jvmTest` and `:desktopApp:packageReleaseMsi` (ProGuard shrinking + `jlink` runtime + AppCDS), uploading `motherbase-desktop-X.Y.Z.msi` for GitHub Releases and the in-app OTA updater.
- Validate packaged runtime changes (ProGuard/jlink/PdfWorker) on real Windows MSI / Android APK builds after release.
