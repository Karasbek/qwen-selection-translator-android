package com.karas.qwentranslator

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Debug-only stock Android widgets. No custom ActionMode or translation implementation. */
class SelectionProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }
        column.addView(TextView(this).apply {
            setText(R.string.selection_probe_title)
            textSize = 22f
        })
        column.addView(TextView(this).apply {
            id = View.generateViewId()
            setText(R.string.selection_probe_readonly)
            textSize = 28f
            setPadding(0, 70, 0, 70)
            setTextIsSelectable(true)
        })
        column.addView(EditText(this).apply {
            id = View.generateViewId()
            setText(R.string.selection_probe_editable)
            textSize = 28f
            minLines = 3
        })
        setContentView(column)
    }
}
