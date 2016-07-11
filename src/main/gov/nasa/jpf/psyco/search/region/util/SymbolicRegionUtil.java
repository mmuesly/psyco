/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.nasa.jpf.psyco.search.region.util;

import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.expressions.Negation;
import gov.nasa.jpf.constraints.expressions.Quantifier;
import gov.nasa.jpf.constraints.expressions.QuantifierExpression;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import gov.nasa.jpf.psyco.search.collections.NameMap;
import gov.nasa.jpf.psyco.search.region.SymbolicEntry;
import gov.nasa.jpf.psyco.search.region.SymbolicRegion;
import gov.nasa.jpf.psyco.search.region.SymbolicState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author mmuesly
 */
public class SymbolicRegionUtil implements RegionUtil<SymbolicRegion>{
  private long unique;
  ConstraintSolver solver;
  private final Logger logger;
  public SymbolicRegionUtil(ConstraintSolver solver){
    this.solver = solver;
    this.unique = 1L;
    logger = Logger.getLogger("psyco");
  }
  @Override
  public SymbolicRegion disjunction(SymbolicRegion regionA, SymbolicRegion regionB) {
    SymbolicRegion disjunctedRegion = new SymbolicRegion();
    disjunctedRegion.putAll(regionA);
    for(String key: regionB.keySet()){
      //This assumes, that state names are unique to work!
      if(disjunctedRegion.containsKey(key)){
        continue;
      }
      disjunctedRegion.put(key, regionB.get(key));
    }
    return disjunctedRegion;
  }

  @Override
  public SymbolicRegion conjunction(SymbolicRegion regionA,
          SymbolicRegion regionB) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public SymbolicRegion difference(SymbolicRegion outterRegion, SymbolicRegion excludedRegion) {
    return difference(outterRegion, excludedRegion, this.solver);
  }

  @Override
  public SymbolicRegion difference(SymbolicRegion outterRegion, SymbolicRegion excludedRegion, ConstraintSolver solver) {
    SymbolicRegion result = new SymbolicRegion();
    Expression notRegion = null;
    if(null == excludedRegion || excludedRegion.isEmpty()){
      return outterRegion;
    }
    Set<SymbolicState> toExclude = new HashSet();
    for(SymbolicState state: excludedRegion.values()){
      toExclude.add(state);
    }
    logger.finest("gov.nasa.jpf.psyco.search.region."
            + "util.SymbolicRegionUtil.difference()");
    //logger.finest("notRegion: " + notRegion.toString());
    for(String key : outterRegion.keySet()){
        Expression excludedRegionExpr = convertSetToExpression(toExclude);
        Set<Variable<?>> stateVariables = convertToVariableSet(excludedRegion);
        notRegion = new Negation(excludedRegionExpr);
        notRegion = bindParameters(notRegion, stateVariables, Quantifier.FORALL);
        SymbolicState state = outterRegion.get(key);
        Expression stateRegion = state.toExpression();
        Set<Variable<?>> newStateVariables = convertToVariableSet(state);
        newStateVariables.addAll(stateVariables);
        stateRegion =
                bindParameters(stateRegion,
                        newStateVariables, Quantifier.EXISTS);
        Expression testDiffState = ExpressionUtil.and(stateRegion, notRegion);
        long start = System.currentTimeMillis();
        Valuation val = new Valuation();
//        ConstraintSolver.Result rs = solver.isSatisfiable(testDiffState);
        ConstraintSolver.Result rs = solver.solve(testDiffState, val);
        long stop = System.currentTimeMillis();
        logger.finest("Test diff state: " + testDiffState);
        logger.finer("Time needed for difference: " 
                + Long.toString(stop - start) + " in Millis");
        if(rs == ConstraintSolver.Result.SAT){
          result.put(key, state);
          toExclude.add(state);
          logger.finer("excludedSize: " + toExclude.size());
          logger.finest("RES SAT: " + ExpressionUtil.valuationToExpression(val).toString());
        }
        else{
          logger.finer("result: " + rs);
        }
    }
    logger.finest("Difference Result" + result.toString());
    return result;
  }

  @Override
  public SymbolicRegion exists(SymbolicRegion aRegion, Set<Variable<?>> subsetOfVariables) {
    SymbolicRegion existingRegion = new SymbolicRegion();
    for(String key: aRegion.keySet()){
      SymbolicState state = aRegion.get(key);
      //SymbolicState newState = new SymbolicState();
      for(SymbolicEntry entry: state){
        if(!subsetOfVariables.contains(entry.getVariable())){
          existingRegion.put(key, state);
          break;
          //continue;
        }
        //newState.add(entry);
      }
//      if(!newState.isEmpty()){
//        existingRegion.put(key, newState);
//      }
    }
    return existingRegion;
  }

  @Override
  public Set<Variable<?>> convertToVariableSet(SymbolicRegion region) {
    Set<Variable<?>> resultingSet = new HashSet<>();
    for(SymbolicState state: region.values()){
      for(SymbolicEntry entry: state){
        resultingSet.add(entry.getVariable());
      }
    }
    return resultingSet;
  }

  public Set<Variable<?>> convertToVariableSet(SymbolicState state) {
    Set<Variable<?>> resultingSet = new HashSet<>();
    for(SymbolicEntry entry: state){
      resultingSet.add(entry.getVariable());
    }
    return resultingSet;
  }
  
