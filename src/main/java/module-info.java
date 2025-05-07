module com.example.finalproject {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;

    opens com.example.finalproject to javafx.fxml;
    exports com.example.finalproject;
}