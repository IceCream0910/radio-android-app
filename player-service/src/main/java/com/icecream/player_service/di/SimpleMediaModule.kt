package com.icecream.player_service.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.icecream.player_service.api.RadioStationApi
import com.icecream.player_service.service.SimpleMediaService
import com.icecream.player_service.service.SimpleMediaServiceHandler
import com.icecream.player_service.service.notification.SimpleMediaNotificationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class SimpleMediaModule {

    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

    @Provides
    @Singleton
    @UnstableApi
    fun providePlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes
    ): ExoPlayer =
        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setTrackSelector(DefaultTrackSelector(context))
            .setSeekBackIncrementMs(1) // 뒤로 시크 비활성화
            .setSeekForwardIncrementMs(1) // 앞으로 시크 비활성화
            .build()
            .apply {
                playWhenReady = false
            }

    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context,
        player: ExoPlayer
    ): SimpleMediaNotificationManager =
        SimpleMediaNotificationManager(
            context = context,
            player = player
        )

    @Provides
    @Singleton
    fun provideMediaLibrarySessionCallback(): SimpleMediaService.MediaLibrarySessionCallback =
        SimpleMediaService.MediaLibrarySessionCallback()

    @Provides
    @Singleton
    fun provideServiceHandler(
        player: ExoPlayer
    ): SimpleMediaServiceHandler =
        SimpleMediaServiceHandler(
            player = player
        )

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://radio.yuntae.in/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideRadioStationApi(retrofit: Retrofit): RadioStationApi {
        return retrofit.create(RadioStationApi::class.java)
    }
}
