module Controller {
  requires transitive Coordinator;
  requires org.junit.jupiter.api;
  requires java.desktop;
  exports manager;
}