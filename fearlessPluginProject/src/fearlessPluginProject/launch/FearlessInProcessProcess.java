package fearlessPluginProject.launch;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.IHyperlink;
import org.eclipse.ui.console.IPatternMatchListener;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.PatternMatchEvent;
import org.eclipse.ui.console.TextConsole;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.statushandlers.StatusManager;
import org.eclipse.ui.texteditor.ITextEditor;

import fearlessPluginProject.core.FearlessProjects;

final class _OLD_FearlessInProcessProcess extends PlatformObject implements IProcess{
  private static final String pluginId= "fearlessPluginProject";

  private final ILaunch launch;
  private final String label;
  private final MessageConsole con;
  private final Map<String,String> attrs= new HashMap<>();
  private final AtomicBoolean done= new AtomicBoolean();
  private final AtomicBoolean cancelRequested= new AtomicBoolean();

  private volatile Thread t;
  private volatile int exitValue;
  private volatile JUnitPoller junitPoller;

  _OLD_FearlessInProcessProcess(ILaunch launch,String label,MessageConsole con){
    this.launch= launch;
    this.label= label;
    this.con= con;
  }

  void start(String projectPath){
    if (t != null){ throw new IllegalStateException("start twice"); }
    junitPoller= new JUnitPoller(Path.of(projectPath));
    junitPoller.start();
    t= new Thread(() -> run0(projectPath), "FearlessRun");
    t.setDaemon(true);
    t.start();
  }

  private void run0(String projectPath){
    var projectRoot= Path.of(projectPath);

    Display.getDefault().syncExec(() -> Links.install(con,projectRoot));

    PrintStream oldOut= System.out, oldErr= System.err;
    try(var ms= con.newMessageStream();
        var ps= new PrintStream(ms,true,StandardCharsets.UTF_8)){
      ms.setActivateOnWrite(true);
      ms.setEncoding("UTF-8");

      System.setOut(ps);
      System.setErr(ps);

      _OLD_FearlessReflectiveRunner.runFearless(projectRoot);
      exitValue= 0;
    }
    catch(InterruptedException e){
      exitValue= 143;
      return;
    }
    catch(RuntimeException | Error e){
      exitValue= 1;
      throw e;
    }
    catch(IOException e){ throw new UncheckedIOException(e); }
    finally{
      try{
        var p= junitPoller;
        if (p != null){ p.stopAndFinalImport(); }
      }
      finally{
        System.setOut(oldOut);
        System.setErr(oldErr);
        fireTerminateOnce();
      }
    }
  }

  private void fireTerminateOnce(){
    if (!done.compareAndSet(false,true)){ return; }
    DebugPlugin.getDefault().fireDebugEventSet(new DebugEvent[]{ new DebugEvent(this,DebugEvent.TERMINATE) });
  }

  @Override public String getLabel(){ return label; }
  @Override public ILaunch getLaunch(){ return launch; }
  @Override public boolean isTerminated(){ return done.get(); }
  @Override public boolean canTerminate(){ return !done.get() && !cancelRequested.get(); }

  @Override public void terminate() throws DebugException{
    if (!cancelRequested.compareAndSet(false,true)){ return; }
    var tt= t;
    if (tt != null){ tt.interrupt(); }
  }

  @Override public IStreamsProxy getStreamsProxy(){ return null; }

  @Override public int getExitValue() throws DebugException{
    if (!done.get()){
      throw new DebugException(new Status(IStatus.ERROR, pluginId, "Not terminated"));
    }
    return exitValue;
  }

  @Override public void setAttribute(String key,String value){ attrs.put(key,value); }
  @Override public String getAttribute(String key){ return attrs.get(key); }

  private static final class Links{
    private static final Pattern stackCapture= Pattern.compile(
      "(?:error\\s+)?line:\\s*(\\d+)\\s*in file\\s*([a-z0-9_./]+\\.fear)\\b"
    );
    private static final Pattern stackDetect= Pattern.compile(
      "(?:error\\s+)?line:\\s*\\d+\\s*in file\\s*[a-z0-9_./]+\\.fear\\b"
    );
    private static final Pattern inFileCapture= Pattern.compile(
      "In file:\\s*(?:fear:/)?([a-z0-9_./]+\\.fear)\\b"
    );
    private static final Pattern numberedLine= Pattern.compile("^(\\d{3,})\\|");
    private static final Pattern caretLine= Pattern.compile("^\\s*\\|\\s*\\^+");

