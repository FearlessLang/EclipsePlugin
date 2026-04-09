package fearlessPluginProject.wizards;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.core.runtime.IProgressMonitor;

public final class ImportFearlessProjectWizard extends Wizard implements IImportWizard{
  private static final String pluginId= "fearlessPluginProject";
  private ImportFearlessProjectPage page;
  public ImportFearlessProjectWizard(){
    setWindowTitle("Import Fearless Project");
    setNeedsProgressMonitor(true);
  }
  @Override public void init(IWorkbench workbench, IStructuredSelection selection){}
  @Override public void addPages(){
    page= new ImportFearlessProjectPage();
    addPage(page);
  }
  @Override public boolean performFinish(){
    var dir= page.dir();
    var name= dir.getName();
    try{
      getContainer().run(true, false, m -> {
        try{ importProject(dir, name, m); }
        catch(CoreException e){ throw new InvocationTargetException(e); }
      });
      return true;
    }
    catch(InvocationTargetException e){
      var t= e.getTargetException();
      var st= (t instanceof CoreException ce) ? ce.getStatus() : new Status(IStatus.ERROR, pluginId, t.getMessage(), t);
      ErrorDialog.openError(getShell(), "Import Fearless Project", "Failed to import project.", st);
      return false;
    }
    catch(InterruptedException e){ return false; }
  }
  private static void importProject(File dir, String name, IProgressMonitor m) throws CoreException{
    var fs= dir.listFiles(f -> f.isFile() && f.getName().endsWith(".fearless"));
    if (fs.length == 0){ throw new CoreException(new Status(IStatus.ERROR, pluginId, "No *.fearless file in project root: "+dir)); }
    var ws= ResourcesPlugin.getWorkspace();
    var root= ws.getRoot();
    var project= root.getProject(name);
    if (project.exists()){ throw new CoreException(new Status(IStatus.ERROR, pluginId, "Project '"+name+"' already exists.")); }
    var desc= ws.newProjectDescription(name);
    desc.setLocationURI(dir.toURI());
    project.create(desc, m);
    project.open(m);
    project.setDefaultCharset("UTF-8", m);
    project.refreshLocal(IResource.DEPTH_INFINITE, m);
  }
}