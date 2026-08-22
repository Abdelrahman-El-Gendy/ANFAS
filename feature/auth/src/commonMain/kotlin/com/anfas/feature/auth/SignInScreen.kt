package com.anfas.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // imePadding so the submit button is not left under the keyboard on a phone;
                // verticalScroll so a short landscape window can still reach it.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .widthIn(max = FORM_MAX_WIDTH),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.mode) {
                // Deliberately blank rather than a spinner: deciding between sign-in and setup is
                // a single indexed COUNT(*), so a spinner would flash for one frame and read as
                // jank rather than progress.
                SignInMode.Checking -> Spacer(Modifier.size(0.dp))

                SignInMode.SignIn -> Form(state, component, s, isSetup = false)

                SignInMode.FirstRun -> Form(state, component, s, isSetup = true)
            }
        }
    }
}

@Composable
private fun Form(state: SignInState, component: SignInComponent, s: AppStrings, isSetup: Boolean) {
    val scheme = MaterialTheme.colorScheme

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