    static void install(MessageConsole con,Path projectRoot){
      con.addPatternMatchListener(new StackFrames(con,projectRoot));
      con.addPatternMatchListener(new InFileHeaders(con,projectRoot));
    }

    private static final class StackFrames implements IPatternMatchListener{
      private final MessageConsole con;
      private final Path projectRoot;
      private volatile TextConsole console;

      StackFrames(MessageConsole con,Path projectRoot){
        this.con= con;
        this.projectRoot= projectRoot;
      }

      @Override public void connect(TextConsole console){ this.console= console; }
      @Override public void disconnect(){ this.console= null; }
      @Override public String getPattern(){ return stackDetect.pattern(); }
      @Override public int getCompilerFlags(){ return 0; }
      @Override public String getLineQualifier(){ return null; }

      @Override public void matchFound(PatternMatchEvent event){
        var c= console;
        if (c == null){ return; }
        try{
          var doc= c.getDocument();
          var off= event.getOffset();
          var len= event.getLength();
          var s= doc.get(off,len);

          var m= stackCapture.matcher(s);
          if (!m.find()){ return; }

          var line= Integer.parseInt(m.group(1));
          var rel= m.group(2);
          var relOff= off + m.start(2);

          con.addHyperlink(new FileLineLink(projectRoot,rel,line), relOff, rel.length());
        }
        catch(BadLocationException e){ throw new IllegalStateException(e); }
      }
    }

    private static final class InFileHeaders implements IPatternMatchListener{
      private final MessageConsole con;
      private final Path projectRoot;
      private volatile TextConsole console;

      InFileHeaders(MessageConsole con,Path projectRoot){
        this.con= con;
        this.projectRoot= projectRoot;
      }

      @Override public void connect(TextConsole console){ this.console= console; }
      @Override public void disconnect(){ this.console= null; }
      @Override public String getPattern(){ return inFileCapture.pattern(); }
      @Override public int getCompilerFlags(){ return 0; }
      @Override public String getLineQualifier(){ return null; }

      @Override public void matchFound(PatternMatchEvent event){
        var c= console;
        if (c == null){ return; }
        try{
          var doc= c.getDocument();
          var off= event.getOffset();
          var len= event.getLength();
          var s= doc.get(off,len);

          var m= inFileCapture.matcher(s);
          if (!m.find()){ return; }

          var rel= m.group(1);
          var relOff= off + m.start(1);

          con.addHyperlink(new InFileLink(c,projectRoot,off,rel), relOff, rel.length());
        }
        catch(BadLocationException e){ throw new IllegalStateException(e); }
      }
    }

    private record FileLineLink(Path projectRoot,String rel,int line1) implements IHyperlink{
      @Override public void linkActivated(){ openAt(projectRoot.resolve(rel), line1); }
      @Override public void linkEntered(){}
      @Override public void linkExited(){}
    }

    private record InFileLink(TextConsole console,Path projectRoot,int headerOffset,String rel) implements IHyperlink{
      @Override public void linkActivated(){
        var line= inferLineFromCaret(console.getDocument(), headerOffset);
        openAt(projectRoot.resolve(rel), line);
      }
      @Override public void linkEntered(){}
      @Override public void linkExited(){}
    }

    private static int inferLineFromCaret(IDocument doc,int headerOffset){
      try{
        var startLine= doc.getLineOfOffset(headerOffset);
        Integer lastNum= null;

        for (int i=startLine+1;i<doc.getNumberOfLines();++i){
          var li= doc.getLineInformation(i);
          var s= doc.get(li.getOffset(), li.getLength());

          var m1= numberedLine.matcher(s);
          if (m1.find()){
            lastNum= Integer.parseInt(m1.group(1));
            continue;
          }
          if (caretLine.matcher(s).find()){
            if (lastNum == null){ throw new IllegalStateException("Caret without numbered line"); }
            return lastNum;
          }
          if (lastNum != null && !s.isBlank()){ break; }
        }
        throw new IllegalStateException("No caret line after In file:");
      }
      catch(BadLocationException e){ throw new IllegalStateException(e); }
    }

