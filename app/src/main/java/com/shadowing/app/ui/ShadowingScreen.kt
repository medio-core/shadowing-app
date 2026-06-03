package com.shadowing.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.shadowing.app.model.InternalSubtitleTrack
import com.shadowing.app.model.JapaneseMatchQuality
import com.shadowing.app.model.JapaneseMatchResult
import com.shadowing.app.model.SubtitleCue
import com.shadowing.app.model.SubtitleMatcher
import com.shadowing.app.model.formatCueTimeMs
import com.shadowing.app.model.formatOffsetSec
import com.shadowing.app.model.withOffset
import com.shadowing.app.viewmodel.ShadowingUiState
import com.shadowing.app.viewmodel.ShadowingViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private const val PREVIEW_SEEK_INTERVAL_MS = 250L
private const val SNAP_WATCH_INTERVAL_MS = 120L
private const val SNAP_RECHECK_DELAY_MS = 40L
private const val SNAP_RETRY_COUNT = 2
private const val SNAP_TOLERANCE_PX = 2
private val SUBTITLE_ITEM_HEIGHT = 112.dp
private const val SUBTITLE_BODY_MAX_FONT_SP = 22f
private const val SUBTITLE_BODY_MIN_FONT_SP = 8f
private const val FOOTER_DRAG_THRESHOLD_PX = 40f
private val TIMING_HANDLE_HEIGHT = 40.dp
private const val TOP_BUTTON_HEIGHT_RATIO = 0.045f
private const val TOP_BUTTON_MIN_FONT_SP = 8f
private const val TOP_BUTTON_MAX_FONT_SP = 18f
private const val VIDEO_HEIGHT_RATIO_WITH_AD = 0.28f
private const val VIDEO_HEIGHT_RATIO_NO_AD = 0.30f
private const val CONTROL_HEIGHT_RATIO = 0.11f
private val NO_AD_TOP_PADDING = 4.dp

/**
 * シャドーイング練習のメイン画面。
 */
@Composable
fun ShadowingScreen(
    viewModel: ShadowingViewModel,
    onPickVideo: () -> Unit,
    onPickJapaneseSrt: () -> Unit,
    onPickEnglishSrt: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val showAds = !appSettings.adsRemoved
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val adWidthDp = maxWidth.value.toInt().coerceAtLeast(1)
            val adSize = rememberAdaptiveTopBannerAdSize(adWidthDp)
            val adHeight = if (showAds) adSize.toHeightDp() else 0.dp
            val adEquivalentHeight = if (showAds) {
                0.dp
            } else {
                adSize.toHeightDp() + maxHeight * 0.01f
            }

            var showTimingPanel by remember { mutableStateOf(false) }

            val topMarginHeight = if (showAds) {
                maxHeight * 0.01f
            } else {
                NO_AD_TOP_PADDING
            }
            val topButtonHeight = maxHeight * TOP_BUTTON_HEIGHT_RATIO
            val chromeGap = if (showAds) maxHeight * 0.01f else 0.dp
            val practiceChromeHeight = adHeight + topMarginHeight + topButtonHeight +
                chromeGap + TIMING_HANDLE_HEIGHT
            val targetTopChromeHeight = if (showTimingPanel && !showAds) {
                practiceChromeHeight + adEquivalentHeight
            } else {
                practiceChromeHeight
            }
            val topChromeHeight by animateDpAsState(
                targetValue = targetTopChromeHeight,
                animationSpec = tween(280),
                label = "topChromeHeight",
            )
            val mainContentHeight = (maxHeight - topChromeHeight).coerceAtLeast(0.dp)
            val sectionPadding = 8.dp
            val mainSectionGap = mainContentHeight * 0.01f
            val subtitlePadding = 4.dp
            val videoHeightRatio = if (showAds) {
                VIDEO_HEIGHT_RATIO_WITH_AD
            } else {
                VIDEO_HEIGHT_RATIO_NO_AD
            }

            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                        targetState = showTimingPanel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topChromeHeight),
                        transitionSpec = {
                            if (targetState) {
                                slideInVertically(
                                    initialOffsetY = { height -> -height },
                                    animationSpec = tween(280),
                                ) togetherWith slideOutVertically(
                                    targetOffsetY = { height -> -height },
                                    animationSpec = tween(280),
                                )
                            } else {
                                slideInVertically(
                                    initialOffsetY = { height -> height },
                                    animationSpec = tween(280),
                                ) togetherWith slideOutVertically(
                                    targetOffsetY = { height -> height },
                                    animationSpec = tween(280),
                                )
                            }
                        },
                        label = "topChrome",
                    ) { timingOpen ->
                        if (timingOpen) {
                            TopTimingChrome(
                                uiState = uiState,
                                sectionPadding = sectionPadding,
                                onToggleTiming = { showTimingPanel = false },
                                onVerticalDragEnd = { dragPx ->
                                    if (dragPx < -FOOTER_DRAG_THRESHOLD_PX) {
                                        showTimingPanel = false
                                    }
                                },
                                onNudgeEnglish = viewModel::nudgeEnglishOffset,
                                onNudgeJapanese = viewModel::nudgeJapaneseOffset,
                                onResetEnglish = viewModel::resetEnglishOffset,
                                onResetJapanese = viewModel::resetJapaneseOffset,
                            )
                        } else {
                            TopPracticeChrome(
                                showAds = showAds,
                                adSize = adSize,
                                adHeight = adHeight,
                                topMarginHeight = topMarginHeight,
                                topButtonHeight = topButtonHeight,
                                sectionPadding = sectionPadding,
                                uiState = uiState,
                                onPickVideo = onPickVideo,
                                onPickJapaneseSrt = onPickJapaneseSrt,
                                onPickEnglishSrt = onPickEnglishSrt,
                                onOpenSettings = onOpenSettings,
                                onToggleTiming = { showTimingPanel = true },
                                onVerticalDragEnd = { dragPx ->
                                    if (dragPx > FOOTER_DRAG_THRESHOLD_PX) {
                                        showTimingPanel = true
                                    }
                                },
                                viewModel = viewModel,
                            )
                        }
                    }

                ShadowingMainSections(
                    uiState = uiState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    videoHeight = mainContentHeight * videoHeightRatio,
                    controlHeight = mainContentHeight * CONTROL_HEIGHT_RATIO,
                    mainSectionGap = mainSectionGap,
                    sectionPadding = sectionPadding,
                    subtitlePadding = subtitlePadding,
                    viewModel = viewModel,
                )
            }
        }
    }
}

