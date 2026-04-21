package com.software_project_team_15b.Ticketmaster.Domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;


public class Lottery<T> {
    HashMap<Integer, T> lotteryMap;
    int nextNumber;

    public Lottery() {
        lotteryMap = new HashMap<>();
        nextNumber = 0;
    }

    public void add(T option) {
        lotteryMap.put(nextNumber, option);
        nextNumber++;
    }

    public void remove(T option) {
        lotteryMap.values().remove(option);
    }

    public T getRandom() {
        int randomNumber = (int) (Math.random() * lotteryMap.size());
        while (!lotteryMap.containsKey(randomNumber)) {
            randomNumber = (randomNumber + 1) % lotteryMap.size();
        }
        return lotteryMap.get(randomNumber);
    }

    public Set<T> getRandom(int count) {
        Set<T> chosen = new HashSet<T>();
        for (int i = 0; i < count; i++) {
            chosen.add(getRandom());
        }
        return chosen;
    }
}
