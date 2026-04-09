package fearlessPluginProject.launch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.junit.JUnitCorePlugin;
import org.eclipse.jdt.internal.junit.model.JUnitModel;
import org.eclipse.jdt.internal.junit.model.TestRunSession;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.junit.model.ITestRunSession;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

final class JUnitPoller{
  private static final String junitViewId= "org.eclipse.jdt.junit.ResultView";
  private static final long periodMs= 10_000;
  private static final String sessionName= "Fearless unit tests";
  private static final Pattern archivedLogName= Pattern.compile(
    "unit_test_log\\$\\d{8}_\\d{6}_\\d{3}Z\\.log"
  );

  private final Path projectRoot;
  private final AtomicBoolean stop= new AtomicBoolean();
  private volatile Thread t;
  private String lastImportedXml;

  JUnitPoller(Path projectRoot){ this.projectRoot= projectRoot; }

  void start(){
    if (t != null){ throw new IllegalStateException("poller start twice"); }
    lastImportedXml= null;
    importXml(true,this::writeEmptyXml);
    t= new Thread(this::loop, "FearlessJUnitPoller");
    t.setDaemon(true);
    t.start();
  }

  void stopAndFinalImport(){
    stop.set(true);
    var tt= t;
    if (tt != null){ tt.interrupt(); }
    tick(true,true);
  }

  private void loop(){
    tick(false,false);
    while(!stop.get()){
      try{ Thread.sleep(periodMs); }
      catch(InterruptedException e){ /* ok */ }
      if (stop.get()){ break; }
      tick(false,false);
    }
  }

  private void tick(boolean sync,boolean allowArchivedFallback){
    Path source;
    try{ source= sourceLog(allowArchivedFallback); }
    catch(IOException e){ return; }
    if (source == null){ return; }
    importXml(sync,() -> FearlessJUnitImportXml.write(source,importXmlPath()));
  }

  @FunctionalInterface
  private interface XmlProducer{
    String produce() throws IOException;
  }

  private void importXml(boolean sync,XmlProducer producer){
    var d= Display.getDefault();
    if (d == null || d.isDisposed()){ return; }
    Runnable r= () -> {
      String xml;
      try{ xml= producer.produce(); }
      catch(IOException e){ return; }
      if (xml.equals(lastImportedXml)){ return; }

      var win= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
      if (win == null){ return; }
      var page= win.getActivePage();
      if (page == null){ return; }

      try{ page.showView(junitViewId, null, IWorkbenchPage.VIEW_VISIBLE); }
      catch(PartInitException e){ return; }

      ITestRunSession imported;
      try{ imported= JUnitCore.importTestRunSession(importXmlPath().toFile()); }
      catch(CoreException e){ return; }

      lastImportedXml= xml;
      pruneHistory(imported);
    };
    if (Display.getCurrent() != null){ r.run(); return; }
    if (sync){ d.syncExec(r); }
    else{ d.asyncExec(r); }
  }

  private String writeEmptyXml() throws IOException{
    var xml= ""
      + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<testrun name=\""+sessionName+"\" tests=\"0\" started=\"0\" failures=\"0\" errors=\"0\" ignored=\"0\">\n"
      + "<testsuite name=\""+sessionName+"\">\n"
      + "</testsuite>\n"
      + "</testrun>\n";
    var path= importXmlPath();
    Files.createDirectories(path.getParent());
    Files.writeString(path, xml, StandardCharsets.UTF_8);
    return xml;
  }

  private Path sourceLog(boolean allowArchivedFallback) throws IOException{
    var live= liveLog();
    if (Files.isRegularFile(live)){ return live; }
    if (!allowArchivedFallback){ return null; }
    return latestArchivedLog();
  }

  private Path liveLog(){
    return projectRoot.resolve(".out").resolve("logs").resolve("_base").resolve("unit_test_log.log");
  }

  private Path latestArchivedLog() throws IOException{
    var dir= liveLog().getParent();
    if (dir == null || !Files.isDirectory(dir)){ return null; }
    try(var s= Files.list(dir)){
      return s
        .filter(Files::isRegularFile)
        .filter(p -> archivedLogName.matcher(p.getFileName().toString()).matches())
        .max(Comparator.comparing(p -> p.getFileName().toString()))
        .orElse(null);
    }
  }

  private Path importXmlPath(){
    return projectRoot.resolve(".out").resolve("eclipse_plugin").resolve("unit_test_log.xml");
  }

  private static void pruneHistory(ITestRunSession keep0){
    JUnitModel model= JUnitCorePlugin.getModel();//discouraged access
    TestRunSession keep= keep0 instanceof TestRunSession ? (TestRunSession)keep0 : null;//discouraged access

    boolean keepFirst= keep == null;
    for (TestRunSession s : model.getTestRunSessions()){//discouraged access
      if (keep != null){
        if (s == keep){ continue; }
        model.removeTestRunSession(s);//discouraged access
        continue;
      }
      if (keepFirst){
        keepFirst= false;
        continue;
      }
      model.removeTestRunSession(s);//discouraged access
    }
  }
}