package fearlessPluginProject.wizards;

import java.io.File;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public final class NewFearlessProjectPage extends WizardPage{
  private Text dirText;
  private File dir;

  public NewFearlessProjectPage(){
    super("newFearlessProjectPage");
    setTitle("New Fearless Project");
    setDescription("Choose an empty folder for the new Fearless project.");
  }

  public File dir(){ return dir; }

  @Override public void createControl(Composite parent){
    var c= new Composite(parent, SWT.NONE);
    c.setLayout(new GridLayout(3, false));

    var hint= new Label(c, SWT.WRAP);
    hint.setText(
      "Pick an empty folder anywhere on disk. Outside the workspace is recommended. "
      + "You can also type a new folder path that does not exist yet.");
    hint.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

    new Label(c, SWT.NONE).setText("Folder:");

    dirText= new Text(c, SWT.BORDER);
    dirText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    dirText.addModifyListener(_unused -> validate());

    var browse= new Button(c, SWT.PUSH);
    browse.setText("Browse...");
    browse.addListener(SWT.Selection, _unused -> {
      var dlg= new DirectoryDialog(getShell());
      dlg.setMessage("Select an existing empty folder, or select a parent and then type a new child folder path.");
      var sel= dlg.open();
      if (sel != null){ dirText.setText(sel); }
    });

    setControl(c);
    setPageComplete(false);
  }

  private void validateErr(String msg){
    setErrorMessage(msg);
    setPageComplete(false);
    dir= null;
  }

  private void validate(){
    var s= dirText.getText().trim();
    if (s.isEmpty()){ validateErr("Select a folder."); return; }

    var f= new File(s).getAbsoluteFile();
    var name= f.getName();
    if (name.isEmpty()){ validateErr("Folder name is empty. Do not select a drive root."); return; }

    if (f.exists()){
      if (!f.isDirectory()){ validateErr("Selected path is not a folder: "+s); return; }
      var children= f.list();
      if (children == null){ validateErr("Cannot read folder contents: "+s); return; }
      if (children.length != 0){ validateErr("Folder is not empty: "+s); return; }
    } else {
      var parent= f.getParentFile();
      if (parent == null || !parent.isDirectory()){ validateErr("Parent folder does not exist: "+s); return; }
    }

    var ws= ResourcesPlugin.getWorkspace();
    var root= ws.getRoot();

    var nameStatus= ws.validateName(name, IResource.PROJECT);
    if (!nameStatus.isOK()){ validateErr(nameStatus.getMessage()); return; }

    var project= root.getProject(name);
    if (project.exists()){
      validateErr("A project named '"+name+"' already exists in this workspace.");
      return;
    }

    var locStatus= ws.validateProjectLocationURI(project, f.toURI());
    if (!locStatus.isOK()){ validateErr(locStatus.getMessage()); return; }

    setErrorMessage(null);
    setPageComplete(true);
    dir= f;
  }
}