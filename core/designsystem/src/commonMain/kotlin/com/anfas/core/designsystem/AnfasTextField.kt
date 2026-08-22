package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = AnfasTheme.textStyles.labelCaps,
            color = if (errorMessage != null) scheme.error else scheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // heightIn rather than height: a larger system font size must be able to grow
                // the field instead of clipping the text inside it.
                .heightIn(min = FIELD_HEIGHT)
                .background(scheme.background, AnfasShapes.base)
                .border(1.dp, borderColor, AnfasShapes.base)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
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
                    keyboardActions = KeyboardActions(
                        onDone = { onImeAction?.invoke() },
                        onGo = { onImeAction?.invoke() },
                        onNext = { onImeAction?.invoke() },
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
