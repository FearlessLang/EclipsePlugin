package controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.Fs;

final class MainTest{
  private static Path folder(Path dir, String name){
    var res= dir.resolve(name);
    Fs.ensureDir(res);
    return res;
  }
  @Test void aStartedFolderIsTheProjectItself(@TempDir Path dir){
    var project= folder(dir,"myProject");
    assertEquals(project,Main.projectFolder(project.toString(),folder(dir,"manager")).orElseThrow());
  }
  @Test void aStartedFileIsTheFolderAround(@TempDir Path dir){
    var project= folder(dir,"myProject");
    var file= project.resolve("hello.fearless");
    Fs.writeUtf8(file,"anything");
    assertEquals(project,Main.projectFolder(file.toString(),folder(dir,"manager")).orElseThrow());
  }
  @Test void theManagerFolderIsNotAProject(@TempDir Path dir){
    var managerDir= folder(dir,"manager");
    assertTrue(Main.projectFolder(managerDir.toString(),managerDir).isEmpty());
  }
  @Test void aFileInTheManagerFolderIsNotAProjectEither(@TempDir Path dir){
    var managerDir= folder(dir,"manager");
    var file= managerDir.resolve("example.fearless");
    Fs.writeUtf8(file,"anything");
    assertTrue(Main.projectFolder(file.toString(),managerDir).isEmpty());
  }
  @Test void aBlankMessageIsNoProject(@TempDir Path dir){
    assertTrue(Main.projectFolder("",folder(dir,"manager")).isEmpty());
  }
}