package com.didiglobal.booster.task.spi

import com.android.build.api.variant.Variant
import com.android.build.gradle.api.BaseVariant

interface VariantProcessor {

    @Deprecated(
        message = "BaseVariant is deprecated,  please use process(variant: Pair<Project, Variant>) method instead",
        replaceWith = ReplaceWith(
            expression = "process(variant: Pair<Project, Variant>)"
        )
    )
    fun process(variant: BaseVariant) {}

    fun process(variant: Variant) {}

}
