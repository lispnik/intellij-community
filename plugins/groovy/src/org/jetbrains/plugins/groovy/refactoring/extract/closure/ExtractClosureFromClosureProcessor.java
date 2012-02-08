/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.extract.closure;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.introduceParameter.ExternalUsageInfo;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class ExtractClosureFromClosureProcessor extends ExtractClosureProcessorBase {
  public ExtractClosureFromClosureProcessor(@NotNull ExtractClosureHelper helper) {
    super(helper);
  }

  @Override
  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    //todo
    return true;
  }


  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    //To change body of implemented methods use File | Settings | File Templates. todo
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    final List<UsageInfo> result = new ArrayList<UsageInfo>();

    final GrVariable var = (GrVariable)myHelper.getToSearchFor();

    for (PsiReference ref : ReferencesSearch.search(var, GlobalSearchScope.allScope(myHelper.getProject()), true)) {
      final PsiElement element = ref.getElement();
      if (element.getLanguage() != GroovyFileType.GROOVY_LANGUAGE) {
        result.add(new OtherLanguageUsageInfo(ref));
        continue;
      }

      final GrCall call = GroovyRefactoringUtil.getCallExpressionByMethodReference(element);
      if (call == null) continue;

      result.add(new ExternalUsageInfo(element));
    }

    return result.toArray(new UsageInfo[result.size()]);
  }


}
