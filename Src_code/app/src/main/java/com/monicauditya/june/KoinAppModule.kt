

package com.monicauditya.june

import com.monicauditya.redhat1406.RedHat1406
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("com.monicauditya.june")
class KoinAppModule {

    @Single
    fun provideRedHat1406(): RedHat1406 {
        return RedHat1406()
    }
}
