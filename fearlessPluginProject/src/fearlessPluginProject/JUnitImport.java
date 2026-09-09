package fearlessPluginProject;

import java.io.File;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

/// Imports a JUnit XML report (see managerInfo.JUnitReport in Coordinator) into Eclipse's
/// real JUnit view, the same mechanism the view's own toolbar Import... action uses.
public final class JUnitImport{
  private static final String viewId= "org.eclipse.jdt.junit.ResultView";
  private JUnitImport(){}
  public static void doImport(java.nio.file.Path xmlFile){
    var d= Display.getDefault();
    if (d == null || d.isDisposed()){ return; }
    d.asyncExec(()->importNow(xmlFile.toFile()));
  }
  private static void importNow(File xmlFile){
    var win= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    if (win == null){ return; }
    var page= win.getActivePage();
    if (page == null){ return; }
    try{ page.showView(viewId, null, IWorkbenchPage.VIEW_VISIBLE); }
    catch(Exception e){ return; }
    try{ JUnitCore.importTestRunSession(xmlFile); }
    catch(CoreException e){}
  }
}
