module kurs.source.kurs2 {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fasterxml.jackson.databind;
    requires com.opencsv;
    requires java.xml;

    // ADD THIS LINE - Export your package to JavaFX
    exports kurs.source.kurs2 to javafx.graphics, javafx.base;

    // Keep your existing opens directive
    opens kurs.source.kurs2 to javafx.fxml;
}