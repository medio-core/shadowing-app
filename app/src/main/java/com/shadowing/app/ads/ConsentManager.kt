package com.shadowing.app.ads

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * EEA/UK 向けの広告同意（UMP）を管理する。
 *
 * AdMob のポリシー上、対象地域のユーザーには広告リクエスト前に
 * 認定 CMP による同意取得が必須となるため、canRequestAds が true に
 * なるまで広告リクエストと明示的な Mobile Ads 初期化を開始しない。
 */
object ConsentManager {

    private const val TAG = "ConsentManager"

    private var consentInformation: ConsentInformation? = null

    private val _canRequestAds = MutableStateFlow(false)

    /** 広告リクエストが許可された状態か。 */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /** 設定画面に「プライバシー設定」項目を出す必要があるか。 */
    val privacyOptionsRequired: StateFlow<Boolean> =
        _privacyOptionsRequired.asStateFlow()

    /**
     * 同意情報を更新し、必要なら同意フォームを表示する。
     *
     * @param activity フォーム表示に使う Activity
     * @param debugGeographyEea デバッグ時に EEA 所在として扱うか
     */
    fun gatherConsent(
        activity: Activity,
        debugGeographyEea: Boolean = false,
    ) {
        val paramsBuilder = ConsentRequestParameters.Builder()
        if (debugGeographyEea) {
            paramsBuilder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(
                        ConsentDebugSettings.DebugGeography
                            .DEBUG_GEOGRAPHY_EEA,
                    )
                    .build(),
            )
        }

        val information = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = information

        information.requestConsentInfoUpdate(
            activity,
            paramsBuilder.build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    activity,
                ) { formError ->
                    if (formError != null) {
                        Log.w(
                            TAG,
                            "Consent form error: " +
                                "${formError.errorCode} ${formError.message}",
                        )
                    }
                    publishState(information)
                }
            },
            { requestError ->
                // 同意情報を取得できなくても、既存の同意状態で判断を続ける。
                Log.w(
                    TAG,
                    "Consent info update failed: " +
                        "${requestError.errorCode} ${requestError.message}",
                )
                publishState(information)
            },
        )

        // 前回起動時に同意済みなら、フォーム完了を待たずに広告を出せる。
        publishState(information)
    }

    /**
     * 設定画面から同意内容を再表示する。
     *
     * @param activity フォーム表示に使う Activity
     * @param onDismissed フォームが閉じた後の処理（エラーがあれば渡す）
     */
    fun showPrivacyOptionsForm(
        activity: Activity,
        onDismissed: (FormError?) -> Unit = {},
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            consentInformation?.let { publishState(it) }
            onDismissed(formError)
        }
    }

    /**
     * 同意状態を StateFlow へ反映する。
     */
    private fun publishState(information: ConsentInformation) {
        _privacyOptionsRequired.value =
            information.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

        _canRequestAds.value = information.canRequestAds()
    }
}
