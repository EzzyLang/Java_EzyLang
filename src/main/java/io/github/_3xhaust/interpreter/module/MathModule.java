package io.github._3xhaust.interpreter.module;

import java.util.HashMap;
import java.util.Map;

public class MathModule {
    public static void registerConstants(Map<String, Object> constants) {
        constants.put("PI", Math.PI);
        constants.put("E", Math.E);
    }

    public static void register(Map<String, NativeFunction> nativeFunctions) {
        nativeFunctions.put("sqrt", args -> Math.sqrt((Double) args.get(0)));
        nativeFunctions.put("abs", args -> Math.abs((Double) args.get(0)));
        nativeFunctions.put("pow", args -> Math.pow((Double) args.get(0), (Double) args.get(1)));
        nativeFunctions.put("min", args -> Math.min((Double) args.get(0), (Double) args.get(1)));
        nativeFunctions.put("max", args -> Math.max((Double) args.get(0), (Double) args.get(1)));
        nativeFunctions.put("floor", args -> Math.floor((Double) args.get(0)));
        nativeFunctions.put("ceil", args -> Math.ceil((Double) args.get(0)));
        nativeFunctions.put("round", args -> (double) Math.round((Double) args.get(0)));
        nativeFunctions.put("sin", args -> Math.sin((Double) args.get(0)));
        nativeFunctions.put("cos", args -> Math.cos((Double) args.get(0)));
        nativeFunctions.put("tan", args -> Math.tan((Double) args.get(0)));
        nativeFunctions.put("asin", args -> Math.asin((Double) args.get(0)));
        nativeFunctions.put("acos", args -> Math.acos((Double) args.get(0)));
        nativeFunctions.put("atan", args -> Math.atan((Double) args.get(0)));
        nativeFunctions.put("log", args -> Math.log((Double) args.get(0)));
        nativeFunctions.put("log10", args -> Math.log10((Double) args.get(0)));
        nativeFunctions.put("random", args -> Math.random());
        nativeFunctions.put("toRadians", args -> Math.toRadians((Double) args.get(0)));
        nativeFunctions.put("toDegrees", args -> Math.toDegrees((Double) args.get(0)));
    }
}
