package com.swrneko.glyphmeter.di

import com.swrneko.glyphmeter.charging.ChargingStateSource
import com.swrneko.glyphmeter.charging.SystemChargingStateSource
import com.swrneko.glyphmeter.service.ContextMeterServiceController
import com.swrneko.glyphmeter.service.MeterServiceController
import com.swrneko.glyphmeter.settings.DataStoreSettingsRepository
import com.swrneko.glyphmeter.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Interface-to-implementation bindings. Kept apart from [AppModule] because `@Binds` needs an abstract class. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindChargingStateSource(impl: SystemChargingStateSource): ChargingStateSource

    @Binds
    abstract fun bindMeterServiceController(impl: ContextMeterServiceController): MeterServiceController
}
