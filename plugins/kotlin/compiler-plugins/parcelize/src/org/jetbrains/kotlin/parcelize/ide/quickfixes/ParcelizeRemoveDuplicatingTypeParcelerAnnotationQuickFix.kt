// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.parcelize.ide.quickfixes

import org.jetbrains.kotlin.parcelize.ide.KotlinParcelizeBundle
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory

class ParcelizeRemoveDuplicatingTypeParcelerAnnotationQuickFix(anno: KtAnnotationEntry) : AbstractParcelizeQuickFix<KtAnnotationEntry>(anno) {
    object Factory : AbstractFactory({ findElement<KtAnnotationEntry>()?.let(::ParcelizeRemoveDuplicatingTypeParcelerAnnotationQuickFix) })

    override fun getText() = KotlinParcelizeBundle.message("parcelize.fix.remove.redundant.type.parceler.annotation")

    override fun invoke(ktPsiFactory: KtPsiFactory, element: KtAnnotationEntry) {
        element.delete()
    }
}