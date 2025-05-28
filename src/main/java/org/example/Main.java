package org.example;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Создаем цепочку обработчиков
        FileOperations fileOps = new FileLoggerDecorator(new FileManager());
        FileLoaderAdapter adapter = new FileLoaderAdapter(fileOps);

        while (true) {
            System.out.println("\nВыберите нужный вариант:");
            System.out.println("1. Сохранить в файл");
            System.out.println("2. Прочитать из файла");
            System.out.println("3. Рабочий каталог");
            System.out.println("4. Поиск файла");
            System.out.println("5. Выход");
            System.out.print("Выберите пункт меню: ");

            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("Неверный ввод. Пожалуйста, введите номер: ");
                continue;
            }

            switch (choice) {
                case 1:
                    saveFileOperation(scanner, adapter);
                    break;

                case 2:
                    readFileOperation(scanner, adapter);
                    break;

                case 3:
                    System.out.println("Текущий рабочий каталог: " + System.getProperty("user.dir"));
                    break;

                case 4:
                    searchFileOperation(scanner, adapter);
                    break;

                case 5:
                    System.out.println("Выход...");
                    scanner.close();
                    return;

                default:
                    System.out.println("Неверный выбор. Пожалуйста, попробуйте снова:");
            }
        }
    }

    private static void saveFileOperation(Scanner scanner, FileLoaderAdapter adapter) {
        System.out.print("Введите имя каталога (относительно ./files/): ");
        String dir = scanner.nextLine();
        System.out.print("Введите имя файла: ");
        String filename = scanner.nextLine();
        System.out.println("Введите содержимое файла (введите \"END\" в новой строке, чтобы завершить):");

        StringBuilder content = new StringBuilder();
        String line;
        while (!(line = scanner.nextLine()).equals("END")) {
            content.append(line).append("\n");
        }

        String result = adapter.processFile(filename, dir, content.toString());
        System.out.println("\n" + result);

        Path filePath = Paths.get("./files/" + dir + "/" + filename);
        if (Files.exists(filePath)) {
            System.out.println("Файл успешно создан по адресу: " + filePath.toAbsolutePath());
        } else {
            System.out.println("Предупреждение: Файл не был создан в ожидаемом расположении!");
        }
    }

    private static void readFileOperation(Scanner scanner, FileLoaderAdapter adapter) {
        System.out.print("Введите имя каталога (относительно ./files/): ");
        String readDir = scanner.nextLine();
        System.out.print("Введите имя файла: ");
        String readFilename = scanner.nextLine();

        String fileContent = adapter.loadFile(readFilename, readDir);
        System.out.println("\n" + fileContent);
    }

    private static void searchFileOperation(Scanner scanner, FileLoaderAdapter adapter) {
        System.out.print("Введите имя файла для поиска (можно часть имени): ");
        String filename = scanner.nextLine();
        System.out.print("Введите каталог для поиска (относительно ./files/, оставьте пустым для поиска во всех каталогах): ");
        String directory = scanner.nextLine();

        String searchResult = adapter.searchFile(filename, directory);
        System.out.println("\nРезультаты поиска:\n" + searchResult);
    }
}

interface FileOperations {
    long saveFile(String filename, String directory, String content) throws IOException;
    String readFile(String filename, String directory) throws IOException;
    boolean directoryExists(String directory);
    void createDirectory(String directory) throws IOException;
    Map<String, FileInfo> searchFiles(String filename, String directory) throws IOException;
}

class FileInfo {
    private final String content;
    private final Instant creationTime;
    private final long size;
    private final boolean isValid;

    public FileInfo(String content, Instant creationTime, long size, boolean isValid) {
        this.content = content;
        this.creationTime = creationTime;
        this.size = size;
        this.isValid = isValid;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public long getSize() {
        return size;
    }

    public boolean isValid() {
        return isValid;
    }
}

class FileManager implements FileOperations {
    private static final String BASE_DIR = "./files/";

    @Override
    public long saveFile(String filename, String directory, String content) throws IOException {
        validateInput(filename, directory, content);

        Path dirPath = Paths.get(BASE_DIR + directory);
        if (!Files.exists(dirPath)) {
            createDirectory(directory);
        }

        Path filePath = dirPath.resolve(filename);
        Files.write(filePath, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        return Files.size(filePath);
    }

    @Override
    public String readFile(String filename, String directory) throws IOException {
        validateInput(filename, directory, "dummy");

        Path filePath = Paths.get(BASE_DIR + directory, filename);
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("Файл не найден: " + filePath);
        }

        return new String(Files.readAllBytes(filePath));
    }

    @Override
    public boolean directoryExists(String directory) {
        return Files.exists(Paths.get(BASE_DIR + directory));
    }

    @Override
    public void createDirectory(String directory) throws IOException {
        Path dirPath = Paths.get(BASE_DIR + directory);
        Files.createDirectories(dirPath);
        System.out.println("Каталог создан по адресу: " + dirPath.toAbsolutePath());
    }

