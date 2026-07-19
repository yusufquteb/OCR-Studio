package com.ocrstudio.engine.parser

import android.content.Context
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.ParserProfileIds
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParserProfileRegistry @Inject constructor(
    @AppContext context: Context
) {
    private val profiles: Map<String, ParserProfile> = run {
        val abbreviations = HadithAbbreviations.load(context)
        val roots = RootsDictionary.load(context)
        listOf(
            GenericProfile(),
            MujamMufahrasProfile(abbreviations, roots),
            HadithProfile(),
            TafsirProfile()
        ).associateBy { it.id }
    }

    fun byId(id: String): ParserProfile = profiles[id] ?: profiles.getValue(ParserProfileIds.GENERIC)

    fun all(): List<ParserProfile> = profiles.values.toList()
}
