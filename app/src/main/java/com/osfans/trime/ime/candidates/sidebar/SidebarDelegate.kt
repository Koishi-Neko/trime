/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.sidebar

import android.os.Build
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.core.Candidates
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.syllable.SyllableCandidate
import com.osfans.trime.ime.candidates.syllable.SyllableCandidateRules
import com.osfans.trime.ime.candidates.symbol.SymbolKeyboardKind
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.dsl.recyclerview.recyclerView
import timber.log.Timber

/**
 * The vertical sidebar floating over the leftmost column of the keyboard
 * (where the theme keeps its punctuation keys), under the candidate bar.
 *
 * It is a plain sibling of the candidate bar: both react to the same
 * [Candidates.Bulk] update and split it with [SyllableCandidateRules], so the
 * syllables shown here are exactly the ones the bar drops. Selecting a
 * syllable row goes through the same `selectCandidate` call the bar uses, with
 * the candidate's global index. While nothing is being composed the sidebar
 * switches to the symbol set of the current keyboard instead, and a tap
 * commits that symbol to the input connection.
 *
 * The view overlays the keyboard rather than taking a column next to it: the
 * keyboard keeps the exact bounds it has without the sidebar, and the sidebar
 * is added after it so it draws on top and takes the touches in its bounds
 * (its background is opaque, hiding the punctuation keys underneath). The view
 * is `GONE` whenever there is nothing to show, which lets the touches reach
 * the punctuation keys again.
 *
 * Its geometry is the leftmost key column of the keyboard made taller — see
 * [SidebarGeometry] — and it wears the key material: the column starts on the
 * keyboard's own left edge, takes the key back color with the theme key border
 * and round corner, and stops on the bottom row, so the 「符」 key the theme
 * keeps there stays reachable.
 */