/**
 * 広告・ファイル読み込み・ハンドル（通常時の上部エリア）。
 */
@Composable
private fun TopPracticeChrome(
    showAds: Boolean,
    adSize: com.google.android.gms.ads.AdSize,
    adHeight: Dp,
    topMarginHeight: Dp,
    topButtonHeight: Dp,
    sectionPadding: Dp,
    uiState: ShadowingUiState,
    onPickVideo: () -> Unit,
    onPickJapaneseSrt: () -> Unit,
    onPickEnglishSrt: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTiming: () -> Unit,
    onVerticalDragEnd: (Float) -> Unit,
    viewModel: ShadowingViewModel,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = if (showAds) {
            Arrangement.Top
        } else {
            Arrangement.SpaceBetween
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showAds) {
                TopAdBanner(
                    adSize = adSize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(adHeight),
                )
            }
            Spacer(modifier = Modifier.height(topMarginHeight))
            PracticeFileButtonRow(
                uiState = uiState,
                topButtonHeight = topButtonHeight,
                sectionPadding = sectionPadding,
                onPickVideo = onPickVideo,
                onPickJapaneseSrt = onPickJapaneseSrt,
                onPickEnglishSrt = onPickEnglishSrt,
                onOpenSettings = onOpenSettings,
                viewModel = viewModel,
            )
        }
        TimingPanelHandle(
            expanded = false,
            sectionPadding = sectionPadding,
            onToggle = onToggleTiming,
            onVerticalDragEnd = onVerticalDragEnd,
        )
    }
}

/**
 * タイミング調整（広告＋ボタン相当の上部エリアのみ使用）。
 */
@Composable
private fun TopTimingChrome(
    uiState: ShadowingUiState,
    sectionPadding: Dp,
    onToggleTiming: () -> Unit,
    onVerticalDragEnd: (Float) -> Unit,
    onNudgeEnglish: (Long) -> Unit,
    onNudgeJapanese: (Long) -> Unit,
    onResetEnglish: () -> Unit,
    onResetJapanese: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalArrangement = Arrangement.Top,
    ) {
        TimingAdjustSection(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(
                    horizontal = sectionPadding,
                    vertical = 2.dp,
                ),
            englishOffsetMs = uiState.englishOffsetMs,
            japaneseOffsetMs = uiState.japaneseOffsetMs,
            englishLoaded = uiState.englishCues.isNotEmpty(),
            japaneseLoaded = uiState.japaneseCues.isNotEmpty(),
            onNudgeEnglish = onNudgeEnglish,
            onNudgeJapanese = onNudgeJapanese,
            onResetEnglish = onResetEnglish,
            onResetJapanese = onResetJapanese,
        )
        TimingPanelHandle(
            expanded = true,
            sectionPadding = sectionPadding,
            onToggle = onToggleTiming,
            onVerticalDragEnd = onVerticalDragEnd,
        )
    }
}

/**
 * ファイル読み込み行と設定ボタン。
 */
@Composable
private fun PracticeFileButtonRow(
    uiState: ShadowingUiState,
    topButtonHeight: Dp,
    sectionPadding: Dp,
    onPickVideo: () -> Unit,
    onPickJapaneseSrt: () -> Unit,
    onPickEnglishSrt: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ShadowingViewModel,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = sectionPadding)
            .height(topButtonHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileLoadRow(
            uiState = uiState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onPickVideo = onPickVideo,
            onPickJapaneseSrt = onPickJapaneseSrt,
            onPickEnglishSrt = onPickEnglishSrt,
            onUseInternalJapanese = viewModel::loadInternalJapaneseSubtitle,
            onUseInternalEnglish = viewModel::loadInternalEnglishSubtitle,
        )
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxHeight(),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "設定",
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * 動画・字幕・操作（上部エリアの下に固定表示）。
 */
@Composable
private fun ShadowingMainSections(
    uiState: ShadowingUiState,
    modifier: Modifier = Modifier,
    videoHeight: Dp,
    controlHeight: Dp,
    mainSectionGap: Dp,
    sectionPadding: Dp,
    subtitlePadding: Dp,
    viewModel: ShadowingViewModel,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
    ) {
        VideoPlayerSection(
            videoUri = uiState.videoUri,
            seekStartMs = uiState.seekStartMs,
            segmentEndMs = uiState.segmentEndMs,
            isPlayingSegment = uiState.isPlayingSegment,
            isPlayingVideo = uiState.isPlayingVideo,
            playbackSpeed = uiState.playbackSpeed,
            seekRequestId = uiState.segmentSeekRequestId,
            playRequestId = uiState.videoPlayRequestId,
            videoSeekRequestId = uiState.videoSeekRequestId,
            videoSeekPositionMs = uiState.videoSeekPositionMs,
            sessionRestoreSeekMs = uiState.sessionRestoreSeekMs,
            preferredTextLanguage = uiState.preferredTextLanguage,
            modifier = Modifier
                .fillMaxWidth()
                .height(videoHeight),
            onPlaybackPositionChanged = viewModel::onPlaybackPositionChanged,
            onPlayerSubtitleTextChanged = viewModel::onPlayerSubtitleTextChanged,
            onSegmentFinished = viewModel::onSegmentPlaybackFinished,
            onSessionRestoreSeekApplied = viewModel::onSessionRestoreSeekApplied,
        )

        Spacer(modifier = Modifier.height(mainSectionGap))

        SubtitleSeekRow(
            uiState = uiState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = subtitlePadding),
            onCueSelected = viewModel::playCueAtIndex,
            onCuePreview = viewModel::previewCueAtIndex,
            onVideoSeek = viewModel::seekVideoTo,
        )

        Spacer(modifier = Modifier.height(mainSectionGap))

        ControlRow(
            videoLoaded = uiState.videoUri != null,
            isPlayingVideo = uiState.isPlayingVideo,
            segmentEnabled = uiState.canNavigate,
            playbackSpeed = uiState.playbackSpeed,
            playbackSpeedOptions = ShadowingViewModel.PLAYBACK_SPEED_OPTIONS,
            currentIndex = if (uiState.usesPlayerSubtitles) {
                uiState.playerSubtitleIndex
            } else {
                uiState.currentIndex
            },
            totalCues = uiState.totalCues,
            onPlaybackSpeedSelected = viewModel::setPlaybackSpeed,
            onToggleVideoPlayback = viewModel::toggleVideoPlayback,
            onPrevious = viewModel::previousCue,
            onRepeat = viewModel::repeatCurrentSegment,
            onNext = viewModel::nextCue,
            modifier = Modifier
                .padding(horizontal = sectionPadding)
                .height(controlHeight),
        )
    }
}

