package com.intellij.ide.starters.shared

import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.icons.AllIcons
import org.jetbrains.annotations.Nls
import javax.swing.Icon

enum class LibraryLinkType(val icon: Icon?) {
  GUIDE(AllIcons.Nodes.HomeFolder),
  REFERENCE(AllIcons.Actions.Preview),
  WEBSITE(AllIcons.Nodes.PpWeb),
  SPECIFICATION(AllIcons.Nodes.PpWeb);

  @Nls
  fun getTitle(): String {
    return when (this) {
      GUIDE -> JavaStartersBundle.message("starter.link.guide")
      REFERENCE -> JavaStartersBundle.message("starter.link.reference")
      WEBSITE -> JavaStartersBundle.message("starter.link.website")
      SPECIFICATION -> JavaStartersBundle.message("starter.link.specification")
    }
  }
}