package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The design's circular avatar, rendered as initials.
 *
 * The export fills every one of these with a generated photograph, and we have no photographs:
 * nothing in the app uploads one, for staff or for members. Initials are what the data actually
 * supports, and a grey silhouette repeated down a table tells you nothing about which row is
 * which — initials at least distinguish them.
 *
 * Takes [initials] rather than any domain type, so `:core:designsystem` stays domain-free and the
 * same circle serves staff and members.
 */
@Composable
fun AnfasAvatar(initials: String, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            // A larger circle needs a larger glyph, or a 64dp avatar reads as an empty ring.
            style = if (size >= LARGE_AVATAR) {
                AnfasTheme.textStyles.headlineSmall
            } else {
                AnfasTheme.textStyles.labelCaps
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * First letters of the first two words, uppercased.
 *
 * Empty for a blank name rather than a placeholder glyph: an avatar showing "?" claims we tried
 * and failed, when in fact there is simply no name yet.
 */
fun initialsOf(fullName: String): String = fullName.trim().split(' ')
    .filter { it.isNotBlank() }
    .take(2)
    .map { it.first().uppercaseChar() }
    .joinToString("")

private val LARGE_AVATAR = 48.dp