/**
 * 下部ページ切替用の縦ドラッグを検知する。
 */
private fun Modifier.footerPageDragGesture(
    onFooterDrag: (Float) -> Unit,
): Modifier = pointerInput(Unit) {
    var totalDrag = 0f
    detectVerticalDragGestures(
        onDragStart = { totalDrag = 0f },
        onDragEnd = {
            onFooterDrag(totalDrag)
            totalDrag = 0f
        },
        onDragCancel = { totalDrag = 0f },
        onVerticalDrag = { _, dragAmount ->
            totalDrag += dragAmount
        },
    )
}

/**
 * 画面上部のドラッグハンドル（タップでも切り替え）。
 */
@Composable
private fun TimingPanelHandle(
    expanded: Boolean,
    sectionPadding: Dp,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onVerticalDragEnd: (Float) -> Unit,
) {
    val label = if (expanded) {
        "▲ タイミング調整を閉じる"
    } else {
        "▼ タイミング調整を開く"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(TIMING_HANDLE_HEIGHT)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .footerPageDragGesture(onVerticalDragEnd)
            .clickable(onClick = onToggle)
            .padding(horizontal = sectionPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.45f,
                    ),
                    shape = RoundedCornerShape(2.dp),
                ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 動画ボタンの短い表示ラベルを返す。
 */
private fun fileButtonVideoLabel(loaded: Boolean): String {
    return if (loaded) {
        "動画✓"
    } else {
        "動画"
    }
}

/**
 * 翻訳字幕が読み込み済みか判定する。
 */
private fun isTranslationSubtitleLoaded(uiState: ShadowingUiState): Boolean {
    return uiState.japaneseCues.isNotEmpty() ||
        uiState.usePlayerSubtitleAsJapanese
}

/**
 * 英語字幕が読み込み済みか判定する。
 */
private fun isEnglishSubtitleLoaded(uiState: ShadowingUiState): Boolean {
    return uiState.englishCues.isNotEmpty() ||
        uiState.usePlayerSubtitleAsEnglish
}

/**
 * 翻訳字幕ボタンの短い表示ラベルを返す。
 */
private fun fileButtonTranslationLabel(loaded: Boolean): String {
    return if (loaded) {
        "翻訳✓"
    } else {
        "翻訳"
    }
}

/**
 * 英語字幕ボタンの短い表示ラベルを返す。
 */
private fun fileButtonEnglishLabel(loaded: Boolean): String {
    return if (loaded) {
        "英語✓"
    } else {
        "英語"
    }
}

/**
 * 上部ファイル読み込み用のコンパクトボタン。
 */
@Composable
private fun CompactFileButton(
    text: String,
    onClick: () -> Unit,
    loaded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val content = @Composable {
        AutoFitButtonLabel(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        )
    }
    if (loaded) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
            content = { content() },
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
            content = { content() },
        )
    }
}

/**
 * ボタン内で折り返さず、枠に収まる最大サイズで1行表示する。
 */
@Composable
private fun AutoFitButtonLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val baseStyle = MaterialTheme.typography.labelMedium
    val color = LocalContentColor.current

    SubcomposeLayout(modifier = modifier) { constraints ->
        val maxWidthPx = constraints.maxWidth
        val maxHeightPx = constraints.maxHeight
        if (maxWidthPx <= 0 || maxHeightPx <= 0 || text.isEmpty()) {
            return@SubcomposeLayout layout(0, 0) {}
        }

        val pxPerSp = 1.sp.toPx()
        val fontSizeSp = measureFitSingleLineFontSize(
            textMeasurer = textMeasurer,
            text = text,
            maxWidthPx = maxWidthPx,
            maxHeightPx = maxHeightPx,
            baseStyle = baseStyle,
            minSp = TOP_BUTTON_MIN_FONT_SP,
            maxSp = (maxHeightPx / pxPerSp)
                .coerceIn(TOP_BUTTON_MIN_FONT_SP, TOP_BUTTON_MAX_FONT_SP),
        )
        val textStyle = subtitleFitTextStyle(
            baseStyle = baseStyle,
            fontSizeSp = fontSizeSp,
        )
        val placeable = subcompose("button-label") {
            Text(
                text = text,
                style = textStyle,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        }.first().measure(Constraints())

        layout(maxWidthPx, maxHeightPx) {
            val offsetX = ((maxWidthPx - placeable.width) / 2f).roundToInt()
                .coerceAtLeast(0)
            val offsetY = ((maxHeightPx - placeable.height) / 2f).roundToInt()
                .coerceAtLeast(0)
            placeable.placeRelative(offsetX, offsetY)
        }
    }
}

/**
 * 1行テキストが枠内に収まる最大フォントサイズ(sp)を返す。
 */
private fun measureFitSingleLineFontSize(
    textMeasurer: TextMeasurer,
    text: String,
    maxWidthPx: Int,
    maxHeightPx: Int,
    baseStyle: TextStyle,
    minSp: Float,
    maxSp: Float,
): Float {
    if (maxWidthPx <= 0 || maxHeightPx <= 0 || text.isEmpty()) {
        return minSp
    }

    var low = minSp
    var high = maxSp
    var best = minSp
    while (low <= high + 0.01f) {
        val mid = (low + high) / 2f
        val result = textMeasurer.measure(
            text = text,
            style = subtitleFitTextStyle(baseStyle, mid),
            maxLines = 1,
            softWrap = false,
        )
        if (
            result.size.width <= maxWidthPx &&
            result.size.height <= maxHeightPx
        ) {
            best = mid
            low = mid + 0.125f
        } else {
            high = mid - 0.125f
        }
    }
    return best
}

/**
 * ファイル読み込みボタン行。
 */