  @Override
  public SymbolicRegion rename(SymbolicRegion region,
          List<Variable<?>> primeNames, List<Variable<?>> variableNames) {
      SymbolicRegion resultingRegion = new SymbolicRegion();
    for(String key: region.keySet()){
      SymbolicState state = region.get(key);
      SymbolicState renamedState = renameState(state, primeNames, variableNames);
      resultingRegion.put(key, renamedState);
    }
    return resultingRegion;
  }
  
  private SymbolicState renameState(SymbolicState state, 
          List<Variable<?>> primeNames, List<Variable<?>> variableNames ){
    SymbolicState renamedState = state;
    System.out.println("gov.nasa.jpf.psyco.search.region.util.SymbolicRegionUtil.renameState()");
    System.out.println("stateToRename: " );
    for(SymbolicEntry entry: state){
      System.out.println("Var: " + entry.getVariable() + " : " + entry.getValue());
    }
    Set<Variable<?>> variablesInTheState = 
            ExpressionUtil.freeVariables(state.toExpression());
    logger.finest("gov.nasa.jpf.psyco.search.region"
            + ".util.SymbolicRegionUtil.renameState()");
    logger.finest("State bevor rename: " + state.toExpression().toString());
    for(int i = 0; i < primeNames.size(); i++){
        Variable primeName = primeNames.get(i);
        Variable variableName = variableNames.get(i);
        renamedState = 
                renameAllVariableEntrys(renamedState, primeName, variableName);
        variablesInTheState.remove(variableName);
        variablesInTheState.remove(primeName);
    }
    for(Variable var: variablesInTheState){
      if(!var.getName().startsWith("uVarReplacement")){
        String newParameterName = getUniqueParameterName(var);
        Variable newParameter = 
                new Variable(var.getType(), newParameterName);
        //logger.finest("gov.nasa.jpf.psyco.search.region"
        //        + ".util.SymbolicRegionUtil.renameState()");
        //logger.finest(newParameterName);
        renamedState = 
                renameParameterInEntrys(renamedState, var, newParameter);
      }
    }
    
    logger.finest(renamedState.toExpression().toString());
    return renamedState;
  }
  private SymbolicState renameAllVariableEntrys(
          SymbolicState oldState, Variable primeName, Variable varibaleName){
    SymbolicState renamedState = new SymbolicState();
    NameMap renameVariable = new NameMap();
    renameVariable.mapNames(varibaleName.getName(), getUniqueName());
    logger.finest("renameMap: " + renameVariable.toString());
    for(SymbolicEntry entry: oldState){
      Expression value = entry.getValue();
      value = ExpressionUtil.renameVars(value, renameVariable);
      if(entry.getVariable().equals(primeName)){
        NameMap rename = new NameMap();
        rename.mapNames(primeName.getName(), varibaleName.getName());
        logger.finest("renameMap: " + rename.toString());
        value = ExpressionUtil.renameVars(value, rename);
        SymbolicEntry newEntry = new SymbolicEntry(varibaleName, value);
        renamedState.add(newEntry);
      }else{
        entry.setValue(value);
        renamedState.add(entry);
      }
    }
    return renamedState;
  }

  @Override
  public SymbolicRegion createRegion() {
    return new SymbolicRegion();
  }
  
  private String getUniqueName(){
    String uniqueName = "uVarReplacement_" + unique;
    ++unique;
    return uniqueName;
  }
  
  private String getUniqueParameterName(Variable parameter){
    String uniqueName = "p" + parameter.getName() + "_" + unique;
    ++unique;
    return uniqueName;
  }
  
  private Expression bindParameters(Expression region,
          Set<Variable<?>> stateVariables, Quantifier quantifier){
        Set<Variable<?>> freeVars = ExpressionUtil.freeVariables(region);
    ArrayList<Variable<?>> bound = new ArrayList<>();
    for(Variable var: freeVars){
      if(!stateVariables.contains(var) 
              && !var.getName().startsWith("uVarReplacement")){
        logger.finest("gov.nasa.jpf.psyco.search.region"
                + ".util.SymbolicRegionUtil.bindParameters()");
        logger.finest(var.getName());
        bound.add(var);
      }
    }
    if(!bound.isEmpty()){
      region = new QuantifierExpression(quantifier, bound, region);
    }
    return region;
  }

  private SymbolicState renameParameterInEntrys(SymbolicState renamedState,
          Variable var, Variable newParameter) {
    SymbolicState resultState = new SymbolicState();
    NameMap rename = new NameMap();
    rename.mapNames(var.getName(), newParameter.getName());
    for(SymbolicEntry entry: renamedState){
      Expression valueExpression = entry.getValue();
      valueExpression = ExpressionUtil.renameVars(valueExpression, rename);
      resultState.add(new SymbolicEntry(entry.getVariable(), valueExpression));
    }
    return resultState;
  }
  
  private Expression convertSetToExpression(Set<SymbolicState> states){
    Expression expr = null;
    for(SymbolicState state: states){
      Expression stateExpr = state.toExpression();
      expr = expr==null? stateExpr : ExpressionUtil.or(expr, stateExpr);
    }
    return expr;
  }
}
