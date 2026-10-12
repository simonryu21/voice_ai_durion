package com.lgcns.codeengine.di

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.lgcns.codeengine.ui.common.DialogPermissionUiInteraction
import com.lgcns.codeengine.web.permission.FeaturePermissionUiInteraction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
class PermissionUiInteractionModule {

    @Provides
    @ActivityScoped
    fun provideFeaturePermissionUiInteraction(@ActivityContext context: Context): FeaturePermissionUiInteraction {
        val activity = context as FragmentActivity
        return DialogPermissionUiInteraction(context, activity.supportFragmentManager)
    }
}