@Composable
private fun FileLoadRow(
    uiState: ShadowingUiState,
    modifier: Modifier = Modifier,
    onPickVideo: () -> Unit,
    onPickJapaneseSrt: () -> Unit,
    onPickEnglishSrt: () -> Unit,
    onUseInternalJapanese: (Int) -> Unit,
    onUseInternalEnglish: (Int) -> Unit,
) {
    var subtitleDialog by remember {
        mutableStateOf<SubtitleSourceDialogLanguage?>(null)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val videoLoaded = uiState.videoUri != null
        val englishLoaded = isEnglishSubtitleLoaded(uiState)
        val translationLoaded = isTranslationSubtitleLoaded(uiState)
        CompactFileButton(
            text = fileButtonVideoLabel(videoLoaded),
            onClick = onPickVideo,
            loaded = videoLoaded,
            modifier = Modifier.weight(1f),
        )
        CompactFileButton(
            text = fileButtonEnglishLabel(englishLoaded),
            onClick = { subtitleDialog = SubtitleSourceDialogLanguage.ENGLISH },
            loaded = englishLoaded,
            modifier = Modifier.weight(1f),
        )
        CompactFileButton(
            text = fileButtonTranslationLabel(translationLoaded),
            onClick = { subtitleDialog = SubtitleSourceDialogLanguage.JAPANESE },
            loaded = translationLoaded,
            modifier = Modifier.weight(1f),
        )
    }

    subtitleDialog?.let { language ->
        SubtitleSourceDialog(
            language = language,
            tracks = uiState.internalSubtitleTracks,
            isScanning = uiState.isScanningInternalSubtitles,
            onDismiss = { subtitleDialog = null },
            onPickExternal = {
                subtitleDialog = null
                if (language == SubtitleSourceDialogLanguage.JAPANESE) {
                    onPickJapaneseSrt()
                } else {
                    onPickEnglishSrt()
                }
            },
            onPickInternal = { trackIndex ->
                subtitleDialog = null
                if (language == SubtitleSourceDialogLanguage.JAPANESE) {
                    onUseInternalJapanese(trackIndex)
                } else {
                    onUseInternalEnglish(trackIndex)
                }
            },
        )
    }
}

/**
 * 字幕の読み込み元を選ぶダイアログ。
 */
@Composable
private fun SubtitleSourceDialog(
    language: SubtitleSourceDialogLanguage,
    tracks: List<InternalSubtitleTrack>,
    isScanning: Boolean,
    onDismiss: () -> Unit,
    onPickExternal: () -> Unit,
    onPickInternal: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${language.label}字幕を選択")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isScanning) {
                        "内部字幕を確認中です"
                    } else {
                        "内部字幕または外部SRTを選んでください"
                    },
                )
                if (tracks.isEmpty()) {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("内部字幕なし")
                    }
                } else {
                    tracks.forEach { track ->
                        OutlinedButton(
                            onClick = { onPickInternal(track.trackIndex) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(track.displayName)
                        }
                    }
                }
                OutlinedButton(
                    onClick = onPickExternal,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("外部SRTを選択")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        },
    )
}

/**
 * 字幕ソース選択の対象言語。
 */
private enum class SubtitleSourceDialogLanguage(val label: String) {
    JAPANESE("翻訳"),
    ENGLISH("英語"),
}

/**
 * ExoPlayer の cue 一覧を表示用テキストに変換する。
 */
private fun List<Cue>.toDisplayText(): String {
    return mapNotNull { cue -> cue.text?.toString()?.trim() }
        .filter { text -> text.isNotBlank() }
        .joinToString("\n")
}

/**
 * ExoPlayer による動画再生エリア。
 */