    @Override
    public Map<String, FileInfo> searchFiles(String filename, String directory) throws IOException {
        Map<String, FileInfo> foundFiles = new LinkedHashMap<>();
        Path searchPath = Paths.get(BASE_DIR + directory);

        if (!Files.exists(searchPath)) {
            return foundFiles;
        }

        Files.walk(searchPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().contains(filename))
                .forEach(path -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        String content = new String(Files.readAllBytes(path));
                        String relativePath = searchPath.relativize(path).toString();

                        FileInfo fileInfo = new FileInfo(
                                content,
                                attrs.creationTime().toInstant(),
                                Files.size(path),
                                true
                        );
                        foundFiles.put(relativePath, fileInfo);
                    } catch (IOException e) {
                        try {
                            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                            FileInfo fileInfo = new FileInfo(
                                    "Не удалось прочитать содержимое файла",
                                    attrs.creationTime().toInstant(),
                                    Files.size(path),
                                    false
                            );
                            foundFiles.put(searchPath.relativize(path).toString(), fileInfo);
                        } catch (IOException ex) {
                            // Пропускаем проблемные файлы
                        }
                    }
                });

        return foundFiles;
    }

    private void validateInput(String filename, String directory, String content) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя файла не может быть пустым");
        }
        if (directory == null || directory.trim().isEmpty()) {
            throw new IllegalArgumentException("Каталог не может быть пустым");
        }
        if (content == null) {
            throw new IllegalArgumentException("Содержимое не может быть пустым");
        }
    }
}

class FileLoggerDecorator implements FileOperations {
    private final FileOperations decorated;
    private final Map<String, String> fileTimestamps = new HashMap<>();

    public FileLoggerDecorator(FileOperations decorated) {
        this.decorated = decorated;
    }

    @Override
    public long saveFile(String filename, String directory, String content) throws IOException {
        long size = decorated.saveFile(filename, directory, content);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        fileTimestamps.put(getFileKey(filename, directory), timestamp);
        return size;
    }

    @Override
    public String readFile(String filename, String directory) throws IOException {
        String content = decorated.readFile(filename, directory);
        String timestamp = fileTimestamps.getOrDefault(getFileKey(filename, directory), "unknown");
        return String.format("Файл сохранен: %s\nСодержимое:\n%s", timestamp, content);
    }

    @Override
    public boolean directoryExists(String directory) {
        return decorated.directoryExists(directory);
    }

    @Override
    public void createDirectory(String directory) throws IOException {
        decorated.createDirectory(directory);
    }

    @Override
    public Map<String, FileInfo> searchFiles(String filename, String directory) throws IOException {
        return decorated.searchFiles(filename, directory);
    }

    private String getFileKey(String filename, String directory) {
        return directory + File.separator + filename;
    }
}

class FileLoaderAdapter {
    private final FileOperations fileOperations;

    public FileLoaderAdapter(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    public String processFile(String filename, String directory, String content) {
        try {
            if (!fileOperations.directoryExists(directory)) {
                fileOperations.createDirectory(directory);
            }

            long size = fileOperations.saveFile(filename, directory, content);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            return String.format("Файл успешно сохранен!\n" +
                            "Имя файла: %s\n" +
                            "Каталог: %s\n" +
                            "Размер: %d bytes\n" +
                            "Время: %s",
                    filename, directory, size, timestamp);
        } catch (IOException e) {
            return "Ошибка при сохранении файла: " + e.getMessage();
        }
    }

    public String loadFile(String filename, String directory) {
        try {
            if (!fileOperations.directoryExists(directory)) {
                return "Ошибка: Каталог '" + directory + "' не существует";
            }

            return fileOperations.readFile(filename, directory);
        } catch (IOException e) {
            return "Ошибка при чтении файла: " + e.getMessage();
        }
    }

    public String searchFile(String filename, String directory) {
        try {
            Map<String, FileInfo> foundFiles = fileOperations.searchFiles(filename, directory.isEmpty() ? "" : directory);

            if (foundFiles.isEmpty()) {
                return "Файлы не найдены.";
            }

            StringBuilder result = new StringBuilder();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

            for (Map.Entry<String, FileInfo> entry : foundFiles.entrySet()) {
                FileInfo fileInfo = entry.getValue();
                result.append("Файл: ").append(entry.getKey()).append("\n");
                result.append("Дата создания: ").append(formatter.format(fileInfo.getCreationTime())).append("\n");
                result.append("Размер: ").append(fileInfo.getSize()).append(" bytes\n");
                result.append("Валидность: ").append(fileInfo.isValid() ? "валиден" : "не валиден").append("\n");
                result.append("Содержимое:\n").append(fileInfo.getContent()).append("\n");
                result.append("----------------------------------------\n");
            }

            return result.toString();
        } catch (IOException e) {
            return "Ошибка при поиске файлов: " + e.getMessage();
        }
    }
}
