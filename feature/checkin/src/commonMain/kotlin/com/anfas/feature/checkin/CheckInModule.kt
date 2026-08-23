package com.anfas.feature.checkin

import com.anfas.core.common.AppDispatchers
import com.anfas.core.data.CheckInRepository
import com.anfas.core.data.MemberRepository
import com.anfas.core.model.MemberId
import com.arkivanov.decompose.ComponentContext
import org.koin.core.module.Module
import org.koin.dsl.module

class CheckInComponentFactory internal constructor(
    private val members: MemberRepository,
    private val checkIns: CheckInRepository,
    private val dispatchers: AppDispatchers,
) {
    fun create(
        componentContext: ComponentContext,
        onMemberClicked: (MemberId) -> Unit,
    ): CheckInComponent = CheckInComponent(
        componentContext = componentContext,
        members = members,
        checkIns = checkIns,
        dispatchers = dispatchers,
        onMemberClicked = onMemberClicked,
    )
}

val CheckInModule: Module = module {
    factory {
        CheckInComponentFactory(members = get(), checkIns = get(), dispatchers = get())
    }
}
