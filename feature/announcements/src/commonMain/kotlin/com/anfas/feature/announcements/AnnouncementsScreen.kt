package com.anfas.feature.announcements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anfas.core.data.AnnouncementDetail
import com.anfas.core.designsystem.AnfasBanner
import com.anfas.core.designsystem.AnfasCard
import com.anfas.core.designsystem.AnfasEmptyState
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasScreenHeader
import com.anfas.core.designsystem.AnfasStatusChip
import com.anfas.core.designsystem.AnfasTableDivider
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.BannerTone
import com.anfas.core.designsystem.ChipTone
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.formatLong
import com.anfas.core.i18n.strings
import com.anfas.core.model.Announcement
import com.anfas.core.model.AnnouncementAudience
import com.anfas.core.model.AnnouncementStatus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Staff bulletins — the export's `create-announcement`, scoped down to what this app can
 * actually honour. See `Announcement`'s KDoc in `:core:model`: publishing records a bulletin and
 * freezes a real audience count against it; nothing is delivered to anyone.
 *
 * Departures from the export, all because the capability does not exist rather than because the
 * design was ignored:
 *  - **No rich text, no featured image.** Every other body of text in this app — reminder
 *    templates, therapy notes, class descriptions — is plain text, and there is no general
 *    attachment storage (`:core:ocr`'s capture is narrow and single-purpose).
 *  - **No English/Arabic authoring tabs.** Nothing else in this app stores a record twice, once
 *    per language — a member's name, a class name, are each typed once, in whatever the author
 *    writes. The app-wide language toggle already covers the *chrome* around this screen.
 *  - **No live phone-frame preview.** The preview renders what a member's own app would show,
 *    and there is no member-facing app for it to preview.
 *  - **Two of five audience segments, dropped.** "Women's classes" has no real recipient list —
 *    `:feature:classes` has no per-member enrolment at all — and "Therapy patients" would fold
 *    clinical case status into a marketing target, which nothing else in this app does.
 *  - **No Push Notification, no WhatsApp Broadcast channel toggles.** Neither exists; there is
 *    no member-facing app, no push infrastructure, and the WhatsApp job is separately parked.
 *  - **No "Schedule for later."** There is no background task runner on any platform. Publish
 *    now, or leave it a draft until you come back to it — that covers the real workflow without
 *    inventing scheduling infrastructure.
 *
 * A dedicated list screen rather than the export's split editor-plus-preview layout, matching
 * how Classes and Therapy both use a list-plus-`AnfasDialog` shape rather than a full-page editor
 * for composing one record.
 */
@Composable
fun AnnouncementsScreen(component: AnnouncementsComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    val s = strings

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AnfasTheme.spacing.marginMobile)
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnfasScreenHeader(
            title = s.announcements.title,
            subtitle = s.announcements.subtitle,
            actions = if (state.mayManage) {
                {
                    AnfasPrimaryButton(
                        text = s.announcements.newAnnouncement,
                        icon = AnfasIcons.Add,
                        onClick = component::onNewAnnouncement,
                    )
                }
            } else {
                null
            },
        )

        state.notice?.let { notice ->
            val isFailure = notice is AnnouncementsNotice.Failed
            AnfasBanner(
                message = notice.render(s),
                icon = if (isFailure) AnfasIcons.ErrorOutline else AnfasIcons.Check,
                tone = if (isFailure) BannerTone.Critical else BannerTone.Informational,
                dismissLabel = s.common.dismiss,
                onDismiss = component::onNoticeShown,
            )
        }

        when (val content = state.content) {
            AnnouncementsContent.Loading -> Box(Modifier.fillMaxSize())

            is AnnouncementsContent.Failed -> AnfasEmptyState(
                icon = AnfasIcons.ErrorOutline,
                title = s.announcements.loadFailedTitle,
                message = content.message,
            )

            AnnouncementsContent.Empty -> AnfasEmptyState(
                icon = AnfasIcons.Campaign,
                title = s.announcements.emptyTitle,
                message = s.announcements.emptyMessage,
            )

            is AnnouncementsContent.Loaded -> AnfasCard(
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(
                        items = content.announcements,
                        key = { it.announcement.id.value },
                    ) { detail ->
                        AnnouncementRow(
                            detail = detail,
                            mayManage = state.mayManage,
                            onClick = { component.onEditAnnouncement(detail.announcement.id) },
                            onDelete = { component.onRequestDelete(detail.announcement.id) },
                            s = s,
                        )
                        AnfasTableDivider()
                    }
                }
            }
        }
    }

    state.form?.let { form ->
        AnnouncementFormDialog(form = form, state = state, component = component, s = s)
    }
    state.publishConfirm?.let { form ->
        PublishConfirmDialog(form = form, reach = state.liveReach, component = component, s = s)
    }
    state.deleteConfirmId?.let {
        DeleteConfirmDialog(component = component, s = s)
    }
}

@Composable
private fun AnnouncementRow(
    detail: AnnouncementDetail,
    mayManage: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    s: AppStrings,
) {
    val scheme = MaterialTheme.colorScheme
    val announcement = detail.announcement
    val zone = TimeZone.currentSystemDefault()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (mayManage) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = announcement.title,
                    style = AnfasTheme.textStyles.bodyLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(modifier = Modifier.padding(start = 8.dp)) {
                    val published = announcement.status == AnnouncementStatus.PUBLISHED
                    AnfasStatusChip(
                        label = if (published) {
                            s.announcements.statusPublished
                        } else {
                            s.announcements.statusDraft
                        },
                        tone = if (published) ChipTone.Positive else ChipTone.Neutral,
                    )
                }
            }
            Text(
                text = announcement.body,
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildList {
                    add(
                        detail.createdByName?.let { s.announcements.createdBy(it) }
                            ?: s.announcements.createdByUnknown,
                    )
                    add(
                        s.announcements.createdOn(
                            s.formatLong(announcement.createdAt.toLocalDateTime(zone).date),
                        ),
                    )
                    announcement.recipientCountAtPublish?.let {
                        add(s.announcements.reachedAtPublish(it))
                    }
                }.joinToString(" · "),
                style = AnfasTheme.textStyles.labelCaps,
                color = scheme.onSurfaceVariant,
            )
        }
        if (mayManage && announcement.isDraft) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDelete)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = AnfasIcons.Close,
                    contentDescription = s.announcements.deleteDraft,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

internal fun AnnouncementsNotice.render(s: AppStrings): String = when (this) {
    AnnouncementsNotice.DraftSaved -> s.announcements.draftSaved
    AnnouncementsNotice.Published -> s.announcements.published
    AnnouncementsNotice.Deleted -> s.announcements.deleted
    is AnnouncementsNotice.Failed -> message
}

internal fun AnnouncementAudience.label(s: AppStrings): String = when (this) {
    AnnouncementAudience.ALL_MEMBERS -> s.announcements.audienceAll
    AnnouncementAudience.ACTIVE_ONLY -> s.announcements.audienceActive
    AnnouncementAudience.EXPIRING_THIS_MONTH -> s.announcements.audienceExpiring
}
