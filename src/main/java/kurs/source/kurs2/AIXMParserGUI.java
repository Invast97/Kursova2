package kurs.source.kurs2;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

// Розширений AIXMRecord із повним зберіганням даних
record AIXMRecord(String tagName, String gmlId, String name, String type, String description,
                  Map<String, String> additionalData, Map<String, String> timeData,
                  Map<String, String> geometryData, Map<String, String> validationData) {
    public AIXMRecord {
        Objects.requireNonNull(tagName, "Tag name cannot be null");
        Objects.requireNonNull(gmlId, "GML ID cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(description, "Description cannot be null");
        Objects.requireNonNull(additionalData, "Additional data cannot be null");
        Objects.requireNonNull(timeData, "Time data cannot be null");
        Objects.requireNonNull(geometryData, "Geometry data cannot be null");
        Objects.requireNonNull(validationData, "Validation data cannot be null");
    }


    // цей треба закоментувати бо я маю покращений
    /*
    public AIXMRecord(String tagName, String gmlId) {
        this(tagName, gmlId, "", "", "", new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }
     */
}

public class AIXMParserGUI extends Application {

    private TabPane tabPane;
    private Label statusLabel;
    private Button loadDirectoryButton;
    private Map<String, List<AIXMRecord>> parsedData = new HashMap<>();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("AIXM Multi-Parser - Enhanced");

        VBox root = createMainLayout();
        Scene scene = new Scene(root, 1400, 900);

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private VBox createMainLayout() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        // Заголовок
        HBox headerBox = createHeaderSection();

        // Секція для статусу
        // нехай стилі будуть поки тут, потім перенесу в файл
        statusLabel = new Label("Готово до роботи. Оберіть директорію з AIXM файлами.");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        // Таб для різних AIXM типів
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        root.getChildren().addAll(headerBox, statusLabel, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        return root;
    }

    private HBox createHeaderSection() {
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(10));
        headerBox.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 5;");

        Label titleLabel = new Label("AIXM Parser & Viewer - Enhanced");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        loadDirectoryButton = new Button("Обрати директорію");
        loadDirectoryButton.setOnAction(e -> loadDirectory());
        loadDirectoryButton.setStyle("-fx-font-size: 12px; -fx-padding: 8 15;");

        Button refreshButton = new Button("Оновити");
        refreshButton.setOnAction(e -> refreshCurrentDirectory());
        refreshButton.setStyle("-fx-font-size: 12px; -fx-padding: 8 15;");

        headerBox.getChildren().addAll(titleLabel, loadDirectoryButton, refreshButton);

        return headerBox;
    }

    private void loadDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Оберіть директорію з AIXM файлами");

        File selectedDirectory = directoryChooser.showDialog(null);
        if (selectedDirectory != null) {
            processDirectory(selectedDirectory);
        }
    }

    // Кнопка "Оновити"
    //без функціоналу
    private void refreshCurrentDirectory() {
        statusLabel.setText("Функція оновлення буде реалізована пізніше");
    }

    private void processDirectory(File directory) {
        statusLabel.setText("Обробка директорії: " + directory.getAbsolutePath());

        try {
            Map<String, List<AIXMRecord>> fileResults = AIXMMultiParser.parseDirectory(directory);
            Map<String, List<AIXMRecord>> groupedByType = groupRecordsByType(fileResults);

            this.parsedData = groupedByType;
            createTabsForTypes(groupedByType);

            int totalRecords = groupedByType.values().stream().mapToInt(List::size).sum();
            statusLabel.setText(String.format("Завантажено %d типів AIXM даних (%d записів) з %d файлів",
                    groupedByType.size(), totalRecords, fileResults.size()));

        } catch (Exception e) {
            statusLabel.setText("Помилка при обробці директорії: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Map<String, List<AIXMRecord>> groupRecordsByType(Map<String, List<AIXMRecord>> fileResults) {
        return fileResults.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(
                        record -> extractTypeFromTag(record.tagName()),
                        Collectors.toList()
                ));
    }

    private String extractTypeFromTag(String tagName) {
        return tagName.contains(":") ?
                tagName.substring(tagName.indexOf(":") + 1) :
                tagName;
    }

    private void createTabsForTypes(Map<String, List<AIXMRecord>> groupedData) {
        tabPane.getTabs().clear();

        groupedData.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String typeName = entry.getKey();
                    List<AIXMRecord> records = entry.getValue();

                    Tab tab = createTabForType(typeName, records);
                    tabPane.getTabs().add(tab);
                });
    }

    private Tab createTabForType(String typeName, List<AIXMRecord> records) {
        Tab tab = new Tab(String.format("%s (%d)", typeName, records.size()));

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        TableView<AIXMRecord> table = createEnhancedTableForRecords(records);
        HBox exportBox = createExportButtons(typeName, records);

        content.getChildren().addAll(exportBox, table);
        VBox.setVgrow(table, Priority.ALWAYS);

        tab.setContent(content);
        return tab;
    }

    private TableView<AIXMRecord> createEnhancedTableForRecords(List<AIXMRecord> records) {
        TableView<AIXMRecord> table = new TableView<>();

        // Основні колонки таблиці
        TableColumn<AIXMRecord, String> tagColumn = new TableColumn<>("Тип");
        tagColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(extractTypeFromTag(cellData.getValue().tagName())));
        tagColumn.setPrefWidth(120);

        TableColumn<AIXMRecord, String> idColumn = new TableColumn<>("GML ID");
        idColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().gmlId()));
        idColumn.setPrefWidth(180);

        TableColumn<AIXMRecord, String> nameColumn = new TableColumn<>("Назва");
        nameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().name().isEmpty() ? "—" : cellData.getValue().name()));
        nameColumn.setPrefWidth(150);

        TableColumn<AIXMRecord, String> typeColumn = new TableColumn<>("Підтип");
        typeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().type().isEmpty() ? "—" : cellData.getValue().type()));
        typeColumn.setPrefWidth(100);

        // Колонки дати/час
        TableColumn<AIXMRecord, String> timeColumn = new TableColumn<>("Часові дані");
        timeColumn.setCellValueFactory(cellData -> {
            Map<String, String> timeData = cellData.getValue().timeData();
            if (timeData.isEmpty()) return new SimpleStringProperty("—");
            String timeInfo = timeData.entrySet().stream()
                    .limit(2)
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .collect(Collectors.joining("; "));
            return new SimpleStringProperty(timeInfo);
        });
        timeColumn.setPrefWidth(200);

        // Геометрія ( розташування) колонка
        TableColumn<AIXMRecord, String> geomColumn = new TableColumn<>("Геометрія");
        geomColumn.setCellValueFactory(cellData -> {
            Map<String, String> geomData = cellData.getValue().geometryData();
            if (geomData.isEmpty()) return new SimpleStringProperty("—");
            String geomInfo = geomData.entrySet().stream()
                    .limit(2)
                    .map(entry -> entry.getKey() + ": " +
                            (entry.getValue().length() > 30 ? entry.getValue().substring(0, 30) + "..." : entry.getValue()))
                    .collect(Collectors.joining("; "));
            return new SimpleStringProperty(geomInfo);
        });
        geomColumn.setPrefWidth(200);

        // додаткові дані
        TableColumn<AIXMRecord, String> additionalColumn = new TableColumn<>("Додаткові дані");
        additionalColumn.setCellValueFactory(cellData -> {
            Map<String, String> additionalData = cellData.getValue().additionalData();
            if (additionalData.isEmpty()) return new SimpleStringProperty("—");
            String info = additionalData.entrySet().stream()
                    .limit(3)
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .collect(Collectors.joining("; "));
            return new SimpleStringProperty(info);
        });
        additionalColumn.setPrefWidth(250);

        // валідація колонок
        TableColumn<AIXMRecord, String> validationColumn = new TableColumn<>("Валідація");
        validationColumn.setCellValueFactory(cellData -> {
            Map<String, String> validationData = cellData.getValue().validationData();
            if (validationData.isEmpty()) return new SimpleStringProperty("—");
            String validationInfo = validationData.entrySet().stream()
                    .limit(2)
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .collect(Collectors.joining("; "));
            return new SimpleStringProperty(validationInfo);
        });
        validationColumn.setPrefWidth(180);

        table.getColumns().addAll(tagColumn, idColumn, nameColumn, typeColumn,
                timeColumn, geomColumn, additionalColumn, validationColumn);

        ObservableList<AIXMRecord> data = FXCollections.observableArrayList(records);
        table.setItems(data);

        return table;
    }

    private HBox createExportButtons(String typeName, List<AIXMRecord> records) {
        HBox exportBox = new HBox(10);
        exportBox.setAlignment(Pos.CENTER_LEFT);
        exportBox.setPadding(new Insets(5));

        Button csvButton = new Button("Експорт CSV");
        csvButton.setOnAction(e -> exportToCSV(typeName, records));

        Button jsonButton = new Button("Експорт JSON");
        jsonButton.setOnAction(e -> exportToJSON(typeName, records));

        Label exportLabel = new Label("Експорт даних:");
        exportLabel.setStyle("-fx-font-weight: bold;");

        exportBox.getChildren().addAll(exportLabel, csvButton, jsonButton);

        return exportBox;
    }

    //Експорт CSV
    private void exportToCSV(String typeName, List<AIXMRecord> records) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Зберегти CSV файл");
        fileChooser.setInitialFileName(typeName + "_export.csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                // покращений заголовок
                writer.write("Tag Name,GML ID,Name,Type,Description,Time Data,Geometry Data,Additional Data,Validation Data\n");

                // покращена дата імпорт
                for (AIXMRecord record : records) {
                    String timeInfo = formatMapForCSV(record.timeData());
                    String geomInfo = formatMapForCSV(record.geometryData());
                    String additionalInfo = formatMapForCSV(record.additionalData());
                    String validationInfo = formatMapForCSV(record.validationData());

                    writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            record.tagName(), record.gmlId(), record.name(),
                            record.type(), record.description(), timeInfo, geomInfo, additionalInfo, validationInfo));
                }

                statusLabel.setText("CSV експорт завершено: " + file.getAbsolutePath());

            } catch (IOException e) {
                statusLabel.setText("Помилка при експорті CSV: " + e.getMessage());
            }
        }
    }

    //Форматер map CSV
    private String formatMapForCSV(Map<String, String> map) {
        return map.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    //експорт джсон
    private void exportToJSON(String typeName, List<AIXMRecord> records) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Зберегти JSON файл");
        fileChooser.setInitialFileName(typeName + "_export.json");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json"));

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode root = mapper.createObjectNode();

                root.put("type", typeName);
                root.put("count", records.size());
                root.put("exportDate", new Date().toString());

                ArrayNode recordsArray = mapper.createArrayNode();
                for (AIXMRecord record : records) {
                    ObjectNode recordNode = mapper.createObjectNode();
                    recordNode.put("tagName", record.tagName());
                    recordNode.put("gmlId", record.gmlId());
                    recordNode.put("name", record.name());
                    recordNode.put("type", record.type());
                    recordNode.put("description", record.description());

                    //  повна дата для категорій
                    recordNode.set("timeData", mapper.valueToTree(record.timeData()));
                    recordNode.set("geometryData", mapper.valueToTree(record.geometryData()));
                    recordNode.set("additionalData", mapper.valueToTree(record.additionalData()));
                    recordNode.set("validationData", mapper.valueToTree(record.validationData()));

                    recordsArray.add(recordNode);
                }

                root.set("records", recordsArray);

                mapper.writerWithDefaultPrettyPrinter().writeValue(file, root);
                statusLabel.setText("JSON експорт завершено: " + file.getAbsolutePath());

            } catch (IOException e) {
                statusLabel.setText("Помилка при експорті JSON: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

// Покращений AIXMMultiParser із комплексним вилученням даних
// Функція,яка за раз все парсить ( кожен файл маппер)
class AIXMMultiParser {

    public static Map<String, List<AIXMRecord>> parseDirectory(File dir) {
        return Optional.of(dir)
                .filter(File::isDirectory)
                .map(File::listFiles)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(f -> f.getName().endsWith(".BASELINE"))
                .parallel()
                .collect(Collectors.toConcurrentMap(
                        File::getName,
                        AIXMMultiParser::parseFile,
                        (existing, replacement) -> existing
                ));
    }

    public static List<AIXMRecord> parseFile(File xmlFile) {
        return parseDocument(xmlFile)
                .map(AIXMMultiParser::extractMemberElements)
                .orElse(Collections.emptyList())
                .stream()
                .map(AIXMMultiParser::parseElementComprehensively)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toUnmodifiableList());
    }

    private static Optional<Document> parseDocument(File f) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder db = dbf.newDocumentBuilder();
            return Optional.of(db.parse(f));
        } catch (Exception e) {
            System.err.println("Error parsing file " + f.getName() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private static List<Element> extractMemberElements(Document doc) {
        NodeList members = doc.getElementsByTagNameNS("*", "hasMember");
        return IntStream.range(0, members.getLength())
                .mapToObj(members::item)
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .flatMap(AIXMMultiParser::extractChildElements)
                .collect(Collectors.toUnmodifiableList());
    }

    private static Stream<Element> extractChildElements(Element member) {
        NodeList children = member.getChildNodes();
        return IntStream.range(0, children.getLength())
                .mapToObj(children::item)
                .filter(Element.class::isInstance)
                .map(Element.class::cast);
    }

    // Комплексний метод аналізу, який витягує всі можливі дані
    private static Optional<AIXMRecord> parseElementComprehensively(Element elem) {
        String tag = elem.getTagName();
        String id = elem.getAttribute("gml:id");

        if (tag == null || tag.trim().isEmpty()) {
            return Optional.empty();
        }

        // Вилучення типів даних з файлів
        String name = extractName(elem);
        String type = extractType(elem);
        String description = extractDescription(elem);
        Map<String, String> additionalData = extractAdditionalData(elem);
        Map<String, String> timeData = extractTimeData(elem);
        Map<String, String> geometryData = extractGeometryData(elem);
        Map<String, String> validationData = extractValidationData(elem);

        return Optional.of(new AIXMRecord(tag, id != null ? id : "",
                name, type, description, additionalData, timeData, geometryData, validationData));
    }

    //Покращене вилучення імен з більшою кількістю шаблонів
    //Добавлено для покращення парсингу
    private static String extractName(Element elem) {

        String[] namePatterns = {
                "name", "designator", "locationIndicatorICAO", "cityName", "txtName",
                "codeId", "identifier", "designatorPrefix", "designatorSecondLetter",
                "designatorNumber", "routeDesignator"
        };

        for (String pattern : namePatterns) {
            String result = getTextContent(elem, pattern);
            if (!result.isEmpty()) return result;
        }

        return "";
    }

    private static String extractType(Element elem) {
        String[] typePatterns = {
                "type", "usage", "category", "codeType", "codeCategory", "classification",
                "militaryUse", "interpretation", "ruleType", "serviceType"
        };

        for (String pattern : typePatterns) {
            String result = getTextContent(elem, pattern);
            if (!result.isEmpty()) return result;
        }

        return "";
    }

    private static String extractDescription(Element elem) {
        String[] descPatterns = {
                "annotation", "remark", "txtRmk", "txtDescr", "note", "comment"
        };

        for (String pattern : descPatterns) {
            String result = getTextContent(elem, pattern);
            if (!result.isEmpty()) {
                return result.length() > 200 ? result.substring(0, 200) + "..." : result;
            }
        }

        return "";
    }

    // Вилученняя даних повязаних з дата/час
    private static Map<String, String> extractTimeData(Element elem) {
        Map<String, String> timeData = new HashMap<>();

        String[] timePatterns = {
                "beginPosition", "endPosition", "validTime", "featureLifetime",
                "interpretation", "sequenceNumber", "correctionNumber"
        };

        for (String pattern : timePatterns) {
            String value = getTextContent(elem, pattern);
            if (!value.isEmpty()) {
                timeData.put(pattern, value);
            }
        }
        extractAttributes(elem, timeData, "indeterminatePosition");
        return timeData;
    }

    // Витяг даних повязаних з розташуванням
    private static Map<String, String> extractGeometryData(Element elem) {
        Map<String, String> geomData = new HashMap<>();

        String[] geomPatterns = {
                "pos", "posList", "coordinates", "Point", "LineString", "Polygon",
                "Surface", "Curve", "elevation", "valElev", "geodeticDatum",
                "horizontalAccuracy", "verticalAccuracy"
        };

        for (String pattern : geomPatterns) {
            String value = getTextContent(elem, pattern);
            if (!value.isEmpty()) {
                geomData.put(pattern, value);
            }
        }

        return geomData;
    }

    // витяг валідації та метаданих
    private static Map<String, String> extractValidationData(Element elem) {
        Map<String, String> validationData = new HashMap<>();

        String[] validationPatterns = {
                "source", "dataQuality", "accuracy", "resolution", "dataSource",
                "publishedDate", "effectiveDate", "responsibleParty"
        };

        for (String pattern : validationPatterns) {
            String value = getTextContent(elem, pattern);
            if (!value.isEmpty()) {
                validationData.put(pattern, value);
            }
        }

        return validationData;
    }

    // покращений витяг додаткових даних
    private static Map<String, String> extractAdditionalData(Element elem) {
        Map<String, String> data = new HashMap<>();

        // То для спец даних з файлів
        // там прікол що деякі файли містять унікальні рядки
        // вроді би всі витягнув
        String[] specificPatterns = {
                "frequency", "valFreq", "upperLimit", "lowerLimit", "uomDistVer",
                "codeDatum", "codeUnit", "codeDirection", "valBearing", "valDist",
                "codeDesig", "codeWorkHr", "codeLgt", "valLen", "valWid"
        };

        for (String pattern : specificPatterns) {
            String value = getTextContent(elem, pattern);
            if (!value.isEmpty()) {
                data.put(pattern, value);
            }
        }

        // Витяг женериків з нащадків даних
        extractAllChildrenData(elem, data);

        return data;
    }

    // витяг дати з елементів
    private static void extractAllChildrenData(Element parent, Map<String, String> data) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element childElem = (Element) child;
                String localName = childElem.getLocalName();
                String textContent = childElem.getTextContent();

                if (localName != null && textContent != null &&
                        !textContent.trim().isEmpty() && textContent.trim().length() < 100 &&
                        !textContent.contains("\n") && !data.containsKey(localName)) {
                    data.put(localName, textContent.trim());
                }
            }
        }
    }

    // витяг з спец іменем
    private static void extractAttributes(Element elem, Map<String, String> data, String attributeName) {
        NodeList allElements = elem.getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            if (element.hasAttribute(attributeName)) {
                data.put(attributeName, element.getAttribute(attributeName));
            }
        }
    }

    // Уніфікований метод вилучення текстового вмісту
    private static String getTextContent(Element parent, String tagName) {
        // Try direct child first
        NodeList directChildren = parent.getChildNodes();
        for (int i = 0; i < directChildren.getLength(); i++) {
            Node child = directChildren.item(i);
            if (child instanceof Element) {
                Element childElem = (Element) child;
                String localName = childElem.getLocalName();
                if (localName != null && localName.equals(tagName)) {
                    String textContent = childElem.getTextContent();
                    return textContent != null ? textContent.trim() : "";
                }
            }
        }

        // Через вкладений пощук
        NodeList allElements = parent.getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Node node = allElements.item(i);
            if (node instanceof Element) {
                Element childElem = (Element) node;
                String localName = childElem.getLocalName();
                if (localName != null && localName.equals(tagName)) {
                    String textContent = childElem.getTextContent();
                    return textContent != null ? textContent.trim() : "";
                }
            }
        }

        return "";
    }
}