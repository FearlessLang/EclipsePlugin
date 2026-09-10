package fearlessPluginProject;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.console.IConsoleConstants;

/// Registered on org.eclipse.ui.perspectives; opening it (Window > Open Perspective) is
/// what FearlessWatcher treats as "the manager is allowed to touch this workspace" - see
/// FearlessWatcher.fearlessActive().
public final class FearlessPerspective implements IPerspectiveFactory{
  public static final String id= "fearlessPluginProject.perspective";
  private static final String junitView= "org.eclipse.jdt.junit.ResultView";
  @Override public void createInitialLayout(IPageLayout layout){
    var editorArea= layout.getEditorArea();
    IFolderLayout left= layout.createFolder("fearlessPluginProject.left", IPageLayout.LEFT, 0.25f, editorArea);
    left.addView(IPageLayout.ID_PROJECT_EXPLORER);
    IFolderLayout bottom= layout.createFolder("fearlessPluginProject.bottom", IPageLayout.BOTTOM, 0.7f, editorArea);
    bottom.addView(IPageLayout.ID_PROBLEM_VIEW);
    bottom.addView(IConsoleConstants.ID_CONSOLE_VIEW);
    bottom.addView(junitView);
    layout.addShowViewShortcut(IPageLayout.ID_PROJECT_EXPLORER);
    layout.addShowViewShortcut(IPageLayout.ID_PROBLEM_VIEW);
    layout.addShowViewShortcut(IConsoleConstants.ID_CONSOLE_VIEW);
    layout.addShowViewShortcut(junitView);
    layout.addPerspectiveShortcut(id);
  }
}
