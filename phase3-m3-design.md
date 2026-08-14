# Phase 3 Design: Material 3 Unification + Targeted Compose — simplenote-android

Architect synthesis of the four recon reports, verified read-only against `fork/trunk` = d12a1109 (remotes: `fork` = joashrajin/simplenote-android, `origin` = Automattic/simplenote-android).

## 0. Verification of recon claims (done myself, read-only git)

| Claim | Verdict |
|---|---|
| fork/trunk = d12a1109 | CONFIRMED (`git rev-parse fork/trunk`) |
| Exactly **34** custom attrs in `Simplenote/src/main/res/values/attrs.xml` (not ~43) | CONFIRMED (`git grep -c '<attr'` = 34; full file read) |
| 7 theme files: `values`, `values-night`, `values-v27`, `values-night-v27`, `values-v31`, `values-v35`, `values-night-v35` (all `styles.xml`) | CONFIRMED (`git ls-tree`) |
| material 1.12.0 / appcompat 1.7.1 / legacy-preference-v14 1.0.0 / Kotlin 2.2.0 / AGP 8.11.1 | CONFIRMED (build.gradle:3,19; Simplenote/build.gradle:130-134) |
| Base parent `Theme.MaterialComponents.DayNight.NoActionBar` (values/styles.xml:11, values-night:5) | CONFIRMED |
| 21 files with `AlertDialog.Builder`, **zero** `MaterialAlertDialogBuilder` | CONFIRMED |
| 4 dead attrs (`notePinColor`, `pinIconSelector`, `tagChipShadowColor`, `settingsTextColor`) — set in themes, **never read** anywhere | CONFIRMED (grep for `?attr/` reads: zero hits outside theme `<item>` declarations). Note: recon 4's "preference text color driven by settingsTextColor" is wrong — it is set but never consumed. |
| 6 `Widget.Design.BottomSheet.Modal` pins, 4 `design_bottom_sheet` internal-ID reach-ins | CONFIRMED |
| `gradle.properties`: `-Xmx1536m`, Jetifier on | CONFIRMED |
| `ThemedAppCompatActivity.recreate()` = finish()+startActivity+fade, style applied via `setTheme(ThemeUtils.getStyle(this))` post-super | CONFIRMED (file read) |
| `fork/joashrajin/phase1-di-hygiene` touches Simplenote/build.gradle, di/{Data,Network,Thread}Module.kt, Simperium{Tags,Collaborators}Repository.kt, {Request,Complete}MagicLinkViewModel.kt | CONFIRMED (`git diff --name-only`) |
| Upstream #1771/#1661 | CONFIRMED as **open upstream GitHub issues** "Amoled/Black mode" / "Amoled Theme" (not PRs). Constraint interpretation: the design must preserve the true-black `Style.Black` scheme (verified: night Style.Black sets windowBackground/mainBackgroundColor/colorPrimary to `@android:color/black`) and leave a clean overlay slot for a future user-facing AMOLED toggle. |
| Base-theme key fact (read myself): in default light theme, `toolbarColor` = `mainBackgroundColor` = `drawerBackgroundColor` = `sheetBackgroundColor` = `actionModeBackgroundColor` = `@color/background_light`; `colorAccent` = `iconTintColor` = `fabColor` = `actionModeTextColor` = `@color/item_default_light` (the accent) | CONFIRMED — this makes the surface/primary mappings below sound. New finding: **`actionModeTextColor` is accent-colored, not on-surface** (recon 1 proposed colorOnSurface — corrected to colorPrimary below). Also **Classic overrides `toolbarIconColor` to its accent** (values-night:~236) — so toolbarIconColor cannot be safely collapsed into colorOnSurfaceVariant; it stays a KEEP attr (correction to recon 1). |
| Not verifiable offline, accepted from recon 4 with fallback noted: compose-bom `2026.08.00` → ui 1.12.0 / material3 1.4.0; Accompanist Mdc3Theme removed | Accepted (recon cites official BOM mapping page; fallback: latest stable BOM at PR-time, same code shape) |