    private static void openAt(Path abs,int line1){
      try{
        var files= ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(abs.toUri());
        if (files.length != 1){ throw new IllegalStateException("Expected 1 IFile for "+abs+" got "+files.length); }
        IFile file= files[0];

        var win= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (win == null){ throw new IllegalStateException("No active workbench window"); }
        var page= win.getActivePage();
        if (page == null){ throw new IllegalStateException("No active workbench page"); }

        var part= IDE.openEditor(page,file,true);
        if (part instanceof ITextEditor te){
          var doc= te.getDocumentProvider().getDocument(te.getEditorInput());
          revealLine(te,doc,line1);
        }
      }
      catch(PartInitException e){ throw new IllegalStateException(e); }
    }

    private static void revealLine(ITextEditor te,IDocument doc,int line1){
      try{
        var line0= line1-1;
        var info= doc.getLineInformation(line0);
        te.selectAndReveal(info.getOffset(), info.getLength());
      }
      catch(BadLocationException e){ throw new IllegalStateException("Bad line "+line1, e); }
    }
  }
}

//-----
final class _OLD_FearlessLaunchDelegate implements org.eclipse.debug.core.model.ILaunchConfigurationDelegate{
  private static final String pluginId= "fearlessPluginProject";

  @Override public void launch(ILaunchConfiguration c,String mode,ILaunch launch,IProgressMonitor m) throws CoreException{
    cleanOldFearlessLaunchesOrThrowIfRunning(launch);

    var projectName= c.getAttribute(FearlessLaunchConfig.attrProject, "");
    if (projectName.isEmpty()){ throw err("Missing project attribute"); }
    var p= ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
    if (!p.isAccessible()){ throw err("Project not accessible: "+projectName); }
    if (!FearlessProjects.isFearlessProject(p)){ throw err("Not a Fearless project: "+projectName); }

    var con= console("Fearless");
    con.clearConsole();
    showConsole(con);
    showDebugView();

    var proc= new _OLD_FearlessInProcessProcess(launch,"Fearless - "+p.getName(),con);
    launch.addProcess(proc);
    DebugPlugin.getDefault().fireDebugEventSet(new DebugEvent[]{ new DebugEvent(proc,DebugEvent.CREATE) });

    proc.start(p.getLocation().toOSString());
  }
  private static void showDebugView(){
    Display.getDefault().asyncExec(() -> {
      try{
        IWorkbenchWindow w= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (w == null){ return; }
        IWorkbenchPage page= w.getActivePage();
        if (page == null){ return; }
        page.showView(org.eclipse.debug.ui.IDebugUIConstants.ID_DEBUG_VIEW);
      }
      catch(PartInitException ignored){}
    });
  }
  private static void cleanOldFearlessLaunchesOrThrowIfRunning(ILaunch current) throws CoreException{
    var mgr= DebugPlugin.getDefault().getLaunchManager();
    var dead= new ArrayList<ILaunch>();
    for (var l: mgr.getLaunches()){
      if (l == current){ continue; }

      var lc= l.getLaunchConfiguration();
      if (lc == null){ continue; }
      try{
        if (!FearlessLaunchConfig.typeId.equals(lc.getType().getIdentifier())){ continue; }
      }
      catch(CoreException e){ continue; }

      if (!l.isTerminated()){ throw err("Fearless already running"); }
      dead.add(l);
    }
    if (!dead.isEmpty()){ mgr.removeLaunches(dead.toArray(ILaunch[]::new)); }
  }

  private static CoreException err(String msg){
    var st= new Status(IStatus.ERROR, pluginId, msg);
    popup(st);
    return new CoreException(st);
  }
  private static void popup(IStatus st){
    Display.getDefault().asyncExec(() ->
      StatusManager.getManager().handle(st, StatusManager.SHOW | StatusManager.BLOCK)
    );
  }
  private static MessageConsole console(String name){
    var mgr= ConsolePlugin.getDefault().getConsoleManager();
    for (var c: mgr.getConsoles()){
      if (c instanceof MessageConsole mc && name.equals(mc.getName())){ return mc; }
    }
    var mc= new MessageConsole(name, null);
    mgr.addConsoles(new IConsole[]{mc});
    return mc;
  }
  private static void showConsole(MessageConsole con){
    Display.getDefault().asyncExec(() -> {
      try{
        var w= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (w == null){ return; }
        var page= w.getActivePage();
        if (page == null){ return; }
        var v= (IConsoleView)page.showView(IConsoleConstants.ID_CONSOLE_VIEW);
        v.display(con);
      }
      catch(PartInitException ignored){}
    });
  }
}