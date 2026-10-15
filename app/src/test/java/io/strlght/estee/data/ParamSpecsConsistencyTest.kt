package io.strlght.estee.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ParamSpecsConsistencyTest : StringSpec({

    val specNames = ParamSpecs.GROUPS.flatMap { group -> group.specs.map { it.name } }

    val fieldNames = InferenceParams.serializer().descriptor
        .let { descriptor -> (0 until descriptor.elementsCount).map { descriptor.getElementName(it) } }

    "spec names are unique" {
        specNames.distinct() shouldBe specNames
    }

    "spec names cover every InferenceParams field exactly" {
        specNames shouldContainExactlyInAnyOrder fieldNames
    }
})
