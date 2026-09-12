package controller;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import controller.Info.Obj;
import controller.Info.Obj.Field;
import core.TName;
import fileSupport.StringFiles;
import realSourceOracle.RealSourceOracleWithZip;
import userMessages.UserError;
import userMessages.Violation;
import utils.Join;
import utils.OneOr;
import utils.Push;

public final class Registry{
  public enum Kind{
    idle("idle"), code("code"), dataReadOnly("data:readOnly"), dataReadWrite("data:readWrite");
    public final String text;
    Kind(String text){ this.text= text; }
    public boolean isData(){ return this == dataReadOnly || this == dataReadWrite; }
  }
  public record Entry(String alias, Path path, Kind kind, List<String> mains,
      Map<String,List<String>> reads, Map<String,List<String>> edits, long compiled, long run){
    public Entry withKind(Kind k){ return new Entry(alias,path,k,mains,reads,edits,compiled,run); }
    public Entry withMains(List<String> m){ return new Entry(alias,path,kind,List.copyOf(m),reads,edits,compiled,run); }
    public Entry withLinks(Map<String,List<String>> r, Map<String,List<String>> e){ return new Entry(alias,path,kind,mains,Map.copyOf(r),Map.copyOf(e),compiled,run); }
    Entry withTimes(long[] t){ return new Entry(alias,path,kind,mains,reads,edits,t[0],t[1]); }
  }
  private static final List<String> keys= List.of("path","kind","mains","reads","edits");
  private static final String kinds= "\"idle\", \"code\", \"data:readOnly\" or \"data:readWrite\"";
  private static final String mainShape= "a Fearless main name: a package name, a dot, then a type name, like \"hello.Hello1\"";
  private static final String typeShape= "a Fearless type name: after any leading underscores, it starts with an uppercase letter";
  private final Path dir;
  public Registry(Path dir){ this.dir= dir; }
  private Path infoFile(){ return dir.resolve("projects.info"); }
  private Path activityFile(){ return dir.resolve("activity.txt"); }
  public List<Entry> all(){
    var times= readTimes();
    return raw().stream().map(e->e.withTimes(times.getOrDefault(e.path(),new long[]{-1,-1}))).toList();
  }
  public Optional<Entry> of(Path folder){
    var f= norm(folder);
    return OneOr.opt("registered "+f, all().stream().filter(e->e.path().equals(f)));
  }
  public boolean has(Path folder){ return of(folder).isPresent(); }
  public Optional<Path> overlapping(Path folder){
    var f= norm(folder);
    return all().stream().map(Entry::path).filter(o->!o.equals(f) && (f.startsWith(o) || o.startsWith(f))).findFirst();
  }
  public void add(String alias, Path folder){
    var f= norm(folder);
    var current= raw();
    if (current.stream().anyMatch(e->e.path().equals(f))){ return; }
    assert current.stream().noneMatch(e->e.alias().equals(alias));
    assert overlapping(f).isEmpty();
    write(Push.of(current,new Entry(alias,f,Kind.idle,List.of(),Map.of(),Map.of(),-1,-1)));
  }
  public void remove(Path folder){
    var f= norm(folder);
    write(raw().stream().filter(e->!e.path().equals(f)).toList());
  }
  public void update(Path folder, UnaryOperator<Entry> op){
    var f= norm(folder);
    var current= raw();
    assert current.stream().anyMatch(e->e.path().equals(f));
    write(current.stream().map(e->e.path().equals(f) ? op.apply(e) : e).toList());
  }
  public void compiled(Path folder, long millis){ updateTimes(folder,t->new long[]{millis,t[1]}); }
  public void ran(Path folder, long millis){ updateTimes(folder,t->new long[]{t[0],millis}); }
  public String text(){ return Files.exists(infoFile()) ? read(infoFile()) : Info.print(toInfo(List.of())); }
  public void commit(String text){ write(entries(text)); }
  public Optional<String> linkProblem(Entry e){
    if (e.kind() != Kind.code){ return Optional.empty(); }
    var all= all();
    return problemIn(e.reads(),"reads",false,all).or(()->problemIn(e.edits(),"edits",true,all));
  }
  private static Optional<String> problemIn(Map<String,List<String>> links, String field, boolean needsWrite, List<Entry> all){
    for (var alias: links.keySet()){
      var target= all.stream().filter(o->o.alias().equals(alias)).findFirst();
      if (target.isEmpty()){
        return Optional.of("\""+field+"\" refers to \""+alias+"\", but no project called \""+alias+"\" is registered.");
      }
      var kind= target.get().kind();
      var wrongKind= needsWrite ? kind != Kind.dataReadWrite : !kind.isData();
      if (wrongKind){
        var needed= needsWrite ? "\"data:readWrite\"" : "\"data:readOnly\" or \"data:readWrite\"";
        return Optional.of("\""+field+"\" refers to \""+alias+"\", but the kind of \""+alias+"\" is \""+kind.text+"\"; \""+field+"\" accepts only "+needed+".");
      }
      try{ new RealSourceOracleWithZip(target.get().path()); }
      catch(UserError err){ return Optional.of("\""+field+"\" refers to \""+alias+"\", which is itself invalid:\n"+err.getMessage()); }
    }
    return Optional.empty();
  }
  private List<Entry> raw(){ return Files.exists(infoFile()) ? entries(read(infoFile())) : List.of(); }
  private List<Entry> entries(String text){ return fromInfo(text,Info.parse(text,infoFile().toUri())); }
  private void write(List<Entry> entries){ writeText(infoFile(),Info.print(toInfo(entries))); }
  private static Path norm(Path folder){ return folder.toAbsolutePath().normalize(); }
  private static String read(Path file){ return StringFiles.read(file,UserError.onFileError()); }
  private void updateTimes(Path folder, UnaryOperator<long[]> op){
    var f= norm(folder);
    var times= readTimes();
    times.put(f,op.apply(times.getOrDefault(f,new long[]{-1,-1})));
    var lines= times.entrySet().stream().map(e->e.getValue()[0]+" "+e.getValue()[1]+" "+e.getKey().toUri());
    writeText(activityFile(),Join.of(lines,"","\n","\n",""));
  }
  private Map<Path,long[]> readTimes(){
    var out= new LinkedHashMap<Path,long[]>();
    if (!Files.exists(activityFile())){ return out; }
    for (var line: read(activityFile()).lines().toList()){
      var parts= line.split(" ",3);
      out.put(Path.of(URI.create(parts[2])),new long[]{Long.parseLong(parts[0]),Long.parseLong(parts[1])});
    }
    return out;
  }
  private void writeText(Path file, String text){
    var tmp= dir.resolve(UUID.randomUUID()+".tmp");
    StringFiles.writeNew(tmp,text,UserError.onFileError());
    try{ Files.move(tmp,file,ATOMIC_MOVE); }
    catch(IOException e){ throw Violation.couldNotSaveRegisteredFolders(dir,e); }
  }
  public static List<Entry> fromInfo(String source, Info root){
    if (!(root instanceof Obj top)){
      throw Info.err(source,root.span(),"The whole file must be an object {...} mapping each project name to the metadata of that project.");
    }
    var entries= new ArrayList<Entry>();
    for (var field: top.fields()){
      if (!Names.isName(field.key())){
        throw Info.err(source,field.keySpan(),"\""+field.key()+"\" is not a valid project name: a project name uses only lowercase letters, digits and single underscores, and starts with a letter or an underscore.");
      }
      entries.add(entryOf(source,field));
    }
    for (var a: entries){
      for (var b: entries.subList(entries.indexOf(a)+1,entries.size())){
        if (!a.path().equals(b.path()) && !a.path().startsWith(b.path()) && !b.path().startsWith(a.path())){ continue; }
        var span= ((Obj)top.field(b.alias()).orElseThrow().value()).field("path").orElseThrow().value().span();
        throw Info.err(source,span,"\""+b.alias()+"\" has the same path as \""+a.alias()+"\", or one is inside the other; every file belongs to exactly one project.");
      }
    }
    return entries;
  }
  private static Entry entryOf(String source, Field field){
    if (!(field.value() instanceof Obj obj)){
      throw Info.err(source,field.value().span(),"The metadata of \""+field.key()+"\" must be an object {...}.");
    }
    for (var f: obj.fields()){
      if (!keys.contains(f.key())){ throw Info.err(source,f.keySpan(),"Unknown project attribute \""+f.key()+"\": the attributes of a project are "+Join.of(keys.stream().map(k->"\""+k+"\""),"",", ","")+"."); }
    }
    var mains= names(source,obj,"mains","\"mains\"",Registry::isMainName,mainShape);
    return new Entry(field.key(),pathOf(source,field.key(),obj),kindOf(source,obj),mains,aliasMap(source,obj,"reads"),aliasMap(source,obj,"edits"),-1,-1);
  }
  private static Path pathOf(String source, String alias, Obj obj){
    var field= obj.field("path").orElseThrow(()->Info.err(source,obj.span(),"Project \""+alias+"\" is missing its \"path\": the absolute path of the project folder."));
    var s= str(source,field.value(),"\"path\"");
    if (s.isEmpty()){ throw Info.err(source,field.value().span(),"\"path\" cannot be empty: it is the absolute path of the project folder."); }
    Path path;
    try{ path= Path.of(s); }
    catch(InvalidPathException e){ throw Info.err(source,field.value().span(),"\"path\" is not a path this system accepts: "+e.getMessage()); }
    if (!path.isAbsolute()){ throw Info.err(source,field.value().span(),"\"path\" must be an absolute path, not \""+s+"\"."); }
    return path.normalize();
  }
  private static Kind kindOf(String source, Obj obj){
    var field= obj.field("kind");
    if (field.isEmpty()){ return Kind.idle; }
    var s= str(source,field.get().value(),"\"kind\"");
    return OneOr.opt("kind "+s, List.of(Kind.values()).stream().filter(k->k.text.equals(s)))
      .orElseThrow(()->Info.err(source,field.get().value().span(),"\"kind\" must be one of "+kinds+", not \""+s+"\"."));
  }
  private static String str(String source, Info value, String label){
    if (!(value instanceof Info.Str s)){ throw Info.err(source,value.span(),label+" must be a string \"...\"."); }
    return s.value();
  }
  private static List<String> names(String source, Obj obj, String key, String label, Predicate<String> ok, String shape){
    var field= obj.field(key);
    if (field.isEmpty()){ return List.of(); }
    if (!(field.get().value() instanceof Info.Lst l)){ throw Info.err(source,field.get().value().span(),label+" must be a list [...] of strings."); }
    var seen= new LinkedHashSet<String>();
    for (var item: l.items()){
      var s= str(source,item,"Every entry in "+label);
      if (!ok.test(s)){ throw Info.err(source,item.span(),"\""+s+"\" in "+label+" is not "+shape+"."); }
      if (!seen.add(s)){ throw Info.err(source,item.span(),"\""+s+"\" is repeated in "+label+"."); }
    }
    return List.copyOf(seen);
  }
  private static Map<String,List<String>> aliasMap(String source, Obj obj, String key){
    var field= obj.field(key);
    if (field.isEmpty()){ return Map.of(); }
    if (!(field.get().value() instanceof Obj o)){
      throw Info.err(source,field.get().value().span(),"\""+key+"\" must be an object {...} mapping a project name to a list of type names.");
    }
    var out= new LinkedHashMap<String,List<String>>();
    for (var f: o.fields()){
      if (!Names.isName(f.key())){ throw Info.err(source,f.keySpan(),"\""+f.key()+"\" in \""+key+"\" is not a valid project name."); }
      out.put(f.key(),names(source,o,f.key(),"\""+key+"\".\""+f.key()+"\"",TName::isTypeName,typeShape));
    }
    return Collections.unmodifiableMap(out);
  }
  private static boolean isMainName(String s){
    var dot= s.indexOf('.');
    return dot > 0 && TName.isPkgName(s.substring(0,dot)) && TName.isTypeName(s.substring(dot+1));
  }
  public static Info toInfo(List<Entry> entries){
    return new Obj(entries.stream().map(e->new Field(e.alias(),Info.noSpan,entryToInfo(e))).toList(),Info.noSpan);
  }
  private static Info entryToInfo(Entry e){
    var fields= new ArrayList<Field>();
    fields.add(new Field("path",Info.noSpan,new Info.Str(e.path().toString().replace('\\','/'),Info.noSpan)));
    fields.add(new Field("kind",Info.noSpan,new Info.Str(e.kind().text,Info.noSpan)));
    if (!e.mains().isEmpty()){ fields.add(new Field("mains",Info.noSpan,strList(e.mains()))); }
    if (!e.reads().isEmpty()){ fields.add(new Field("reads",Info.noSpan,aliasMap(e.reads()))); }
    if (!e.edits().isEmpty()){ fields.add(new Field("edits",Info.noSpan,aliasMap(e.edits()))); }
    return new Obj(fields,Info.noSpan);
  }
  private static Info strList(List<String> xs){ return new Info.Lst(xs.stream().<Info>map(x->new Info.Str(x,Info.noSpan)).toList(),Info.noSpan); }
  private static Info aliasMap(Map<String,List<String>> m){
    return new Obj(m.entrySet().stream().map(e->new Field(e.getKey(),Info.noSpan,strList(e.getValue()))).toList(),Info.noSpan);
  }
}