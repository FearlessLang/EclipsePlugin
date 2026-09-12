module Controller {
  requires transitive Coordinator;
  requires java.desktop;
  exports manager;
  exports managerRun;
}