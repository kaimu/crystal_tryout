import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Цена товара
 */
class Price {
    final long id;
    final String productCode;
    final int number;
    final int depart;
    // В реальной жизни, верятно, потребовался бы публичный getter для `begin` и `end`
    protected Date begin;
    protected Date end;
    final long value;

    /**
     * @param id          идентификатор в БД
     * @param productCode код товара
     * @param number      номер цены
     * @param depart      номер отдела
     * @param begin       начало действия
     * @param end         конец действия
     * @param value       значение цены в копейках
     */
    Price(long id, String productCode, int number, int depart, @NotNull Date begin, @NotNull Date end, long value) {
        this.id = id;
        this.productCode = productCode;
        this.number = number;
        this.depart = depart;
        this.begin = begin;
        this.end = end;
        this.value = value;
    }

    /**
     * Вкупе с `Cloneable` при переопределении позволяет создавать копии дочерним классам, сохраняя тип
     */
    protected Price copy() {
        return new Price(id, productCode, number, depart, begin, end, value);
    }

    /**
     * Метод объединения имеющихся цен
     *
     * @param currentPrices коллекция имеющихся цен
     * @param newPrices     коллекция новых цен
     * @return коллекция объединенных цен
     */
    @NotNull
    static Collection<? extends Price> unionPrices(@NotNull Collection<? extends Price> currentPrices,
                                                   @NotNull Collection<? extends Price> newPrices) {
        // Для линейности поиска внутри старых цен, преобразуем коллекцию в HashMap
        // (по коду товара -> номеру отдела -> номеру цены)
        var result = pricesToHashMap(currentPrices);

        for (Price newPrice : newPrices) {

            // Если товар еще не имеет цен,
            if (!result.containsKey(newPrice.productCode)) {
                // то новая цена просто добавляется к товару
                var pricesWithinNumber = new HashMap<Integer, ArrayList<Price>>();
                var department = new HashMap<Integer, HashMap<Integer, ArrayList<Price>>>();
                pricesWithinNumber.put(newPrice.number, new ArrayList<>() {{
                    add(newPrice);
                }});
                department.put(newPrice.depart, pricesWithinNumber);
                result.put(newPrice.productCode, department);
                continue;
            }

            // Если цены не пересекаются в отделах,
            var product = result.get(newPrice.productCode);
            if (!product.containsKey(newPrice.depart)) {
                // то новая цена просто добавляется к товару
                var pricesWithinNumber = new HashMap<Integer, ArrayList<Price>>();
                pricesWithinNumber.put(newPrice.number, new ArrayList<>() {{
                    add(newPrice);
                }});
                product.put(newPrice.depart, pricesWithinNumber);
                continue;
            }

            // Если цены не пересекаются в номерах цен
            var department = product.get(newPrice.depart);
            if (!department.containsKey(newPrice.number)) {
                // то новая цена просто добавляется к товару
                department.put(newPrice.number, new ArrayList<>() {{
                    add(newPrice);
                }});
                continue;
            }

            var pricesWithinNumber = department.get(newPrice.depart);
            var pricesToAdd = new ArrayList<Price>();
            pricesToAdd.add(newPrice);
            for (Price existingPrice : pricesWithinNumber) {
                if (newPrice.begin.after(existingPrice.end) || newPrice.begin.equals(existingPrice.end)
                        || newPrice.end.before(existingPrice.begin) || newPrice.end.equals(existingPrice.begin)) {
                    // Если цены не пересекаются в периодах действия, переходим к след. имеющейся цене
                    continue;
                }
                // Если имеющаяся цена пересекается в периоде действия с новой ценой, и значения цен одинаковы,
                // период действия имеющейся цены увеличивается согласно периоду новой цены
                if (existingPrice.value == newPrice.value) {
                    pricesToAdd.clear();
                    existingPrice.begin =
                            newPrice.begin.before(existingPrice.begin) ? newPrice.begin : existingPrice.begin;
                    existingPrice.end =
                            newPrice.end.after(existingPrice.end) ? newPrice.end : existingPrice.end;
                    continue;
                }
                // Если новая цена "наложилась" внутрь существующей цены,
                if (newPrice.begin.after(existingPrice.begin) && newPrice.end.before(existingPrice.end)) {
                    // то нужно добавить еще одну суффиксную цену
                    Price suffixPrice;
                    suffixPrice = existingPrice.copy();
                    suffixPrice.begin = newPrice.end;
                    suffixPrice.end = existingPrice.end;
                    pricesToAdd.add(suffixPrice);
                }
                // Если значения цен отличаются, период действия старой цены уменьшается согласно периоду новой цены:
                if (newPrice.begin.after(existingPrice.begin) && newPrice.begin.before(existingPrice.end)) {
                    // новая цена начинается внутри существующей, срезаем конец существующей
                    existingPrice.end = newPrice.begin;
                } else if (newPrice.end.after(existingPrice.begin)) {
                    // новая цена заканчивается внутри существующей, срезаем начало существующей
                    existingPrice.begin = newPrice.end;
                }
            }
            pricesWithinNumber.addAll(pricesToAdd);
            // В процессе наложения цен, какая-то из сущестующих могла получить 0 или отрицательную продолжительность
            pricesWithinNumber.removeIf(p -> p.begin.after(p.end) || p.begin.equals(p.end));
        }

        return flatten(result);
    }


    // Возвращает HashMap для быстрого поиска текущих цен по коду товара -> номеру отдела -> номеру цены
    private static HashMap<String, HashMap<Integer, HashMap<Integer, ArrayList<Price>>>> pricesToHashMap
    (Collection<? extends Price> currentPrices) {
        var result = new HashMap<String, HashMap<Integer, HashMap<Integer, ArrayList<Price>>>>();
        for (Price item : currentPrices) {
            if (!result.containsKey(item.productCode)) {
                // Добавляем код товара
                result.put(item.productCode, new HashMap<>());
            }
            var perDepart = result.get(item.productCode);
            if (!perDepart.containsKey(item.depart)) {
                // Добавляем номер отдела
                perDepart.put(item.depart, new HashMap<>());
            }
            var perNumber = perDepart.get(item.depart);
            if (!perNumber.containsKey(item.number)) {
                // Добавляем номер цены
                perNumber.put(item.number, new ArrayList<>());
            }
            perNumber.get(item.number).add(item);
        }
        return result;
    }

    // Собирает HashMap цен обратно в плоский список
    private static Collection<? extends Price> flatten
    (HashMap<String, HashMap<Integer, HashMap<Integer, ArrayList<Price>>>> hashMap) {
        var result = new ArrayList<Price>();
        for (var perProduct : hashMap.values()) {
            for (var perDepart : perProduct.values()) {
                for (var perPriceNumber : perDepart.values()) {
                    result.addAll(perPriceNumber);
                }
            }
        }
        return result;
    }
}
