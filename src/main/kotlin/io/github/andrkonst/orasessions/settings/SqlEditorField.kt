package io.github.andrkonst.orasessions.settings

import com.intellij.lang.Language
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorTextField

/**
 * Multi-line editable SQL field highlighted with the Oracle dialect. Not bound to a project:
 * a default-project editor triggers project services (hover documentation) that are not allowed there.
 * Without a project EditorTextField does not install a highlighter, so a lexer-based one is set explicitly.
 */
class SqlEditorField private constructor(private val file: LightVirtualFile) :
    EditorTextField(createDocument(file), null, fileType()) {

    constructor() : this(LightVirtualFile("query.sql", language, ""))

    init {
        setOneLineMode(false)
        addSettingsProvider {
            it.settings.isLineNumbersShown = true
            it.settings.isUseSoftWraps = false
            it.setVerticalScrollbarVisible(true)
            it.setHorizontalScrollbarVisible(true)
            it.highlighter = EditorHighlighterFactory.getInstance()
                .createEditorHighlighter(file, EditorColorsManager.getInstance().globalScheme, null)
        }
    }

    fun onTextChanged(listener: (String) -> Unit) {
        addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = listener(text)
        })
    }

    private companion object {
        val language: Language get() = Language.findLanguageByID("Oracle") ?: PlainTextLanguage.INSTANCE

        fun fileType() = language.associatedFileType ?: PlainTextFileType.INSTANCE

        fun createDocument(file: LightVirtualFile) =
            FileDocumentManager.getInstance().getDocument(file) ?: EditorFactory.getInstance().createDocument("")
    }
}
