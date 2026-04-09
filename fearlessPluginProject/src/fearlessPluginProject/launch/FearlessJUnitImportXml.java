package fearlessPluginProject.launch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class FearlessJUnitImportXml{
  private static final String sessionName="Fearless unit tests";

  static String write(Path sourceLog,Path destXml) throws IOException{
    var raw= Files.readString(sourceLog,StandardCharsets.UTF_8);
    var plans= new ArrayList<Plan>();
    var completed= new ArrayList<String>();
    scan(raw,plans,completed);

    var rendered= new ArrayList<String>();
    int runPlans= 0;
    int completedIx= 0;
    for (var p : plans){
      if (p.kind.equals("DISABLED")){
        rendered.add(disabledCaseXml(p));
        continue;
      }
      if (!p.kind.equals("RUN")){ continue; }
      runPlans += 1;
      rendered.add(completedIx<completed.size() ? completed.get(completedIx++) : incompleteCaseXml(p));
    }
    while(completedIx<completed.size()){
      rendered.add(completed.get(completedIx++));
    }

    var tests= rendered.size();
    var started= completed.size();
    var failures= count(rendered,"<failure");
    var errors= count(rendered,"<error");
    var ignored= count(rendered," ignored=\"true\"") + count(rendered,"<skipped");
    var incomplete= started<runPlans;
    var suiteBody= String.join("\n",rendered);
    var nl= suiteBody.isEmpty() || suiteBody.endsWith("\n") ? "" : "\n";

    var xml=
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<testrun name=\""+esc(sessionName)+"\" tests=\""+tests+"\" started=\""+started
      + "\" failures=\""+failures+"\" errors=\""+errors+"\" ignored=\""+ignored+"\">\n"
      + "<testsuite name=\""+esc(sessionName)+"\""+(incomplete ? " incomplete=\"true\"" : "")+">\n"
      + suiteBody + nl
      + "</testsuite>\n"
      + "</testrun>\n";

    var parent= destXml.getParent();
    if (parent != null){ Files.createDirectories(parent); }
    Files.writeString(destXml,xml,StandardCharsets.UTF_8);
    return xml;
  }

  private static void scan(String raw,List<Plan> plans,List<String> completed){
    int from= 0;
    for(;;){
      var p= nextPlan(raw,from);
      var t= raw.indexOf("<testcase",from);
      if (p == -1 && t == -1){ return; }
      if (p != -1 && (t == -1 || p<t)){
        var lineEnd= raw.indexOf('\n',p);
        var end= lineEnd == -1 ? raw.length() : lineEnd;
        var line= raw.substring(p,end);
        if (line.endsWith("\r")){ line= line.substring(0,line.length()-1); }
        var plan= parsePlan(line);
        if (plan != null){ plans.add(plan); }
        from = lineEnd == -1 ? raw.length() : lineEnd + 1;
        continue;
      }
      var end= raw.indexOf("</testcase>",t);
      if (end == -1){ return; }
      end += "</testcase>".length();
      completed.add(raw.substring(t,end));
      from = end;
    }
  }

  private static int nextPlan(String raw,int from){
    int i= raw.indexOf("PLAN|",from);
    while(i != -1 && !isLineStart(raw,i)){
      i= raw.indexOf("PLAN|",i + 5);
    }
    return i;
  }

  private static boolean isLineStart(String raw,int i){
    return i == 0 || raw.charAt(i - 1) == '\n' || raw.charAt(i - 1) == '\r';
  }

  private static Plan parsePlan(String line){
    if (!line.startsWith("PLAN|")){ return null; }
    int p1= line.indexOf('|',5);
    if (p1 == -1){ return null; }
    int p2= line.indexOf('|',p1 + 1);
    if (p2 == -1){ return null; }
    int p3= line.indexOf('|',p2 + 1);
    if (p3 == -1){ return null; }
    int p4= line.lastIndexOf('|');
    if (p4 <= p3){ return null; }

    var kind= line.substring(5,p1);
    if (!kind.equals("RUN") && !kind.equals("DISABLED")){ return null; }

    var file= line.substring(p1 + 1,p2);
    var className= line.substring(p2 + 1,p3);
    var name= line.substring(p3 + 1,p4);
    var lineNo= line.substring(p4 + 1);
    return new Plan(kind,file,className,name,lineNo);
  }

  private static String incompleteCaseXml(Plan p){
    return "<testcase classname=\""+esc(p.className)+"\" name=\""+esc(p.name)+"\" file=\""+esc(p.file)
      + "\" line=\""+esc(p.lineNo)+"\" incomplete=\"true\"/>";
  }

  private static String disabledCaseXml(Plan p){
    return "<testcase classname=\""+esc(p.className)+"\" name=\""+esc(p.name)+"\" file=\""+esc(p.file)
      + "\" line=\""+esc(p.lineNo)+"\" ignored=\"true\"><skipped/></testcase>";
  }

  private static int count(List<String> xs,String needle){
    int n= 0;
    for (var x : xs){ n += count(x,needle); }
    return n;
  }

  private static int count(String s,String needle){
    int n= 0;
    for (int from= 0;;){
      var i= s.indexOf(needle,from);
      if (i == -1){ return n; }
      n += 1;
      from = i + needle.length();
    }
  }
  private static String esc(String s){
    return s
      .replace("&","&amp;")
      .replace("\"","&quot;")
      .replace("<","&lt;")
      .replace(">","&gt;");
  }
  private record Plan(String kind,String file,String className,String name,String lineNo){}
}