@Composable
private fun VideoPlayerSection(
    videoUri: Uri?,
    seekStartMs: Long,
    segmentEndMs: Long,
    isPlayingSegment: Boolean,
    isPlayingVideo: Boolean,
    playbackSpeed: Float,
    seekRequestId: Long,
    playRequestId: Long,
    videoSeekRequestId: Long,
    videoSeekPositionMs: Long,
    sessionRestoreSeekMs: Long?,
    preferredTextLanguage: String?,
    modifier: Modifier = Modifier,
    onPlaybackPositionChanged: (Long, Long) -> Unit,
    onPlayerSubtitleTextChanged: (String, Long) -> Unit,
    onSegmentFinished: () -> Unit,
    onSessionRestoreSeekApplied: () -> Unit,
) {
    val context = LocalContext.current
    val videoTitle = remember(videoUri) {
        videoUri?.let { uri -> context.resolveDisplayName(uri) }
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            /**
             * ExoPlayer が実際に表示する字幕文字列をUIへ渡す。
             */
            override fun onCues(cueGroup: CueGroup) {
                val subtitleText = cueGroup.cues.toDisplayText()
                if (subtitleText.isNotBlank()) {
                    onPlayerSubtitleTextChanged(subtitleText, player.currentPosition)
                }
            }

            /**
             * 古い形式の cue 通知でも同じ字幕文字列をUIへ渡す。
             */
            override fun onCues(cues: List<Cue>) {
                val subtitleText = cues.toDisplayText()
                if (subtitleText.isNotBlank()) {
                    onPlayerSubtitleTextChanged(subtitleText, player.currentPosition)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(videoUri) {
        if (videoUri == null) {
            player.stop()
            player.clearMediaItems()
            return@LaunchedEffect
        }
        player.setMediaItem(MediaItem.fromUri(videoUri))
        player.prepare()
        val restoreMs = sessionRestoreSeekMs
        if (restoreMs != null && restoreMs > 0L) {
            player.seekTo(restoreMs)
            onSessionRestoreSeekApplied()
        }
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        onPlaybackPositionChanged(player.currentPosition, duration)
    }

    LaunchedEffect(playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(preferredTextLanguage) {
        val builder = player.trackSelectionParameters.buildUpon()
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        builder.setPreferredTextLanguage(preferredTextLanguage)
        player.trackSelectionParameters = builder.build()
    }

    LaunchedEffect(seekRequestId) {
        if (videoUri == null || seekRequestId == 0L) {
            return@LaunchedEffect
        }
        player.seekTo(seekStartMs)
        if (isPlayingSegment) {
            player.play()
        }
    }

    LaunchedEffect(playRequestId) {
        if (videoUri == null || playRequestId == 0L) {
            return@LaunchedEffect
        }
        player.play()
    }

    LaunchedEffect(videoSeekRequestId) {
        if (videoUri == null || videoSeekRequestId == 0L) {
            return@LaunchedEffect
        }
        player.seekTo(videoSeekPositionMs)
    }

    LaunchedEffect(isPlayingSegment, isPlayingVideo) {
        if (videoUri == null) {
            return@LaunchedEffect
        }
        if (isPlayingSegment || isPlayingVideo) {
            player.play()
        } else {
            player.pause()
        }
    }

    LaunchedEffect(isPlayingSegment, seekStartMs, segmentEndMs) {
        if (videoUri == null || !isPlayingSegment || segmentEndMs <= seekStartMs) {
            return@LaunchedEffect
        }
        while (true) {
            if (player.currentPosition >= segmentEndMs - 50L) {
                player.pause()
                player.seekTo(seekStartMs)
                onSegmentFinished()
                break
            }
            delay(50L)
        }
    }

    LaunchedEffect(videoUri) {
        if (videoUri == null) {
            return@LaunchedEffect
        }
        while (true) {
            val duration = player.duration.takeIf { it > 0L } ?: 0L
            onPlaybackPositionChanged(player.currentPosition, duration)
            delay(200L)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (videoUri == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("動画ファイルを選択してください")
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        this.player = player
                        useController = false
                        subtitleView?.visibility = View.GONE
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { view ->
                    view.player = player
                    view.subtitleView?.visibility = View.GONE
                },
            )
            if (!videoTitle.isNullOrBlank()) {
                Text(
                    text = videoTitle,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Content URI から表示用ファイル名を取得する。
 */
private fun Context.resolveDisplayName(uri: Uri): String {
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }
    return uri.lastPathSegment ?: "動画ファイル"
}

/**
 * タイミング調整の対象字幕。
 */
private enum class TimingAdjustTarget(val label: String) {
    ENGLISH("英語字幕"),
    TRANSLATION("翻訳字幕"),
}

/**
 * 英語・翻訳字幕のタイミング調整UI。
 */
@Composable
private fun TimingAdjustSection(
    englishOffsetMs: Long,
    japaneseOffsetMs: Long,
    englishLoaded: Boolean,
    japaneseLoaded: Boolean,
    onNudgeEnglish: (Long) -> Unit,
    onNudgeJapanese: (Long) -> Unit,
    onResetEnglish: () -> Unit,
    onResetJapanese: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var target by remember { mutableStateOf(TimingAdjustTarget.ENGLISH) }

    LaunchedEffect(englishLoaded, japaneseLoaded) {
        if (!englishLoaded && japaneseLoaded) {
            target = TimingAdjustTarget.TRANSLATION
        } else if (englishLoaded && !japaneseLoaded) {
            target = TimingAdjustTarget.ENGLISH
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = target == TimingAdjustTarget.ENGLISH,
                    onClick = { target = TimingAdjustTarget.ENGLISH },
                    label = {
                        Text(
                            TimingAdjustTarget.ENGLISH.label,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = englishLoaded,
                )
                FilterChip(
                    selected = target == TimingAdjustTarget.TRANSLATION,
                    onClick = { target = TimingAdjustTarget.TRANSLATION },
                    label = {
                        Text(
                            TimingAdjustTarget.TRANSLATION.label,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = japaneseLoaded,
                )
            }
            when (target) {
                TimingAdjustTarget.ENGLISH -> {
                    TimingAdjustRow(
                        label = TimingAdjustTarget.ENGLISH.label,
                        offsetMs = englishOffsetMs,
                        enabled = englishLoaded,
                        onNudge = onNudgeEnglish,
                        onReset = onResetEnglish,
                    )
                }
                TimingAdjustTarget.TRANSLATION -> {
                    TimingAdjustRow(
                        label = TimingAdjustTarget.TRANSLATION.label,
                        offsetMs = japaneseOffsetMs,
                        enabled = japaneseLoaded,
                        onNudge = onNudgeJapanese,
                        onReset = onResetJapanese,
                    )
                }
            }
        }
    }
}

/**
 * 1言語分のオフセット調整行。
 */
@Composable
private fun TimingAdjustRow(
    label: String,
    offsetMs: Long,
    enabled: Boolean,
    onNudge: (Long) -> Unit,
    onReset: () -> Unit,
) {
    val step = ShadowingViewModel.OFFSET_STEP_MS
    val largeStep = ShadowingViewModel.OFFSET_LARGE_STEP_MS

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = formatOffsetSec(offsetMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TimingNudgeButton(
                text = "-0.5",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onNudge(-largeStep) },
            )
            TimingNudgeButton(
                text = "-0.1",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onNudge(-step) },
            )
            TimingNudgeButton(
                text = "+0.1",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onNudge(step) },
            )
            TimingNudgeButton(
                text = "+0.5",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onNudge(largeStep) },
            )
            TextButton(
                onClick = onReset,
                enabled = enabled && offsetMs != 0L,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "戻す",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * タイミング調整用のコンパクトボタン。
 */
@Composable
private fun TimingNudgeButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * 字幕欄と縦型シークスライダーを横並びで表示する。
 */
@Composable
private fun SubtitleSeekRow(
    uiState: ShadowingUiState,
    modifier: Modifier = Modifier,
    onCueSelected: (Int) -> Unit,
    onCuePreview: (Int) -> Unit,
    onVideoSeek: (Long) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!uiState.seekSliderOnRight) {
            VideoSeekSliderSlot(uiState, onVideoSeek)
        }
        SubtitleSection(
            uiState = uiState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onCueSelected = onCueSelected,
            onCuePreview = onCuePreview,
        )
        if (uiState.seekSliderOnRight) {
            VideoSeekSliderSlot(uiState, onVideoSeek)
        }
    }
}

/**
 * 縦型シークスライダーの配置スロットを表示する。
 */
@Composable
private fun VideoSeekSliderSlot(
    uiState: ShadowingUiState,
    onVideoSeek: (Long) -> Unit,
) {
    VerticalVideoSeekSlider(
        positionMs = uiState.playbackPositionMs,
        durationMs = uiState.seekableDurationMs,
        onSeek = onVideoSeek,
        modifier = Modifier
            .width(18.dp)
            .fillMaxHeight(),
    )
}

/**
 * 動画の現在位置を縦方向に表示するインジケータ。
 */
@Composable
private fun VerticalVideoSeekSlider(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = durationMs > 0L
    val progress = if (enabled) {
        positionMs.toFloat() / durationMs.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)

    fun seekFromY(y: Float, height: Float) {
        if (!enabled || height <= 0f) {
            return
        }
        val nextProgress = (y.coerceIn(0f, height) / height).coerceIn(0f, 1f)
        onSeek((durationMs * nextProgress).toLong())
    }

    Card(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 6.dp)
                .pointerInput(enabled, durationMs) {
                    detectTapGestures { offset ->
                        seekFromY(offset.y, size.height.toFloat())
                    }
                }
                .pointerInput(enabled, durationMs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            seekFromY(offset.y, size.height.toFloat())
                        },
                        onDrag = { change, _ ->
                            seekFromY(change.position.y, size.height.toFloat())
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val dotRadius = 2.dp.toPx()
                val markerRadius = 7.dp.toPx()
                val markerY = size.height * progress
                for (tick in 0..4) {
                    drawCircle(
                        color = Color.LightGray,
                        radius = dotRadius,
                        center = Offset(centerX, size.height * tick / 4f),
                    )
                }
                drawCircle(
                    color = Color(0xFF6F4DBF),
                    radius = markerRadius,
                    center = Offset(centerX, markerY),
                )
            }
        }
    }
}

/**
 * 英語・翻訳字幕を横スワイプで切り替える表示エリア。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubtitleSection(
    uiState: ShadowingUiState,
    modifier: Modifier = Modifier,
    onCueSelected: (Int) -> Unit,
    onCuePreview: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    Card(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            if (page == 0) {
                EnglishSubtitlePage(
                    uiState = uiState,
                    onCueSelected = onCueSelected,
                    onCuePreview = onCuePreview,
                )
            } else {
                JapaneseSubtitlePage(
                    uiState = uiState,
                    onCueSelected = onCueSelected,
                    onCuePreview = onCuePreview,
                )
            }
        }
    }
}

/**
 * 日本語マッチ状態の説明文を返す。
 */
private fun japaneseMatchHint(result: JapaneseMatchResult?): String? {
    if (result == null) {
        return null
    }
    return when (result.quality) {
        JapaneseMatchQuality.OVERLAP -> null
        JapaneseMatchQuality.NEARBY ->
            "対応は不明確（時刻が近いのみ）"
        JapaneseMatchQuality.NONE -> {
            val gapSec = formatOffsetSec(result.centerDistanceMs)
            "最も近い行とも $gapSec 離れています。翻訳オフセットを調整"
        }
    }
}

/**
 * 1言語分の字幕カード。
 */
@Composable
private fun EnglishSubtitlePage(
    uiState: ShadowingUiState,
    onCueSelected: (Int) -> Unit,
    onCuePreview: (Int) -> Unit,
) {
    if (uiState.usePlayerSubtitleAsEnglish) {
        PlayerSubtitlePage(
            title = "英語（動画内字幕）",
            cues = uiState.playerSubtitleCues,
            currentIndex = uiState.playerSubtitleIndex,
            emptyMessage = "動画内字幕を使用中です。再生するとここに表示されます",
            onCueSelected = onCueSelected,
            onCuePreview = onCuePreview,
        )
        return
    }

    SubtitleListPage(
        title = "英語（横スワイプで翻訳）",
        cues = uiState.englishCues,
        currentIndex = uiState.currentIndex,
        emptyMessage = "英語SRTを読み込んでください",
        onCueSelected = onCueSelected,
        onCuePreview = onCuePreview,
    ) { _, cue, selected ->
        val adjusted = cue.withOffset(uiState.englishOffsetMs)
        SubtitleListItemContent(
            cue = adjusted,
            text = adjusted.text,
            hint = null,
            emphasized = selected,
        )
    }
}

/**
 * 日本語字幕ページを表示する。
 */
@Composable
private fun JapaneseSubtitlePage(
    uiState: ShadowingUiState,
    onCueSelected: (Int) -> Unit,
    onCuePreview: (Int) -> Unit,
) {
    if (uiState.usePlayerSubtitleAsJapanese) {
        PlayerSubtitlePage(
            title = "翻訳（動画内字幕）",
            cues = uiState.playerSubtitleCues,
            currentIndex = uiState.playerSubtitleIndex,
            emptyMessage = "動画内字幕を使用中です。再生するとここに表示されます",
            onCueSelected = onCueSelected,
            onCuePreview = onCuePreview,
        )
        return
    }

    SubtitleListPage(
        title = "翻訳（横スワイプで英語）",
        cues = uiState.englishCues,
        currentIndex = uiState.currentIndex,
        emptyMessage = "英語SRTを読み込んでください",
        onCueSelected = onCueSelected,
        onCuePreview = onCuePreview,
    ) { _, englishCue, selected ->
        val result = if (uiState.japaneseCues.isEmpty()) {
            null
        } else {
            SubtitleMatcher.findJapaneseMatch(
                reference = englishCue.withOffset(uiState.englishOffsetMs),
                japaneseCues = uiState.japaneseCues,
                japaneseOffsetMs = uiState.japaneseOffsetMs,
            )
        }
        val matchedCues = if (uiState.japaneseCues.isEmpty()) {
            emptyList()
        } else {
            SubtitleMatcher.findJapaneseMatches(
                reference = englishCue.withOffset(uiState.englishOffsetMs),
                japaneseCues = uiState.japaneseCues,
                japaneseOffsetMs = uiState.japaneseOffsetMs,
            ).map { match -> match.cue }
        }
        val cue = matchedCues.firstOrNull()
        val text = when {
            uiState.japaneseCues.isEmpty() ->
                "翻訳SRTを読み込んでください"
            matchedCues.isNotEmpty() -> matchedCues.joinToString("\n") { it.text }
            else -> "対応する翻訳字幕がありません"
        }
        SubtitleListItemContent(
            title = buildJapaneseCueTitle(
                englishCue = englishCue,
                japaneseCues = matchedCues,
            ),
            text = text,
            hint = japaneseMatchHint(result),
            emphasized = selected,
        )
    }
}

/**
 * プレイヤーがデコードした現在の字幕を表示する。
 */
@Composable
private fun PlayerSubtitlePage(
    title: String,
    cues: List<SubtitleCue>,
    currentIndex: Int,
    emptyMessage: String,
    onCueSelected: (Int) -> Unit,
    onCuePreview: (Int) -> Unit,
) {
    SubtitleListPage(
        title = title,
        cues = cues,
        currentIndex = currentIndex,
        emptyMessage = emptyMessage,
        onCueSelected = onCueSelected,
        onCuePreview = onCuePreview,
    ) { _, cue, selected ->
        SubtitleListItemContent(
            cue = cue,
            text = cue.text,
            hint = null,
            emphasized = selected,
        )
    }
}

/**
 * 字幕リストページを固定サイズの中で縦スクロール表示する。
 */
@Composable
private fun SubtitleListPage(
    title: String,
    cues: List<SubtitleCue>,
    currentIndex: Int,
    emptyMessage: String,
    onCueSelected: (Int) -> Unit,
    onCuePreview: (Int) -> Unit,
    itemContent: @Composable (Int, SubtitleCue, Boolean) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex, cues.size) {
        if (cues.isEmpty() || listState.isScrollInProgress) {
            return@LaunchedEffect
        }
        listState.scrollToItem(currentIndex.coerceIn(cues.indices))
        listState.animateItemToCenter(currentIndex.coerceIn(cues.indices))
    }

    LaunchedEffect(listState, cues.size) {
        var lastPreviewIndex: Int? = null
        var lastPreviewAtMs = 0L
        snapshotFlow {
            if (listState.isScrollInProgress) {
                centeredVisibleIndex(listState)
            } else {
                null
            }
        }.distinctUntilChanged().collect { centeredIndex ->
            if (centeredIndex == null || centeredIndex !in cues.indices) {
                return@collect
            }
            val nowMs = System.currentTimeMillis()
            val shouldPreview = centeredIndex != lastPreviewIndex &&
                nowMs - lastPreviewAtMs >= PREVIEW_SEEK_INTERVAL_MS
            if (shouldPreview) {
                lastPreviewIndex = centeredIndex
                lastPreviewAtMs = nowMs
                onCuePreview(centeredIndex)
            }
        }
    }

    LaunchedEffect(listState, cues.size, currentIndex) {
        var hasUserScrolled = false
        while (true) {
            delay(SNAP_WATCH_INTERVAL_MS)
            if (cues.isEmpty()) {
                continue
            }
            if (listState.isScrollInProgress) {
                hasUserScrolled = true
                continue
            }

            var focusedIndex = centeredVisibleIndex(listState)
                ?.takeIf { it in cues.indices }
                ?: continue
            repeat(SNAP_RETRY_COUNT) {
                val moved = listState.animateItemToCenter(focusedIndex)
                delay(SNAP_RECHECK_DELAY_MS)
                focusedIndex = centeredVisibleIndex(listState)
                    ?.takeIf { it in cues.indices }
                    ?: focusedIndex
                if (!moved) {
                    return@repeat
                }
            }

            if (hasUserScrolled || focusedIndex != currentIndex) {
                hasUserScrolled = false
                onCueSelected(focusedIndex)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        if (cues.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(emptyMessage)
            }
            return@Column
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val centerPadding = ((maxHeight - SUBTITLE_ITEM_HEIGHT) / 2)
                .coerceAtLeast(0.dp)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = centerPadding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(cues) { index, cue ->
                    val selected = index == currentIndex
                    SubtitleListItem(
                        onClick = { onCueSelected(index) },
                    ) {
                        itemContent(index, cue, selected)
                    }
                }
            }
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(SUBTITLE_ITEM_HEIGHT),
            ) {
                drawRoundRect(
                    color = Color(0xFF6F4DBF),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}

/**
 * 1件分の字幕アイテムを表示する。
 */
@Composable
private fun SubtitleListItem(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(SUBTITLE_ITEM_HEIGHT)
            .clickable(onClick = onClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            content()
        }
    }
}

/**
 * 字幕アイテム内のテキストを表示する。
 */
@Composable
private fun SubtitleListItemContent(
    title: String? = null,
    cue: SubtitleCue? = null,
    text: String,
    hint: String?,
    emphasized: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (cue != null) {
            CueTitleRow(cue = cue)
        } else if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 2,
                overflow = TextOverflow.Clip,
            )
        }
        AutoFitSubtitleBody(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fillMaxHeight(),
        )
    }
}

/**
 * 字幕本文をカード残り領域いっぱいに表示する。
 */
@Composable
private fun AutoFitSubtitleBody(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val baseStyle = MaterialTheme.typography.bodyLarge
    val flowText = remember(text) { normalizeSubtitleFlowText(text) }

    SubcomposeLayout(modifier = modifier) { constraints ->
        val maxWidthPx = constraints.maxWidth
        val maxHeightPx = constraints.maxHeight
        if (
            maxWidthPx <= 0 ||
            maxHeightPx <= 0 ||
            maxHeightPx == Constraints.Infinity ||
            flowText.isEmpty()
        ) {
            return@SubcomposeLayout layout(0, 0) {}
        }

        val pxPerSp = 1.sp.toPx()
        val fontSizeSp = measureFitSubtitleFontSize(
            textMeasurer = textMeasurer,
            text = flowText,
            maxWidthPx = maxWidthPx,
            maxHeightPx = maxHeightPx,
            baseStyle = baseStyle,
            minSp = SUBTITLE_BODY_MIN_FONT_SP,
            maxSp = (maxHeightPx / pxPerSp)
                .coerceIn(SUBTITLE_BODY_MIN_FONT_SP, SUBTITLE_BODY_MAX_FONT_SP),
        )
        val textStyle = subtitleFitTextStyle(
            baseStyle = baseStyle,
            fontSizeSp = fontSizeSp,
        )
        val measured = subcompose("subtitle-measure") {
            Text(
                text = flowText,
                style = textStyle,
                color = color,
                overflow = TextOverflow.Clip,
                softWrap = true,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }.first().measure(Constraints(maxWidth = maxWidthPx))

        val scale = minOf(
            maxWidthPx.toFloat() / measured.width.coerceAtLeast(1),
            maxHeightPx.toFloat() / measured.height.coerceAtLeast(1),
        ).coerceAtMost(1f)
        val scaledHeight = measured.height * scale
        val offsetY = ((maxHeightPx - scaledHeight) / 2f).roundToInt()
            .coerceAtLeast(0)

        val placeable = subcompose("subtitle-body") {
            Text(
                text = flowText,
                style = textStyle,
                color = color,
                overflow = TextOverflow.Clip,
                softWrap = true,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            )
        }.first().measure(Constraints(maxWidth = maxWidthPx))

        layout(maxWidthPx, maxHeightPx) {
            placeable.placeRelative(0, offsetY)
        }
    }
}

/**
 * カード表示用に字幕本文の改行を空白へ統一する。
 */
private fun normalizeSubtitleFlowText(text: String): String {
    return text
        .replace('\r', ' ')
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

/**
 * 字幕番号とタイムスタンプを表示する。
 */
@Composable
private fun CueTitleRow(cue: SubtitleCue) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildCueTimeTitle(cue),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/**
 * 字幕本文用の TextStyle を組み立てる。
 */
private fun subtitleFitTextStyle(
    baseStyle: TextStyle,
    fontSizeSp: Float,
): TextStyle {
    return baseStyle.copy(
        fontSize = fontSizeSp.sp,
        lineHeight = fontSizeSp.sp,
        letterSpacing = 0.sp,
    )
}

/**
 * 枠内に全文が収まる最大のフォントサイズ(sp)を返す。
 */
private fun measureFitSubtitleFontSize(
    textMeasurer: TextMeasurer,
    text: String,
    maxWidthPx: Int,
    maxHeightPx: Int,
    baseStyle: TextStyle,
    minSp: Float,
    maxSp: Float,
): Float {
    if (maxWidthPx <= 0 || maxHeightPx <= 0 || text.isEmpty()) {
        return minSp
    }

    var low = minSp
    var high = maxSp
    var best = minSp
    while (low <= high + 0.01f) {
        val mid = (low + high) / 2f
        val result = textMeasurer.measure(
            text = text,
            style = subtitleFitTextStyle(baseStyle, mid),
            constraints = Constraints(maxWidth = maxWidthPx),
        )
        if (result.size.height <= maxHeightPx) {
            best = mid
            low = mid + 0.125f
        } else {
            high = mid - 0.125f
        }
    }
    return best
}

/**
 * リストの中央付近にある字幕インデックスを返す。
 */
private fun centeredVisibleIndex(listState: LazyListState): Int? {
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) {
        return null
    }

    val viewportCenter = (
        listState.layoutInfo.viewportStartOffset +
            listState.layoutInfo.viewportEndOffset
        ) / 2

    return visibleItems.minByOrNull { item ->
        val itemCenter = item.offset + item.size / 2
        kotlin.math.abs(itemCenter - viewportCenter)
    }?.index
}

/**
 * 指定した字幕アイテムの中心を固定枠の中心へなめらかに合わせる。
 */
private suspend fun LazyListState.animateItemToCenter(index: Int): Boolean {
    val item = layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == index }
        ?: return false
    val viewportCenter = (
        layoutInfo.viewportStartOffset +
            layoutInfo.viewportEndOffset
        ) / 2
    val itemCenter = item.offset + item.size / 2
    val delta = itemCenter - viewportCenter
    if (kotlin.math.abs(delta) <= SNAP_TOLERANCE_PX) {
        return false
    }
    animateScrollBy(delta.toFloat())
    return true
}

/**
 * 字幕アイテムの時刻タイトル文字列を作る。
 */
private fun buildCueTimeTitle(cue: SubtitleCue): String {
    return buildString {
        append("#")
        append(cue.index)
        append(" ")
        append(formatCueTimeMs(cue.startMs))
        append(" → ")
        append(formatCueTimeMs(cue.endMs))
    }
}

/**
 * 日本語ページ用のタイトル文字列を作る。
 */
private fun buildJapaneseCueTitle(
    englishCue: SubtitleCue,
    japaneseCues: List<SubtitleCue>,
): String {
    return if (japaneseCues.isEmpty()) {
        "#${englishCue.index}"
    } else if (japaneseCues.size == 1) {
        "#${englishCue.index} / 翻訳 #${japaneseCues.first().index}"
    } else {
        val first = japaneseCues.first().index
        val last = japaneseCues.last().index
        "#${englishCue.index} / 翻訳 #$first-#$last"
    }
}

/**
 * 前へ・再生（リピート）・次へコントロール。
 */
@Composable
private fun ControlRow(
    videoLoaded: Boolean,
    isPlayingVideo: Boolean,
    segmentEnabled: Boolean,
    playbackSpeed: Float,
    playbackSpeedOptions: List<Float>,
    currentIndex: Int,
    totalCues: Int,
    modifier: Modifier = Modifier,
    onPlaybackSpeedSelected: (Float) -> Unit,
    onToggleVideoPlayback: () -> Unit,
    onPrevious: () -> Unit,
    onRepeat: () -> Unit,
    onNext: () -> Unit,
) {
    var showSpeedDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (totalCues > 0) {
            Text(
                text = "${currentIndex + 1} / $totalCues",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = { showSpeedDialog = true },
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Text(
                    text = formatPlaybackSpeed(playbackSpeed),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }

            FilledIconButton(
                onClick = onToggleVideoPlayback,
                enabled = videoLoaded,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(
                    if (isPlayingVideo) {
                        Icons.Default.Pause
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = if (isPlayingVideo) {
                        "動画を一時停止"
                    } else {
                        "動画を再生"
                    },
                )
            }

            FilledIconButton(
                onClick = onPrevious,
                enabled = segmentEnabled && currentIndex > 0,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "前へ")
            }

            FilledIconButton(
                onClick = onRepeat,
                enabled = segmentEnabled,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(
                    Icons.Default.Replay,
                    contentDescription = "区間再生",
                    modifier = Modifier.size(30.dp),
                )
            }

            FilledIconButton(
                onClick = onNext,
                enabled = segmentEnabled && currentIndex < totalCues - 1,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "次へ")
            }
        }

    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("再生速度") },
            text = {
                Column {
                    playbackSpeedOptions.forEach { speed ->
                        TextButton(
                            onClick = {
                                onPlaybackSpeedSelected(speed)
                                showSpeedDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = formatPlaybackSpeed(speed),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("閉じる")
                }
            },
        )
    }
}

/**
 * 再生速度をボタン表示用の文字列へ変換する。
 */
private fun formatPlaybackSpeed(speed: Float): String {
    return formatPlaybackSpeedLabel(speed)
}
