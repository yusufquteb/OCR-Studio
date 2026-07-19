package com.ocrstudio.core.common

import javax.inject.Qualifier

/**
 * Qualifier for an injected application Context, defined here (rather than using Hilt's
 * dagger.hilt.android.qualifiers.ApplicationContext) so library modules that just need a
 * Context for @Inject-constructor classes don't need a dependency on hilt-android. The :app
 * module's Hilt AppModule bridges this to the real ApplicationContext.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppContext
