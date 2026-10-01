package com.swrneko.glyphmeter.di

import javax.inject.Qualifier

/** The single application-wide thread on which everything touching the Glyph display runs. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GlyphDispatcher
