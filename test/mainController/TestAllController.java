// java --module-path ../../Commons/Commons.jar --add-modules Commons mainController/TestAllController.java
package mainController;

public class TestAllController{
  public static void main(String[] args) throws InterruptedException{
    ControllerBuild.baseJars();
    ControllerBuild.coordinatorJar();
    ControllerBuild.controllerTest();
    ControllerBuild.runJUnit(ControllerBuild.out.resolve("controller-test"));
  }
}