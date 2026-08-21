package com.anfas.feature.members

import com.anfas.core.common.AppDispatchers
import com.anfas.core.data.MemberRepository
import com.anfas.core.model.MemberId
import com.arkivanov.decompose.ComponentContext

/**
 * Lets :composeApp create a [MembersListComponent] without knowing what it depends on.
 *
 * Without this, the navigation root would need [MemberRepository] and [AppDispatchers] in
 * scope just to construct a child, which would drag every feature's dependencies into the app
 * shell. The router supplies only what is genuinely its business: the context and where the
 * navigation callbacks go.
 */
class MembersListComponentFactory internal constructor(
    private val repository: MemberRepository,
    private val dispatchers: AppDispatchers,
) {
    fun create(
        componentContext: ComponentContext,
        onMemberClicked: (MemberId) -> Unit,
        onAddMemberClicked: () -> Unit,
        onScanSheetClicked: () -> Unit,
    ): MembersListComponent = MembersListComponent(
        componentContext = componentContext,
        repository = repository,
        dispatchers = dispatchers,
        onMemberClicked = onMemberClicked,
        onAddMemberClicked = onAddMemberClicked,
        onScanSheetClicked = onScanSheetClicked,
    )
}