## 1. Attr → M3-role mapping decision table (all 34, decision-complete)

Legend: **ROLE** = usages rewritten to the M3 attr, custom attr deleted; **KEEP** = survives Phase 3 as an app attr set by base + overlays; **DEAD** = zero consumers, delete immediately; **DEL@PRn** = deleted in that PR. "Today (light)" values verified from Base.Theme.Simplestyle.

| # | Attr | Today (light) | Decision | Lands in |
|---|---|---|---|---|
| 1 | drawerBackgroundSelector | bg_drawer_selector_light | DELETE → M3 NavigationView active indicator (`colorSecondaryContainer`); Classic's accent selector becomes the Classic overlay's `colorSecondaryContainer`; delete 3 selector files | PR6 |
| 2 | notePinColor | text_title_light | **DEAD** | PR1 |
| 3 | noteTitleColor | text_title_light | ROLE `colorOnSurface` | PR7 |
| 4 | notePreviewColor | text_content_light | ROLE `colorOnSurfaceVariant` | PR7 |
| 5 | noteEditorTextColor | text_title_light | ROLE `colorOnSurface` (Black's gray_0 override moves into Black overlay's colorOnSurface) | PR7 |
| 6 | mainBackgroundColor | background_light | ROLE `colorSurface`; `android:windowBackground` stays a literal `@color` token per theme/overlay (attr-indirection in windowBackground is unsafe pre-26; minSdk 23). Watch the 2 bare `?mainBackgroundColor` sites (fragment_login.xml:7, fragment_magic_link_code.xml:8) | PR7 |
| 7 | sheetBackgroundColor | background_light | DELETE → M3 sheet default `colorSurfaceContainerLow` (small deliberate tonal shift, golden-reviewed) | PR11 |
| 8 | dividerColor ⚠ collides w/ MaterialDivider | divider_light | RENAME `simplenoteDividerColor` in PR1 → ROLE `colorOutlineVariant` in PR7 (5 layout sites) | PR1→PR7 |
| 9 | iconTintColor | item_default_light (accent) | ROLE `colorPrimary` (1 site) | PR7 |
| 10 | hintTextColor ⚠ collides w/ TextInputLayout | text_title_disabled | RENAME `editorHintTextColor` in PR1 → replace 2 layout sites with `?android:textColorHint`, delete | PR1→PR7 |
| 11 | tagChipShadowColor | — | **DEAD** | PR1 |
| 12 | listDividerDrawable | divider_light drawable | DELETE → one shared inset-divider drawable on `?attr/colorOutlineVariant` (3 ListView sites) | PR7 |
| 13 | listBackgroundSelector | bg_list_default | **KEEP** — 7 per-style ripple/activated drawables, no single role | — |
| 14 | listSearchHighlightForegroundColor | simplenote_blue_60 | **KEEP** (tertiary-container migration deferred, out of scope) | — |
| 15 | listSearchHighlightBackgroundColor | simplenote_blue_5 | **KEEP** | — |
| 16 | editorSearchHighlightForegroundColor | simplenote_blue_60 | **KEEP** | — |
| 17 | editorSearchHighlightBackgroundColor | simplenote_blue_5 | **KEEP** | — |
| 18 | emptyImageColor | empty_light | **KEEP** — per-style artistic value (Sepia override); mapping to a container role would leak into chips/keys | — |
| 19 | chipCheckedOffBackgroundColor | gray_5 | DELETE → `Widget.Material3.Chip.Input` defaults (code sites NoteEditorFragment.java:1920-1939 rewritten to style-driven chips) | PR7 |
| 20 | chipCheckedOnBackgroundColor | gray_10 | DELETE → checked `colorSecondaryContainer` (deliberate visual change: gray→accent-tinted; golden-reviewed) | PR7 |
| 21 | chipTextColor | text_chip_light | DELETE → Chip.Input text defaults | PR7 |
| 22 | keyBackgroundColor | gray_5 | ROLE `colorSurfaceContainerHigh` (bg_key.xml only) | PR7 |
| 23 | pinIconSelector | bg_pin_selector_light | **DEAD** (+ delete bg_pin_selector_{light,dark}.xml) | PR1 |
| 24 | toolbarColor | background_light | ROLE `colorSurface` (verified equal to mainBackgroundColor in every non-Sepia theme; Sepia/Matrix/Black overlays retint colorSurface anyway) | PR7 |
| 25 | toolbarIconColor | gray_50 | **KEEP** — Classic overrides it to accent; collapsing to colorOnSurfaceVariant would silently break Classic. Base aliases it to the onSurfaceVariant token; Classic overlay overrides. Heaviest consumer set (8 vector drawables + 7 code sites) stays untouched | — |
| 26 | toolbarPopupTheme | ThemeOverlay.MaterialComponents.Light | DELETE → M3 toolbar popup defaults; Sepia overlay keeps its popup overlay via toolbar style item | PR6 |
| 27 | actionBarTextColor | text_title_light | ROLE `colorOnSurface` (theme-internal only) | PR7 |
| 28 | actionModeTextColor | item_default_light (**accent**) | ROLE **`colorPrimary`** (corrects recon 1's colorOnSurface — verified accent-valued in base and all styles) | PR7 |
| 29 | actionModeBackgroundColor | background_light | ROLE `colorSurface` (Black's dark_0 via overlay) | PR7 |
| 30 | settingsTextColor | black | **DEAD** | PR1 |
| 31 | fabColor | item_default_light (accent) | DELETE → `Widget.Simplenote.Fab` (parent Widget.Material3.FloatingActionButton.Primary) on `colorPrimary` — preserves accent-filled look, adopts M3 squircle deliberately | PR6 |
| 32 | fabIconColor | white | DELETE → `colorOnPrimary` | PR6 |
| 33 | drawerBackgroundColor | background_light | DELETE → NavigationView M3 default `colorSurfaceContainerLow`; the 2 non-drawer stray uses (activity_tag_add.xml:14, TagsActivity.kt:142) → `colorSurface` | PR6/PR7 |
| 34 | styleFontFamily | sans-serif | **KEEP** — typography, feeds M3 textAppearances' fontFamily from overlays (Matrix/Mono monospace, Publication serif) | — |

**End state: 8 custom attrs remain** (13-17 highlights ×4, listBackgroundSelector, emptyImageColor, toolbarIconColor, styleFontFamily). `colorAccent` stays aliased to the colorPrimary token throughout Phase 3 (appcompat still resolves it; retire in a Phase 4 cleanup).

**Aliasing mechanism (the "no half-themed screens" guarantee):** alias via **shared `@color` tokens**, not attr→attr indirection. PR3 introduces a semantic token layer (`values/colors_m3.xml` + `values-night/colors_m3.xml`): `sn_surface`, `sn_surface_container{,_low,_high,_highest}`, `sn_on_surface`, `sn_on_surface_variant`, `sn_primary`, `sn_on_primary`, `sn_secondary_container`, `sn_outline_variant` — each pointing at today's exact palette values (night ladder maps 1:1: surface=background_dark, containerLow=dark_4, container=dark_8, containerHigh=dark_16, containerHighest=dark_24 — the hand-built M2 elevation ladder IS the M3 container ladder). Both the M3 role and the legacy custom attr point at the same token, so every screen renders pixel-identically whether its layout has been rewritten yet or not. This avoids `resolveAttribute` edge cases at the ~15 code read-sites.

## 2. Theme hierarchy target

Names kept as-is (`*.Simplestyle`) to minimize diff churn — only parents and contents change.

```
Theme.Material3.DayNight.NoActionBar
└── Base.Theme.Simplestyle          values/styles.xml ONLY (night deltas live in values-night/colors_m3.xml)
    │   full M3 scheme from sn_* tokens; 8 KEEP attrs; materialAlertDialogTheme + alertDialogTheme;
    │   component style pointers (Widget.Simplenote.Fab, Chip, Toolbar overlays)
    └── Theme.Simplestyle           window config; edge-to-edge via enableEdgeToEdge()/SystemBarUtils, no
        │                           per-API statusBar/navBar items
        ├── Theme.Simplestyle.Splash        (values-v31 one-line re-parent alias SURVIVES)
        ├── Theme.Simplestyle.About
        ├── Theme.Simplestyle.Passcode      (re-parent to Theme.Material3.Light.NoActionBar, stays day-only)
        └── Theme.Simplestyle.Transparent   (ONE theme; widget-config dialogs get style via overlay)

ThemeOverlay.Simplestyle.Style.{Default,Classic,Black,Matrix,Mono,Publication,Sepia}
    applied by ThemedAppCompatActivity via getTheme().applyStyle(overlay, true)
    contents: colorPrimary(+on/container), colorSecondaryContainer, surface roles where restyled
    (Sepia/Matrix/Black), android:windowBackground, styleFontFamily, the 8 KEEP attrs.
    Because dialogs/sheets/popups resolve theme attrs from the activity context, the entire
    Theme.Transparent.* (×7), Theme.Simplestyle.BottomSheetDialog.* (×7 light + ×7 night),
    and Dialog.Black/Dialog.Sepia families become unnecessary and die.

ThemeOverlay.Simplestyle.Amoled   (AMOLED slot for upstream #1771/#1661: pure-black surface roles,
    stackable after any style overlay; no user-facing toggle shipped in Phase 3 — Black overlay
    already yields true black in night mode, preserving today's AMOLED answer)
```

**Files that die:** `values-v27/styles.xml`, `values-night-v27/styles.xml`, `values-v35/styles.xml`, `values-night-v35/styles.xml` (PR4 — edge-to-edge + `WindowInsetsControllerCompat.isAppearanceLightSystemBars` replace every windowLight*/navBarColor item; SystemBarUtils.kt already half-built for this), and `values-night/styles.xml` shrinks from 336 lines to a ≤25-line rump by PR5 (night deltas relocate to color-token forks; anything structural that genuinely differs — e.g. a night-only popup overlay — may remain). `values-v31/styles.xml` survives (splash). Also deleted: `androidx.legacy:legacy-preference-v14` (PR3 — `PreferenceThemeOverlay.v14.Material` is aliased by preference 1.2.1; verify at build), the 7-variant Transparent family, the 16 bottom-sheet themes, Dialog.Black/Dialog.Sepia.

## 3. Dialog / component sweep batching

- **Batch A — theme wiring + shared utils (PR8, part 1):** add `materialAlertDialogTheme` → `ThemeOverlay.Simplestyle.Dialog` (parent ThemeOverlay.Material3.MaterialAlertDialog; keep `alertDialogTheme` for `PreferenceDialogFragmentCompat`/ListPreference). Swap builders in DialogUtils.java:23 (highest-leverage choke point), BrowserUtils.java:63/84, NoteUtils.java:74, SimplenoteProgressDialogFragment.java:48. Drop `ContextThemeWrapper(R.style.Dialog)` at converted sites.
- **Batch B — auth cluster (PR8, part 2):** NewCredentialsActivity ×6, MagicLinkableFragment.kt:116, SignupFragment.java:211, SimplenoteAuthenticationActivity.java:276 (framework `android.app.AlertDialog` → Material — behavior-class change, test explicitly). Zero Phase 1/2 file collision (Phase 1 touches the magic-link **ViewModels**, not these fragments).
- **Batch C — screen dialogs (PR9):** TagsActivity.kt:125, TagDialogFragment.kt:47 (Espresso net: TagDialogFragmentTest — re-check button-text matchers, M3 doesn't all-caps), AddTagActivity.kt:125 (preserve HtmlCompat+LinkMovementMethod), StyleActivity.java:92, CollaboratorsActivity.kt:188 dialog only if PR13 hasn't landed (else skipped), ShortcutDialogFragment.java:33, AboutFragment.java:209, WordPressDialogFragment.java:129 (4-state machine — most careful item), NoteWidget{Dark,Light}ConfigureActivity.java:110 (verify materialAlertDialogTheme resolves under Theme.Simplestyle.Transparent + overlay; historic widget-crash hotspot).
- **Batch D — god-class dialogs (PR10, lands last, ≤60-line diff):** NotesActivity.java:379,1032; PreferencesFragment.java:417 (destructive red positive button → `ThemeOverlay.Simplestyle.Dialog.Destructive` redefining colorPrimary, replacing the onShow paint), :483, :521, :774.
- **Skipped entirely:** AddCollaboratorFragment.kt — dies in PR13 as a Compose AlertDialog; do not double-migrate.
- **Bottom sheets (PR11, own PR — highest visual risk):** delete the 6 `Widget.Design.BottomSheet.Modal` pins; collapse all 16 sheet themes to M3 defaults (sheets inherit the activity's style overlay through `requireContext()`, so `ThemeUtils.getThemeFromStyle` and `BottomSheetDialogBase.getTheme()` per-style mapping are deleted); replace all 4 `com.google.android.material.R.id.design_bottom_sheet` reach-ins with public `BottomSheetDialog.getBehavior()` and the width clamp with `android:maxWidth` in the sheet style; re-verify the three STATE-forcing onShow handlers (History EXPANDED+skipCollapsed, Info half-peek, Share HALF_EXPANDED) against the M3 modal (28dp corners, drag handle, edge-to-edge).
- **Snackbars/menus:** no code changes; M3 inverse-surface recolor accepted, contrast checked under all 7 overlays in PR3's golden review.

## 4. Compose enablement config (exact, for this toolchain) + first two conversions

**PR12 build changes** (legacy buildscript style — no plugins DSL, matching repo convention):

```groovy
// root build.gradle (buildscript.dependencies)
classpath "org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlin_version"   // 2.2.0 — MUST equal Kotlin

// Simplenote/build.gradle
apply plugin: 'org.jetbrains.kotlin.plugin.compose'
android { buildFeatures { compose true } }        // no composeOptions block on Kotlin 2.x
dependencies {
    implementation platform('androidx.compose:compose-bom:2026.08.00')  // ui 1.12.0, material3 1.4.0
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.activity:activity-compose'                  // activity 1.10.1 already pinned
    implementation "androidx.lifecycle:lifecycle-runtime-compose:$rootProject.lifecycleVersion" // 2.9.2
    implementation 'androidx.compose.runtime:runtime-livedata'           // all candidate VMs are LiveData
    debugImplementation 'androidx.compose.ui:ui-tooling'
    debugImplementation 'androidx.compose.ui:ui-test-manifest'
    androidTestImplementation 'androidx.compose.ui:ui-test-junit4'
}

// gradle.properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g   // 1536m will OOM with Compose + kapt/Hilt
```

Plus `SimplenoteTheme` bridge (~100 lines, no Accompanist — Mdc3Theme is removed upstream): resolves the M3 roles + the 8 KEEP attrs from the **hosting activity's themed context** (TypedArray/`MaterialColors.getColor`) into a `ColorScheme` + typography whose fontFamily reads `styleFontFamily`. Because premium styles are activity-level overlays, Sepia/Matrix/Black/AMOLED flow into Compose for free. Sequencing M3-first (PRs 3-7) means the bridge maps 1:1 from roles instead of 34 legacy attrs.

**Conversion 1 — Collaborators (PR13, smallest, best-shaped VMs):** `CollaboratorsActivity.kt` → `setContent { SimplenoteTheme { CollaboratorsScreen(vm) } }`: Scaffold + TopAppBar, LazyColumn over `UiState.CollaboratorsList`, empty/trash/deleted states from the sealed UiState, events via SingleLiveEvent observation in the activity. `AddCollaboratorFragment` folds into a Compose `AlertDialog` with TextField + inline error (replacing the onShow positive-button-override hack). Deletes: activity_collaborators.xml, add_collaborator.xml, CollaboratorsAdapter, AddCollaboratorFragment. VMs (`CollaboratorsViewModel`, `AddCollaboratorViewModel`) and their unit tests frozen — consumed via `observeAsState()`. New compose-ui tests for list/empty/dialog-error; Roborazzi goldens re-recorded for the screen across 4 styles × 2 modes.

**Conversion 2 — Tags (PR14):** `TagsActivity.kt` → Scaffold with M3 SearchBar-style TopAppBar search (replaces the HTML-hint SearchView hack), LazyColumn via `TagItemAdapter`-equivalent composable, FAB, empty state; delete activity_tags.xml + TagItemAdapter. Delete-confirm dialog → Compose AlertDialog. **Explicit decisions:** the `MorphSetup` shared-element morph to AddTagActivity is dropped (accepted loss; AddTagActivity itself remains a View faux-dialog activity this phase); `TagDialogFragment` (rename dialog) remains a View DialogFragment (already Material-swapped in PR9); `TagsViewModel` frozen except the one mandatory fix: `LongDeleteTagEvent(val view: View)` leaks a View through the VM — replace with an index/tag payload, popup anchored in the composable (small, isolated VM change + test update). Instrumented `TagsActivityTest` reworked to compose-test APIs.

Settings/auth conversions are explicitly **later-wave** (Settings blocked on Phase 1 PreferencesRepository/AccountRepository; auth VMs are contested by phase1-di-hygiene).

## 5. Ordered PR sequence (14 PRs)

Screenshot strategy used throughout: **Roborazzi** (latest 1.x) + Robolectric 4.x @GraphicsMode(NATIVE), confined to a new `screenshotTest` JVM-test package so the existing plain-JVM suite (`returnDefaultValues=true`) is untouched. Goldens in `Simplenote/src/test/screenshots/`; `recordRoborazziDebug` locally, `verifyRoborazziDebug` wired into .buildkite/pipeline.yml. Matrix: {NotesActivity list+drawer, NoteEditorActivity, TagsActivity, PreferencesActivity, StyleActivity, tag-delete dialog, info bottom sheet} × {light, dark} × {Default, Classic, Black, Sepia, Matrix} ≈ 70 goldens. Every visual PR ships the golden diff as reviewable images. What Robolectric cannot prove (system bars, real sheet gestures) gets a stated emulator checklist.

| # | PR | Files (key) | Verification | Risk | Size |
|---|---|---|---|---|---|
| 1 | Theme prep: delete 4 dead attrs, rename 2 colliding attrs | attrs.xml, 7× styles.xml, ~8 layouts (hint/divider sites), delete bg_pin_selector_* | build + lintDebug + unit tests; grep proves zero remaining `hintTextColor`/`dividerColor` app usages. Zero visual change | Low | S |
| 2 | Roborazzi harness + baseline goldens | Simplenote/build.gradle (test deps), new screenshotTest classes, goldens, .buildkite/pipeline.yml | `verifyRoborazziDebug` green on trunk; goldens reviewed once as the baseline | Low (test-only) | M |
| 3 | M3 base theme + full token aliasing | values/styles.xml, values-night/styles.xml, new colors_m3.xml ×2, pin styles (chip pill shape, FAB circle, liftOnScroll=false, AppBar), drop legacy-preference-v14 | Golden diff ≈ zero (allowed drift: switch/checkbox/snackbar recolors, reviewed image-by-image); manual pass over auth TextInputs + Settings switches; theme-switch recreate() QA | High leverage, contained by aliasing | M-L |
| 4 | Edge-to-edge + delete per-API theme files | delete values-{v27,night-v27,v35,night-v35}/styles.xml; SystemBarUtils.kt, ThemedAppCompatActivity (enableEdgeToEdge); merge Sepia dupes | Emulator matrix API 23/26/27/31/35/36: status/nav bar color + light-icon flags per style×mode; goldens for content unchanged | Med (system bars per-API) | M |
| 5 | Premium styles → ThemeOverlays (+AMOLED slot) | styles ×2, colors_m3 ×2, ThemeUtils.java, ThemedAppCompatActivity.java, PrefUtils.java (getStyleWidgetDialog → base+overlay), 2 widget-config activities; delete Theme.Transparent.* family + night style forks | Full golden matrix all 7 styles × 2 modes; StyleActivity picker + premium gating; widget-config dialog on device; recreate() flow | High | L |
| 6 | Component styles to Material3 | Toolbar/AppBar overlays → ThemeOverlay.Material3.*, TextInput → Widget.Material3 OutlinedBox (drop TextInput shim), Button.Flat → M3, auth AppCompatButton → MaterialButton (7 layouts), Widget.Simplenote.Fab (squircle, colorPrimary), NavigationView M3 indicator (delete drawerBackgroundSelector), toolbarPopupTheme deleted, fix app:theme misuses, typography renames (Headline6→TitleLarge etc.) | Golden diff with **intentional** deltas documented per component; drawer/FAB/auth manual pass | Med | L |
| 7 | Attr retirement: usages → M3 roles | Commit A (resources): ~90 layouts, drawables, bg_key/bg_list_popup etc. Commit B (code, tiny): ThemeUtils.java:108, NoteListFragment.java:195/920/994, NoteEditorFragment.java:213/337/350/1920-1939 (chips → Chip.Input style), NoteMarkdownFragment.kt, NotesActivity.java:448/464/997, TagsActivity.kt, FullScreenDialogFragment.java; delete mapped attrs from attrs.xml (34→8) | Goldens: zero-diff expected except chips (gray→secondaryContainer, deliberate); editor chip add/check/remove manual test; grep proves no `?attr/<deleted>` remains | Med (breadth, not depth) | L (mechanical) |
| 8 | Dialog sweep A: theme wiring + shared utils + auth cluster | styles (materialAlertDialogTheme + ThemeOverlay.Simplestyle.Dialog), DialogUtils, BrowserUtils, NoteUtils, SimplenoteProgressDialogFragment, NewCredentialsActivity, MagicLinkableFragment, SignupFragment, SimplenoteAuthenticationActivity | Dialog goldens (error/progress/delete-confirm) per style; auth error-path manual run; ListPreference dialog still themed (alertDialogTheme retained) | Low-Med | M |
| 9 | Dialog sweep B: screen dialogs | TagsActivity, TagDialogFragment, AddTagActivity, StyleActivity, ShortcutDialogFragment, AboutFragment, WordPressDialogFragment, NoteWidget{Dark,Light}ConfigureActivity | Espresso: TagDialogFragmentTest, AddTagActivityTest, TagsActivityTest (fix capitalization matchers); WordPress 4-state manual walkthrough; widget-config dialog on device (crash hotspot) | Med | M |
| 10 | Dialog sweep C: god-class dialogs | NotesActivity.java (2 sites), PreferencesFragment.java (4 sites + Destructive overlay) | Empty-trash, logged-in, delete-account (red button), unsynced-notes 3-button dialogs manually + goldens. **≤60-line mechanical diff, lands in a coordinated window with Phase 2** | Med (collision, not code) | S |
| 11 | Bottom sheets to M3 | BottomSheetDialogBase, History/Info/ShareBottomSheetDialog (behavior API + maxWidth), styles ×2 (delete 6 pins + 16 sheet themes), ThemeUtils (delete getThemeFromStyle) | Sheet goldens × styles; manual: History expanded+slider, Info half-peek, Share, rotation re-attach path in NotesActivity (code untouched), tablet width clamp | High (visual) | M |
| 12 | Compose enablement + SimplenoteTheme bridge | root build.gradle, Simplenote/build.gradle, gradle.properties, SimplenoteTheme.kt (+ debug preview) | Clean build; bridge screenshotTest rendering a role-swatch grid per style×mode; record build-time delta. Trivial-but-certain build.gradle conflict with phase1-di-hygiene — rebase last | Low | S-M |
| 13 | Collaborators screen in Compose | CollaboratorsActivity.kt, new CollaboratorsScreen.kt + AddCollaboratorDialog.kt; delete AddCollaboratorFragment, 2 layouts, CollaboratorsAdapter | VM unit tests untouched (LiveData frozen); new compose-ui tests; goldens re-recorded; manual Simperium sync smoke | Med | M |
| 14 | Tags screen in Compose | TagsActivity.kt, new TagsScreen.kt; delete activity_tags.xml, TagItemAdapter; TagsViewModel LongDeleteTagEvent(View) fix + test | TagsViewModelTest updated for event change only; TagsActivityTest → compose tests; search/rename/delete/reorder manual; goldens | Med-High (front-door list screen) | M-L |

Dependency edges: 1→3→{4,5}→6→7→{8→9→10, 11}→12→13→14. PRs 4 and 5 can swap; 8-10 can interleave with 11-12. Every PR leaves the app fully coherent: aliasing (PR3) keeps unrewritten screens pixel-identical, and each deliberate visual change (chips, sheets, FAB shape, dialogs) ships whole-app-wide in its own PR, never per-screen.

**Phase 1/2 collision ledger (every shared file):** Simplenote/build.gradle (PR2, PR12 ↔ phase1-di-hygiene — trivial dep-block conflicts, rebase Phase 3 side); Request/CompleteMagicLinkViewModel (Phase 1 owns; Phase 3 never touches — auth dialog work is fragment-side only); NotesActivity.java (PR7b attr reads + PR10 dialogs only), NoteEditorFragment.java (PR7b: attr reads, chips, action-mode tints; sheets PR11 touches the dialog classes, not the fragment), NoteListFragment.java (PR7b reads), PreferencesFragment.java (PR10) — all four god classes are touched by exactly two small mechanical commits (PR7b, PR10) whose landing windows are coordinated with Phase 2, everything else in Phase 3 avoids them; ThemedAppCompatActivity.java (PR4, PR5), ThemeUtils.java (PR5, PR7b, PR11), PrefUtils.java (PR5), SystemBarUtils.kt (PR4), DialogUtils/NoteUtils (PR8), TagsActivity.kt (PR7b, PR9, PR14), StyleActivity.java (PR9), AddTagActivity.kt (PR9), CollaboratorsActivity.kt (PR13). Rule: Phase 3 owns res/ (collision-free by construction — Phases 1-2 never touch resources); for shared .java/.kt files, Phase 3 commits are mechanical and ≤60 lines so they rebase in minutes in either direction.

## 6. Explicit non-goals

- **Note editor** conversion or restyle beyond theme-attr pass-through (stays a View; NoteEditorFragment untouched except PR7b mechanical reads/chips).
- **RemoteViews widgets**: all 30+ pre-baked `note_widget_*`/`note_list_widget_*` layouts and literal-color drawables untouched; only their config-dialog builders are swapped (PR9).
- **Markdown WebView CSS**: per-style `{light,dark}_<style>.css` assets and `getCssFromStyle` stay as-is.
- No **DynamicColors / Material You**, no new **AMOLED user-facing toggle** (overlay slot only — upstream #1771/#1661 remain future feature work the hierarchy now supports in ~20 lines).
- No **LiveData→StateFlow / SingleLiveEvent** modernization (Phase 1's lane); VM signatures frozen except the one Tags View-leak fix.
- No **Settings or auth Compose conversion** this wave (Settings blocked on Phase 1 repos; auth VMs contested).
- No **NewCredentialsActivity rewrite**, no **BiometricPrompt** migration in PasscodeLock, no PasscodeLock module fold-in.
- No build-system modernization beyond Compose enablement: version catalog, plugins DSL, KSP-for-Hilt, Jetifier removal, `shrinkResources` all deferred.
- No renaming of `Simplestyle` theme/style identifiers; no changes to Simperium's `R.style.Simperium`.
- Search-highlight attrs stay custom (tertiary-role migration deferred).
- CLAUDE.md `:Wear` staleness noted but docs fixes are not part of this phase's PRs.