package com.anfas.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anfas.core.auth.CredentialProblem
import com.anfas.core.designsystem.AnfasCallout
import com.anfas.core.designsystem.AnfasIconButton
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasTextField
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.strings

/**
 * Staff sign-in, and first-run owner setup.
 *
 * Departures from the export's `staff-login`, each because the thing behind it does not exist:
 *  - **No "Forgot password?".** It needs an email or SMS round trip and there is no server. The
 *    offline equivalent is an owner resetting a staff password from their own account, which
 *    belongs with staff management rather than here.
 *  - **No "Remember me" checkbox.** The session is persisted either way — there is no token to
 *    expire and no server to re-authenticate against — so a checkbox that changes nothing would
 *    be a lie. Sign-out is the way to end a session, and it is in the app chrome.
 *  - **The language toggle is the shell's**, already above this screen, rather than drawn again.
 */
@Composable
fun SignInScreen(component: SignInComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    val s = strings

    BoxWithConstraints(
        // imePadding belongs on the container, not on the centred child. As a modifier on the
        // Column it made the *child* taller by the keyboard's height, and a Box centring that
        // child then split the difference: content rose by only half the keyboard, and the
        // Column's scroll viewport still extended behind it, so a focused field could be
        // scrolled "into view" and remain two-thirds covered. Insetting the container shrinks
        // the space the child is centred in, which is what was meant all along.
        //
        // BoxWithConstraints rather than Box so [maxHeight] is the height that is actually left
        // once the keyboard has taken its share, which is what decides whether the heading fits.
        modifier = modifier.fillMaxSize().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        // Measured against this container, never against the window: by the time the form is laid
        // out it has already lost the status bar, the shell's 56dp top bar and the keyboard, and
        // on a landscape phone what remains is about 80dp -- barely one field. A window-sized
        // breakpoint would call that "a phone in landscape" and keep the heading.
        val short = maxHeight < SHORT_VIEWPORT
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // verticalScroll so a window too short for the form -- a phone in landscape with
                // the keyboard up is the real case -- can still reach the submit button.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = if (short) 12.dp else 32.dp)
                .widthIn(max = FORM_MAX_WIDTH),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (short) 8.dp else 16.dp),
        ) {
            when (state.mode) {
                // Deliberately blank rather than a spinner: deciding between sign-in and setup is
                // a single indexed COUNT(*), so a spinner would flash for one frame and read as
                // jank rather than progress.
                SignInMode.Checking -> Spacer(Modifier.size(0.dp))

                SignInMode.SignIn -> Form(state, component, s, isSetup = false, short = short)

                SignInMode.FirstRun -> Form(state, component, s, isSetup = true, short = short)
            }
        }
    }
}

@Composable
private fun Form(
    state: SignInState,
    component: SignInComponent,
    s: AppStrings,
    isSetup: Boolean,
    short: Boolean,
) {
    val scheme = MaterialTheme.colorScheme

    // The heading is dropped, not shrunk, when there is no room for it. A landscape phone with the
    // keyboard open leaves roughly one field's worth of height, and spending it on a title and a
    // tagline is what pushed the fields themselves off the bottom. Nothing is lost that the user
    // needs in order to type: they arrived here deliberately, the fields are labelled, and the
    // submit button says which of the two things this form does.
    if (!short) {
        Text(
            text = if (isSetup) s.auth.setupTitle else s.auth.signInTitle,
            style = AnfasTheme.textStyles.headlineLarge,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (isSetup) s.auth.setupMessage else s.auth.signInTagline,
            style = AnfasTheme.textStyles.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.size(8.dp))
    }

    state.error?.let { error ->
        AnfasCallout(
            title = when (error) {
                SignInError.InvalidCredentials -> s.auth.invalidCredentials
                SignInError.AccountDisabled -> s.auth.accountDisabled
                is SignInError.Unexpected -> error.message
            },
            // No second line: the title carries the whole message, and there is no remedy to
            // offer beyond retyping.
        )
    }

    if (isSetup) {
        AnfasTextField(
            value = state.displayName,
            onValueChange = component::onDisplayNameChanged,
            label = s.auth.displayName,
            enabled = !state.isSubmitting,
            errorMessage = s.auth.problemDisplayNameBlank
                .takeIf { state.problem(CredentialProblem.DisplayNameBlank) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    AnfasTextField(
        value = state.username,
        onValueChange = component::onUsernameChanged,
        label = s.auth.username,
        enabled = !state.isSubmitting,
        errorMessage = when {
            state.problem(CredentialProblem.UsernameTaken) -> s.auth.problemUsernameTaken
            state.problem(CredentialProblem.UsernameTooShort) -> s.auth.problemUsernameTooShort
            else -> null
        },
        modifier = Modifier.fillMaxWidth(),
    )

    AnfasTextField(
        value = state.password,
        onValueChange = component::onPasswordChanged,
        label = s.auth.password,
        enabled = !state.isSubmitting,
        isPassword = !state.passwordVisible,
        errorMessage = s.auth.problemPasswordTooShort
            .takeIf { state.problem(CredentialProblem.PasswordTooShort) },
        // Go, not Done: the keyboard's action key submits, which is how anyone signs in without
        // reaching for the button.
        imeAction = ImeAction.Go,
        onImeAction = component::onSubmit,
        trailing = {
            AnfasIconButton(
                icon = if (state.passwordVisible) {
                    AnfasIcons.VisibilityOff
                } else {
                    AnfasIcons.Visibility
                },
                contentDescription = if (state.passwordVisible) {
                    s.auth.hidePassword
                } else {
                    s.auth.showPassword
                },
                onClick = component::onTogglePasswordVisible,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.size(8.dp))

    AnfasPrimaryButton(
        text = when {
            state.isSubmitting && isSetup -> s.auth.creating
            state.isSubmitting -> s.auth.signingIn
            isSetup -> s.auth.createOwner
            else -> s.auth.signIn
        },
        onClick = component::onSubmit,
        enabled = state.canSubmit,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Wider than this and the form's fields become uncomfortably long lines on a desktop window. */
private val FORM_MAX_WIDTH = 420.dp

/**
 * Below this much height the heading is dropped so the fields get the space.
 *
 * Sized from the content rather than picked: the three-field setup form needs about 250dp, and the
 * heading plus its spacing is another 150dp. Anything under 400dp cannot show both, and a landscape
 * phone with the keyboard up offers roughly 80dp.
 */
private val SHORT_VIEWPORT = 400.dp
