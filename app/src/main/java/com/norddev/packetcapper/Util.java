package com.norddev.packetcapper;

import java.util.Map;
import java.util.TreeMap;

public class Util {


    public static Map<Integer, Float> generateFrequencyRange(int startRate, int endRate, int step, float startFrequency, float endFrequency) {
        Map<Integer, Float> map = new TreeMap<>();
        float frequency = startFrequency;
        int numSteps = (endRate - startRate) / step;
        float frequencyStep = (endFrequency - startFrequency) / numSteps;
        for (int rate = startRate; rate <= endRate; rate += step) {
            map.put(rate, frequency);
            frequency += frequencyStep;
        }
        return map;
    }

    public static Integer getNearestKey(Map<Integer, Float> map, long target) {
        double minDiff = Double.MAX_VALUE;
        Integer nearest = null;
        for (Integer key : map.keySet()) {
            double diff = Math.abs(target - key);
            if (diff < minDiff) {
                nearest = key;
                minDiff = diff;
            }
        }
        return nearest;
    }

}
