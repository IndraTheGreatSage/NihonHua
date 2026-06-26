package com.example.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

object AdRewardManager {
    private const val TAG = "AdRewardManager"

    // NOTE: Ganti ini dengan Rewarded Ad Unit ID dari AdMob.
    // Mis: "ca-app-pub-XXXXXXXXXXXXXXXX/YYY"
    private const val REWARDED_AD_UNIT_ID = "ca-app-pub-5840993098114934/5241702474"

    private var rewardedAd: RewardedAd? = null
    private var isLoading = AtomicBoolean(false)

    fun init(context: Context) {
        // App ID kamu: NihonHuaca-app-pub-5840993098114934~3700968631
        // MobileAds.initialize() akan membaca konfigurasi App ID dari Google Mobile Ads SDK.
        MobileAds.initialize(context) {}
    }



    fun load(context: Context) {
        if (isLoading.getAndSet(true)) return

        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading.set(false)
                    Log.d(TAG, "RewardedAd loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading.set(false)
                    Log.e(TAG, "RewardedAd failed to load: ${error.message}")
                }
            }
        )
    }

    fun show(
        activity: Activity,
        onRewardEarned: (rewardMinutes: Long) -> Unit,
        onAdFailed: (message: String) -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            onAdFailed("Rewarded ad not ready")
            load(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                load(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                load(activity)
                onAdFailed(adError.message)
            }
        }

        ad.show(activity) { rewardItem: RewardItem ->
            // rewardItem.amount & rewardItem.type tergantung setting AdMob reward
            // default fallback: treat any reward as "minutes".
            val amount = try { rewardItem.amount.toLong() } catch (_: Exception) { 60L }
            val type = rewardItem.type.lowercase()

            // Mapping yang paling umum:
            // - "hour" / "hours" / "hr" => amount jam
            // - "minute" / "minutes" / "min" => amount menit
            // - lainnya => amount menit
            val minutes = when {
                type.contains("hour") || type.contains("hr") -> amount * 60L
                type.contains("min") -> amount
                else -> amount
            }

            onRewardEarned(minutes)
        }
    }
}

