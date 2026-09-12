package fearlessPluginProject;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.MessageConsole;

/// One Eclipse console per project, named after it, holding what the manager's
/// Output area for that project holds: compile output, run output, problems.
public final class Consoles{
  private Consoles(){}
  public static void print(String name, String text, boolean fresh){
    var manager= ConsolePlugin.getDefault().getConsoleManager();
    MessageConsole console= null;
    for (var c : manager.getConsoles()){ if (c.getName().equals(name)){ console= (MessageConsole)c; } }
    if (console == null){ console= new MessageConsole(name, null); manager.addConsoles(new IConsole[]{console}); }
    if (fresh){ console.clearConsole(); }
    try(var stream= console.newMessageStream()){ stream.print(text); }
    catch(IOException e){ throw new UncheckedIOException(e); }
    manager.showConsoleView(console);
  }
}
