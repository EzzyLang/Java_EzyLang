package io.github._3xhaust.interpreter;

import java.util.ArrayList;
import java.util.HashMap;

public class Environment {
    final ArrayList<HashMap<String, Object>> variableScopes = new ArrayList<>();
    final ArrayList<HashMap<String, Object>> constantScopes = new ArrayList<>();
    final ArrayList<HashMap<String, Object>> constraintScopes = new ArrayList<>();

    public Environment() {
        variableScopes.add(new HashMap<>());
        constantScopes.add(new HashMap<>());
        constraintScopes.add(new HashMap<>());
    }

    public void enterScope() {
        variableScopes.add(new HashMap<>());
        constantScopes.add(new HashMap<>());
        constraintScopes.add(new HashMap<>());
    }

    public void exitScope() {
        if (!variableScopes.isEmpty()) variableScopes.remove(variableScopes.size() - 1);
        if (!constantScopes.isEmpty()) constantScopes.remove(constantScopes.size() - 1);
        if (!constraintScopes.isEmpty()) constraintScopes.remove(constraintScopes.size() - 1);
    }

    public Object findVariable(String name) {
        HashMap<String, Object> top = variableScopes.get(variableScopes.size() - 1);
        Object val = top.get(name);
        if (val != null || top.containsKey(name)) return val;
        for (int i = variableScopes.size() - 2; i >= 0; i--) {
            HashMap<String, Object> scope = variableScopes.get(i);
            val = scope.get(name);
            if (val != null || scope.containsKey(name)) return val;
        }
        return null;
    }

    public Object findConstant(String name) {
        HashMap<String, Object> top = constantScopes.get(constantScopes.size() - 1);
        Object val = top.get(name);
        if (val != null || top.containsKey(name)) return val;
        for (int i = constantScopes.size() - 2; i >= 0; i--) {
            HashMap<String, Object> scope = constantScopes.get(i);
            val = scope.get(name);
            if (val != null || scope.containsKey(name)) return val;
        }
        return null;
    }

    public void setVariable(String name, Object value) {
        variableScopes.get(variableScopes.size() - 1).put(name, value);
    }

    public void setConstant(String name, Object value) {
        constantScopes.get(constantScopes.size() - 1).put(name, value);
    }

    public boolean hasVariableInCurrentScope(String name) {
        return variableScopes.get(variableScopes.size() - 1).containsKey(name);
    }

    public boolean hasConstantInCurrentScope(String name) {
        return constantScopes.get(constantScopes.size() - 1).containsKey(name);
    }

    public Object findConstraint(String name) {
        for (int i = constraintScopes.size() - 1; i >= 0; i--) {
            if (constraintScopes.get(i).containsKey(name)) {
                return constraintScopes.get(i).get(name);
            }
        }
        return null;
    }

    public void setConstraint(String name, Object value) {
        constraintScopes.get(constraintScopes.size() - 1).put(name, value);
    }

    public void updateVariable(String name, Object value) {
        for (int i = variableScopes.size() - 1; i >= 0; i--) {
            if (variableScopes.get(i).containsKey(name)) {
                variableScopes.get(i).put(name, value);
                return;
            }
        }
    }

    public void removeVariable(String name) {
        variableScopes.get(variableScopes.size() - 1).remove(name);
    }

    public HashMap<String, Object> getTopVariableScope() {
        return variableScopes.get(variableScopes.size() - 1);
    }

    public HashMap<String, Object> getTopConstantScope() {
        return constantScopes.get(constantScopes.size() - 1);
    }

    public HashMap<String, Object> getGlobalVariableScope() {
        return variableScopes.get(0);
    }

    public HashMap<String, Object> getGlobalConstantScope() {
        return constantScopes.get(0);
    }
}
