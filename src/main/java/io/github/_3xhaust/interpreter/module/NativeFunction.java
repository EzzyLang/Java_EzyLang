package io.github._3xhaust.interpreter.module;

import java.util.List;

import io.github._3xhaust.ezylang.exception.ParseException;

@FunctionalInterface
public interface NativeFunction {
    Object execute(List<Object> args) throws ParseException;
}
