package com.mamesoke.gitdiscover

import android.app.Application
import com.mamesoke.gitdiscover.app.AppContainer

class GitDiscoverApplication : Application() {
    val container by lazy { AppContainer() }
}
