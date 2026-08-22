package com.anfas.feature.members

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.MemberRepository
import com.anfas.core.data.SubscriptionRepository
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.SubscriptionTerm
import com.anfas.core.model.TermProgress
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * One member, in detail.
 *
 * Combines the member row with their current subscription term rather than having the screen
 * observe two things: the two have to agree, and a profile that renders an Active chip beside an
 * expired term is worse than one that waits a frame longer.
 *
 * [TermProgress] is recomputed on every emission from *today*, not stored. A profile left open
 * overnight would otherwise keep claiming a membership expires tomorrow.
 */
class MemberProfileComponent(
    componentContext: ComponentContext,
    private val memberId: MemberId,
    members: MemberRepository,
    subscriptions: SubscriptionRepository,
    dispatchers: AppDispatchers,
    private val onRenewClicked: (MemberId) -> Unit,
    private val onBackClicked: () -> Unit,
) : ComponentContext by componentContext {

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("MemberProfile"))

    val state: StateFlow<MemberProfileState> = combine(
        members.observeMember(memberId),
        subscriptions.observeCurrentTerm(memberId),
    ) { memberResult, termResult ->
        MemberProfileState(content = contentOf(memberResult, termResult))
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = MemberProfileState(),
    )

    fun onRenew() = onRenewClicked(memberId)

    fun onBack() = onBackClicked()

    private fun contentOf(
        memberResult: AppResult<Member?>,
        termResult: AppResult<SubscriptionTerm?>,
    ): MemberProfileContent {
        // A member that failed to load is fatal to the screen. A *term* that failed is not: the
        // identity half is still worth showing, and renewal is still the right next action, so
        // the term is treated as absent rather than blanking the profile.
        val member = when (memberResult) {
            is AppResult.Failure -> return MemberProfileContent.Failed(memberResult.error.message)
            is AppResult.Success -> memberResult.value ?: return MemberProfileContent.Missing
        }
        val term = (termResult as? AppResult.Success)?.value

        return MemberProfileContent.Loaded(
            member = member,
            term = term,
            progress = term?.let {
                TermProgress.of(it, Clock.System.todayIn(TimeZone.currentSystemDefault()))
            },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
