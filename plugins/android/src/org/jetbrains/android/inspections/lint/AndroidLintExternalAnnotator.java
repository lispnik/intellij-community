package org.jetbrains.android.inspections.lint;

import com.android.sdklib.SdkConstants;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.Lint;
import com.android.tools.lint.detector.api.Issue;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.CustomEditInspectionToolsSettingsAction;
import com.intellij.codeInspection.ex.DisableInspectionToolAction;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLintExternalAnnotator extends ExternalAnnotator<State, State> {

  @Override
  public State collectionInformation(@NotNull PsiFile file) {
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module == null) {
      return null;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }

    final VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) {
      return null;
    }

    final FileType fileType = file.getFileType();
    
    if (fileType == StdFileTypes.XML) {
      if (facet.getLocalResourceManager().getFileResourceType(file) == null &&
          AndroidRootUtil.getManifestFile(module) != vFile) {
        return null;
      }
    }
    else if (fileType == FileTypes.PLAIN_TEXT) {
      if (!SdkConstants.FN_PROGUARD_CFG.equals(file.getName())) {
        return null;
      }
    }
    else if (fileType != StdFileTypes.JAVA) {
      return null;
    }

    final List<Issue> issues = getIssuesFromInspections(file.getProject(), file);
    if (issues.size() == 0) {
      return null;
    }
    return new State(module, vFile, file.getText(), issues);
  }

  @Override
  public State doAnnotate(final State state) {
    final IntellijLintClient client = new IntellijLintClient(state);
    try {
      final Lint lint = new Lint(new IssueRegistry() {
        @Override
        public List<Issue> getIssues() {
          return state.getIssues();
        }
      }, client);
      
      lint.analyze(Collections.singletonList(new File(state.getMainFile().getPath())), null);
    }
    finally {
      Disposer.dispose(client);
    }
    return state;
  }

  @NotNull
  static List<Issue> getIssuesFromInspections(@NotNull Project project, @Nullable PsiElement context) {
    final List<Issue> result = new ArrayList<Issue>();
    final BuiltinIssueRegistry fullRegistry = new BuiltinIssueRegistry();

    for (Issue issue : fullRegistry.getIssues()) {
      final String inspectionShortName = AndroidLintInspectionBase.getInspectionShortNameByIssue(issue);
      if (inspectionShortName == null) {
        continue;
      }

      final HighlightDisplayKey key = HighlightDisplayKey.find(inspectionShortName);
      if (key == null) {
        continue;
      }

      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
      final boolean enabled = context != null ? profile.isToolEnabled(key, context) : profile.isToolEnabled(key);

      if (!enabled) {
        continue;
      }
      result.add(issue);      
    }
    return result;
  } 

  @Override
  public void apply(@NotNull PsiFile file, State state, @NotNull AnnotationHolder holder) {
    if (state.isDirty()) {
      return;
    }

    for (ProblemData problemData : state.getProblems()) {
      final Issue issue = problemData.getIssue();
      final String message = problemData.getMessage();
      final TextRange range = problemData.getTextRange();
      
      if (range.getStartOffset() == range.getEndOffset()) {
        continue;
      }

      final Pair<AndroidLintInspectionBase, HighlightDisplayLevel> pair = getHighlighLevelAndInspection(issue, file);
      if (pair == null) {
        continue;
      }
      final AndroidLintInspectionBase inspection = pair.getFirst();
      final HighlightDisplayLevel displayLevel = pair.getSecond();
      
      final Annotation annotation = createAnnotation(holder, message, range, displayLevel);

      if (inspection != null) {
        final HighlightDisplayKey key = HighlightDisplayKey.find(inspection.getShortName());
        
        if (key != null) {
          annotation.registerFix(new MyDisableInspectionFix(key));
          annotation.registerFix(new CustomEditInspectionToolsSettingsAction(key, new Computable<String>() {
            @Override
            public String compute() {
              return "Edit '" + inspection.getDisplayName() + "' inspection settings";
            }
          }));
        }
      }
    }
  }

  @SuppressWarnings("deprecation")
  @NotNull
  private static Annotation createAnnotation(@NotNull AnnotationHolder holder,
                                             @NotNull String message,
                                             @NotNull TextRange range,
                                             @NotNull HighlightDisplayLevel displayLevel) {
    if (displayLevel == HighlightDisplayLevel.ERROR) {
      return holder.createErrorAnnotation(range, message);
    }
    else if (displayLevel == HighlightDisplayLevel.WEAK_WARNING ||
             displayLevel == HighlightDisplayLevel.INFO) {
      return holder.createInfoAnnotation(range, message);
    }
    else {
      return holder.createWarningAnnotation(range, message);
    }
  }

  @Nullable
  private static Pair<AndroidLintInspectionBase, HighlightDisplayLevel> getHighlighLevelAndInspection(@NotNull Issue issue,
                                                                                                      @NotNull PsiElement context) {
    final String inspectionShortName = AndroidLintInspectionBase.getInspectionShortNameByIssue(issue);
    if (inspectionShortName == null) {
      return null;
    }

    final HighlightDisplayKey key = HighlightDisplayKey.find(inspectionShortName);
    if (key == null) {
      return null;
    }

    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(context.getProject()).getInspectionProfile();
    if (!profile.isToolEnabled(key, context)) {
      return null;
    }

    final InspectionToolWrapper toolWrapper =
      (InspectionToolWrapper)profile.getInspectionTool(inspectionShortName, context);
    if (toolWrapper == null) {
      return null;
    }
    
    final AndroidLintInspectionBase inspection = (AndroidLintInspectionBase)toolWrapper.getTool();
    final HighlightDisplayLevel errorLevel = profile.getErrorLevel(key, context); 
    return new Pair<AndroidLintInspectionBase, HighlightDisplayLevel>(inspection,
                                                                      errorLevel != null ? errorLevel : HighlightDisplayLevel.WARNING);
  }

  private static class MyDisableInspectionFix implements IntentionAction, Iconable {
    private final DisableInspectionToolAction myDisableInspectionToolAction;

    private MyDisableInspectionFix(@NotNull HighlightDisplayKey key) {
      myDisableInspectionToolAction = new DisableInspectionToolAction(key);
    }

    @NotNull
    @Override
    public String getText() {
      return "Disable inspection";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      myDisableInspectionToolAction.invoke(project, editor, file);
    }

    @Override
    public boolean startInWriteAction() {
      return myDisableInspectionToolAction.startInWriteAction();
    }

    @Override
    public Icon getIcon(@IconFlags int flags) {
      return myDisableInspectionToolAction.getIcon(flags);
    }
  }
}