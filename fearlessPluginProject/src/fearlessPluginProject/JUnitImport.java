package fearlessPluginProject;

import java.io.File;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

/// Imports a JUnit XML report (see managerInfo.JUnitReport in Coordinator) into Eclipse's
/// real JUnit view, the same mechanism the view's own Import... action uses.
/// The view brings itself to the top of its stack on every import; the part that was
/// active before is activated again right after, so a run in progress never steals
/// the focus from whatever the user is doing.
public final class JUnitImport{
  private static final String viewId= "org.eclipse.jdt.junit.ResultView";
  private JUnitImport(){}
  public static void doImport(java.nio.file.Path xmlFile){
    Display.getDefault().asyncExec(()->importNow(xmlFile.toFile()));
  }
  private static void importNow(File xmlFile){
    var win= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    if (win == null){ return; }
    var page= win.getActivePage();
    var active= page.getActivePart();
    try{ if (page.findView(viewId) == null){ page.showView(viewId, null, IWorkbenchPage.VIEW_VISIBLE); } }
    catch(PartInitException e){ throw new IllegalStateException(e); }
    try{ JUnitCore.importTestRunSession(xmlFile); }
    catch(CoreException e){ return; }
    if (active != null){ Display.getDefault().asyncExec(()->page.activate(active)); }
  }
}
