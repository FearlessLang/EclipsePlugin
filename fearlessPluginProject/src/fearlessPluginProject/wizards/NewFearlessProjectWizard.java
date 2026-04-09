package fearlessPluginProject.wizards;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard;

public final class NewFearlessProjectWizard extends Wizard implements INewWizard{
  private IWorkbench workbench;
  private NewFearlessProjectPage page;

  public NewFearlessProjectWizard(){
    setWindowTitle("New Fearless Project");
  }

  @Override public void init(IWorkbench workbench, IStructuredSelection selection){
    this.workbench= workbench;
  }

  @Override public void addPages(){
    page= new NewFearlessProjectPage();
    addPage(page);
  }

  @Override public boolean performFinish(){
    try{
      getContainer().run(true, false, monitor -> {
        try{ createProject(page.dir(), monitor); }
        catch(Exception e){ throw new InvocationTargetException(e); }
      });
      return true;
    } catch(InvocationTargetException e){
      var t= e.getTargetException();
      var msg= t == null ? e.toString() : (t.getMessage() == null ? t.toString() : t.getMessage());
      MessageDialog.openError(getShell(), "New Fearless Project", msg);
      return false;
    } catch(InterruptedException e){
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private void createProject(File dir, IProgressMonitor monitor) throws Exception{
    var ws= ResourcesPlugin.getWorkspace();
    var root= ws.getRoot();
    var name= dir.getName();
    var project= root.getProject(name);

    var desc= ws.newProjectDescription(name);
    var chosen= Path.fromOSString(dir.getAbsolutePath());
    var defaultLoc= root.getLocation().append(name);
    if (!chosen.equals(defaultLoc)){ desc.setLocation(chosen); }

    project.create(desc, monitor);
    project.open(monitor);

    createStarterFiles(project, monitor);
    project.refreshLocal(IResource.DEPTH_INFINITE, monitor);

    var window= workbench.getActiveWorkbenchWindow();
    if (window != null){
      window.getShell().getDisplay().asyncExec(() ->
        BasicNewResourceWizard.selectAndReveal(project, window));
    }
  }

  private static void createStarterFiles(org.eclipse.core.resources.IProject project, IProgressMonitor monitor) throws Exception{
    var start= project.getFile("start.fearless");
    if (!start.exists()){ start.create(new ByteArrayInputStream(new byte[0]), true, monitor); }

    var example= project.getFolder("_example");
    if (!example.exists()){ example.create(true, true, monitor); }

    var app= project.getFile("_example/_rank_app.fear");
    if (!app.exists()){
      var text= "use base.Main as Main;\nHello: Main { sys -> sys.out.println(`hello world`) }\n";
      var bytes= text.getBytes(StandardCharsets.UTF_8);
      app.create(new ByteArrayInputStream(bytes), true, monitor);
    }
  }
}