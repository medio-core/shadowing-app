package com.shadowing.app.ui

import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shadowing.app.R
import com.shadowing.app.data.AppSettings
import com.shadowing.app.viewmodel.ShadowingViewModel

/**
 * 設定画面内のサブページ種別。
 */
private enum class SettingsSubPage {
    Main,
    PrivacyPolicy,
    TermsOfService,
}

/**
 * アプリ設定画面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    settingsMessage: String?,
    onSettingsMessageShown: () -> Unit,
    onBack: () -> Unit,
    onRestoreSessionChanged: (Boolean) -> Unit,
    onDefaultSpeedSelected: (Float) -> Unit,
    onDefaultSeekSliderSideChanged: (Boolean) -> Unit,
    onShowGuideAgain: () -> Unit,
    privacyOptionsRequired: Boolean,
    onOpenPrivacyOptions: () -> Unit,
    onDebugHideAds: () -> Unit,
    onDebugShowAds: () -> Unit,
) {
    val context = LocalContext.current
    val appName = context.getString(R.string.app_name)
    val privacyPolicyLastUpdated =
        context.getString(R.string.privacy_policy_last_updated)
    val termsOfServiceLastUpdated =
        context.getString(R.string.terms_of_service_last_updated)
    val contactEmail = context.getString(R.string.legal_contact_email)
    var subPage by remember { mutableStateOf(SettingsSubPage.Main) }
    val currentOnBack by rememberUpdatedState(onBack)

    val navigateBack = {
        when (subPage) {
            SettingsSubPage.Main -> currentOnBack()
            SettingsSubPage.PrivacyPolicy,
            SettingsSubPage.TermsOfService,
            -> subPage = SettingsSubPage.Main
        }
    }

    BackHandler(onBack = navigateBack)

    val isDebugBuild =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(settingsMessage) {
        val message = settingsMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onSettingsMessageShown()
    }

    val topBarTitle = when (subPage) {
        SettingsSubPage.Main -> "設定"
        SettingsSubPage.PrivacyPolicy -> "プライバシーポリシー"
        SettingsSubPage.TermsOfService -> "利用規約"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(topBarTitle) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る",
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (subPage) {
            SettingsSubPage.PrivacyPolicy -> {
                LegalDocumentBody(
                    body = buildPrivacyPolicyText(
                        appName = appName,
                        lastUpdated = privacyPolicyLastUpdated,
                        contactEmail = contactEmail,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
            SettingsSubPage.TermsOfService -> {
                LegalDocumentBody(
                    body = buildTermsOfServiceText(
                        appName = appName,
                        lastUpdated = termsOfServiceLastUpdated,
                        contactEmail = contactEmail,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
            SettingsSubPage.Main -> {
                SettingsMainContent(
                    settings = settings,
                    isDebugBuild = isDebugBuild,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onRestoreSessionChanged = onRestoreSessionChanged,
                    onDefaultSpeedSelected = onDefaultSpeedSelected,
                    onDefaultSeekSliderSideChanged =
                        onDefaultSeekSliderSideChanged,
                    onShowGuideAgain = onShowGuideAgain,
                    privacyOptionsRequired = privacyOptionsRequired,
                    onOpenPrivacyOptions = onOpenPrivacyOptions,
                    onDebugHideAds = onDebugHideAds,
                    onDebugShowAds = onDebugShowAds,
                    onOpenPrivacyPolicy = {
                        subPage = SettingsSubPage.PrivacyPolicy
                    },
                    onOpenTermsOfService = {
                        subPage = SettingsSubPage.TermsOfService
                    },
                )
            }
        }
    }
}

/**
 * 設定画面のメインコンテンツを表示する。
 */
@Composable
private fun SettingsMainContent(
    settings: AppSettings,
    isDebugBuild: Boolean,
    modifier: Modifier = Modifier,
    onRestoreSessionChanged: (Boolean) -> Unit,
    onDefaultSpeedSelected: (Float) -> Unit,
    onDefaultSeekSliderSideChanged: (Boolean) -> Unit,
    onShowGuideAgain: () -> Unit,
    privacyOptionsRequired: Boolean,
    onOpenPrivacyOptions: () -> Unit,
    onDebugHideAds: () -> Unit,
    onDebugShowAds: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTermsOfService: () -> Unit,
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingSwitchRow(
            title = "起動時に前回セッションを復元",
            checked = settings.restoreSessionOnLaunch,
            onCheckedChange = onRestoreSessionChanged,
        )

        PlaybackSpeedDropdown(
            label = "初期再生速度",
            selectedSpeed = settings.defaultPlaybackSpeed,
            options = ShadowingViewModel.PLAYBACK_SPEED_OPTIONS,
            onSpeedSelected = onDefaultSpeedSelected,
        )

        SeekSliderSideDropdown(
            label = "初期スライダー位置",
            onRight = settings.defaultSeekSliderOnRight,
            onSideSelected = onDefaultSeekSliderSideChanged,
        )

        TextButton(onClick = onShowGuideAgain) {
            Text("使い方ガイドを再表示")
        }

        if (isDebugBuild) {
            Text(
                text = "開発者向け",
                style = MaterialTheme.typography.titleSmall,
            )
            if (settings.adsRemoved) {
                Text(
                    text = "広告は非表示です（開発用設定）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onDebugShowAds) {
                    Text("開発用: 広告を再表示")
                }
            } else {
                TextButton(onClick = onDebugHideAds) {
                    Text("開発用: 広告を非表示")
                }
            }
        }

        Text(
            text = "法的情報",
            style = MaterialTheme.typography.titleSmall,
        )
        TextButton(onClick = onOpenPrivacyPolicy) {
            Text("プライバシーポリシー")
        }
        TextButton(onClick = onOpenTermsOfService) {
            Text("利用規約")
        }

        // UMP が同意を収集した地域では、再設定導線の常設が必須。
        if (privacyOptionsRequired) {
            TextButton(onClick = onOpenPrivacyOptions) {
                Text("広告のプライバシー設定")
            }
        }
    }
}

/**
 * 法的情報本文をスクロール表示する。
 */
@Composable
private fun LegalDocumentBody(
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 設定用のスイッチ行を表示する。
 */
@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
