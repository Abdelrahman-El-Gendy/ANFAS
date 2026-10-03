package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * A labelled single-line input.
 *
 * `AnfasSearchField` is not this: it owns a magnifier and a clear button and is styled for a
 * search affordance. This is the general form field the login screen and every future editing
 * screen needs.
 *
 * [errorMessage] is a parameter rather than a boolean because a field marked wrong with no reason
 * given is the most common form-design failure — the caller must supply the explanation to get the
 * red border.
 */
@Composable
fun AnfasTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    /** False for a multi-line body -- the announcement composer's only user so far. Every
     * other field in this app is one line, so this defaults to preserve that. */
    singleLine: Boolean = true,
    errorMessage: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        errorMessage != null -> scheme.error
        focused -> scheme.primaryContainer
        else -> scheme.outlineVariant
    }

    // Left null when the caller has no submit action, so the keyboard's own default for the
    // action key applies -- advance focus for Next, dismiss for Done.
    val imeActionHandler: (KeyboardActionScope.() -> Unit)? =
        onImeAction?.let { handler -> { handler() } }

    /*
     * Keep a focused field above the keyboard, and keep it there when the keyboard's height
     * changes.
     *
     * Compose brings a newly-focused field into view on its own, but it does that at the moment
     * focus arrives -- when the keyboard has not opened yet and the field is usually already
     * visible, so nothing scrolls. The viewport then shrinks by the keyboard's height and no
     * second request is made, which leaves the field the user is typing into partly or wholly
     * covered. Verified on a device rather than reasoned about: on a landscape phone the field
     * ended up two-thirds under the keyboard even though the scroll container was correctly
     * inset.
     *
     * Keying the effect on [imeBottom] as well as focus is the whole point -- it re-runs each
     * time the keyboard's height changes, including the switch to a taller layout when a
     * suggestion strip or an emoji row appears. Harmless where there is no keyboard: the inset
     * stays 0 and the request resolves against whatever scrollable ancestor exists, or does
     * nothing at all if there is none.
     */
    val bringIntoView = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (focused) bringIntoView.bringIntoView()
    }

    Column(
        modifier = modifier.bringIntoViewRequester(bringIntoView),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = AnfasTheme.textStyles.labelCaps,
            color = if (errorMessage != null) scheme.error else scheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // heightIn rather than height: a larger system font size must be able to grow
                // the field instead of clipping the text inside it. A multi-line field's floor
                // is four text rows, matching the export's `rows="4"` body textarea.
                .heightIn(min = if (singleLine) FIELD_HEIGHT else MULTILINE_FIELD_HEIGHT)
                .background(scheme.background, AnfasShapes.base)
                .border(1.dp, borderColor, AnfasShapes.base)
                .padding(horizontal = 12.dp, vertical = if (singleLine) 0.dp else 12.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    interactionSource = interaction,
                    textStyle = AnfasTheme.textStyles.bodyMedium.copy(color = scheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.primary),
                    visualTransformation = if (isPassword) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction,
                        // Off for passwords and usernames alike: a capitalised first letter is
                        // wrong for both, and a username is normalised anyway.
                        autoCorrectEnabled = false,
                    ),
                    // Null, not a lambda that might do nothing. KeyboardActions falls back to the
                    // platform default only for the handlers left null, and a supplied handler
                    // replaces that default even when its body is empty -- so
                    // `onNext = { onImeAction?.invoke() }` killed the keyboard's Next key on every
                    // field that does not submit, which is all of them but the last. Found by
                    // pressing Next on a device and watching focus stay put.
                    keyboardActions = KeyboardActions(
                        onDone = imeActionHandler,
                        onGo = imeActionHandler,
                        onNext = imeActionHandler,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            trailing?.invoke()
        }
        errorMessage?.let {
            Text(
                text = it,
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.error,
            )
        }
    }
}

private val FIELD_HEIGHT = 46.dp
private val MULTILINE_FIELD_HEIGHT = 112.dp