class SidebarDelegate(override val di: DI) :
    DIAware,
    InputBroadcastReceiver {
    private val context: ContextThemeWrapper by instance()
    private val rime: RimeSession by instance()
    private val scope: ThemeScope by instance()
    private val service: TrimeInputMethodService by instance()
    private val keyboardWindow: KeyboardWindow by instance()
    private val windowManager: BoardWindowManager by instance()

    private val prefs by lazy { AppPrefs.defaultInstance().candidates }

    private val theme: Theme get() = scope.theme

    /** Syllables of the last candidate update, as the bar filtered them out. */
    private var syllables: List<SyllableCandidate> = emptyList()

    /** Candidate rime highlights, as an index into the whole candidate list. */
    private var highlighted: Int = -1

    /** Theme id of the keyboard in use; empty until the first one is attached. */
    private var keyboardId: String = ""

    /** The window the manager last put on screen, for the log line only. */
    private var attachedWindow: BoardWindow? = null

    private var lastLoggedState: String = ""

    private var lastLoggedGeometry: String = ""

    private val adapter =
        SidebarViewAdapter(scope).apply {
            setOnItemClickListener { _, _, position ->
                when (val entry = items.getOrNull(position)) {
                    is SidebarEntry.Syllable -> {
                        rime.launchOnReady { it.selectCandidate(entry.globalIndex, global = true) }
                    }

                    is SidebarEntry.Symbol -> commitSymbol(entry)

                    null -> Unit
                }
            }
        }

    val view: RecyclerView by lazy {
        context.recyclerView(R.id.sidebar_view) {
            visibility = View.GONE
            itemAnimator = null
            adapter = this@SidebarDelegate.adapter
            layoutManager = LinearLayoutManager(context)
            isFocusable = false
            isFocusableInTouchMode = false
            // keep touches in the column from falling through to the
            // punctuation keys underneath
            isClickable = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                defaultFocusHighlightEnabled = false
            }
            applyBackground(this)
        }.also { it.addOnAttachStateChangeListener(columnBoundsListener) }
    }

    /**
     * The input view builds its children *after* the keyboard window has created
     * the keyboard, and the keyboard announces itself right there — so the
     * sidebar is told how big it should be before it has been put into a parent
     * and has layout params. The bounds are kept and applied here instead.
     */
    private val columnBoundsListener =
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = applyBounds()

            override fun onViewDetachedFromWindow(v: View) = Unit
        }

    /**
     * Rime does not announce a keyboard switch, and the switch itself is
     * posted to the main thread, so the sidebar cannot learn about it from the
     * candidate broadcasts alone: it follows the keyboard id instead.
     *
     * Started only after [view] exists: the flow replays the current id, and a
     * replayed id would otherwise evaluate the content before the view field
     * is initialized.
     */
    private val keyboardIdJob: Job =
        service.lifecycleScope.launch {
            keyboardWindow.currentKeyboardId.collect {
                keyboardId = it
                applyGeometry()
                reevaluate()
            }
        }

    /** Stops following the keyboard; the input view is being torn down. */
    fun dispose() {
        keyboardIdJob.cancel()
    }

    /** Bounds of the column the keyboard in use gives the sidebar; null while unknown. */
    private var columnBounds: SidebarBounds? = null
        set(value) {
            field = value
            val height = value?.rowHeight ?: 0
            if (adapter.rowHeight != height) {
                adapter.rowHeight = height
                rebindRows()
            }
            logGeometry(value)
        }

    /**
     * Restyles after a scheme switch. Rows re-read their colors on the next
     * bind, so a plain refresh is enough to repaint the visible ones.
     */
    fun refreshColors() {
        applyBackground(view)
        rebindRows()
    }

    /** Rebinds the visible rows; a list that is mid-layout is notified afterwards. */
    private fun rebindRows() {
        val list = view
        if (list.isComputingLayout) {
            list.post { adapter.notifyDataSetChanged() }
        } else {
            adapter.notifyDataSetChanged()
        }
    }

    /**
     * Paints the column with the keyboard key material — key back color, key
     * border, theme key round corner — instead of the candidate bar's, so it
     * reads as one tall key of the column it replaces. Rebuilt on a scheme
     * switch, when [refreshColors] runs.
     */
    private fun applyBackground(target: RecyclerView) {
        val material =
            runCatching {
                scope.decorDrawable(
                    "key_back_color",
                    "key_border_color",
                    context.dp(theme.style.keyBorder),
                    context.dp(theme.style.roundCorner).toFloat(),
                )
            }.onFailure { Timber.w(it, "Sidebar: cannot resolve the key material for the column") }
                .getOrNull()
        // a theme that cannot supply the material leaves the column as it was
        // rather than taking the keyboard down with it
        if (material != null) target.background = material
    }

    /**
     * Commits a symbol row straight to the input connection, for both the
     * punctuation set and the calculator set: the sidebar only covers a column
     * whose keys are symbols anyway, so this is what a tap on the theme's own
     * symbol key does, without going through rime.
     */
    private fun commitSymbol(symbol: SidebarEntry.Symbol) {
        service.commitText(symbol.text)
        repeat(symbol.cursorBack) { moveCaretLeft() }
    }

    /**
     * Moves the caret one character left. `commitText` cannot place the caret
     * inside the text it commits — the framework javadoc says as much — so a
     * bracket pair is committed whole and the caret then walks back over the
     * closing half. An editor that ignores arrow keys leaves the caret after
     * the pair; the text itself is committed either way.
     */
    private fun moveCaretLeft() {
        val ic = service.currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
    }

    /**
     * Takes the bounds of the column the sidebar covers from the keyboard in
     * use: the drawn bounds of the leftmost key of every row but the bottom
     * one. Tiling the rows inside it also gives the list its key row height, so
     * three symbols fill the visible column and the rest scrolls.
     *
     * A keyboard that cannot be measured (no keys, a single row) leaves the
     * bounds null and the view keeps the percentage fallback of its layout
     * params, which is what the sidebar starts with.
     */
    private fun applyGeometry() {
        val keyboard = keyboardWindow.attachedKeyboard
        columnBounds =
            keyboard?.let {
                SidebarGeometry.column(
                    keys = it.keys.map { key -> SidebarKeyBounds(key.x, key.y, key.width, key.height, key.row) },
                    horizontalGap = it.horizontalGap,
                    verticalGap = it.verticalGap,
                )
            }
    }

    /**
     * Applies [columnBounds] to the view. The view is built before the input
     * view puts it into a parent, so it may still have no layout params: there
     * is nothing to update then, and [columnBoundsListener] applies the bounds
     * once the view is attached.
     */
    private fun applyBounds() {
        val bounds = columnBounds ?: return
        val params = view.layoutParams as? ConstraintLayout.LayoutParams ?: return
        params.width = bounds.width
        params.height = bounds.height
        // the view is anchored below the bar and above the bottom padding:
        // a top bias pins it to the first key row instead of centring it
        params.verticalBias = 0f
        params.topMargin = bounds.top
        params.marginStart = bounds.left
        view.layoutParams = params
    }

    /**
     * Logs the geometry changes. The keyboard id and the column it yields are
     * the two things to check in logcat when the sidebar sits in the wrong
     * place or keeps its fallback size.
     */
    private fun logGeometry(bounds: SidebarBounds?) {
        val state =
            if (bounds == null) {
                "keyboard=$keyboardId column=unavailable (percent fallback)"
            } else {
                "keyboard=$keyboardId column=${bounds.left},${bounds.top} ${bounds.width}x${bounds.height} rows=${bounds.rows}"
            }
        if (state == lastLoggedGeometry) return
        lastLoggedGeometry = state
        Timber.d("Sidebar: %s", state)
    }

    private fun reevaluate() {
        val composing = rime.run { statusCached.isComposing }
        val entries =
            SidebarContentResolver.resolve(
                syllables = syllables,
                composing = composing,
                keyboardKind = SymbolKeyboardKind.of(keyboardId),
                symbolsEnabled = prefs.symbolSidebar.getValue(),
                keyboardOnScreen = keyboardOnScreen(),
            )
        adapter.updateEntries(entries, highlighted)
        view.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        logDecision(composing, entries)
    }

    /**
     * Whether the keyboard is the window on screen. Pulled from the window
     * manager instead of tracked from the broadcasts, so the answer cannot go
     * stale: the keyboard area is shared by the keyboard, the liquid keyboard
     * panel, the menus and the clipboard.
     */
    private fun keyboardOnScreen(): Boolean = runCatching { windowManager.isAttached(keyboardWindow) }.getOrDefault(true)

    /**
     * Logs only the decisions that change. The theme id of the keyboard is the
     * one thing a theme can name out of recognition, so it is worth having in
     * logcat when the symbol sets do not show up where they should.
     */
    private fun logDecision(
        composing: Boolean,
        entries: List<SidebarEntry>,
    ) {
        val state =
            "keyboard=$keyboardId window=${attachedWindow?.javaClass?.simpleName ?: "none"} " +
                "composing=$composing entries=${entries.size}"
        if (state == lastLoggedState) return
        lastLoggedState = state
        Timber.d("Sidebar: $state")
    }

    override fun onWindowAttached(window: BoardWindow) {
        attachedWindow = window
        // a panel replaced the keyboard: the sidebar belongs to the keyboard
        reevaluate()
    }

    override fun onWindowDetached(window: BoardWindow) {
        // the next window announces itself right after this
        reevaluate()
    }

    override fun onCandidateListUpdate(data: Candidates.Bulk) {
        syllables = SyllableCandidateRules.split(data.candidates).syllables
        highlighted = data.highlighted
        reevaluate()
    }

    override fun onCompositionUpdate(data: CompositionProto) {
        reevaluate()
    }

    override fun onKeyAppearanceUpdate(
        composing: Boolean,
        menu: Boolean,
        paging: Boolean,
    ) {
        reevaluate()
    }
}
