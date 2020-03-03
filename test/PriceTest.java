import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PriceTest {
    // Создаем подкласс для тестов, т.к. реализовывать в исходном классе `equals` только для тестирования
    // семантически неверно
    static class PriceTestable extends Price implements Comparable<PriceTestable>, Cloneable {
        PriceTestable(long id, String productCode, int number, int depart,
                      @NotNull Date begin, @NotNull Date end, long value) {
            super(id, productCode, number, depart, begin, end, value);
        }

        @Override
        public boolean equals(Object obj) {
            if (getClass() != obj.getClass())
                return false;
            return compareTo((PriceTestable) obj) == 0;
        }

        @Override
        public int compareTo(@NotNull PriceTestable otherPrice) {
            return Comparator.comparing((PriceTestable p) -> p.productCode)
                    .thenComparingInt(p -> p.depart)
                    .thenComparingInt(p -> p.number)
                    .thenComparing(p -> p.begin)
                    .thenComparing(p -> p.end)
                    .thenComparingLong(p -> p.value).compare(this, otherPrice);
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
        }

        @Override
        protected Price copy() {
            try {
                return (Price) this.clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void unionPrices() throws Exception {
        var df = new SimpleDateFormat("dd.MM.yyyy");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));

        // A. Если товар еще не имеет цен, то новая цена просто добавляется к товару
        var currentPrices = new ArrayList<PriceTestable>();
        currentPrices.add(new PriceTestable(0, "product A", 1, 1, df.parse("01.01.2020"), df.parse("31.01.2020"), 50));
        var newPrices = new ArrayList<PriceTestable>();
        newPrices.add(new PriceTestable(0, "product B", 1, 1, df.parse("01.01.2020"), df.parse("31.01.2020"), 50));
        var expected = new ArrayList<PriceTestable>();
        expected.add(new PriceTestable(0, "product A", 1, 1, df.parse("01.01.2020"), df.parse("31.01.2020"), 50));
        expected.add(new PriceTestable(0, "product B", 1, 1, df.parse("01.01.2020"), df.parse("31.01.2020"), 50));
        var actual = (List<PriceTestable>) PriceTestable.unionPrices(currentPrices, newPrices);
        actual.sort(PriceTestable::compareTo); // Сортируем, т.к. порядок в результирующей коллекции не гарантирован
        assertIterableEquals(expected, actual, "По одной цене на два товара");

        // B. Если цены не пересекаются в отделах, то новая цена просто добавляется к товару
        newPrices = new ArrayList<>();
        newPrices.add(new PriceTestable(0, "product A", 1, 2, df.parse("10.01.2020"), df.parse("20.01.2020"), 60));
        expected = new ArrayList<>();
        expected.add(new PriceTestable(0, "product A", 1, 1, df.parse("01.01.2020"), df.parse("31.01.2020"), 50));
        expected.add(new PriceTestable(0, "product A", 1, 2, df.parse("10.01.2020"), df.parse("20.01.2020"), 60));
        actual = (List<PriceTestable>) PriceTestable.unionPrices(currentPrices, newPrices);
        actual.sort(PriceTestable::compareTo);
        assertIterableEquals(expected, actual, "По одной цене на один товар в двух отделах");

        // C. Если цены не пересекаются в номерах цен, то новая цена просто добавляется к товару
        newPrices = new ArrayList<>();
        newPrices.add(new PriceTestable(0, "product A", 2, 1, df.parse("10.01.2020"), df.parse("20.01.2020"), 60));
        expected = new ArrayList<>();
        expected.add(new PriceTestable(0, "product A", 1, 1, df.parse("01.01.2020"), df.parse("31.01.2020"), 50));
        expected.add(new PriceTestable(0, "product A", 2, 1, df.parse("10.01.2020"), df.parse("20.01.2020"), 60));
        actual = (List<PriceTestable>) PriceTestable.unionPrices(currentPrices, newPrices);
        actual.sort(PriceTestable::compareTo);
        assertIterableEquals(expected, actual, "По одной цене на два разных номера");

        // D. Если имеющиеся цены не пересекаются в периодах действия с новыми, то новые цены просто добавляются
        newPrices = new ArrayList<>();
        newPrices.add(new PriceTestable(0, "product A", 1, 1, df.parse("20.12.2019"), df.parse("01.01.2020"), 60));
        newPrices.add(new PriceTestable(0, "product A", 1, 1, df.parse("31.01.2020"), df.parse("10.02.2020"), 60));
        expected = new ArrayList<>();
        expected.add(new PriceTestable(0, "product A", 1, 1, df.parse("20.12.2019"), df.parse("01.01.2020"), 60));
        expected.add(new PriceTestable(0, "product A", 1, 1, df.parse("01.01.2020"), df.parse("31.01.2020"), 50));
        expected.add(new PriceTestable(0, "product A", 1, 1, df.parse("31.01.2020"), df.parse("10.02.2020"), 60));
        actual = (List<PriceTestable>) PriceTestable.unionPrices(currentPrices, newPrices);
        actual.sort(PriceTestable::compareTo);
        assertIterableEquals(expected, actual, "По одной цене на три разных периода");

        // E. Если имеющаяся цена пересекается в периоде действия с новой ценой, и значения цен одинаковы,
        // период действия имеющейся цены увеличивается согласно периоду новой цены
        newPrices = new ArrayList<>();
        newPrices.add(new PriceTestable(0, "product A", 1, 1, df.parse("30.01.2020"), df.parse("10.02.2020"), 50));
        expected = new ArrayList<>();
        expected.add(new PriceTestable(0, "product A", 1, 1, df.parse("01.01.2020"), df.parse("10.02.2020"), 50));
        actual = (List<PriceTestable>) PriceTestable.unionPrices(currentPrices, newPrices);
        actual.sort(PriceTestable::compareTo);
        assertIterableEquals(expected, actual, "Одна цена с расширенным периодом");

        // F. Пересечения в номерах цен и периодах действия:

        // Пример 1
        currentPrices = new ArrayList<>();
        currentPrices.add(new PriceTestable(0, "product A", 1, 1, df.parse("01.01.2020"), df.parse("31.01.2020"), 50));
        newPrices = new ArrayList<>();
        newPrices.add(new PriceTestable(0, "product A", 1, 1, df.parse("10.01.2020"), df.parse("20.01.2020"), 60));
        expected = new ArrayList<>() {{
            add(new PriceTestable(0, "product A", 1, 1, df.parse("01.01.2020"), df.parse("10.01.2020"), 50));
            add(new PriceTestable(0, "product A", 1, 1, df.parse("10.01.2020"), df.parse("20.01.2020"), 60));
            add(new PriceTestable(0, "product A", 1, 1, df.parse("20.01.2020"), df.parse("31.01.2020"), 50));
        }};
        actual = (List<PriceTestable>) PriceTestable.unionPrices(currentPrices, newPrices);
        actual.sort(PriceTestable::compareTo);
        assertArrayEquals(expected.toArray(), actual.toArray(),
                "Ожидается результат: [50 (с 01.01 по 10.01), 60 (с 10.01 по 20.01), 50 (с 20.01 по 31.01)]\n");

        // Пример 2
        currentPrices = new ArrayList<>();
        currentPrices.add(new PriceTestable(0, "product A", 1, 1, df.parse("01.01.2020"), df.parse("20.01.2020"), 100));
        currentPrices.add(new PriceTestable(0, "product A", 1, 1, df.parse("20.01.2020"), df.parse("31.01.2020"), 120));
        newPrices = new ArrayList<>();
        newPrices.add(new PriceTestable(0, "product A", 1, 1, df.parse("10.01.2020"), df.parse("23.01.2020"), 110));
        expected = new ArrayList<>() {{
            add(new PriceTestable(0, "product A", 1, 1, df.parse("01.01.2020"), df.parse("10.01.2020"), 100));
            add(new PriceTestable(0, "product A", 1, 1, df.parse("10.01.2020"), df.parse("23.01.2020"), 110));
            add(new PriceTestable(0, "product A", 1, 1, df.parse("23.01.2020"), df.parse("31.01.2020"), 120));
        }};
        actual = (List<PriceTestable>) PriceTestable.unionPrices(currentPrices, newPrices);
        actual.sort(PriceTestable::compareTo);
        assertArrayEquals(expected.toArray(), actual.toArray(),
                "Ожидается результат: [100 (с 01.01 по 10.01), 110 (с 10.01 по 23.01), 120 (с 23.01 по 31.01)]\n");

        // Пример 3
        currentPrices = new ArrayList<>();
        currentPrices.add(new PriceTestable(0, "product A", 1, 1, df.parse("01.01.2020"), df.parse("9.01.2020"), 80));
        currentPrices.add(new PriceTestable(0, "product A", 1, 1, df.parse("8.01.2020"), df.parse("18.01.2020"), 87));
        currentPrices.add(new PriceTestable(0, "product A", 1, 1, df.parse("18.01.2020"), df.parse("31.01.2020"), 90));
        newPrices = new ArrayList<>();
        newPrices.add(new PriceTestable(0, "product A", 1, 1, df.parse("05.01.2020"), df.parse("13.01.2020"), 80));
        newPrices.add(new PriceTestable(0, "product A", 1, 1, df.parse("13.01.2020"), df.parse("25.01.2020"), 85));
        expected = new ArrayList<>() {{
            add(new PriceTestable(0, "product A", 1, 1, df.parse("01.01.2020"), df.parse("13.01.2020"), 80));
            add(new PriceTestable(0, "product A", 1, 1, df.parse("13.01.2020"), df.parse("25.01.2020"), 85));
            add(new PriceTestable(0, "product A", 1, 1, df.parse("25.01.2020"), df.parse("31.01.2020"), 90));
        }};
        actual = (List<PriceTestable>) PriceTestable.unionPrices(currentPrices, newPrices);
        actual.sort(PriceTestable::compareTo);
        assertArrayEquals(expected.toArray(), actual.toArray(),
                "Ожидается результат: [80 (с 01.01 по 13.01), 85 (с 13.01 по 25.01), 90 (с 25.01 по 31.01)]\n");
    }
}