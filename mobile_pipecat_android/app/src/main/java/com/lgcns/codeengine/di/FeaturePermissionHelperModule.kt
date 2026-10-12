package com.lgcns.codeengine.di

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import com.lgcns.codeengine.web.permission.ActivityPermissionHelper
import com.lgcns.codeengine.web.permission.FragmentPermissionHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.FragmentComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import dagger.hilt.android.scopes.FragmentScoped

@Module
@InstallIn(ActivityComponent::class)
class ActivityFeaturePermissionHelperModule {

    @Provides
    @ActivityScoped
    fun provideFeaturePermissionHelper(@ActivityContext context: Context): ActivityPermissionHelper {
        return ActivityPermissionHelper(context as ComponentActivity)
    }
}

@Module
@InstallIn(FragmentComponent::class)
class FragmentFeaturePermissionHelperModule {

    @Provides
    @FragmentScoped
    fun provideFeaturePermissionHelper(fragment: Fragment): FragmentPermissionHelper {
        return FragmentPermissionHelper(fragment)
    }
}