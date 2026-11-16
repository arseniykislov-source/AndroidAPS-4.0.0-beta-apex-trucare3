package app.aaps.pump.apex.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.pump.apex.ApexService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injector for [ApexService] - the `@ContributesAndroidInjector` replacement.
 * The service is created by Android, so [app.aaps.core.objects.workflow.MetroService] fills its
 * `@Inject` fields from this map.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object ApexMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ApexService::class)
    fun bindApexService(injector: MembersInjector<ApexService>): MembersInjector<*> = injector
}
