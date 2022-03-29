import com.opencsv.RFC4180Parser;
import com.opencsv.RFC4180ParserBuilder;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    static boolean isNumber;

    static String file;
    static String applicationYmlFile = "application.yml";

    static List<List<List<Long>>> indexList = new ArrayList<>();
    static byte arrLength;

    public static void main(String[] args) {

        if (args.length == 2) {
            file = checkFileName(args);
        } else {
            file = "airports.csv";
        }

        // Проверяем параметры запуска на наличие параметров
        int column = checkColumnSettings(args);

        // Смотрим какая выбранная колонка, и делаем вывод о данных, хранящихся в ней
        isNumber = (column == 0 || column >= 6) && column <= 9;

        // Индексация файла перед передачей управления пользователю
        preStart(column);

        System.out.println("" +
                "Программа по поиску в CSV файле. \n" +
                "Данная программа разрешает искать пустые строки! \n" +
                "Выбрана колонка под номером = " + (column + 1) + "\n" +
                "Для поиска введите строку ниже и нажмите enter \n" +
                "Автор: Арсений Валеев, тестовое задание для Renue. \n" +
                "Для выхода введите \"exit\" \n");

        while (true) {
            System.out.print("Введите строку: ");
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine().toLowerCase();

            if (input.equals("exit")) {
                break;
            }

            // Проверка строки с числами на буквенное содержание
            if (isNumber) {
                if (input.matches("\\w+")) {
                    System.out.println("<-----ERROR----->");
                    System.out.println("Некорректные данные! \nСтобец не содержир буквы!");
                    System.out.println("<--------------->");
                    continue;
                }
            }

            long start = System.currentTimeMillis();

            int pos0, pos1;
            List<String[]> output;

            if (input.isEmpty())
            {
                output = filterData(indexList.get(arrLength - 1).get(arrLength - 1), input, column);
            }
            else if (input.length() == 1)
            {
                pos0 = charToArrayIndex(input, 0, isNumber);
                List<Long> mergeList = new ArrayList<>();
                // В случае если введен только 1 символ, идет слияние всех внутренних списков в общий
                for (List<Long> arr : indexList.get(pos0)) {
                    mergeList.addAll(arr);
                }
                output = filterData(mergeList, input, column);
            }
            else
            {
                pos0 = charToArrayIndex(input, 0, isNumber);
                pos1 = charToArrayIndex(input, 1, isNumber);
                output = filterData(indexList.get(pos0).get(pos1), input, column);
            }

            output = sortData(output, column);

            long end = System.currentTimeMillis();

            showResult(output, start, end, column);
        }
    }

    public static String checkFileName(String[] args) {
        String str = args[0];
        if (str.endsWith(".csv") && str.length() > 4) {
            return str;
        }
        throw new IllegalArgumentException("Название файла не корректно или таковой отсутствует");
    }

    public static int checkColumnSettings(String[] args) {
        int column;
        // Отсутствие параметров запуска, берем настройки по умолчанию
        if (args.length < 1) {
            column = selectColumnFromYamlFile(applicationYmlFile);
        } else {
            // Иначе берем из настроек
            column = Integer.parseInt(args[1]) - 1;
        }

        if (column < 0 || column > 13) {
            System.out.println("Не корректное значение номера столбца! (Меньше 1 или больше 14)! \n Берется значение по умолчанию!");
            column = selectColumnFromYamlFile(applicationYmlFile);
            System.out.println("Значение переменной \"column\" установлено на " + column);
        }
        return column;
    }

    public static int selectColumnFromYamlFile(String file) {
        int column;
        try(InputStream inputStream = Main.class.getClassLoader().getResourceAsStream(file)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);
            column = (Integer) data.get("defaultColumn");
        } catch(FileNotFoundException e) {
            throw new UncheckedIOException("Файл с параметрами по умолчанию не найден!", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Ошибка при работе с YML файлом", e);
        }

        if (column < 1) {
            throw new IllegalArgumentException("Ошибка в параметрах по умолчанию!");
        }

        return column - 1;
    }

    /*
        Суть этого метода в индексации файла, перед передачей управления пользователю.
        Я индексирую колонку по первым ДВУМ! символам.
        Например: если строка начинается на буквы AB, то будет записано положение данной строки в виде
        Long: bytePosition в массив(список) с индексом [0][1], т.к. исчесление индекса идет следующим образом.
        А=0, B=1 ... Z=25 и все что не входит в этот диапазон записывается в резервную ячейку 26.

        Тем самым я не храню файл в памяти, и это позволяет очень быстро находить нужные строки,
        в случае если введено более 1 символа.
     */
    public static void preStart(int column) {

        /*
            В зависимотсти от колонки идет выбор размерности списка.
            Для буквенных это 26 ячеек для каждой буквы и 27 резервная, для символов выходящих за пределы латиницы
            Для числовых это 10 ячеек для каждого числа и 11 резервная, для знака "-", "." и иных непредвиденных символов
         */
        if (!isNumber)
            arrLength = 27;
        else
            arrLength = 11;


        for (int i = 0; i < arrLength; i++) {
            indexList.add(new ArrayList<>());
            for (int j = 0; j < arrLength; j++)
                indexList.get(i).add(new ArrayList<>());
        }

        try(BufferedReader reader = new BufferedReader(new FileReader (file))) {

            //CSVParser csvParser = new CSVParser();
            // Данный парсер, в отличии от предыдущего, не пропускает символ "\", из-за чего программа работает корректно
            RFC4180Parser rfc4180Parser = new RFC4180ParserBuilder().build();

            long bytePosition = 0;
            int pos0, pos1;
            String str;
            while ((str = reader.readLine()) != null) {

                String[] parse = rfc4180Parser.parseLine(str);

                // Проверка на пустую ячейку, записываем в резервную последнюю ячейку
                if (parse[column].isEmpty()) {
                    pos0 = arrLength - 1;
                    pos1 = pos0;
                } else {
                    pos0 = charToArrayIndex(parse[column], 0, isNumber);

                    if (parse[column].length() < 2)
                        pos1 = arrLength - 1;
                    else
                        pos1 = charToArrayIndex(parse[column], 1, isNumber);
                }

                indexList.get(pos0).get(pos1).add(bytePosition);
                bytePosition += str.getBytes().length + 1;
            }

        } catch (FileNotFoundException e) {
            throw new UncheckedIOException("Файл " + file + " в папке с .jar файлом не найден!", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Ошибка при работе с " + file + " файлом", e);
        }
    }

    public static int charToArrayIndex(String str, int charAt, boolean isNumber) {
        int pos;
        if (!isNumber) {
            pos = str.toLowerCase().charAt(charAt) - 'a';
            if (pos > 25 || pos < 0) pos = 26;
        } else {
            pos = str.toLowerCase().charAt(charAt) - '1';
            if (pos > 9 || pos < 0) pos = 10;
        }
        return pos;
    }

    public static List<String[]> filterData(List<Long> list, String input, int column) {

        List<String[]> strings = new ArrayList<>();

        try(RandomAccessFile raf = new RandomAccessFile ( file, "r")) {

            RFC4180Parser rfc4180Parser = new RFC4180ParserBuilder().build();

            String str;
            String[] parseString;

            for (Long aLong : list) {
                raf.seek(aLong);
                str = raf.readLine();

                parseString = rfc4180Parser.parseLine(str);

                if (input.isEmpty() && input.equals(parseString[column])) {
                    strings.add(parseString);
                }

                if (!input.isEmpty() && parseString[column].toLowerCase().startsWith(input)) {
                    strings.add(parseString);
                }
            }

        } catch (IOException  e) {
            throw new UncheckedIOException("Ошибка при фильтрации данных из " + file + " файла", e);
        }
        return strings;
    }

    public static List<String[]> sortData(List<String[]> list, int column) {
        list = list.stream().sorted(Comparator.comparing(a -> a[column])).collect(Collectors.toList());
        return list;
    }

    public static void showResult(List<String[]> output, long start, long end, int column) {
        for (String[] strings : output) {
            System.out.print(strings[column] + " - [ ");
            for (int i = 0; i < strings.length; i++) {
                if (i != 0 && i != column) System.out.print(" | ");
                if (i != column) System.out.print(strings[i]);
            }
            System.out.println(" ]");
        }

        System.out.println("Совпадений: " + output.size());
        System.out.println("Время поиска и сортировки: " + (end - start) + " мс");
    }
}
