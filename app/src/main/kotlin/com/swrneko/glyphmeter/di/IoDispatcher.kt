package com.swrneko.glyphmeter.di

import javax.inject.Qualifier

/** Dispatcher for blocking calls such as access evaluation (secure settings, Shizuku IPC). Swappable in tests. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
