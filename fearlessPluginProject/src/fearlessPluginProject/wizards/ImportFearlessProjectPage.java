package fearlessPluginProject.wizards;

import java.io.File;

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

public final class ImportFearlessProjectPage extends WizardPage{
  private Text dirText;
  private File dir;
  public ImportFearlessProjectPage(){
    super("importFearlessProjectPage");
    setTitle("Import Fearless Project");
    setDescription("Select an existing folder to import as a project.");
  }
  public File dir(){ return dir; }
  @Override public void createControl(Composite parent){
    var c= new Composite(parent, SWT.NONE);
    c.setLayout(new GridLayout(3, false));
    new Label(c, SWT.NONE).setText("Folder:");
    dirText= new Text(c, SWT.BORDER);
    dirText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    dirText.addModifyListener(_unused -> validate());
    var browse= new Button(c, SWT.PUSH);
    browse.setText("Browse...");
    browse.addListener(SWT.Selection, _unused -> {
      var dlg= new DirectoryDialog(getShell());
      dlg.setMessage("Select an existing folder to import as a Fearless project.");
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
    var f= new File(s);
    if (!f.isDirectory()){ validateErr("Selected file is not a folder: "+s); return; }
    var name= f.getName();
    if (name.isEmpty()){ validateErr("Folder name is empty (do not select a drive root)."); return; }
    var project= ResourcesPlugin.getWorkspace().getRoot().getProject(name);
    if (project.exists()){ validateErr("A project named '"+name+"' already exists in this workspace."); return; }
    setErrorMessage(null);
    setPageComplete(true);
    dir= f;
  }
}