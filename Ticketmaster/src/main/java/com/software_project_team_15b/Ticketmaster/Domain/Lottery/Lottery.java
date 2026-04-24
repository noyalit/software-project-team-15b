package com.software_project_team_15b.Ticketmaster.Domain.Lottery;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;


public class Lottery<T> {
    ConcurrentHashMap<Integer, T> lotteryMap;
    private final AtomicInteger nextNumber;

    public Lottery() {
        lotteryMap = new ConcurrentHashMap<>();
        nextNumber = new AtomicInteger(0);
    }

    public void add(T option) {
        int id = nextNumber.getAndIncrement();
        lotteryMap.put(id, option);
    }

    // Safe function to get a random value from the map, by ChatGPT
    @SuppressWarnings("unchecked")
    public T popRandom() {
        while (true) {
            Object[] entries = lotteryMap.entrySet().toArray();

            if (entries.length == 0) {
                return null;
            }

            int i = ThreadLocalRandom.current().nextInt(entries.length);
            var entry = (java.util.Map.Entry<Integer, T>) entries[i];

            T value = lotteryMap.remove(entry.getKey());

            if (value != null) {
                return value;
            }
        }
    }

    public HashSet<T> popRandom(int count) {
        HashSet<T> chosen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            T value = popRandom();
            if (value == null) {
                break;
            }
            chosen.add(value);
        }
        return chosen;
    }
}
