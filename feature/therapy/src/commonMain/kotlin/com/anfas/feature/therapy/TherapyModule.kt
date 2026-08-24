package com.anfas.feature.therapy

import com.anfas.core.common.AppDispatchers
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.MemberRepository
import com.anfas.core.data.TherapyRepository
import com.anfas.core.model.MemberId
import com.arkivanov.decompose.ComponentContext
import org.koin.core.module.Module
import org.koin.dsl.module

class TherapyComponentFactory internal constructor(
    private val members: MemberRepository,
    private val therapy: TherapyRepository,
    private val auth: AuthRepository,
    private val dispatchers: AppDispatchers,
) {
    fun create(
        componentContext: ComponentContext,
        memberId: MemberId,
        onBackClicked: () -> Unit,
    ): TherapyComponent = TherapyComponent(
        componentContext = componentContext,
        memberId = memberId,
        members = members,
        therapy = therapy,
        auth = auth,
        dispatchers = dispatchers,
        onBackClicked = onBackClicked,
    )
}

/**
 * Koin module for the therapy feature. Factories only — the component owns a coroutine scope
 * tied to its Decompose lifecycle and must never be a singleton.
 */
val TherapyModule: Module = module {
    factory {
        TherapyComponentFactory(members = get(), therapy = get(), auth = get(), dispatchers = get())
    }
}
