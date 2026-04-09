package fearlessPluginProject.launch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.widgets.Display;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

//TODO: Unreviewd AI
final class FearlessConfig{
  private static final String pluginId= "fearlessPluginProject";
  private static final String fileName= "fearless_config.txt";
  private static final String exeKey= "fearless_exe";
  private static final String template=
    "# Fearless workspace configuration\n"
    + "# Set the absolute path to the Fearless executable.\n"
    + "# Example:\n"
    + "# fearless_exe=C:\\Users\\name\\...\\fearless.exe\n"
    + "\n"
    + "fearless_exe=C:\\...\\fearless.exe\n";

  static Path fearlessExeOrNull() throws CoreException{
    var cfg= configFile();
    if (Files.notExists(cfg)){
      createTemplate(cfg);
      open(cfg);
      info(
        "Fearless setup",
        "This is the first Fearless launch in this workspace.\n\n"
        + "I created "+fileName+" in the workspace root.\n"
        + "Edit "+exeKey+" to point to your Fearless executable, then launch again."
      );
      return null;
    }
    var exe= parseExeOrNull(cfg);
    if (exe == null){ return null; }
    if (!exe.isAbsolute()){
      open(cfg);
      info(
        "Fearless setup",
        fileName+" has an invalid "+exeKey+" value.\n\n"
        + "It must be an absolute path.\n"
        + "Fix the file and launch again."
      );
      return null;
    }
    if (!Files.isRegularFile(exe)){
      open(cfg);
      info(
        "Fearless setup",
        fileName+" points to a missing file.\n\n"
        + exe+"\n\n"
        + "Fix "+exeKey+" and launch again."
      );
      return null;
    }
    return exe;
  }

  private static Path parseExeOrNull(Path cfg) throws CoreException{
    Map<String,String> kv= new HashMap<>();
    try{
      for (var raw: Files.readAllLines(cfg,StandardCharsets.UTF_8)){
        var line= raw.trim();
        if (line.isEmpty() || line.startsWith("#")){ continue; }
        int eq= line.indexOf('=');
        if (eq <= 0){
          open(cfg);
          info(
            "Fearless setup",
            fileName+" contains an invalid line:\n\n"
            + raw+"\n\n"
            + "Expected key=value, for example:\n"
            + exeKey+"=C:\\...\\fearless.exe"
          );
          return null;
        }
        var k= line.substring(0,eq).trim();
        var v= line.substring(eq+1).trim();
        kv.put(k,v);
      }
    }
    catch(IOException e){ throw new CoreException(new Status(IStatus.ERROR,pluginId,"Cannot read "+cfg,e)); }

    var v= kv.get(exeKey);
    if (v == null || v.isBlank()){
      open(cfg);
      info(
        "Fearless setup",
        fileName+" does not define "+exeKey+".\n\n"
        + "Add a line like:\n"
        + exeKey+"=C:\\...\\fearless.exe"
      );
      return null;
    }
    try{ return Path.of(v); }
    catch(RuntimeException e){
      open(cfg);
      info(
        "Fearless setup",
        fileName+" contains an invalid "+exeKey+" path:\n\n"
        + v+"\n\n"
        + "Fix it and launch again."
      );
      return null;
    }
  }

  private static void createTemplate(Path cfg) throws CoreException{
    try{ Files.writeString(cfg,template,StandardCharsets.UTF_8); }
    catch(IOException e){ throw new CoreException(new Status(IStatus.ERROR,pluginId,"Cannot create "+cfg,e)); }
  }

  private static Path configFile(){
    return ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath().resolve(fileName);
  }

  private static void open(Path cfg){
    Display.getDefault().asyncExec(() -> {
      try{
        var w= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (w == null){ return; }
        var page= w.getActivePage();
        if (page == null){ return; }
        IDE.openEditorOnFileStore(page,EFS.getStore(cfg.toUri()));
      }
      catch(CoreException ignored){}
    });
  }

  private static void info(String title,String msg){
    Display.getDefault().asyncExec(() -> {
      var w= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
      var shell= w == null ? null : w.getShell();
      MessageDialog.openInformation(shell,title,msg);
    });
  }
}